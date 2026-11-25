package com.aese.ipcalc.scan

import java.io.File
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Pruebas de bajo nivel contra un host. Todas son bloqueantes: llamar desde Dispatchers.IO. */
object HostProbe {

    /** Puertos usados para detectar equipos que no responden a ICMP. */
    val DISCOVERY_PORTS = intArrayOf(80, 443, 22, 445, 139, 53, 8080, 62078, 5353, 7000, 554, 8443)

    data class TcpResult(val open: Boolean, val alive: Boolean, val millis: Long)

    /**
     * Intenta conectar por TCP. Un "connection refused" rápido también demuestra
     * que el equipo existe (respondió con RST).
     */
    fun tcp(ip: String, port: Int, timeoutMs: Int): TcpResult {
        val start = System.nanoTime()
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            TcpResult(open = true, alive = true, millis = elapsed(start))
        } catch (e: ConnectException) {
            val refused = e.message?.contains("ECONNREFUSED") == true ||
                e.message?.contains("refused", ignoreCase = true) == true
            TcpResult(open = false, alive = refused, millis = elapsed(start))
        } catch (e: SocketTimeoutException) {
            TcpResult(false, false, elapsed(start))
        } catch (e: Exception) {
            TcpResult(false, false, elapsed(start))
        }
    }

    /** InetAddress.isReachable (ICMP si el sistema lo permite, si no TCP eco). */
    fun reachable(ip: String, timeoutMs: Int): Long? {
        val start = System.nanoTime()
        return try {
            if (InetAddress.getByName(ip).isReachable(timeoutMs)) elapsed(start) else null
        } catch (e: Exception) {
            null
        }
    }

    /** Ejecuta /system/bin/ping una vez y devuelve el tiempo en ms si responde. */
    fun systemPing(ip: String, timeoutSec: Int = 1): Double? {
        return try {
            val p = ProcessBuilder("ping", "-c", "1", "-W", timeoutSec.toString(), ip)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor((timeoutSec + 2).toLong(), TimeUnit.SECONDS)) {
                p.destroy(); return null
            }
            if (p.exitValue() != 0) return null
            Regex("time[=<]([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            null
        }
    }

    /** Comprobación combinada de presencia. Devuelve latencia en ms o null. */
    fun isAlive(ip: String, timeoutMs: Int): Double? {
        systemPing(ip, maxOf(1, timeoutMs / 1000))?.let { return it }
        for (port in DISCOVERY_PORTS.take(6)) {
            val r = tcp(ip, port, timeoutMs / 2)
            if (r.alive) return r.millis.toDouble()
        }
        return null
    }

    fun reverseDns(ip: String): String? = try {
        val name = InetAddress.getByName(ip).canonicalHostName
        if (name == ip) null else name
    } catch (e: Exception) {
        null
    }

    data class NetBiosInfo(val name: String?, val group: String?, val mac: String?)

    /** Consulta NBSTAT (UDP 137): devuelve nombre NetBIOS y MAC de equipos Windows/Samba. */
    fun netBios(ip: String, timeoutMs: Int = 800): NetBiosInfo? {
        return try {
            DatagramSocket().use { sock ->
                sock.soTimeout = timeoutMs
                val req = NetBios.nbstatRequest()
                sock.send(DatagramPacket(req, req.size, InetAddress.getByName(ip), 137))
                val buf = ByteArray(1024)
                val pkt = DatagramPacket(buf, buf.size)
                sock.receive(pkt)
                NetBios.parseNbstat(buf, pkt.length)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Tabla ARP del sistema. En Android 10+ suele estar bloqueada para apps,
     * por eso se intenta de dos formas y se ignoran los errores.
     */
    fun arpTable(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            File("/proc/net/arp").readLines().drop(1).forEach { line ->
                val cols = line.split(Regex("\\s+"))
                if (cols.size >= 4 && cols[3] != "00:00:00:00:00:00") map[cols[0]] = cols[3].uppercase()
            }
        } catch (_: Exception) {
        }
        if (map.isEmpty()) {
            try {
                val p = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor(2, TimeUnit.SECONDS)
                out.lines().forEach { line ->
                    val m = Regex("^(\\S+) .*lladdr ([0-9a-fA-F:]{17})").find(line)
                    if (m != null) map[m.groupValues[1]] = m.groupValues[2].uppercase()
                }
            } catch (_: Exception) {
            }
        }
        return map
    }

    private fun elapsed(startNs: Long) = (System.nanoTime() - startNs) / 1_000_000
}

object NetBios {
    fun nbstatRequest(): ByteArray {
        val id = Random.nextInt(0, 0xFFFF)
        val header = byteArrayOf(
            (id shr 8).toByte(), id.toByte(), 0x00, 0x00, // flags
            0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        // Nombre "*" relleno con ceros, codificado en "first-level encoding"
        val raw = ByteArray(16).also { it[0] = '*'.code.toByte() }
        val encoded = ByteArray(34)
        encoded[0] = 32
        for (i in 0 until 16) {
            val b = raw[i].toInt() and 0xFF
            encoded[1 + i * 2] = ('A'.code + (b shr 4)).toByte()
            encoded[2 + i * 2] = ('A'.code + (b and 0x0F)).toByte()
        }
        encoded[33] = 0
        val tail = byteArrayOf(0x00, 0x21, 0x00, 0x01) // NBSTAT, IN
        return header + encoded + tail
    }

    fun parseNbstat(buf: ByteArray, len: Int): HostProbe.NetBiosInfo? {
        // 12 cabecera + 34 nombre + 2 tipo + 2 clase + 4 ttl + 2 rdlength
        val base = 56
        if (len <= base) return null
        val count = buf[base].toInt() and 0xFF
        var name: String? = null
        var group: String? = null
        for (i in 0 until count) {
            val off = base + 1 + i * 18
            if (off + 18 > len) break
            val n = String(buf, off, 15, Charsets.US_ASCII).trim()
            val suffix = buf[off + 15].toInt() and 0xFF
            val flags = ((buf[off + 16].toInt() and 0xFF) shl 8) or (buf[off + 17].toInt() and 0xFF)
            val isGroup = flags and 0x8000 != 0
            if (suffix == 0x00 && !isGroup && name == null) name = n
            if (suffix == 0x00 && isGroup && group == null) group = n
        }
        val macOff = base + 1 + count * 18
        val mac = if (macOff + 6 <= len) {
            (0 until 6).joinToString(":") { "%02X".format(buf[macOff + it].toInt() and 0xFF) }
                .takeIf { it != "00:00:00:00:00:00" }
        } else null
        return HostProbe.NetBiosInfo(name, group, mac)
    }
}
