package com.aese.ipcalc.desktop

import com.aese.ipcalc.core.Ipv4
import com.aese.ipcalc.core.Ipv4Network
import com.aese.ipcalc.scan.LocalNetwork
import com.aese.ipcalc.scan.OS
import com.aese.ipcalc.scan.Platform
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/** Implementación para Windows (y Linux/macOS) basada en java.net y comandos del sistema. */
class DesktopPlatform : Platform {

    @Volatile
    private var currentIface: NetworkInterface? = null

    override fun multicastInterface(): NetworkInterface? = currentIface

    override fun readLocal(): LocalNetwork? {
        val (gateway, gwIfaceIp) = defaultRoute()
        val candidates = NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .filter { it.interfaceAddresses.any { a -> a.address is Inet4Address } }
        val virtualWords = listOf("virtual", "vmware", "hyper-v", "vethernet", "wsl", "docker", "loopback", "vpn", "tap", "bluetooth")
        fun isVirtual(n: NetworkInterface) = virtualWords.any { n.displayName.contains(it, ignoreCase = true) || n.name.contains(it, ignoreCase = true) }

        val chosen = candidates.firstOrNull { n -> gwIfaceIp != null && n.interfaceAddresses.any { it.address.hostAddress == gwIfaceIp } }
            ?: candidates.firstOrNull { n -> !isVirtual(n) && n.interfaceAddresses.any { it.address.isSiteLocalAddress } }
            ?: candidates.firstOrNull()
            ?: return null
        currentIface = chosen

        val v4 = chosen.interfaceAddresses.firstOrNull { it.address is Inet4Address }?.let { ia ->
            Ipv4.parse(ia.address.hostAddress)?.let { Ipv4Network(it, ia.networkPrefixLength.toInt()) }
        }
        val v6 = chosen.interfaceAddresses.filter { it.address is Inet6Address }
            .map { "${it.address.hostAddress.substringBefore('%')}/${it.networkPrefixLength}" }
        val dn = chosen.displayName ?: chosen.name
        val transport = when {
            listOf("wi-fi", "wifi", "wireless", "wlan", "802.11").any { dn.contains(it, ignoreCase = true) } -> "Wi-Fi"
            else -> "Ethernet"
        }
        return LocalNetwork(dn, transport, v4, gateway, dnsServers(), v6)
    }

    /** Devuelve (puerta de enlace, IP de la interfaz) de la ruta por defecto. */
    private fun defaultRoute(): Pair<String?, String?> = try {
        if (OS.isWindows) {
            val out = run("route", "print", "-4", "0.0.0.0")
            // "0.0.0.0  0.0.0.0  192.168.1.1  192.168.1.50  25"
            out.lines().map { it.trim().split(Regex("\\s+")) }
                .filter { it.size >= 5 && it[0] == "0.0.0.0" && it[1] == "0.0.0.0" && Ipv4.parse(it[2]) != null }
                .minByOrNull { it[4].toIntOrNull() ?: 9999 }
                ?.let { it[2] to it[3] } ?: (null to null)
        } else {
            val out = run("ip", "-4", "route", "show", "default")
            val gw = Regex("via (\\S+)").find(out)?.groupValues?.get(1)
            val src = Regex("src (\\S+)").find(out)?.groupValues?.get(1)
            gw to src
        }
    } catch (e: Exception) {
        null to null
    }

    private fun dnsServers(): List<String> = try {
        if (OS.isWindows) {
            // Salida independiente del idioma: solo direcciones
            val out = run("powershell", "-NoProfile", "-Command",
                "(Get-DnsClientServerAddress -AddressFamily IPv4 | Where-Object {\$_.ServerAddresses} | Select-Object -ExpandProperty ServerAddresses) -join ' '")
            out.trim().split(Regex("\\s+")).filter { Ipv4.parse(it) != null }.distinct().take(4)
        } else {
            java.io.File("/etc/resolv.conf").readLines()
                .mapNotNull { Regex("^nameserver\\s+(\\S+)").find(it)?.groupValues?.get(1) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader(OS.consoleCharset).readText()
        p.waitFor(8, TimeUnit.SECONDS)
        return out
    }
}
