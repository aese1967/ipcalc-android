package com.aese.ipcalc.core

object Subnetting {

    data class SplitResult(
        val parent: Ipv4Network,
        val newPrefix: Int,
        val subnetCount: Long,
        val hostsPerSubnet: Long,
        val subnets: List<Ipv4Network>,
        val truncated: Boolean,
    )

    /** Divide [parent] en subredes de tamaño [newPrefix]. Devuelve como máximo [limit] filas. */
    fun split(parent: Ipv4Network, newPrefix: Int, limit: Int = 1024): ParseResult<SplitResult> {
        if (newPrefix !in 0..32) return ParseResult.Error("El nuevo prefijo debe estar entre 0 y 32")
        if (newPrefix < parent.prefix) {
            return ParseResult.Error("El nuevo prefijo (/$newPrefix) debe ser mayor o igual que /${parent.prefix}. Para agrupar usa superred.")
        }
        val count = 1L shl (newPrefix - parent.prefix)
        val size = 1L shl (32 - newPrefix)
        val n = minOf(count, limit.toLong()).toInt()
        val list = (0 until n).map { i -> Ipv4Network(parent.network + i * size, newPrefix) }
        val hosts = Ipv4Network(parent.network, newPrefix).usableHosts
        return ParseResult.Ok(SplitResult(parent, newPrefix, count, hosts, list, count > limit))
    }

    /** Subred más pequeña que tenga al menos [hosts] hosts utilizables. */
    fun prefixForHosts(hosts: Long): Int? {
        if (hosts < 1) return null
        if (hosts == 1L) return 32
        if (hosts == 2L) return 30 // convención habitual; /31 solo para enlaces punto a punto
        for (p in 30 downTo 0) {
            if ((1L shl (32 - p)) - 2 >= hosts) return p
        }
        return null
    }

    /** Prefijo mínimo para obtener al menos [count] subredes dentro de [parent]. */
    fun prefixForSubnetCount(parent: Ipv4Network, count: Long): Int? {
        if (count < 1) return null
        var bits = 0
        while ((1L shl bits) < count) bits++
        val p = parent.prefix + bits
        return if (p <= 32) p else null
    }

    /** Superred que contiene a la red dada con el prefijo más corto [newPrefix]. */
    fun supernet(net: Ipv4Network, newPrefix: Int): Ipv4Network? =
        if (newPrefix in 0..net.prefix) Ipv4Network(net.network, newPrefix) else null

    /** Superred mínima que agrega todas las redes indicadas. */
    fun summarize(nets: List<Ipv4Network>): Ipv4Network? {
        if (nets.isEmpty()) return null
        val low = nets.minOf { it.network }
        val high = nets.maxOf { it.broadcast }
        var p = 32
        while (p > 0 && !Ipv4Network(low, p).contains(high)) p--
        return Ipv4Network(Ipv4Network(low, p).network, p)
    }
}

object Vlsm {
    data class Request(val name: String, val hosts: Long)

    data class Allocation(
        val name: String,
        val hostsRequested: Long,
        val subnet: Ipv4Network,
    ) {
        val hostsAvailable: Long get() = subnet.usableHosts
        val wasted: Long get() = hostsAvailable - hostsRequested
    }

    data class Plan(
        val base: Ipv4Network,
        val allocations: List<Allocation>,
        val usedAddresses: Long,
        val freeBlocks: List<Ipv4Network>,
    ) {
        val totalAddresses: Long get() = base.totalAddresses
        val usagePercent: Double get() = usedAddresses * 100.0 / totalAddresses
    }

    /**
     * Asigna subredes de tamaño variable ordenando de mayor a menor necesidad,
     * así cada bloque queda alineado a su tamaño.
     */
    fun plan(base: Ipv4Network, requests: List<Request>): ParseResult<Plan> {
        if (requests.isEmpty()) return ParseResult.Error("Añade al menos una subred")
        val baseNet = Ipv4Network(base.network, base.prefix)
        val sorted = requests.withIndex().sortedWith(
            compareByDescending<IndexedValue<Request>> { it.value.hosts }.thenBy { it.index }
        )
        var cursor = baseNet.network
        val end = baseNet.broadcast
        val result = mutableListOf<Allocation>()
        for ((_, req) in sorted) {
            val p = Subnetting.prefixForHosts(req.hosts)
                ?: return ParseResult.Error("\"${req.name}\": número de hosts no válido")
            if (p < baseNet.prefix) {
                return ParseResult.Error("\"${req.name}\" necesita /$p, que no cabe en ${baseNet.cidr()}")
            }
            val size = 1L shl (32 - p)
            // alinear (por seguridad; con orden descendente ya lo está)
            val aligned = (cursor + size - 1) / size * size
            if (aligned + size - 1 > end) {
                return ParseResult.Error("No queda espacio para \"${req.name}\" (${req.hosts} hosts) en ${baseNet.cidr()}")
            }
            result += Allocation(req.name, req.hosts, Ipv4Network(aligned, p))
            cursor = aligned + size
        }
        val used = result.sumOf { it.subnet.totalAddresses }
        val free = freeBlocks(cursor, end)
        return ParseResult.Ok(Plan(baseNet, result, used, free))
    }

    /** Descompone el rango libre [start, end] en bloques CIDR alineados. */
    fun freeBlocks(start: Long, end: Long): List<Ipv4Network> {
        val out = mutableListOf<Ipv4Network>()
        var s = start
        while (s <= end) {
            var p = 32
            while (p > 0) {
                val bigger = p - 1
                val size = 1L shl (32 - bigger)
                if (s % size != 0L || s + size - 1 > end) break
                p = bigger
            }
            out += Ipv4Network(s, p)
            s += 1L shl (32 - p)
        }
        return out
    }
}
