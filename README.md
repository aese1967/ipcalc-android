# IPCalc Red

App para **Android** y **Windows** (Kotlin + Compose) para cálculo de direcciones IP y escaneo de red local.
Las dos versiones comparten el mismo código (carpeta `common/`).

| Carpeta | Contenido |
|---|---|
| `common/` | Lógica de cálculo, escáner y pantallas (compartido) |
| `app/` | Arranque y red específicos de Android |
| `desktop/` | Arranque y red para Windows (Compose Desktop) |

## Funciones

**Calculadora IPv4**
- Entrada como `10.0.0.1/22`, `10.0.0.1 255.255.252.0`, wildcard `10.0.0.1/0.0.3.255` o solo la IP (máscara de clase).
- Máscara, wildcard, red, broadcast, primer/último host, hosts utilizables (con casos /31 y /32), clase, tipo de dirección (privada RFC 1918, CGNAT, loopback, enlace local, documentación, multicast…), hexadecimal, entero y PTR.
- Representación binaria con los bits de red resaltados.
- Tabla de máscaras /0–/32 y tabla de clases A–E.
- Toca cualquier resultado para copiarlo.

**Calculadora IPv6**
- Forma comprimida (RFC 5952) y expandida, red, primera/última dirección, total de direcciones, tipo (global, ULA, enlace local, multicast, documentación, IPv4 mapeada, 6to4, Teredo, NAT64), PTR `ip6.arpa`.
- División en subredes de cualquier prefijo.

**Subredes**
- Dividir una red por prefijo, por número de subredes (`x4`) o por hosts necesarios.
- VLSM: lista de subredes con nombre y hosts; asigna de mayor a menor y muestra el espacio libre.
- Superred / resumen de rutas a partir de varias redes.

**Escáner de red**
- Datos de la red actual: IP, máscara, puerta de enlace, DNS, IPv6.
- Barrido de la subred (ping del sistema + sondeo TCP, detecta equipos que bloquean ICMP).
- Descubrimiento multicast/broadcast: **mDNS/Bonjour, SSDP/UPnP, WS-Discovery (cámaras ONVIF, impresoras) y NetBIOS**. Estos mensajes alcanzan todo el segmento físico, así que revelan equipos con IP de **otra red** (aparecen marcados "otra subred").
- Nombre (DNS inverso, NetBIOS, mDNS, UPnP), MAC (NetBIOS / tabla ARP cuando Android lo permite) y fabricante.

**Herramientas**
- Escáner de puertos TCP (comunes, 1–1024 o lista personalizada).
- Ping.
- "IPs por defecto": prueba ~40 direcciones de fábrica habituales de routers, cámaras y equipos.

### Encontrar un equipo con IP de otra red

Android sin root no puede enviar ARP ni barrer cualquier red, así que no es posible recorrer "todas las redes".
Con el equipo conectado a la misma Wi-Fi o switch:
1. Escáner con la opción **Multicast** activada.
2. **IPs por defecto** en Herramientas.
3. Poner una **IP estática** en el móvil dentro del rango sospechado (Ajustes › Wi-Fi › red › Configuración IP › Estática) y escanear.

## Versión Windows

Mismas pantallas que en Android. En el PC además:
- El barrido usa ICMP real (sin ser administrador) y lee la tabla ARP con `arp -a`, así aparecen con su MAC también los equipos que bloquean el ping.
- Elige automáticamente la tarjeta de red de la ruta por defecto (ignora adaptadores virtuales) para el multicast.
- Si Windows pregunta por el Firewall al escanear, pulsa **Permitir** (redes privadas) para recibir las respuestas mDNS/UPnP/NetBIOS.

## Compilar

GitHub Actions compila todo en cada push a `main` y lo publica en **Releases › latest**:
- `IPCalcRed.apk` (Android)
- `IPCalcRed-Setup.exe` (instalador Windows) y `IPCalcRed.msi`
- `IPCalcRed-portable-windows.zip` (descomprimir y ejecutar `IPCalcRed.exe`, no necesita instalar Java)

También quedan como artefactos en la pestaña **Actions**.

En local (Android Studio o línea de comandos con el SDK de Android instalado):

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # pruebas de la lógica de cálculo

# Solo Windows/escritorio (no necesita el SDK de Android)
SKIP_ANDROID=1 ./gradlew :desktop:run
SKIP_ANDROID=1 ./gradlew :desktop:packageExe   # desde Windows
```

Requisitos: JDK 17+, Android SDK 35. Android mínimo: 8.0 (API 26).

El APK va firmado con una clave fija de uso personal (`app/signing/ipcalc.jks`) para que cada versión nueva se instale encima de la anterior. Mantén el repositorio privado.
Para instalarlo, permite "instalar apps de origen desconocido" en el móvil.

> Usa el escáner solo en redes propias o con autorización.
