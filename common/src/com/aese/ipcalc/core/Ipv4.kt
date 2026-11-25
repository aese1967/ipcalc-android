package com.aese.ipcalc.core

/**
 * Utilidades IPv4. Las direcciones se representan como Long sin signo (0 .. 2^32-1).
 */
object Ipv4 {
    const val MAX: Long = 0xFFFFFFFFL

    fun parse(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return null
            val n = p.toInt()
            if (n > 255) return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }

    fun format(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    fun prefixToMask(prefix: Int): Long {
        require(prefix in 0..32)
        return if (prefix == 0) 0L else (MAX shl (32 - prefix)) and MAX
    }

    /** Devuelve el prefijo si la máscara es contigua, o null si no es válida. */
    fun maskToPrefix(mask: Long): Int? {
        val prefix = java.lang.Long.bitCount(mask and MAX)
        return if (prefixToMask(prefix) == mask) prefix else null
    }

    fun toBinary(v: Long): String =
        (0..3).joinToString(".") { i ->
            ((v shr (24 - 8 * i)) and 0xFF).toString(2).padStart(8, '0')
        }

    fun toHex(v: Long): String = "0x" + v.toString(16).uppercase().padStart(8, '0')

    fun reversePointer(v: Long): String =
        "${v and 0xFF}.${(v shr 8) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 24) and 0xFF}.in-addr.arpa"

    /** Clase clásica (A-E) según el primer octeto. */
    fun ipClass(v: Long): Char {
        val first = (v shr 24).toInt()
        return when {
            first < 128 -> 'A'
            first < 192 -> 'B'
            first < 224 -> 'C'
            first < 240 -> 'D'
            else -> 'E'
        }
    }

    /** Prefijo por defecto de la clase (null para D y E). */
    fun classDefaultPrefix(v: Long): Int? = when (ipClass(v)) {
        'A' -> 8
        'B' -> 16
        'C' -> 24
        else -> null
    }

    private data class Range(val net: Long, val prefix: Int, val label: String)

    private val specialRanges = listOf(
        Range(parse("0.0.0.0")!!, 8, "\"Esta red\" (RFC 1122)"),
        Range(parse("10.0.0.0")!!, 8, "Privada (RFC 1918)"),
        Range(parse("100.64.0.0")!!, 10, "CGNAT compartida (RFC 6598)"),
        Range(parse("127.0.0.0")!!, 8, "Loopback (RFC 1122)"),
        Range(parse("169.254.0.0")!!, 16, "Enlace local / APIPA (RFC 3927)"),
        Range(parse("172.16.0.0")!!, 12, "Privada (RFC 1918)"),
        Range(parse("192.0.0.0")!!, 24, "Asignación IETF (RFC 6890)"),
        Range(parse("192.0.2.0")!!, 24, "Documentación TEST-NET-1 (RFC 5737)"),
        Range(parse("192.88.99.0")!!, 24, "Relay 6to4 (RFC 7526)"),
        Range(parse("192.168.0.0")!!, 16, "Privada (RFC 1918)"),
        Range(parse("198.18.0.0")!!, 15, "Pruebas de rendimiento (RFC 2544)"),
        Range(parse("198.51.100.0")!!, 24, "Documentación TEST-NET-2 (RFC 5737)"),
        Range(parse("203.0.113.0")!!, 24, "Documentación TEST-NET-3 (RFC 5737)"),
        Range(parse("224.0.0.0")!!, 4, "Multicast (RFC 5771)"),
        Range(parse("255.255.255.255")!!, 32, "Broadcast limitado"),
        Range(parse("240.0.0.0")!!, 4, "Reservada (RFC 1112)"),
    )

    fun addressType(v: Long): String =
        specialRanges.firstOrNull { (v and prefixToMask(it.prefix)) == it.net }?.label ?: "Pública"

    fun isPrivate(v: Long): Boolean = addressType(v).startsWith("Privada")
}

data class Ipv4Network(val address: Long, val prefix: Int) {
    val mask: Long get() = Ipv4.prefixToMask(prefix)
    val wildcard: Long get() = mask.inv() and Ipv4.MAX
    val network: Long get() = address and mask
    val broadcast: Long get() = network or wildcard
    val totalAddresses: Long get() = 1L shl (32 - prefix)

    /** Hosts utilizables: /31 tiene 2 (RFC 3021) y /32 tiene 1. */
    val usableHosts: Long
        get() = when (prefix) {
            32 -> 1
            31 -> 2
            else -> totalAddresses - 2
        }

    val firstHost: Long get() = if (prefix >= 31) network else network + 1
    val lastHost: Long get() = if (prefix >= 31) broadcast else broadcast - 1

    fun contains(ip: Long): Boolean = (ip and mask) == network

    fun cidr(): String = "${Ipv4.format(network)}/$prefix"

    override fun toString(): String = "${Ipv4.format(address)}/$prefix"
}

sealed class ParseResult<out T> {
    data class Ok<T>(val value: T, val note: String? = null) : ParseResult<T>()
    data class Error(val message: String) : ParseResult<Nothing>()
}

object Ipv4Parser {
    /**
     * Acepta:
     *  - 10.0.0.1/22
     *  - 10.0.0.1/255.255.252.0  ó  10.0.0.1 255.255.252.0  (máscara)
     *  - 10.0.0.1/0.0.3.255  ó  10.0.0.1 0.0.3.255          (wildcard)
     *  - 10.0.0.1                                           (máscara de clase)
     */
    fun parse(input: String): ParseResult<Ipv4Network> {
        val text = input.trim()
        if (text.isEmpty()) return ParseResult.Error("Introduce una dirección IPv4")
        val pieces = text.split('/', ' ', '\t').filter { it.isNotBlank() }
        if (pieces.size > 2) return ParseResult.Error("Formato no reconocido")
        val ip = Ipv4.parse(pieces[0]) ?: return ParseResult.Error("Dirección IPv4 no válida: ${pieces[0]}")
        if (pieces.size == 1) {
            val p = Ipv4.classDefaultPrefix(ip)
                ?: return ParseResult.Error("Las clases D y E no tienen máscara por defecto; indica /prefijo")
            return ParseResult.Ok(Ipv4Network(ip, p), "Máscara de clase ${Ipv4.ipClass(ip)} aplicada")
        }
        return when (val m = parseMask(pieces[1])) {
            is ParseResult.Ok -> ParseResult.Ok(Ipv4Network(ip, m.value), m.note)
            is ParseResult.Error -> m
        }
    }

    /** Interpreta "/24", "24", "255.255.255.0" o "0.0.0.255" y devuelve el prefijo. */
    fun parseMask(text: String): ParseResult<Int> {
        val t = text.trim().removePrefix("/")
        if (t.isNotEmpty() && t.all { it.isDigit() }) {
            val p = t.toIntOrNull()
            return if (p != null && p in 0..32) ParseResult.Ok(p)
            else ParseResult.Error("El prefijo debe estar entre 0 y 32")
        }
        val v = Ipv4.parse(t) ?: return ParseResult.Error("Máscara no válida: $t")
        Ipv4.maskToPrefix(v)?.let { return ParseResult.Ok(it) }
        Ipv4.maskToPrefix(v.inv() and Ipv4.MAX)?.let {
            return ParseResult.Ok(it, "Interpretado como máscara wildcard")
        }
        return ParseResult.Error("La máscara no es contigua: $t")
    }
}
