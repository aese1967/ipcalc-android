import com.aese.ipcalc.scan.*
import org.junit.Assert.*
import org.junit.Test
class ScanTest {
  @Test fun dnsRoundtrip() {
    val q = Dns.query(listOf("_http._tcp.local"), 12)
    val m = Dns.parse(q, q.size)!!
    assertFalse(m.isResponse)
    assertEquals(0, m.records.size)
  }
  @Test fun nbstat() {
    val r = NetBios.nbstatRequest(); assertEquals(50, r.size)
    assertEquals("CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", String(r, 13, 32))
    val resp = ByteArray(56) + byteArrayOf(1) + "MIPC           ".toByteArray() + byteArrayOf(0, 0x04, 0) + byteArrayOf(0x11,0x22,0x33,0x44,0x55,0x66)
    val info = NetBios.parseNbstat(resp, resp.size)!!
    assertEquals("MIPC", info.name); assertEquals("11:22:33:44:55:66", info.mac)
  }
  @Test fun ports() {
    assertEquals(listOf(22, 80, 81, 82), Services.parseList("80-82, 22"))
    assertNull(Services.parseList("0-5"))
    assertEquals("Raspberry Pi", Oui.vendor("B8:27:EB:00:11:22"))
    assertEquals("MAC aleatoria/privada", Oui.vendor("DA:27:EB:00:11:22"))
  }
  @Test fun localTcp() {
    val ss = java.net.ServerSocket(0)
    val r = HostProbe.tcp("127.0.0.1", ss.localPort, 500); ss.close()
    assertTrue(r.open)
    val r2 = HostProbe.tcp("127.0.0.1", ss.localPort, 500)
    assertTrue(r2.alive && !r2.open)
  }
}
