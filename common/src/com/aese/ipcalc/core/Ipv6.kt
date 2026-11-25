package com.aese.ipcalc.core

import java.math.BigInteger

object Ipv6 {
    val MAX: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

    /** Convierte texto IPv6 (con "::" y/o IPv4 embebida) a BigInteger. */
    fun parse(text: String): BigInteger? {
        var t = text.trim().lowercase()
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length - 1)
        val zone = t.indexOf('%')
        if (zone >= 0) t = t.substring(0, zone)
        if (t.isEmpty() || t.count { it == ':' } < 2 && !t.contains("::")) return null
        if (t.indexOf("::") != t.lastIndexOf("::")) return null

        val groups = mutableListOf<Int>()
        fun parseSide(side: String, allowIpv4: Boolean): List<Int>? {
            if (side.isEmpty()) return emptyList()
            val parts = side.split(':')
            val out = mutableListOf<Int>()
            for ((i, p) in parts.withIndex()) {
                if (allowIpv4 && i == parts.lastIndex && p.contains('.')) {
                    val v4 = Ipv4.parse(p) ?: return null
                    out += ((v4 shr 16) and 0xFFFF).toInt()
                    out += (v4 and 0xFFFF).toInt()
                } else {
                    if (p.isEmpty() || p.length > 4 || !p.all { it in '0'..'9' || it in 'a'..'f' }) return null
                    out += p.toInt(16)
                }
            }
            return out
        }

        if (t.contains("::")) {
            val idx = t.indexOf("::")
            val left = parseSide(t.substring(0, idx), false) ?: return null
            val right = parseSide(t.substring(idx + 2), true) ?: return null
            val missing = 8 - left.size - right.size
            if (missing < 1) return null
            groups += left
            repeat(missing) { groups += 0 }
            groups += right
        } else {
            val all = parseSide(t, true) ?: return null
            if (all.size != 8) return null
            groups += all
        }
        var v = BigInteger.ZERO
        for (g in groups) v = v.shiftLeft(16).or(BigInteger.valueOf(g.toLong()))
        return v
    }

    fun groups(v: BigInteger): IntArray =
        IntArray(8) { i -> v.shiftRight(16 * (7 - i)).and(BigInteger.valueOf(0xFFFF)).toInt() }

    fun expanded(v: BigInteger): String =
        groups(v).joinToString(":") { it.toString(16).padStart(4, '0') }

    /** Formato comprimido canónico (RFC 5952). */
    fun compressed(v: BigInteger): String {
        val g = groups(v)
        var bestStart = -1
        var bestLen = 0
        var i = 0
        while (i < 8) {
            if (g[i] == 0) {
                var j = i
                while (j < 8 && g[j] == 0) j++
                if (j - i > bestLen && j - i >= 2) {
                    bestStart = i; bestLen = j - i
                }
                i = j
            } else i++
        }
        val hex = g.map { it.toString(16) }
        if (bestStart < 0) return hex.joinToString(":")
        val left = hex.subList(0, bestStart).joinToString(":")
        val right = hex.subList(bestStart + bestLen, 8).joinToString(":")
        return "$left::$right"
    }

    fun mask(prefix: Int): BigInteger {
        require(prefix in 0..128)
        return if (prefix == 0) BigInteger.ZERO
        else MAX.shiftLeft(128 - prefix).and(MAX)
    }

    fun reversePointer(v: BigInteger): String =
        expanded(v).replace(":", "").reversed().toCharArray().joinToString(".") + ".ip6.arpa"

    private data class Range(val net: BigInteger, val prefix: Int, val label: String)

    private val ranges by lazy {
        listOf(
            Range(parse("::")!!, 128, "No especificada"),
            Range(parse("::1")!!, 128, "Loopback"),
            Range(parse("::ffff:0:0")!!, 96, "IPv4 mapeada"),
            Range(parse("64:ff9b::")!!, 96, "Traducción NAT64 (RFC 6052)"),
            Range(parse("100::")!!, 64, "Descarte (RFC 6666)"),
            Range(parse("2001::")!!, 32, "Teredo (RFC 4380)"),
            Range(parse("2001:db8::")!!, 32, "Documentación (RFC 3849)"),
            Range(parse("2002::")!!, 16, "6to4 (RFC 3056)"),
            Range(parse("fc00::")!!, 7, "Local única ULA (RFC 4193)"),
            Range(parse("fe80::")!!, 10, "Enlace local"),
            Range(parse("ff00::")!!, 8, "Multicast"),
            Range(parse("2000::")!!, 3, "Unicast global"),
        )
    }

    fun addressType(v: BigInteger): String =
        ranges.firstOrNull { v.and(mask(it.prefix)) == it.net }?.label ?: "Reservada"
}

data class Ipv6Network(val address: BigInteger, val prefix: Int) {
    val network: BigInteger get() = address.and(Ipv6.mask(prefix))
    val last: BigInteger get() = network.or(Ipv6.mask(prefix).xor(Ipv6.MAX))
    val totalAddresses: BigInteger get() = BigInteger.ONE.shiftLeft(128 - prefix)

    fun cidr(): String = "${Ipv6.compressed(network)}/$prefix"
}

object Ipv6Parser {
    fun parse(input: String): ParseResult<Ipv6Network> {
        val text = input.trim()
        if (text.isEmpty()) return ParseResult.Error("Introduce una dirección IPv6")
        val slash = text.lastIndexOf('/')
        val addrPart = if (slash >= 0) text.substring(0, slash) else text
        val prefix = if (slash >= 0) {
            text.substring(slash + 1).trim().toIntOrNull()
                ?.takeIf { it in 0..128 }
                ?: return ParseResult.Error("El prefijo debe estar entre 0 y 128")
        } else 64
        val v = Ipv6.parse(addrPart) ?: return ParseResult.Error("Dirección IPv6 no válida")
        return ParseResult.Ok(Ipv6Network(v, prefix), if (slash < 0) "Prefijo /64 aplicado por defecto" else null)
    }

    /** Lista las primeras [limit] subredes de tamaño [newPrefix]. */
    fun split(parent: Ipv6Network, newPrefix: Int, limit: Int = 256): ParseResult<Pair<BigInteger, List<Ipv6Network>>> {
        if (newPrefix !in parent.prefix..128) {
            return ParseResult.Error("El nuevo prefijo debe estar entre /${parent.prefix} y /128")
        }
        val count = BigInteger.ONE.shiftLeft(newPrefix - parent.prefix)
        val size = BigInteger.ONE.shiftLeft(128 - newPrefix)
        val n = count.min(BigInteger.valueOf(limit.toLong())).toInt()
        val list = (0 until n).map { i ->
            Ipv6Network(parent.network.add(size.multiply(BigInteger.valueOf(i.toLong()))), newPrefix)
        }
        return ParseResult.Ok(count to list)
    }
}

/** Formatea números grandes de forma legible (p. ej. 2^64). */
fun formatBig(n: BigInteger): String {
    val bits = n.bitLength() - 1
    val plain = n.toString().reversed().chunked(3).joinToString(".").reversed()
    return if (n.bitCount() == 1 && bits >= 20) "2^$bits ($plain)" else plain
}

fun formatCount(n: Long): String = formatBig(BigInteger.valueOf(n))
