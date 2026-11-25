package com.aese.ipcalc.scan

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

/** Un equipo que respondió a algún protocolo de descubrimiento. */
data class Sighting(
    val ip: String,
    val protocol: String,
    val name: String? = null,
    val detail: String? = null,
    val mac: String? = null,
)

/**
 * Descubrimiento por multicast/broadcast. Estos mensajes llegan a todo el segmento
 * físico (misma Wi-Fi / mismo switch) aunque el equipo tenga una IP de otra red,
 * y la respuesta revela su IP real.
 */
object Discovery {

    private const val MDNS_ADDR = "224.0.0.251"
    private const val SSDP_ADDR = "239.255.255.250"

    val mdnsServiceTypes = listOf(
        "_services._dns-sd._udp.local", "_http._tcp.local", "_https._tcp.local",
        "_workstation._tcp.local", "_device-info._tcp.local", "_googlecast._tcp.local",
        "_airplay._tcp.local", "_raop._tcp.local", "_ipp._tcp.local", "_ipps._tcp.local",
        "_printer._tcp.local", "_pdl-datastream._tcp.local", "_scanner._tcp.local",
        "_smb._tcp.local", "_afpovertcp._tcp.local", "_ssh._tcp.local", "_sftp-ssh._tcp.local",
        "_rtsp._tcp.local", "_hap._tcp.local", "_homekit._tcp.local", "_spotify-connect._tcp.local",
        "_companion-link._tcp.local", "_sonos._tcp.local", "_amzn-wplay._tcp.local",
        "_androidtvremote2._tcp.local", "_esphomelib._tcp.local", "_hue._tcp.local",
        "_mqtt._tcp.local", "_nvstream._tcp.local", "_adb-tls-connect._tcp.local",
    )

    // ---------- mDNS / Bonjour ----------

    fun mdns(iface: NetworkInterface? = null, listenMs: Int = 4000): List<Sighting> {
        val out = mutableListOf<Sighting>()
        val group = InetAddress.getByName(MDNS_ADDR)
        val sock: DatagramSocket = try {
            MulticastSocket(null as java.net.SocketAddress?).apply {
                reuseAddress = true
                bind(InetSocketAddress(5353))
                if (iface != null) {
                    networkInterface = iface
                    joinGroup(InetSocketAddress(group, 5353), iface)
                } else {
                    @Suppress("DEPRECATION")
                    joinGroup(group)
                }
                timeToLive = 255
            }
        } catch (e: Exception) {
            // Si 5353 está ocupado, consulta "legacy" con puerto efímero (respuesta unicast)
            DatagramSocket()
        }
        sock.use { s ->
            s.soTimeout = 300
            // Se envía en dos tandas para no superar el tamaño de un datagrama
            mdnsServiceTypes.chunked(10).forEach { chunk ->
                val q = Dns.query(chunk, type = 12)
                s.send(DatagramPacket(q, q.size, group, 5353))
            }
            val deadline = System.currentTimeMillis() + listenMs
            val buf = ByteArray(9000)
            val seenTypes = HashSet<String>()
            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    s.receive(pkt)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val src = pkt.address.hostAddress?.substringBefore('%') ?: continue
                val msg = Dns.parse(buf, pkt.length) ?: continue
                if (!msg.isResponse) continue
                val records = msg.records
                val host = records.firstOrNull { it.type == 1 || it.type == 33 }?.let {
                    if (it.type == 33) it.target else it.name
                }?.removeSuffix(".local")
                val services = records.filter { it.type == 12 }.mapNotNull { r ->
                    r.target?.let { t -> if (r.name == "_services._dns-sd._udp.local") null else t.removeSuffix(".local") }
                }
                // Tipos de servicio anunciados: preguntar por ellos también
                val newTypes = records.filter { it.type == 12 && it.name == "_services._dns-sd._udp.local" }
                    .mapNotNull { it.target }.filter { seenTypes.add(it) }
                if (newTypes.isNotEmpty()) {
                    try {
                        val q = Dns.query(newTypes.take(10), 12)
                        s.send(DatagramPacket(q, q.size, group, 5353))
                    } catch (_: Exception) {
                    }
                }
                out += Sighting(src, "mDNS", host, services.distinct().take(4).joinToString(", ").ifEmpty { null })
                // Registros A pueden anunciar otras IPs (p. ej. de otra subred)
                records.filter { it.type == 1 && it.ipv4 != null && it.ipv4 != src }.forEach {
                    out += Sighting(it.ipv4!!, "mDNS", it.name.removeSuffix(".local"), "anunciado por $src")
                }
            }
        }
        return out
    }

    /** Socket UDP efímero; con [iface] fija la interfaz de salida del multicast. */
    private fun senderSocket(iface: NetworkInterface?): DatagramSocket =
        if (iface == null) DatagramSocket()
        else MulticastSocket().apply { networkInterface = iface; timeToLive = 4 }

    // ---------- SSDP / UPnP ----------

    fun ssdp(iface: NetworkInterface? = null, listenMs: Int = 3500, fetchDescriptions: Boolean = true): List<Sighting> {
        val msg = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $SSDP_ADDR:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n\r\n"
            ).toByteArray()
        val found = LinkedHashMap<String, Pair<String?, String?>>() // ip -> (server, location)
        senderSocket(iface).use { s ->
            s.soTimeout = 300
            s.broadcast = true
            val group = InetAddress.getByName(SSDP_ADDR)
            repeat(2) { s.send(DatagramPacket(msg, msg.size, group, 1900)) }
            try {
                s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 1900))
            } catch (_: Exception) {
            }
            val deadline = System.currentTimeMillis() + listenMs
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    s.receive(pkt)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val src = pkt.address.hostAddress ?: continue
                val text = String(buf, 0, pkt.length, Charsets.UTF_8)
                val headers = text.lines().mapNotNull { l ->
                    val i = l.indexOf(':'); if (i > 0) l.substring(0, i).trim().uppercase() to l.substring(i + 1).trim() else null
                }.toMap()
                val prev = found[src]
                found[src] = (prev?.first ?: headers["SERVER"]) to (prev?.second ?: headers["LOCATION"])
            }
        }
        return found.map { (ip, info) ->
            val (server, location) = info
            var name: String? = null
            var detail = server
            if (fetchDescriptions && location != null) {
                describeUpnp(location)?.let { (n, d) -> name = n; detail = d ?: detail }
            }
            Sighting(ip, "UPnP", name, detail)
        }
    }

    /** Lee el XML de descripción UPnP y devuelve (nombre, fabricante/modelo). */
    private fun describeUpnp(location: String): Pair<String?, String?>? = try {
        val conn = URL(location).openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        val xml = conn.inputStream.bufferedReader().use { it.readText().take(20000) }
        conn.disconnect()
        fun tag(t: String) = Regex("<$t>([^<]*)</$t>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)?.trim()
        val name = tag("friendlyName")
        val detail = listOfNotNull(tag("manufacturer"), tag("modelName")).joinToString(" ").ifBlank { null }
        name to detail
    } catch (e: Exception) {
        null
    }

    // ---------- WS-Discovery (cámaras ONVIF, impresoras, Windows) ----------

    fun wsDiscovery(iface: NetworkInterface? = null, listenMs: Int = 3000): List<Sighting> {
        val id = UUID.randomUUID()
        val probe = """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:wsd="http://schemas.xmlsoap.org/ws/2005/04/discovery">
<soap:Header><wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To><wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action><wsa:MessageID>urn:uuid:$id</wsa:MessageID></soap:Header>
<soap:Body><wsd:Probe/></soap:Body></soap:Envelope>""".toByteArray()
        val out = LinkedHashMap<String, Sighting>()
        senderSocket(iface).use { s ->
            s.soTimeout = 300
            val group = InetAddress.getByName(SSDP_ADDR)
            repeat(2) { s.send(DatagramPacket(probe, probe.size, group, 3702)) }
            val deadline = System.currentTimeMillis() + listenMs
            val buf = ByteArray(16000)
            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    s.receive(pkt)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val src = pkt.address.hostAddress ?: continue
                val xml = String(buf, 0, pkt.length, Charsets.UTF_8)
                val types = Regex("<[^>]*Types>([^<]*)</[^>]*Types>").find(xml)?.groupValues?.get(1)
                    ?.split(' ')?.map { it.substringAfter(':') }?.filter { it.isNotBlank() }?.joinToString(", ")
                val xaddrs = Regex("<[^>]*XAddrs>([^<]*)</[^>]*XAddrs>").find(xml)?.groupValues?.get(1)?.trim()
                val name = when {
                    types?.contains("NetworkVideoTransmitter") == true -> "Cámara ONVIF"
                    types?.contains("Print", ignoreCase = true) == true -> "Impresora"
                    types?.contains("Computer") == true -> "Equipo Windows"
                    else -> null
                }
                out[src] = Sighting(src, "WS-Discovery", name, listOfNotNull(types, xaddrs).joinToString(" · ").ifBlank { null })
                // XAddrs puede contener IPs distintas a la de origen
                xaddrs?.split(' ')?.forEach { url ->
                    val h = Regex("://([0-9.]+)").find(url)?.groupValues?.get(1)
                    if (h != null && h != src && !out.containsKey(h)) out[h] = Sighting(h, "WS-Discovery", name, "anunciado por $src")
                }
            }
        }
        return out.values.toList()
    }

    // ---------- NetBIOS por broadcast ----------

    fun netBiosBroadcast(extraTargets: List<String> = emptyList(), listenMs: Int = 2500): List<Sighting> {
        val out = LinkedHashMap<String, Sighting>()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 300
            val req = NetBios.nbstatRequest()
            (listOf("255.255.255.255") + extraTargets).distinct().forEach { t ->
                try {
                    s.send(DatagramPacket(req, req.size, InetAddress.getByName(t), 137))
                } catch (_: Exception) {
                }
            }
            val deadline = System.currentTimeMillis() + listenMs
            val buf = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    s.receive(pkt)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val src = pkt.address.hostAddress ?: continue
                val info = NetBios.parseNbstat(buf, pkt.length)
                out[src] = Sighting(src, "NetBIOS", info?.name, info?.group?.let { "Grupo: $it" }, info?.mac)
            }
        }
        return out.values.toList()
    }

    // ---------- IPs por defecto habituales ----------

    /** Direcciones de fábrica típicas de routers, puntos de acceso, cámaras y equipos industriales. */
    val defaultIps: List<Pair<String, String>> = listOf(
        "192.168.0.1" to "Router (TP-Link, D-Link, Netgear)",
        "192.168.1.1" to "Router (Linksys, ASUS, operadoras)",
        "192.168.1.254" to "Router de operadora",
        "192.168.0.254" to "Router / AP",
        "192.168.2.1" to "Router (Belkin, Sitecom)",
        "192.168.3.1" to "Router Huawei",
        "192.168.8.1" to "Módem 4G Huawei",
        "192.168.10.1" to "Router / AP",
        "192.168.11.1" to "Router Buffalo",
        "192.168.31.1" to "Router Xiaomi",
        "192.168.50.1" to "Router ASUS",
        "192.168.86.1" to "Google Wifi / Nest",
        "192.168.88.1" to "MikroTik",
        "192.168.100.1" to "Módem de cable / ONT",
        "192.168.178.1" to "FRITZ!Box",
        "192.168.1.64" to "Cámara Hikvision",
        "192.168.1.108" to "Cámara Dahua",
        "192.168.1.10" to "Cámara / NVR (varios)",
        "192.168.1.100" to "Impresora / equipo (varios)",
        "192.168.1.20" to "Ubiquiti UniFi (de fábrica)",
        "192.168.0.10" to "PLC / equipos industriales",
        "192.168.0.100" to "Cámara IP (varios)",
        "192.168.0.120" to "Cámara IP (varios)",
        "192.168.0.20" to "Equipos de fábrica (varios)",
        "192.168.4.1" to "ESP8266 / ESP32 en modo AP",
        "192.168.42.1" to "Hotspot / Raspberry Pi AP",
        "192.168.49.1" to "Wi-Fi Direct Android",
        "10.0.0.1" to "Router (Xfinity, Apple)",
        "10.0.0.138" to "Router de operadora",
        "10.0.1.1" to "Apple AirPort",
        "10.1.1.1" to "Router (varios)",
        "10.10.10.1" to "Router / equipos (varios)",
        "10.90.90.90" to "Switch D-Link",
        "172.16.0.1" to "Router (varios)",
        "169.254.1.1" to "Enlace local / APIPA",
        "192.168.254.254" to "Router (varios)",
        "192.168.123.254" to "Router (varios)",
    ).distinctBy { it.first }

    fun probeDefaultIp(ip: String, timeoutMs: Int): Double? {
        // TCP primero: una IP de otra subred normalmente solo se alcanza por un puerto abierto
        for (port in intArrayOf(80, 443, 23, 22, 8080, 554)) {
            val r = HostProbe.tcp(ip, port, timeoutMs)
            if (r.alive) return r.millis.toDouble()
        }
        return HostProbe.reachable(ip, timeoutMs)?.toDouble()
    }
}

/** Mínimo codificador/decodificador DNS para mDNS. */
object Dns {
    data class Record(val name: String, val type: Int, val target: String?, val ipv4: String?)
    data class Message(val isResponse: Boolean, val records: List<Record>)

    fun query(names: List<String>, type: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0, 0, names.size.toByte(), 0, 0, 0, 0, 0, 0))
        for (n in names) {
            for (label in n.split('.')) {
                val b = label.toByteArray()
                out.write(b.size); out.write(b)
            }
            out.write(0)
            out.write(type shr 8); out.write(type and 0xFF)
            out.write(0x00); out.write(0x01) // clase IN, respuesta multicast
        }
        return out.toByteArray()
    }

    fun parse(buf: ByteArray, len: Int): Message? {
        return try {
            if (len < 12) return null
            fun u16(o: Int) = ((buf[o].toInt() and 0xFF) shl 8) or (buf[o + 1].toInt() and 0xFF)
            val flags = u16(2)
            val qd = u16(4)
            val total = u16(6) + u16(8) + u16(10)
            var off = 12
            repeat(qd) {
                off = readName(buf, len, off).second + 4
            }
            val records = mutableListOf<Record>()
            repeat(total) {
                if (off >= len) return@repeat
                val (name, next) = readName(buf, len, off)
                off = next
                if (off + 10 > len) return@repeat
                val type = u16(off)
                val rdlen = u16(off + 8)
                val rd = off + 10
                var target: String? = null
                var ip: String? = null
                when (type) {
                    1 -> if (rdlen == 4) ip = (0 until 4).joinToString(".") { (buf[rd + it].toInt() and 0xFF).toString() }
                    12, 5 -> target = readName(buf, len, rd).first
                    33 -> target = readName(buf, len, rd + 6).first
                }
                records += Record(name, type, target, ip)
                off = rd + rdlen
            }
            Message(flags and 0x8000 != 0, records)
        } catch (e: Exception) {
            null
        }
    }

    private fun readName(buf: ByteArray, len: Int, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var off = start
        var end = -1
        var jumps = 0
        while (off < len) {
            val l = buf[off].toInt() and 0xFF
            when {
                l == 0 -> { off++; break }
                l and 0xC0 == 0xC0 -> {
                    if (end < 0) end = off + 2
                    off = ((l and 0x3F) shl 8) or (buf[off + 1].toInt() and 0xFF)
                    if (++jumps > 20) break
                }
                else -> {
                    labels += String(buf, off + 1, l, Charsets.UTF_8)
                    off += l + 1
                }
            }
        }
        return labels.joinToString(".") to (if (end >= 0) end else off)
    }
}
