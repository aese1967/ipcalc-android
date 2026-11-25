package com.aese.ipcalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aese.ipcalc.core.Ipv4
import com.aese.ipcalc.scan.Device
import com.aese.ipcalc.scan.LanScanner
import com.aese.ipcalc.scan.NetworkViewModel

@Composable
fun ScannerScreen(vm: NetworkViewModel, onOpenPorts: (String) -> Unit, onCalc: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.refreshLocal() }
    var range by rememberSaveable { mutableStateOf("") }
    var sweep by rememberSaveable { mutableStateOf(true) }
    var discovery by rememberSaveable { mutableStateOf(true) }
    var timeout by rememberSaveable { mutableStateOf("1000") }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val devices = vm.sortedDevices()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val l = vm.local
            SectionCard("Mi red") {
                if (l == null) {
                    Hint("Sin información de red")
                } else {
                    ResultRow("Conexión", listOfNotNull(l.transport, l.interfaceName).joinToString(" · "), mono = false)
                    l.ipv4?.let {
                        ResultRow("IP local", it.toString())
                        ResultRow("Red", it.cidr())
                        ResultRow("Máscara", Ipv4.format(it.mask))
                    } ?: Hint("Sin dirección IPv4")
                    l.gateway?.let { ResultRow("Puerta de enlace", it) }
                    if (l.dns.isNotEmpty()) ResultRow("DNS", l.dns.joinToString(", "))
                    l.ipv6.take(3).forEach { ResultRow("IPv6", it) }
                    l.ipv4?.let { n ->
                        TextButton(onClick = { onCalc(n.toString()) }) { Text("Abrir en la calculadora") }
                    }
                }
            }
        }
        item {
            SectionCard("Escanear") {
                val target = vm.local?.ipv4?.let { LanScanner.targetRange(it).cidr() } ?: "—"
                InputField(
                    range, { range = it }, "Rango (vacío = mi red: $target)",
                    placeholder = "192.168.0.0/24",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sweep, onClick = { sweep = !sweep }, label = { Text("Barrido ping/TCP") })
                    FilterChip(selected = discovery, onClick = { discovery = !discovery }, label = { Text("Multicast") })
                }
                InputField(timeout, { timeout = it.filter(Char::isDigit).take(5) }, "Tiempo de espera (ms)", numeric = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.scanRunning) {
                        OutlinedButton(onClick = { vm.stopScan() }) { Text("Detener") }
                    } else {
                        Button(
                            onClick = { vm.startScan(range, timeout.toIntOrNull()?.coerceIn(100, 10000) ?: 1000, sweep, discovery) },
                            enabled = sweep || discovery,
                        ) { Text("Iniciar escaneo") }
                    }
                    TextButton(onClick = { showHelp = !showHelp }) { Text("¿Otra red?") }
                }
                if (vm.scanRunning) LinearProgressIndicator(progress = { vm.scanProgress }, modifier = Modifier.fillMaxWidth())
                if (vm.scanPhase.isNotEmpty()) Hint(vm.scanPhase)
                vm.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (showHelp) OtherNetworkHelp()
            }
        }
        if (devices.isNotEmpty()) {
            item { Text("${devices.size} equipos", style = MaterialTheme.typography.titleMedium) }
        }
        items(devices, key = { it.ip }) { d -> DeviceCard(d, onOpenPorts) }
    }
}

@Composable
private fun DeviceCard(d: Device, onOpenPorts: (String) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenPorts(d.ip) },
        colors = CardDefaults.cardColors(
            containerColor = if (!d.inLocalSubnet) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    d.isGateway -> Icons.Filled.Router
                    d.isSelf -> Icons.Filled.PhoneAndroid
                    else -> Icons.Filled.Computer
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.ip, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (d.isGateway) Text("  puerta de enlace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (!d.inLocalSubnet && !d.isSelf) Text("  otra subred", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                d.hostname?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val macLine = listOfNotNull(d.mac, d.vendor).joinToString(" · ")
                if (macLine.isNotEmpty()) MonoText(macLine)
                d.details.take(3).forEach { Hint(it) }
                Hint(d.sources.joinToString(" + "))
            }
            d.latencyMs?.let { if (!d.isSelf) Text("${"%.0f".format(it)} ms", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun OtherNetworkHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Buscar un equipo con IP de otra red", fontWeight = FontWeight.SemiBold)
        Hint(
            "Android no permite enviar ARP ni barrer redes ajenas sin root, así que no se pueden recorrer " +
                "todas las redes posibles. Pero si el equipo está conectado físicamente a la misma Wi-Fi o switch, " +
                "hay tres caminos:"
        )
        Hint(
            "1. Multicast: deja activada la opción \"Multicast\". mDNS, UPnP, WS-Discovery y NetBIOS llegan a todo " +
                "el segmento y la respuesta muestra la IP real del equipo, aunque sea de otra red (sale marcado \"otra subred\"). " +
                "Funciona con impresoras, cámaras ONVIF, Chromecast, NAS, PCs Windows, etc."
        )
        Hint(
            "2. IPs de fábrica: en la pestaña Herramientas, \"IPs por defecto\" prueba las direcciones habituales " +
                "(192.168.0.1, 192.168.1.1, 192.168.1.64, 10.0.0.1, 192.168.100.1…)."
        )
        Hint(
            "3. IP estática en el móvil: si sospechas el rango (p. ej. 192.168.0.x), ve a Ajustes > Wi-Fi > tu red > " +
                "Avanzado/Configuración IP > Estática, y pon por ejemplo IP 192.168.0.250, puerta de enlace 192.168.0.1, " +
                "prefijo 24. Vuelve aquí y escanea. Recuerda volver a DHCP al terminar. En redes con cable usa un " +
                "adaptador USB-Ethernet o un PC."
        )
    }
}
