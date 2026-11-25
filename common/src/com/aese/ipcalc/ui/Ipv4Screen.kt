package com.aese.ipcalc.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aese.ipcalc.core.Ipv4
import com.aese.ipcalc.core.Ipv4Network
import com.aese.ipcalc.core.Ipv4Parser
import com.aese.ipcalc.core.ParseResult
import com.aese.ipcalc.core.formatCount

@Composable
fun Ipv4Screen(initial: String?) {
    var input by rememberSaveable { mutableStateOf(initial ?: "192.168.1.10/24") }
    val parsed = remember(input) { Ipv4Parser.parse(input) }
    var showMasks by rememberSaveable { mutableStateOf(false) }
    var showClasses by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InputField(
            value = input,
            onChange = { input = it },
            label = "Dirección IPv4 y máscara",
            placeholder = "10.0.0.1/22 · 10.0.0.1 255.255.252.0 · 10.0.0.1/0.0.3.255",
            error = (parsed as? ParseResult.Error)?.message,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("192.168.1.10/24", "10.0.0.1/8", "172.16.5.4/20", "100.64.1.1/10").forEach { ex ->
                AssistChip(onClick = { input = ex }, label = { Text(ex, fontSize = 11.sp) })
            }
        }
        Hint("Acepta prefijo CIDR, máscara decimal, máscara wildcard o solo la IP (máscara de clase). Toca un resultado para copiarlo.")

        if (parsed is ParseResult.Ok) {
            val n = parsed.value
            parsed.note?.let { Hint(it) }
            Ipv4Results(n)
            BinaryCard(n)
        }

        SectionCard("Tabla de máscaras") {
            TextButton(onClick = { showMasks = !showMasks }) { Text(if (showMasks) "Ocultar" else "Mostrar /0 a /32") }
            if (showMasks) MaskTable()
        }
        SectionCard("Clases de direcciones") {
            TextButton(onClick = { showClasses = !showClasses }) { Text(if (showClasses) "Ocultar" else "Mostrar") }
            if (showClasses) ClassTable()
        }
    }
}

@Composable
fun Ipv4Results(n: Ipv4Network) {
    SectionCard("Resultado") {
        ResultRow("Dirección", Ipv4.format(n.address))
        ResultRow("Prefijo CIDR", "/${n.prefix}")
        ResultRow("Máscara", Ipv4.format(n.mask))
        ResultRow("Wildcard", Ipv4.format(n.wildcard))
        ResultRow("Red", n.cidr())
        ResultRow("Broadcast", Ipv4.format(n.broadcast))
        ResultRow("Primer host", Ipv4.format(n.firstHost))
        ResultRow("Último host", Ipv4.format(n.lastHost))
        ResultRow("Rango de hosts", "${Ipv4.format(n.firstHost)} - ${Ipv4.format(n.lastHost)}")
        ResultRow("Hosts utilizables", formatCount(n.usableHosts))
        ResultRow("Total direcciones", formatCount(n.totalAddresses))
        ResultRow("Clase", Ipv4.ipClass(n.address).toString())
        ResultRow("Tipo", Ipv4.addressType(n.address), mono = false)
        ResultRow("Hexadecimal", Ipv4.toHex(n.address))
        ResultRow("Entero", n.address.toString())
        ResultRow("PTR inverso", Ipv4.reversePointer(n.address))
        if (n.prefix == 31) Hint("/31: enlace punto a punto, ambas direcciones son utilizables (RFC 3021).")
        if (n.prefix == 32) Hint("/32: ruta a un único host.")
    }
}

@Composable
private fun BinaryCard(n: Ipv4Network) {
    SectionCard("Representación binaria") {
        val rows = listOf(
            "Dirección" to n.address, "Máscara" to n.mask, "Wildcard" to n.wildcard,
            "Red" to n.network, "Broadcast" to n.broadcast,
        )
        rows.forEach { (label, v) ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BinaryLine(Ipv4.toBinary(v), n.prefix)
        }
        Hint("En negrita: bits de red (los primeros ${n.prefix}).")
    }
}

@Composable
private fun BinaryLine(bin: String, prefix: Int) {
    // bin = "xxxxxxxx.xxxxxxxx.xxxxxxxx.xxxxxxxx"
    val netColor = MaterialTheme.colorScheme.primary
    val hostColor = MaterialTheme.colorScheme.onSurface
    val text = androidx.compose.ui.text.buildAnnotatedString {
        var bit = 0
        for (c in bin) {
            if (c == '.') {
                append('.')
                continue
            }
            val isNet = bit < prefix
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    fontWeight = if (isNet) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNet) netColor else hostColor,
                )
            )
            append(c)
            pop()
            bit++
        }
    }
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

@Composable
private fun MaskTable() {
    Row(Modifier.fillMaxWidth()) {
        listOf("CIDR" to 0.15f, "Máscara" to 0.33f, "Wildcard" to 0.30f, "Hosts" to 0.22f).forEach { (h, w) ->
            Text(h, Modifier.weight(w), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
    for (p in 0..32) {
        val n = Ipv4Network(0L, p)
        Row(Modifier.fillMaxWidth()) {
            MonoText("/$p", Modifier.weight(0.15f), 11)
            MonoText(Ipv4.format(n.mask), Modifier.weight(0.33f), 11)
            MonoText(Ipv4.format(n.wildcard), Modifier.weight(0.30f), 11)
            MonoText(n.usableHosts.toString(), Modifier.weight(0.22f), 11)
        }
    }
}

@Composable
private fun ClassTable() {
    val rows = listOf(
        listOf("A", "0.0.0.0 - 127.255.255.255", "/8", "Redes grandes"),
        listOf("B", "128.0.0.0 - 191.255.255.255", "/16", "Redes medianas"),
        listOf("C", "192.0.0.0 - 223.255.255.255", "/24", "Redes pequeñas"),
        listOf("D", "224.0.0.0 - 239.255.255.255", "-", "Multicast"),
        listOf("E", "240.0.0.0 - 255.255.255.255", "-", "Experimental"),
    )
    rows.forEach { r ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Text("Clase ${r[0]} · máscara ${r[2]} · ${r[3]}", fontWeight = FontWeight.Medium)
            MonoText(r[1])
        }
    }
    Hint("Privadas (RFC 1918): 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16. CGNAT: 100.64.0.0/10. Enlace local: 169.254.0.0/16.")
}
