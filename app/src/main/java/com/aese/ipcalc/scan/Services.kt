package com.aese.ipcalc.scan

object Services {
    val names: Map<Int, String> = mapOf(
        20 to "FTP-data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        67 to "DHCP", 69 to "TFTP", 80 to "HTTP", 88 to "Kerberos", 110 to "POP3", 111 to "RPC",
        123 to "NTP", 135 to "MS-RPC", 137 to "NetBIOS-NS", 139 to "NetBIOS/SMB", 143 to "IMAP",
        161 to "SNMP", 179 to "BGP", 389 to "LDAP", 443 to "HTTPS", 445 to "SMB", 465 to "SMTPS",
        502 to "Modbus", 515 to "LPD", 548 to "AFP", 554 to "RTSP", 587 to "SMTP-sub", 631 to "IPP",
        636 to "LDAPS", 873 to "rsync", 993 to "IMAPS", 995 to "POP3S", 1080 to "SOCKS",
        1194 to "OpenVPN", 1433 to "MS-SQL", 1521 to "Oracle", 1723 to "PPTP", 1883 to "MQTT",
        1900 to "UPnP", 2049 to "NFS", 2375 to "Docker", 3000 to "HTTP-dev", 3306 to "MySQL",
        3389 to "RDP", 3702 to "WS-Discovery", 5000 to "UPnP/HTTP", 5060 to "SIP", 5353 to "mDNS",
        5432 to "PostgreSQL", 5555 to "ADB", 5900 to "VNC", 6379 to "Redis", 7000 to "AirPlay",
        8000 to "HTTP-alt", 8008 to "HTTP-alt", 8009 to "Chromecast", 8080 to "HTTP-proxy",
        8081 to "HTTP-alt", 8443 to "HTTPS-alt", 8883 to "MQTT-TLS", 8888 to "HTTP-alt",
        9000 to "HTTP-alt", 9100 to "Impresora RAW", 9200 to "Elasticsearch", 27017 to "MongoDB",
        37777 to "DVR Dahua", 34567 to "DVR XMEye", 49152 to "UPnP", 62078 to "iPhone sync",
    )

    val common: List<Int> = listOf(
        21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 161, 443, 445, 515, 554, 631, 993, 1433, 1883,
        1900, 3306, 3389, 5000, 5060, 5353, 5432, 5555, 5900, 7000, 8000, 8008, 8009, 8080, 8443,
        8888, 9000, 9100, 34567, 37777, 49152, 62078,
    )

    fun name(port: Int): String = names[port] ?: ""

    /** "22,80,1000-1010" -> lista de puertos. */
    fun parseList(text: String): List<Int>? {
        val out = sortedSetOf<Int>()
        for (raw in text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }) {
            if (raw.contains('-')) {
                val (a, b) = raw.split('-', limit = 2).map { it.trim().toIntOrNull() ?: return null }
                if (a !in 1..65535 || b !in 1..65535 || a > b) return null
                for (p in a..b) out += p
            } else {
                val p = raw.toIntOrNull() ?: return null
                if (p !in 1..65535) return null
                out += p
            }
        }
        return out.toList().takeIf { it.isNotEmpty() }
    }
}

/** Pequeña tabla de fabricantes por prefijo MAC (OUI). */
object Oui {
    private val table = mapOf(
        "00:1A:11" to "Google", "F4:F5:D8" to "Google", "3C:5A:B4" to "Google", "D8:6C:63" to "Google",
        "00:17:88" to "Philips Hue", "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi", "28:CD:C1" to "Raspberry Pi", "00:0C:29" to "VMware",
        "00:50:56" to "VMware", "08:00:27" to "VirtualBox", "52:54:00" to "QEMU/KVM",
        "00:15:5D" to "Hyper-V", "F0:9F:C2" to "Ubiquiti", "24:A4:3C" to "Ubiquiti",
        "78:8A:20" to "Ubiquiti", "FC:EC:DA" to "Ubiquiti", "00:0C:42" to "MikroTik",
        "4C:5E:0C" to "MikroTik", "6C:3B:6B" to "MikroTik", "E4:8D:8C" to "MikroTik",
        "18:FD:74" to "MikroTik", "00:1B:21" to "Intel", "3C:97:0E" to "Intel",
        "A4:4C:C8" to "Dell", "F8:B1:56" to "Dell", "00:14:22" to "Dell", "3C:D9:2B" to "HP",
        "00:1E:0B" to "HP", "9C:B6:54" to "HP", "00:00:48" to "Epson", "64:EB:8C" to "Epson",
        "00:80:77" to "Brother", "30:05:5C" to "Brother", "00:26:AB" to "Seiko Epson",
        "A8:5E:45" to "ASUS", "04:D9:F5" to "ASUS", "2C:FD:A1" to "ASUS",
        "50:C7:BF" to "TP-Link", "C0:4A:00" to "TP-Link", "98:DA:C4" to "TP-Link", "60:32:B1" to "TP-Link",
        "C8:3A:35" to "Tenda", "00:E0:4C" to "Realtek",
        "5C:0A:5B" to "Samsung", 
        "F0:18:98" to "Apple", "A4:83:E7" to "Apple", "3C:22:FB" to "Apple", "AC:BC:32" to "Apple",
        "BC:D0:74" to "Apple", "F4:0F:24" to "Apple", "64:16:66" to "Nest", "18:B4:30" to "Nest",
        "44:65:0D" to "Amazon", "FC:65:DE" to "Amazon", "68:54:FD" to "Amazon",
        "00:04:4B" to "NVIDIA", "48:B0:2D" to "NVIDIA", "BC:83:85" to "Microsoft",
        "28:18:78" to "Microsoft", "7C:1E:52" to "Microsoft", "24:0A:C4" to "Espressif",
        "30:AE:A4" to "Espressif", "A4:CF:12" to "Espressif", "84:F3:EB" to "Espressif",
        "EC:FA:BC" to "Espressif", "BC:DD:C2" to "Espressif", "00:12:17" to "Cisco-Linksys",
        "00:1D:7E" to "Cisco-Linksys", "00:40:96" to "Cisco", "00:1B:54" to "Cisco",
        "00:09:0F" to "Fortinet", 
        "00:11:32" to "Synology", "00:08:9B" to "QNAP", "24:5E:BE" to "QNAP",
        "BC:AD:28" to "Hikvision", "44:19:B6" to "Hikvision", "C0:56:E3" to "Hikvision",
        "3C:EF:8C" to "Dahua", "E0:50:8B" to "Dahua", "9C:8E:CD" to "Amcrest",
        "00:24:E4" to "Withings", "D0:73:D5" to "LIFX", "B0:BE:76" to "TP-Link",
        "70:3A:CB" to "Google", "00:1E:C2" to "Apple", "00:25:00" to "Apple",
        "34:29:8F" to "Huawei", "48:46:FB" to "Huawei", "00:E0:FC" to "Huawei",
        "64:09:80" to "Xiaomi", "78:11:DC" to "Xiaomi", "50:EC:50" to "Xiaomi",
        "00:1F:A7" to "Sony", "FC:F1:52" to "Sony", 
        "00:0E:58" to "Sonos", "B8:E9:37" to "Sonos", "5C:AA:FD" to "Sonos",
        "00:1C:C0" to "Intel", "00:21:6A" to "Intel",
        "00:30:48" to "Supermicro", "AC:1F:6B" to "Supermicro", "00:25:90" to "Supermicro",
        "00:05:CD" to "Denon", "00:10:18" to "Broadcom",
        "00:90:A9" to "Western Digital", "00:14:EE" to "Western Digital", "00:0D:B9" to "PC Engines",
        "00:04:A3" to "Microchip", "00:26:5A" to "D-Link",
        "1C:7E:E5" to "D-Link", "B0:C5:54" to "D-Link", "C8:D7:19" to "Cisco-Linksys",
        "00:1F:33" to "Netgear", "A0:40:A0" to "Netgear", "9C:3D:CF" to "Netgear",
        "00:0F:B5" to "Netgear", "00:24:B2" to "Netgear", 
        "00:1C:DF" to "Belkin", "94:10:3E" to "Belkin", "EC:1A:59" to "Belkin",
        "00:23:69" to "Cisco-Linksys", "00:0B:86" to "Aruba", "24:DE:C6" to "Aruba",
        "00:1A:1E" to "Aruba", "70:4F:57" to "TP-Link", "14:CC:20" to "TP-Link",
        "00:19:E0" to "TP-Link", "F8:1A:67" to "TP-Link", "00:1E:58" to "D-Link",
        "00:04:0E" to "AVM (FRITZ!Box)", "3C:A6:2F" to "AVM (FRITZ!Box)",
        "C8:0E:14" to "AVM (FRITZ!Box)", "E0:28:6D" to "AVM (FRITZ!Box)", "00:1F:3F" to "AVM (FRITZ!Box)",
        "00:22:6B" to "Cisco-Linksys", "00:18:0A" to "Cisco Meraki", "88:15:44" to "Cisco Meraki",
        "E0:CB:BC" to "Cisco Meraki", "00:0D:3A" to "Microsoft", "00:12:5A" to "Microsoft",
    )

    fun vendor(mac: String?): String? {
        if (mac == null || mac.length < 8) return null
        val first = mac.substring(0, 2).toIntOrNull(16) ?: return null
        if (first and 0x02 != 0) return "MAC aleatoria/privada"
        return table[mac.substring(0, 8).uppercase()]
    }
}
