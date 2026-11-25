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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aese.ipcalc.core.Ipv6
import com.aese.ipcalc.core.Ipv6Parser
import com.aese.ipcalc.core.ParseResult
import com.aese.ipcalc.core.formatBig

@Composable
fun Ipv6Screen() {
    var input by rememberSaveable { mutableStateOf("2001:db8:abcd:12::1/48") }
    var sub by rememberSaveable { mutableStateOf("64") }
    val parsed = remember(input) { Ipv6Parser.parse(input) }

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
            label = "Dirección IPv6 / prefijo",
            placeholder = "2001:db8::1/64",
            error = (parsed as? ParseResult.Error)?.message,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("2001:db8::1/64", "fe80::1/64", "fd12:3456:789a::/48", "::ffff:192.168.1.1/128").forEach { ex ->
                AssistChip(onClick = { input = ex }, label = { Text(ex, fontSize = 11.sp) })
            }
        }
        if (parsed is ParseResult.Ok) {
            val n = parsed.value
            parsed.note?.let { Hint(it) }
            SectionCard("Resultado") {
                ResultRow("Comprimida", Ipv6.compressed(n.address))
                ResultRow("Expandida", Ipv6.expanded(n.address))
                ResultRow("Prefijo", "/${n.prefix}")
                ResultRow("Red", n.cidr())
                ResultRow("Primera dirección", Ipv6.compressed(n.network))
                ResultRow("Última dirección", Ipv6.compressed(n.last))
                ResultRow("Máscara", Ipv6.compressed(Ipv6.mask(n.prefix)))
                ResultRow("Total direcciones", formatBig(n.totalAddresses))
                ResultRow("Tipo", Ipv6.addressType(n.address), mono = false)
                if (n.prefix <= 64) {
                    ResultRow("Subredes /64", formatBig(java.math.BigInteger.ONE.shiftLeft(64 - n.prefix)))
                }
                ResultRow("PTR inverso", Ipv6.reversePointer(n.address))
            }

            SectionCard("Dividir en subredes") {
                InputField(sub, { sub = it.filter(Char::isDigit).take(3) }, "Nuevo prefijo", numeric = true)
                val p = sub.toIntOrNull()
                if (p != null) {
                    when (val r = Ipv6Parser.split(n, p, 256)) {
                        is ParseResult.Error -> Hint(r.message)
                        is ParseResult.Ok -> {
                            val (count, list) = r.value
                            Hint("${formatBig(count)} subredes /$p" + if (count > java.math.BigInteger.valueOf(list.size.toLong())) " (se muestran las primeras ${list.size})" else "")
                            list.forEachIndexed { i, s -> ResultRow("#${i + 1}", s.cidr()) }
                        }
                    }
                }
            }
        }
        Hint("Prefijos habituales: /48 para un sitio, /56 para un hogar, /64 para cada LAN, /127 para enlaces punto a punto.")
    }
}
