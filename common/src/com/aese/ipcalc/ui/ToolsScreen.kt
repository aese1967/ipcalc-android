package com.aese.ipcalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aese.ipcalc.scan.Discovery
import com.aese.ipcalc.scan.NetworkController
import com.aese.ipcalc.scan.Services

@Composable
fun ToolsScreen(vm: NetworkController, presetHost: String?, onPresetConsumed: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(0) }
    var host by rememberSaveable { mutableStateOf("192.168.1.1") }
    LaunchedEffect(presetHost) {
        if (presetHost != null) {
            host = presetHost; mode = 0; onPresetConsumed()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Puertos", "Ping", "IPs por defecto").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i, onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }
        when (mode) {
            0 -> PortTool(vm, host) { host = it }
            1 -> PingTool(vm, host) { host = it }
            else -> DefaultIpsTool(vm) { ip -> host = ip; mode = 0 }
        }
    }
}

@Composable
private fun PortTool(vm: NetworkController, host: String, onHost: (String) -> Unit) {
    var preset by rememberSaveable { mutableStateOf(0) }
    var custom by rememberSaveable { mutableStateOf("1-1024") }
    var timeout by rememberSaveable { mutableStateOf("600") }

    InputField(host, onHost, "Host o IP")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(preset == 0, { preset = 0 }, label = { Text("Comunes (${Services.common.size})") })
        FilterChip(preset == 1, { preset = 1 }, label = { Text("1-1024") })
        FilterChip(preset == 2, { preset = 2 }, label = { Text("Personalizado") })
    }
    if (preset == 2) InputField(custom, { custom = it }, "Puertos", placeholder = "22,80,443,8000-8100")
    InputField(timeout, { timeout = it.filter(Char::isDigit).take(5) }, "Tiempo de espera (ms)", numeric = true)

    val ports = when (preset) {
        0 -> Services.common
        1 -> (1..1024).toList()
        else -> Services.parseList(custom)
    }
    if (ports == null) Text("Lista de puertos no válida", color = MaterialTheme.colorScheme.error)

    if (vm.portRunning) {
        OutlinedButton(onClick = { vm.stopPorts() }) { Text("Detener") }
        LinearProgressIndicator(progress = { vm.portProgress }, modifier = Modifier.fillMaxWidth())
    } else {
        Button(
            onClick = { ports?.let { vm.scanPorts(host, it, timeout.toIntOrNull()?.coerceIn(100, 10000) ?: 600) } },
            enabled = ports != null && host.isNotBlank(),
        ) { Text("Escanear puertos") }
    }
    if (vm.portStatus.isNotEmpty()) Hint(vm.portStatus)
    if (vm.openPorts.isNotEmpty()) {
        SectionCard("Puertos abiertos") {
            vm.openPorts.forEach { r ->
                ResultRow("${r.port}/tcp", listOf(r.service.ifEmpty { "?" }, "${r.millis} ms").joinToString(" · "))
            }
        }
    }
    Hint("Solo se prueban puertos TCP. Escanea únicamente equipos y redes que te pertenezcan o que tengas permiso para revisar.")
}

@Composable
private fun PingTool(vm: NetworkController, host: String, onHost: (String) -> Unit) {
    var count by rememberSaveable { mutableStateOf("4") }
    InputField(host, onHost, "Host o IP")
    InputField(count, { count = it.filter(Char::isDigit).take(3) }, "Número de paquetes", numeric = true)
    if (vm.pingRunning) {
        OutlinedButton(onClick = { vm.stopPing() }) { Text("Detener") }
    } else {
        Button(onClick = { vm.ping(host, count.toIntOrNull()?.coerceIn(1, 100) ?: 4) }, enabled = host.isNotBlank()) {
            Text("Ping")
        }
    }
    if (vm.pingLines.isNotEmpty()) {
        SectionCard {
            vm.pingLines.forEach { MonoText(it) }
        }
    }
}

@Composable
private fun DefaultIpsTool(vm: NetworkController, onPick: (String) -> Unit) {
    var timeout by rememberSaveable { mutableStateOf("800") }
    Hint(
        "Prueba direcciones de fábrica típicas de routers, cámaras y equipos. Si el equipo está en otra red, " +
            "solo responderá si tu router sabe llegar a ella; para equipos recién conectados lo más eficaz es " +
            "la opción Multicast del escáner o poner una IP estática en el móvil dentro de ese rango."
    )
    InputField(timeout, { timeout = it.filter(Char::isDigit).take(5) }, "Tiempo de espera (ms)", numeric = true)
    if (vm.defaultRunning) {
        OutlinedButton(onClick = { vm.stopDefaults() }) { Text("Detener") }
        LinearProgressIndicator(
            progress = { vm.defaultResults.size.toFloat() / Discovery.defaultIps.size },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Button(onClick = { vm.probeDefaults(timeout.toIntOrNull()?.coerceIn(100, 10000) ?: 800) }) { Text("Probar todas") }
    }
    val found = Discovery.defaultIps.filter { vm.defaultResults[it.first] != null }
    if (found.isNotEmpty()) {
        SectionCard("Responden (toca para ver puertos)") {
            found.forEach { (ip, label) ->
                androidx.compose.material3.TextButton(onClick = { onPick(ip) }) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("$ip · ${"%.0f".format(vm.defaultResults[ip] ?: 0.0)} ms", fontWeight = FontWeight.Bold)
                        Hint(label)
                    }
                }
            }
        }
    }
    SectionCard("Lista de direcciones") {
        Discovery.defaultIps.forEach { (ip, label) ->
            val state = when {
                !vm.defaultResults.containsKey(ip) -> "—"
                vm.defaultResults[ip] != null -> "responde"
                else -> "sin respuesta"
            }
            ResultRow("$ip\n$label", state, mono = false)
        }
    }
}
