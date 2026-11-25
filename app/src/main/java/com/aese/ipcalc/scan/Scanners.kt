package com.aese.ipcalc.scan

import com.aese.ipcalc.core.Ipv4
import com.aese.ipcalc.core.Ipv4Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class Device(
    val ip: String,
    val latencyMs: Double? = null,
    val hostname: String? = null,
    val mac: String? = null,
    val vendor: String? = null,
    val sources: Set<String> = emptySet(),
    val details: List<String> = emptyList(),
    val isSelf: Boolean = false,
    val isGateway: Boolean = false,
    val inLocalSubnet: Boolean = true,
) {
    val sortKey: Long get() = Ipv4.parse(ip) ?: Long.MAX_VALUE

    fun merge(s: Sighting): Device = copy(
        hostname = hostname ?: s.name,
        mac = mac ?: s.mac,
        vendor = vendor ?: Oui.vendor(s.mac),
        sources = sources + s.protocol,
        details = (details + listOfNotNull(s.detail)).distinct(),
    )
}

object LanScanner {
    /** Límite de direcciones a barrer para no tardar una eternidad. */
    const val MAX_HOSTS = 4096

    /** Rango a escanear: la subred local, o un /24 alrededor de la IP si es demasiado grande. */
    fun targetRange(net: Ipv4Network): Ipv4Network =
        if (net.totalAddresses > MAX_HOSTS) Ipv4Network(net.address, 24) else net

    suspend fun sweep(
        range: Ipv4Network,
        timeoutMs: Int,
        parallelism: Int = 96,
        onProgress: (done: Int, total: Int) -> Unit,
        onFound: (Device) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val first = range.firstHost
        val last = range.lastHost
        val total = (last - first + 1).toInt()
        val done = AtomicInteger(0)
        val sem = Semaphore(parallelism)
        coroutineScope {
            (first..last).map { ipValue ->
                async {
                    sem.withPermit {
                        ensureActive()
                        val ip = Ipv4.format(ipValue)
                        val lat = HostProbe.isAlive(ip, timeoutMs)
                        if (lat != null) onFound(Device(ip, latencyMs = lat, sources = setOf("Ping/TCP")))
                        onProgress(done.incrementAndGet(), total)
                    }
                }
            }.awaitAll()
        }
    }

    /** Completa nombre, MAC y fabricante de los equipos encontrados. */
    suspend fun enrich(devices: List<Device>, onUpdate: (Device) -> Unit) = withContext(Dispatchers.IO) {
        val arp = HostProbe.arpTable()
        val sem = Semaphore(24)
        coroutineScope {
            devices.map { d ->
                async {
                    sem.withPermit {
                        val nb = HostProbe.netBios(d.ip)
                        val name = d.hostname ?: nb?.name ?: HostProbe.reverseDns(d.ip)
                        val mac = d.mac ?: arp[d.ip] ?: nb?.mac
                        onUpdate(
                            d.copy(
                                hostname = name,
                                mac = mac,
                                vendor = d.vendor ?: Oui.vendor(mac),
                                details = (d.details + listOfNotNull(nb?.group?.let { "Grupo: $it" })).distinct(),
                            )
                        )
                    }
                }
            }.awaitAll()
        }
    }
}

data class PortResult(val port: Int, val service: String, val millis: Long)

object PortScanner {
    suspend fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        parallelism: Int = 128,
        onProgress: (Int, Int) -> Unit,
        onOpen: (PortResult) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val done = AtomicInteger(0)
        val sem = Semaphore(parallelism)
        coroutineScope {
            ports.map { port ->
                async {
                    sem.withPermit {
                        ensureActive()
                        val r = HostProbe.tcp(host, port, timeoutMs)
                        if (r.open) onOpen(PortResult(port, Services.name(port), r.millis))
                        onProgress(done.incrementAndGet(), ports.size)
                    }
                }
            }.awaitAll()
        }
    }
}
