package com.aese.ipcalc.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aese.ipcalc.scan.NetworkController
import com.aese.ipcalc.ui.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private val Light = lightColorScheme(
    primary = Color(0xFF00658E),
    secondary = Color(0xFF4E616D),
    tertiary = Color(0xFF00897B),
    tertiaryContainer = Color(0xFFB2F0E6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD0FF),
    secondary = Color(0xFFB5C9D7),
    tertiary = Color(0xFF6FDBC9),
    tertiaryContainer = Color(0xFF00504A),
)

@Composable
private fun DesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val controller = remember { NetworkController(DesktopPlatform(), scope) }
    DisposableEffect(Unit) {
        onDispose {
            controller.dispose()
            scope.cancel()
        }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "IPCalc Red",
        icon = painterResource("icon.png"),
        state = rememberWindowState(size = DpSize(520.dp, 860.dp)),
    ) {
        DesktopTheme { App(controller) }
    }
}
