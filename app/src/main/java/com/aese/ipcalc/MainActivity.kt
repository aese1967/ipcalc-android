package com.aese.ipcalc

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aese.ipcalc.scan.AndroidPlatform
import com.aese.ipcalc.scan.NetworkController
import com.aese.ipcalc.ui.App
import com.aese.ipcalc.ui.IpCalcTheme

/** Mantiene el controlador vivo durante rotaciones de pantalla. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val controller = NetworkController(AndroidPlatform(app), viewModelScope)

    override fun onCleared() {
        controller.dispose()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            IpCalcTheme { App(vm.controller) }
        }
    }
}
