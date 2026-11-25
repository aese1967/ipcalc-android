package com.aese.ipcalc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aese.ipcalc.scan.NetworkController
import kotlinx.coroutines.launch

private data class Tab(val title: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Calculadora IPv4", "IPv4", Icons.Filled.Calculate),
    Tab("Calculadora IPv6", "IPv6", Icons.Filled.Language),
    Tab("Subredes", "Subredes", Icons.Filled.AccountTree),
    Tab("Escáner de red", "Escáner", Icons.Filled.WifiFind),
    Tab("Herramientas", "Herram.", Icons.Filled.Build),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(controller: NetworkController) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var toolsHost by rememberSaveable { mutableStateOf<String?>(null) }
    var calcInput by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    CompositionLocalProvider(LocalNotify provides notify) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(tabs[tab].title) }) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.title) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> key(calcInput) { Ipv4Screen(calcInput) }
                    1 -> Ipv6Screen()
                    2 -> SubnetScreen()
                    3 -> ScannerScreen(
                        controller,
                        onOpenPorts = { ip -> toolsHost = ip; tab = 4 },
                        onCalc = { cidr -> calcInput = cidr; tab = 0 },
                    )
                    else -> ToolsScreen(controller, toolsHost) { toolsHost = null }
                }
            }
        }
    }
}
