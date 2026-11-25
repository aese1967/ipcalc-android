package com.aese.ipcalc.scan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aese.ipcalc.core.Ipv4
import com.aese.ipcalc.core.Ipv4Network
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

data class LocalNetwork(
    val interfaceName: String?,
    val transport: String,
    val ipv4: Ipv4Network?,
    val gateway: String?,
    val dns: List<String>,
    val ipv6: List<String>,
)

object NetInfo {
    fun read(context: Context): LocalNetwork {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val lp = net?.let { cm.getLinkProperties(it) }
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> "Sin conexión"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Otra"
        }
        var v4: Ipv4Network? = null
        val v6 = mutableListOf<String>()
        lp?.linkAddresses?.forEach { la ->
            when (val a = la.address) {
                is Inet4Address -> if (v4 == null) {
                    Ipv4.parse(a.hostAddress ?: "")?.let { v4 = Ipv4Network(it, la.prefixLength) }
                }
                is Inet6Address -> v6 += "${a.hostAddress?.substringBefore('%')}/${la.prefixLength}"
            }
        }
        if (v4 == null) v4 = fallbackFromInterfaces()
        val gw = lp?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val dns = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
        return LocalNetwork(lp?.interfaceName, transport, v4, gw, dns, v6)
    }

    private fun fallbackFromInterfaces(): Ipv4Network? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .firstOrNull { it.address is Inet4Address }
            ?.let { ia -> Ipv4.parse(ia.address.hostAddress ?: "")?.let { Ipv4Network(it, ia.networkPrefixLength.toInt()) } }
    } catch (e: Exception) {
        null
    }
}
