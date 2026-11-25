package com.aese.ipcalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aese.ipcalc.core.Ipv4
import com.aese.ipcalc.core.Ipv4Network
import com.aese.ipcalc.core.Ipv4Parser
import com.aese.ipcalc.core.ParseResult
import com.aese.ipcalc.core.Subnetting
import com.aese.ipcalc.core.Vlsm
import com.aese.ipcalc.core.formatCount

@Composable
fun SubnetScreen() {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Dividir", "VLSM", "Superred").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }
        when (mode) {
            0 -> SplitTool()
            1 -> VlsmTool()
            else -> SupernetTool()
        }
    }
}

@Composable
private fun SplitTool() {
    var input by rememberSaveable { mutableStateOf("192.168.0.0/24") }
    var byHosts by rememberSaveable { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf("26") }
    val parsed = remember(input) { Ipv4Parser.parse(input) }

    InputField(input, { input = it }, "Red a dividir", error = (parsed as? ParseResult.Error)?.message)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !byHosts, onClick = { byHosts = false; value = "26" }, label = { Text("Por prefijo / nº subredes") })
        FilterChip(selected = byHosts, onClick = { byHosts = true; value = "50" }, label = { Text("Por hosts") })
    }
    InputField(
        value, { value = it.filter { c -> c.isDigit() || c == '/' }.take(10) },
        if (byHosts) "Hosts necesarios por subred" else "Nuevo prefijo (/26) o nº de subredes (x4)",
        numeric = !byHosts,
    )
    if (!byHosts) Hint("Escribe 26 para usar /26, o x4 para pedir 4 subredes.")
    val net = (parsed as? ParseResult.Ok)?.value ?: return
    val newPrefix: Int? = when {
        byHosts -> value.toLongOrNull()?.let { Subnetting.prefixForHosts(it) }
        value.startsWith("x") -> value.drop(1).toLongOrNull()?.let { Subnetting.prefixForSubnetCount(net, it) }
        else -> value.removePrefix("/").toIntOrNull()
    }
    if (newPrefix == null) {
        Hint("Introduce un valor válido")
        return
    }
    when (val r = Subnetting.split(Ipv4Network(net.network, net.prefix), newPrefix, 1024)) {
        is ParseResult.Error -> Text(r.message, color = MaterialTheme.colorScheme.error)
        is ParseResult.Ok -> {
            val s = r.value
            SectionCard("${formatCount(s.subnetCount)} subredes /${s.newPrefix}") {
                ResultRow("Máscara", Ipv4.format(Ipv4.prefixToMask(s.newPrefix)))
                ResultRow("Hosts por subred", formatCount(s.hostsPerSubnet))
                ResultRow("Bits prestados", (s.newPrefix - net.prefix).toString())
                if (s.truncated) Hint("Se muestran las primeras ${s.subnets.size}.")
            }
            s.subnets.forEachIndexed { i, sn -> SubnetRow(i + 1, sn) }
        }
    }
}

@Composable
private fun SubnetRow(index: Int, n: Ipv4Network, title: String? = null, extra: String? = null) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$index ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title ?: n.cidr(), fontWeight = FontWeight.SemiBold)
        }
        if (title != null) MonoText(n.cidr(), size = 13)
        MonoText("Hosts: ${Ipv4.format(n.firstHost)} - ${Ipv4.format(n.lastHost)}")
        MonoText("Broadcast: ${Ipv4.format(n.broadcast)} · Máscara: ${Ipv4.format(n.mask)}")
        if (extra != null) Hint(extra)
    }
}

private class VlsmRow(name: String, hosts: String) {
    var name by mutableStateOf(name)
    var hosts by mutableStateOf(hosts)
}

@Composable
private fun VlsmTool() {
    var base by rememberSaveable { mutableStateOf("192.168.10.0/24") }
    val rows = remember {
        mutableStateListOf(VlsmRow("Ventas", "100"), VlsmRow("Oficina", "50"), VlsmRow("WiFi invitados", "20"), VlsmRow("Enlace WAN", "2"))
    }
    var result by remember { mutableStateOf<ParseResult<Vlsm.Plan>?>(null) }
    val parsed = remember(base) { Ipv4Parser.parse(base) }

    InputField(base, { base = it }, "Red base", error = (parsed as? ParseResult.Error)?.message)
    Text("Subredes necesarias", style = MaterialTheme.typography.titleSmall)
    rows.forEachIndexed { i, row ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(row.name, { row.name = it }, Modifier.weight(0.55f), label = { Text("Nombre") }, singleLine = true)
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                row.hosts, { row.hosts = it.filter(Char::isDigit).take(9) }, Modifier.weight(0.35f),
                label = { Text("Hosts") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            )
            IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Filled.Delete, "Eliminar") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { rows.add(VlsmRow("Subred ${rows.size + 1}", "10")) }) {
            Icon(Icons.Filled.Add, null); Text(" Añadir")
        }
        Button(onClick = {
            val net = (parsed as? ParseResult.Ok)?.value
            result = if (net == null) ParseResult.Error("Red base no válida") else Vlsm.plan(
                net,
                rows.map { Vlsm.Request(it.name.ifBlank { "Subred" }, it.hosts.toLongOrNull() ?: 0) },
            )
        }) { Text("Calcular VLSM") }
    }

    when (val r = result) {
        null -> Hint("Las subredes se asignan de mayor a menor para aprovechar el espacio.")
        is ParseResult.Error -> Text(r.message, color = MaterialTheme.colorScheme.error)
        is ParseResult.Ok -> {
            val plan = r.value
            SectionCard("Plan para ${plan.base.cidr()}") {
                ResultRow("Direcciones usadas", "${formatCount(plan.usedAddresses)} de ${formatCount(plan.totalAddresses)}")
                LinearProgressIndicator(progress = { (plan.usagePercent / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
                Hint("Uso: ${"%.1f".format(plan.usagePercent)} %")
            }
            plan.allocations.forEachIndexed { i, a ->
                SubnetRow(
                    i + 1, a.subnet, title = a.name,
                    extra = "Pedidos ${a.hostsRequested} · disponibles ${a.hostsAvailable} · sobran ${a.wasted}",
                )
            }
            if (plan.freeBlocks.isNotEmpty()) {
                SectionCard("Espacio libre") {
                    plan.freeBlocks.forEach { MonoText(it.cidr(), size = 13) }
                }
            }
        }
    }
}

@Composable
private fun SupernetTool() {
    var text by rememberSaveable { mutableStateOf("192.168.0.0/24\n192.168.1.0/24\n192.168.2.0/24\n192.168.3.0/24") }
    OutlinedTextField(
        text, { text = it }, Modifier.fillMaxWidth(),
        label = { Text("Redes (una por línea)") }, minLines = 4,
    )
    val nets = text.lines().filter { it.isNotBlank() }.map { Ipv4Parser.parse(it) }
    val bad = nets.indexOfFirst { it is ParseResult.Error }
    if (bad >= 0) {
        Text("Línea ${bad + 1}: ${(nets[bad] as ParseResult.Error).message}", color = MaterialTheme.colorScheme.error)
        return
    }
    val list = nets.map { (it as ParseResult.Ok).value }
    val sup = Subnetting.summarize(list) ?: return
    SectionCard("Superred mínima") {
        ResultRow("Superred", sup.cidr())
        ResultRow("Máscara", Ipv4.format(sup.mask))
        ResultRow("Wildcard", Ipv4.format(sup.wildcard))
        ResultRow("Rango", "${Ipv4.format(sup.network)} - ${Ipv4.format(sup.broadcast)}")
        val covered = list.sumOf { it.totalAddresses }
        if (covered < sup.totalAddresses) {
            Hint("Atención: la superred incluye ${formatCount(sup.totalAddresses - covered)} direcciones que no están en la lista (o hay solapes).")
        } else {
            Hint("Las redes se agregan exactamente en una sola ruta.")
        }
    }
}
