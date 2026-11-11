package com.example.applocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.applocker.ui.AppListScreen
import com.example.applocker.ui.LimitConfigScreen
import com.example.applocker.ui.MainViewModel
import com.example.applocker.ui.theme.AppLockerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppLockerTheme {
                AppLockerApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Ajustes (permisos) o de otra app: reevaluar permisos y uso.
        viewModel.refresh()
    }
}

@Composable
private fun AppLockerApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Navegación mínima entre dos pantallas: la lista y la configuración de una app.
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRow = state.apps.firstOrNull { it.app.packageName == selectedPackage }

    if (selectedRow == null) {
        AppListScreen(
            state = state,
            onConfigure = { selectedPackage = it },
            onPermissionsChanged = viewModel::refresh,
        )
    } else {
        val close = { selectedPackage = null }
        BackHandler(onBack = close)
        LimitConfigScreen(
            row = selectedRow,
            onBack = close,
            onSave = { minutes, alerts, enabled ->
                viewModel.saveLimit(selectedRow.app.packageName, minutes, alerts, enabled)
                close()
            },
            onDelete = {
                viewModel.deleteLimit(selectedRow.app.packageName)
                close()
            },
        )
    }
}
