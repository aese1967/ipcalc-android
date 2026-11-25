package com.aese.ipcalc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4Test {
    private fun net(s: String) = (Ipv4Parser.parse(s) as ParseResult.Ok).value

    @Test fun cidr22() {
        val n = net("10.0.0.1/22")
        assertEquals("255.255.252.0", Ipv4.format(n.mask))
        assertEquals("0.0.3.255", Ipv4.format(n.wildcard))
        assertEquals("10.0.0.0", Ipv4.format(n.network))
        assertEquals("10.0.3.255", Ipv4.format(n.broadcast))
        assertEquals("10.0.0.1", Ipv4.format(n.firstHost))
        assertEquals("10.0.3.254", Ipv4.format(n.lastHost))
        assertEquals(1022L, n.usableHosts)
    }

    @Test fun dottedMaskAndWildcard() {
        assertEquals(24, net("192.168.1.10 255.255.255.0").prefix)
        assertEquals(24, net("192.168.1.10/0.0.0.255").prefix)
        assertEquals(26, net("192.168.1.10/255.255.255.192").prefix)
    }

    @Test fun classful() {
        assertEquals(8, net("10.1.2.3").prefix)
        assertEquals(16, net("172.20.1.1").prefix)
        assertEquals(24, net("200.1.1.1").prefix)
    }

    @Test fun edgePrefixes() {
        assertEquals(2L, net("1.1.1.0/31").usableHosts)
        assertEquals(1L, net("1.1.1.1/32").usableHosts)
        assertEquals(4294967294L, net("0.0.0.0/0").usableHosts)
    }

    @Test fun invalid() {
        assertTrue(Ipv4Parser.parse("256.1.1.1/24") is ParseResult.Error)
        assertTrue(Ipv4Parser.parse("1.1.1.1/33") is ParseResult.Error)
        assertTrue(Ipv4Parser.parse("1.1.1.1/255.0.255.0") is ParseResult.Error)
        assertNull(Ipv4.parse("1.2.3"))
    }

    @Test fun types() {
        assertEquals("Privada (RFC 1918)", Ipv4.addressType(Ipv4.parse("172.31.0.1")!!))
        assertEquals("Pública", Ipv4.addressType(Ipv4.parse("8.8.8.8")!!))
        assertEquals("CGNAT compartida (RFC 6598)", Ipv4.addressType(Ipv4.parse("100.100.1.1")!!))
        assertEquals("1.1.168.192.in-addr.arpa", Ipv4.reversePointer(Ipv4.parse("192.168.1.1")!!))
    }

    @Test fun split() {
        val r = (Subnetting.split(net("192.168.0.0/24"), 26) as ParseResult.Ok).value
        assertEquals(4L, r.subnetCount)
        assertEquals("192.168.0.192/26", r.subnets[3].cidr())
        assertEquals(62L, r.hostsPerSubnet)
    }

    @Test fun prefixForHosts() {
        assertEquals(26, Subnetting.prefixForHosts(50))
        assertEquals(24, Subnetting.prefixForHosts(254))
        assertEquals(23, Subnetting.prefixForHosts(255))
        assertEquals(30, Subnetting.prefixForHosts(2))
    }

    @Test fun summarize() {
        val s = Subnetting.summarize(listOf(net("192.168.0.0/24"), net("192.168.3.0/24")))!!
        assertEquals("192.168.0.0/22", s.cidr())
    }

    @Test fun vlsm() {
        val plan = (Vlsm.plan(
            net("192.168.10.0/24"),
            listOf(Vlsm.Request("A", 20), Vlsm.Request("B", 100), Vlsm.Request("C", 2), Vlsm.Request("D", 50)),
        ) as ParseResult.Ok).value
        val byName = plan.allocations.associateBy { it.name }
        assertEquals("192.168.10.0/25", byName["B"]!!.subnet.cidr())
        assertEquals("192.168.10.128/26", byName["D"]!!.subnet.cidr())
        assertEquals("192.168.10.192/27", byName["A"]!!.subnet.cidr())
        assertEquals("192.168.10.224/30", byName["C"]!!.subnet.cidr())
        assertEquals(listOf("192.168.10.228/30", "192.168.10.232/29", "192.168.10.240/28"), plan.freeBlocks.map { it.cidr() })
    }

    @Test fun vlsmOverflow() {
        assertTrue(Vlsm.plan(net("10.0.0.0/28"), listOf(Vlsm.Request("x", 20))) is ParseResult.Error)
    }
}

class Ipv6Test {
    @Test fun compressAndExpand() {
        val v = Ipv6.parse("2001:0db8:0000:0000:0000:ff00:0042:8329")!!
        assertEquals("2001:db8::ff00:42:8329", Ipv6.compressed(v))
        assertEquals("2001:0db8:0000:0000:0000:ff00:0042:8329", Ipv6.expanded(Ipv6.parse("2001:db8::ff00:42:8329")!!))
        assertEquals("::1", Ipv6.compressed(Ipv6.parse("::1")!!))
        assertEquals("::", Ipv6.compressed(Ipv6.parse("::")!!))
        assertEquals("2001:db8:0:1:1:1:1:1", Ipv6.compressed(Ipv6.parse("2001:db8:0:1:1:1:1:1")!!))
        assertEquals("::ffff:c000:280", Ipv6.compressed(Ipv6.parse("::ffff:192.0.2.128")!!))
    }

    @Test fun invalid() {
        assertNull(Ipv6.parse("1::2::3"))
        assertNull(Ipv6.parse("12345::"))
        assertNull(Ipv6.parse("1:2:3:4:5:6:7"))
        assertNull(Ipv6.parse("1:2:3:4:5:6:7:8:9"))
    }

    @Test fun network() {
        val n = (Ipv6Parser.parse("2001:db8:abcd:12::5/48") as ParseResult.Ok).value
        assertEquals("2001:db8:abcd::/48", n.cidr())
        assertEquals("2001:db8:abcd:ffff:ffff:ffff:ffff:ffff", Ipv6.compressed(n.last))
        assertEquals("Documentación (RFC 3849)", Ipv6.addressType(n.address))
        assertEquals("Enlace local", Ipv6.addressType(Ipv6.parse("fe80::1")!!))
    }

    @Test fun split() {
        val n = (Ipv6Parser.parse("2001:db8::/48") as ParseResult.Ok).value
        val (count, list) = (Ipv6Parser.split(n, 64, 4) as ParseResult.Ok).value
        assertEquals(65536, count.toInt())
        assertEquals("2001:db8:0:3::/64", list[3].cidr())
    }
}
