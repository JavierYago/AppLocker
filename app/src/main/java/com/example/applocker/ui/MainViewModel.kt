package com.example.applocker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.applocker.AppLockerApplication
import com.example.applocker.domain.model.AppLimit
import com.example.applocker.domain.model.InstalledApp
import com.example.applocker.permissions.PermissionState
import com.example.applocker.permissions.PermissionUtils
import com.example.applocker.service.UsageMonitorService
import com.example.applocker.usage.UsageSnapshot
import com.example.applocker.usage.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una fila de la lista: la app, su límite (si tiene) y lo que se ha usado hoy. */
data class AppRow(
    val app: InstalledApp,
    val limit: AppLimit?,
    val usedTodayMs: Long,
)

data class MainUiState(
    val loading: Boolean = true,
    val apps: List<AppRow> = emptyList(),
    val permissions: PermissionState = PermissionState(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as AppLockerApplication).container
    private val tracker = UsageTracker(application)

    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val usage = MutableStateFlow<UsageSnapshot?>(null)
    private val permissions = MutableStateFlow(PermissionUtils.currentState(application))

    val uiState: StateFlow<MainUiState> = combine(
        installedApps,
        container.limitRepository.observeLimits(),
        usage,
        permissions,
    ) { apps, limits, snapshot, perms ->
        val limitsByPackage = limits.associateBy { it.packageName }
        MainUiState(
            loading = apps == null,
            apps = apps.orEmpty()
                .map { app ->
                    AppRow(
                        app = app,
                        limit = limitsByPackage[app.packageName],
                        usedTodayMs = snapshot?.usageMs(app.packageName) ?: 0L,
                    )
                }
                // Primero las apps con límite; dentro de cada grupo, orden alfabético.
                .sortedBy { it.limit == null },
            permissions = perms,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            installedApps.value = container.installedAppsRepository.getLaunchableApps()
        }
    }

    /**
     * Se llama cada vez que la pantalla vuelve a primer plano: el usuario puede venir de
     * conceder un permiso en Ajustes, y el uso del día habrá cambiado.
     */
    fun refresh() {
        val app = getApplication<Application>()
        val perms = PermissionUtils.currentState(app)
        permissions.value = perms

        viewModelScope.launch {
            if (perms.usageAccess) {
                usage.value = withContext(Dispatchers.IO) { tracker.refresh() }
            }
            if (container.limitRepository.hasEnabledLimits()) {
                UsageMonitorService.start(app)
            }
        }
    }

    fun saveLimit(packageName: String, dailyLimitMinutes: Int, alerts: List<Int>, enabled: Boolean) {
        viewModelScope.launch {
            container.limitRepository.save(
                AppLimit(
                    packageName = packageName,
                    dailyLimitMinutes = dailyLimitMinutes,
                    alertThresholdsMinutes = alerts.sortedDescending(),
                    enabled = enabled,
                ),
            )
            // Con un límite nuevo, los avisos ya lanzados hoy dejan de ser válidos.
            container.alertStateStore.clear(packageName)
            if (enabled) UsageMonitorService.start(getApplication())
        }
    }

    fun deleteLimit(packageName: String) {
        viewModelScope.launch {
            container.limitRepository.delete(packageName)
            container.alertStateStore.clear(packageName)
        }
    }
}
