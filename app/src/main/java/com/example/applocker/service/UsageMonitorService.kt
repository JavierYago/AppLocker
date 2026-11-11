package com.example.applocker.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.applocker.AppLockerApplication
import com.example.applocker.domain.model.AppLimit
import com.example.applocker.notifications.NotificationHelper
import com.example.applocker.permissions.PermissionUtils
import com.example.applocker.usage.LimitEvaluator
import com.example.applocker.usage.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio en primer plano: el "vigilante" de la app.
 *
 * Mientras la pantalla está encendida comprueba cada 1-2 segundos qué app está delante y
 * cuánto se ha usado hoy cada app limitada; lanza los avisos de tiempo restante y, al
 * agotarse el límite, muestra la pantalla de bloqueo sobre la app.
 *
 * Batería: con la pantalla apagada el bucle se detiene por completo (no hay nada que
 * medir), cada comprobación solo lee los eventos nuevos desde la anterior, y el servicio
 * se para solo si no queda ningún límite activo.
 */
class UsageMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val screenOn = MutableStateFlow(true)

    private lateinit var tracker: UsageTracker
    private lateinit var notifications: NotificationHelper
    private lateinit var overlay: BlockOverlayController
    private val labels = HashMap<String, String>()

    /** Tras pulsar "Ir a inicio" se da un margen para que el launcher pase a primer plano. */
    private var suppressBlockUntil = 0L
    private var lastHomeFallback = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as AppLockerApplication).container
        tracker = UsageTracker(this)
        notifications = container.notificationHelper
        overlay = BlockOverlayController(this, onGoHome = ::onGoHomePressed)

        startInForeground()

        screenOn.value = getSystemService(PowerManager::class.java).isInteractive
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            val activeLimits = container.limitRepository.observeLimits()
                .combine(screenOn) { limits, on -> limits.filter { it.enabled } to on }
                .distinctUntilChanged()
            // collectLatest cancela el bucle anterior cada vez que cambian los límites o
            // el estado de la pantalla, y arranca uno nuevo con los datos actuales.
            activeLimits.collectLatest { (limits, on) ->
                when {
                    limits.isEmpty() -> {
                        hideOverlay()
                        stopSelf()
                    }
                    !on -> hideOverlay()
                    else -> monitor(limits)
                }
            }
        }
    }

    // START_STICKY: si el sistema mata el proceso por falta de memoria, lo recrea después.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(screenReceiver)
        overlay.hide()
        super.onDestroy()
    }

    private fun startInForeground() {
        // Desde Android 14 es obligatorio indicar el tipo de servicio en primer plano.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.MONITOR_NOTIFICATION_ID,
            notifications.buildMonitorNotification(),
            type,
        )
    }

    private suspend fun monitor(limits: List<AppLimit>) {
        val limitedPackages = limits.mapTo(HashSet()) { it.packageName }
        val alertState = (application as AppLockerApplication).container.alertStateStore

        while (currentCoroutineContext().isActive) {
            if (!PermissionUtils.hasUsageAccess(this)) {
                // Sin "Acceso al uso" no hay datos: se espera a que el usuario lo conceda.
                hideOverlay()
                delay(NO_PERMISSION_INTERVAL_MS)
                continue
            }

            val snapshot = tracker.refresh()
            val foreground = snapshot.foregroundPackage
            var appToBlock: AppLimit? = null

            for (limit in limits) {
                val pkg = limit.packageName
                val result = LimitEvaluator.evaluate(
                    limit = limit,
                    usedMs = snapshot.usageMs(pkg),
                    alreadyFired = alertState.firedThresholds(snapshot.dayStart, pkg),
                )
                alertState.markFired(snapshot.dayStart, pkg, result.newlyFired)

                if (result.alertToFire != null) {
                    notifications.showRemainingAlert(pkg, labelOf(pkg), result.remainingMs, limit.dailyLimitMs)
                }
                if (result.notifyLimitReached) {
                    notifications.showLimitReached(pkg, labelOf(pkg), limit.dailyLimitMs)
                }
                if (result.limitReached && pkg == foreground) appToBlock = limit
            }

            val target = appToBlock
            withContext(Dispatchers.Main) {
                if (target != null) block(target) else overlay.hide()
            }

            // Más frecuente cuando hay una app limitada delante (su contador está corriendo).
            delay(if (foreground in limitedPackages) FAST_INTERVAL_MS else SLOW_INTERVAL_MS)
        }
    }

    private fun block(limit: AppLimit) {
        val now = SystemClock.elapsedRealtime()
        if (now < suppressBlockUntil) return

        if (PermissionUtils.canDrawOverlays(this)) {
            overlay.show(labelOf(limit.packageName))
        } else if (now - lastHomeFallback > HOME_FALLBACK_INTERVAL_MS) {
            // Sin permiso de superposición solo queda intentar mandar al usuario al
            // inicio. Android 10+ suele impedir abrir actividades desde segundo plano,
            // así que es un "mejor esfuerzo": la notificación de límite alcanzado ya salió.
            lastHomeFallback = now
            goHome()
        }
    }

    private fun onGoHomePressed() {
        suppressBlockUntil = SystemClock.elapsedRealtime() + GO_HOME_GRACE_MS
        overlay.hide()
        goHome()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(home)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo abrir la pantalla de inicio", e)
        }
    }

    private suspend fun hideOverlay() = withContext(Dispatchers.Main) { overlay.hide() }

    private fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        (application as AppLockerApplication).container.installedAppsRepository.getLabel(packageName)
    }

    companion object {
        private const val TAG = "UsageMonitorService"
        private const val FAST_INTERVAL_MS = 1_000L
        private const val SLOW_INTERVAL_MS = 2_000L
        private const val NO_PERMISSION_INTERVAL_MS = 10_000L
        private const val GO_HOME_GRACE_MS = 2_500L
        private const val HOME_FALLBACK_INTERVAL_MS = 5_000L

        /**
         * Arranca la vigilancia. Es seguro llamarlo de más: si ya está en marcha no pasa
         * nada, y si no hay límites activos el propio servicio se detiene.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, UsageMonitorService::class.java),
                )
            } catch (e: RuntimeException) {
                // Android 12+ no deja arrancar servicios en primer plano con la app en
                // segundo plano (ForegroundServiceStartNotAllowedException). Se
                // reintentará la próxima vez que el usuario abra la app.
                Log.w(TAG, "No se pudo iniciar el servicio de vigilancia", e)
            }
        }
    }
}
