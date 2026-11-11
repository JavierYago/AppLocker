package com.example.applocker.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/** Foto del uso en un instante: qué app está delante y cuánto se ha usado hoy cada una. */
data class UsageSnapshot(
    val timestamp: Long,
    val dayStart: Long,
    val foregroundPackage: String?,
    private val usageByPackage: Map<String, Long>,
) {
    fun usageMs(packageName: String): Long = usageByPackage[packageName] ?: 0L
}

/**
 * Envuelve a [UsageStatsManager] y mantiene el uso del día actualizado de forma
 * incremental: la primera llamada lee los eventos desde medianoche y las siguientes solo
 * los ocurridos desde la última consulta. Así el servicio puede consultar cada segundo
 * sin releer todo el día, que es lo que haría caro (en batería) el sondeo.
 *
 * Requiere el permiso especial de "Acceso al uso"; sin él el sistema devuelve una lista
 * de eventos vacía (no lanza excepción).
 */
class UsageTracker(context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private var calculator: ForegroundTimeCalculator? = null
    private var dayStart = 0L
    private var lastQueryEnd = 0L
    private var lastFullSync = 0L

    @Synchronized
    fun refresh(now: Long = System.currentTimeMillis()): UsageSnapshot {
        val today = startOfDay(now)
        var calc = calculator
        // Se recalcula todo desde medianoche al arrancar, al cambiar de día y cada pocos
        // minutos, para corregir cualquier evento que el sistema entregue con retraso.
        if (calc == null || today != dayStart || now - lastFullSync > FULL_SYNC_INTERVAL_MS) {
            calc = ForegroundTimeCalculator(today)
            calculator = calc
            dayStart = today
            // Se empieza un poco antes de medianoche para detectar una app que siga
            // abierta desde ayer; el calculador descarta el tiempo anterior a dayStart.
            lastQueryEnd = today - MIDNIGHT_LOOKBACK_MS
            lastFullSync = now
        }

        val events = usageStatsManager.queryEvents(lastQueryEnd, now)
        val event = UsageEvents.Event()
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            calc.process(UsageEvent(pkg, event.className, event.eventType, event.timeStamp))
        }
        lastQueryEnd = now

        return UsageSnapshot(now, today, calc.foregroundPackage(), calc.usageByPackage(now))
    }

    companion object {
        private const val FULL_SYNC_INTERVAL_MS = 5 * 60_000L
        private const val MIDNIGHT_LOOKBACK_MS = 3 * 60 * 60_000L

        /** Medianoche local del día al que pertenece [now]. */
        fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
