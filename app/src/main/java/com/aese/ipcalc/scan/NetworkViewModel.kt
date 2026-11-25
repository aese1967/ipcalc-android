package com.aese.ipcalc.scan

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aese.ipcalc.core.Ipv4
import com.aese.ipcalc.core.Ipv4Network
import com.aese.ipcalc.core.Ipv4Parser
import com.aese.ipcalc.core.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class NetworkViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- red local ----------
    var local by mutableStateOf<LocalNetwork?>(null)
        private set

    fun refreshLocal() {
        local = try {
            NetInfo.read(getApplication())
        } catch (e: Exception) {
            null
        }
    }

    // ---------- escáner de la LAN + descubrimiento ----------
    val devices = mutableStateMapOf<String, Device>()
    var scanRunning by mutableStateOf(false)
        private set
    var scanPhase by mutableStateOf("")
        private set
    var scanProgress by mutableStateOf(0f)
        private set
    var scanError by mutableStateOf<String?>(null)
        private set
    private var scanJob: Job? = null

    fun sortedDevices(): List<Device> = devices.values.sortedBy { it.sortKey }

    /**
     * [rangeText] vacío = subred local. Si [discovery] es true, además se usan
     * mDNS, UPnP, WS-Discovery y NetBIOS para encontrar equipos de otras redes.
     */
    fun startScan(rangeText: String, timeoutMs: Int, sweep: Boolean, discovery: Boolean) {
        if (scanRunning) return
        refreshLocal()
        scanError = null
        val localNet = local?.ipv4
        val range: Ipv4Network? = if (rangeText.isBlank()) {
            localNet?.let { LanScanner.targetRange(it) }
        } else {
            when (val r = Ipv4Parser.parse(if (rangeText.contains('/')) rangeText else "$rangeText/24")) {
                is ParseResult.Ok -> r.value
                is ParseResult.Error -> { scanError = r.message; return }
            }
        }
        if (sweep && range == null) {
            scanError = "No hay red IPv4 activa. Conéctate a una Wi-Fi o indica un rango."
            return
        }
        if (sweep && range != null && range.totalAddresses > LanScanner.MAX_HOSTS) {
            scanError = "El rango es demasiado grande (máx. /20, ${LanScanner.MAX_HOSTS} direcciones)"
            return
        }
        devices.clear()
        scanRunning = true
        scanProgress = 0f
        scanJob = viewModelScope.launch {
            val lock = acquireMulticastLock()
            try {
                localNet?.let { n ->
                    val self = Ipv4.format(n.address)
                    devices[self] = Device(self, 0.0, "Este dispositivo", sources = setOf("Local"), isSelf = true)
                }
                if (discovery) {
                    scanPhase = "Escuchando anuncios multicast (mDNS, UPnP, WS-Discovery, NetBIOS)…"
                    runDiscovery(localNet)
                }
                if (sweep && range != null) {
                    scanPhase = "Barriendo ${range.cidr()}…"
                    LanScanner.sweep(range, timeoutMs, onProgress = { d, t ->
                        scanProgress = d.toFloat() / t
                    }) { dev -> withMain { addDevice(dev) } }
                }
                scanPhase = "Obteniendo nombres y MAC…"
                LanScanner.enrich(devices.values.filter { !it.isSelf }) { d -> withMain { devices[d.ip] = d } }
                scanPhase = "Completado: ${devices.size} equipos"
            } catch (e: kotlinx.coroutines.CancellationException) {
                scanPhase = "Detenido (${devices.size} equipos)"
                throw e
            } catch (e: Exception) {
                scanError = e.message ?: e.toString()
            } finally {
                lock?.release()
                scanRunning = false
                scanProgress = 1f
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    private suspend fun runDiscovery(localNet: Ipv4Network?) {
        val directed = localNet?.let { listOf(Ipv4.format(it.broadcast)) } ?: emptyList()
        val sightings = withContext(Dispatchers.IO) {
            val jobs = listOf(
                async { runCatching { Discovery.mdns() }.getOrDefault(emptyList()) },
                async { runCatching { Discovery.ssdp() }.getOrDefault(emptyList()) },
                async { runCatching { Discovery.wsDiscovery() }.getOrDefault(emptyList()) },
                async { runCatching { Discovery.netBiosBroadcast(directed) }.getOrDefault(emptyList()) },
            )
            jobs.awaitAll().flatten()
        }
        sightings.forEach { s -> addSighting(s, localNet) }
    }

    private fun addSighting(s: Sighting, localNet: Ipv4Network?) {
        if (s.ip.contains(':')) return // solo IPv4 en esta lista
        val v = Ipv4.parse(s.ip) ?: return
        val inSubnet = localNet?.contains(v) ?: false
        val existing = devices[s.ip] ?: Device(s.ip, inLocalSubnet = inSubnet, isGateway = s.ip == local?.gateway)
        devices[s.ip] = existing.merge(s)
    }

    private fun addDevice(d: Device) {
        val existing = devices[d.ip]
        val gw = d.ip == local?.gateway
        devices[d.ip] = existing?.copy(
            latencyMs = existing.latencyMs ?: d.latencyMs,
            sources = existing.sources + d.sources,
        ) ?: d.copy(isGateway = gw)
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = try {
        val wm = getApplication<Application>().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.createMulticastLock("ipcalc-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (e: Exception) {
        null
    }

    // ---------- escáner de puertos ----------
    val openPorts = mutableStateListOf<PortResult>()
    var portRunning by mutableStateOf(false)
        private set
    var portProgress by mutableStateOf(0f)
        private set
    var portStatus by mutableStateOf("")
        private set
    private var portJob: Job? = null

    fun scanPorts(host: String, ports: List<Int>, timeoutMs: Int) {
        if (portRunning) return
        openPorts.clear()
        portRunning = true
        portProgress = 0f
        portStatus = "Escaneando ${ports.size} puertos en $host…"
        portJob = viewModelScope.launch {
            try {
                PortScanner.scan(host.trim(), ports, timeoutMs, onProgress = { d, t ->
                    portProgress = d.toFloat() / t
                }) { r ->
                    viewModelScope.launch(Dispatchers.Main) {
                        openPorts.add(r)
                        openPorts.sortBy { it.port }
                    }
                }
                portStatus = "Completado: ${openPorts.size} abiertos de ${ports.size}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                portStatus = "Detenido"
                throw e
            } catch (e: Exception) {
                portStatus = "Error: ${e.message}"
            } finally {
                portRunning = false
            }
        }
    }

    fun stopPorts() {
        portJob?.cancel()
    }

    // ---------- ping ----------
    val pingLines = mutableStateListOf<String>()
    var pingRunning by mutableStateOf(false)
        private set
    private var pingJob: Job? = null
    private var pingProcess: Process? = null

    fun ping(host: String, count: Int) {
        if (pingRunning) return
        pingLines.clear()
        pingRunning = true
        pingJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val p = ProcessBuilder("ping", "-c", count.toString(), "-W", "2", host.trim())
                        .redirectErrorStream(true).start()
                    pingProcess = p
                    p.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> withMain { pingLines.add(line) } }
                    }
                    p.waitFor()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                pingLines.add("Error: ${e.message}")
            } finally {
                pingProcess?.destroy()
                pingRunning = false
            }
        }
    }

    fun stopPing() {
        pingProcess?.destroy()
        pingJob?.cancel()
    }

    private fun withMain(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main) { block() }
    }

    // ---------- IPs por defecto ----------
    /** ip -> latencia (null = sin respuesta). Ausente = aún sin probar. */
    val defaultResults = mutableStateMapOf<String, Double?>()
    var defaultRunning by mutableStateOf(false)
        private set
    private var defaultJob: Job? = null

    fun probeDefaults(timeoutMs: Int) {
        if (defaultRunning) return
        defaultResults.clear()
        defaultRunning = true
        defaultJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val sem = Semaphore(16)
                    coroutineScope {
                        Discovery.defaultIps.map { (ip, _) ->
                            async {
                                sem.withPermit {
                                    val r = Discovery.probeDefaultIp(ip, timeoutMs)
                                    withMain { defaultResults[ip] = r }
                                }
                            }
                        }.awaitAll()
                    }
                }
            } finally {
                defaultRunning = false
            }
        }
    }

    fun stopDefaults() {
        defaultJob?.cancel()
    }

    override fun onCleared() {
        pingProcess?.destroy()
        super.onCleared()
    }
}
