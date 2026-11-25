package com.aese.ipcalc.scan

import com.aese.ipcalc.core.Ipv4Network
import java.net.NetworkInterface
import java.nio.charset.Charset

data class LocalNetwork(
    val interfaceName: String?,
    val transport: String,
    val ipv4: Ipv4Network?,
    val gateway: String?,
    val dns: List<String>,
    val ipv6: List<String>,
)

/** Lo que cambia entre Android y Windows/escritorio. */
interface Platform {
    fun readLocal(): LocalNetwork?

    /** Activa la recepción multicast si el sistema lo exige; devuelve cómo liberarla. */
    fun acquireMulticastLock(): (() -> Unit)? = null

    /** Interfaz por la que enviar multicast (null = la que elija el sistema). */
    fun multicastInterface(): NetworkInterface? = null

    fun pingCommand(host: String, count: Int): List<String> =
        if (OS.isWindows) listOf("ping", "-n", count.toString(), "-w", "2000", host)
        else listOf("ping", "-c", count.toString(), "-W", "2", host)

    val consoleCharset: Charset get() = OS.consoleCharset
}

object OS {
    val isWindows: Boolean = System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true

    /** Codificación de la consola de Windows (cp850 en español); UTF-8 en el resto. */
    val consoleCharset: Charset by lazy {
        if (!isWindows) Charsets.UTF_8
        else runCatching {
            val p = ProcessBuilder("cmd", "/c", "chcp").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val cp = Regex("(\\d{3,5})").find(out)?.value
            Charset.forName(if (cp == "65001") "UTF-8" else "cp$cp")
        }.getOrDefault(Charset.forName("cp850"))
    }
}
