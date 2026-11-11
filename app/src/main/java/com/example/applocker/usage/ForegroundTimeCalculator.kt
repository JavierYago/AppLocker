package com.example.applocker.usage

/** Copia mínima de [android.app.usage.UsageEvents.Event], sin dependencias de Android. */
data class UsageEvent(
    val packageName: String,
    val className: String?,
    val type: Int,
    val timestamp: Long,
)

/**
 * Reconstruye el tiempo en primer plano de cada app a partir del flujo de eventos del
 * sistema. Es el núcleo del motor: Kotlin puro, sin Android, para poder testearlo.
 *
 * Por qué eventos y no `queryUsageStats()`: las estadísticas agregadas se guardan en
 * "cubos" que el sistema vuelca a disco cuando quiere y que no cortan en la medianoche
 * local, así que pueden ir minutos por detrás o mezclar días. Emparejando a mano
 * ACTIVITY_RESUMED / ACTIVITY_PAUSED obtenemos el tiempo exacto, al segundo.
 *
 * Una app está en primer plano mientras tenga al menos una actividad reanudada. Llevamos
 * el conjunto de actividades abiertas por paquete para que navegar entre pantallas de la
 * misma app (A pausa, B reanuda) no cierre el intervalo antes de tiempo.
 *
 * @param dayStart inicio del día (epoch ms). Se le pueden pasar eventos anteriores para
 *   saber qué app estaba abierta al cruzar la medianoche: el tiempo previo a dayStart
 *   nunca se contabiliza.
 */
class ForegroundTimeCalculator(private val dayStart: Long) {

    private class OpenInterval(val start: Long) {
        val activities = HashSet<String>()
    }

    private val totals = HashMap<String, Long>()
    private val open = HashMap<String, OpenInterval>()
    private val seen = HashSet<String>()
    private var lastResumedPackage: String? = null
    private var lastTimestamp = 0L

    fun process(event: UsageEvent) {
        val pkg = event.packageName
        when (event.type) {
            ACTIVITY_RESUMED -> {
                seen += pkg
                // Si la app se abrió ayer y sigue abierta, hoy solo cuenta desde dayStart.
                open.getOrPut(pkg) { OpenInterval(maxOf(event.timestamp, dayStart)) }
                    .activities += event.className.orEmpty()
                lastResumedPackage = pkg
            }

            ACTIVITY_PAUSED, ACTIVITY_STOPPED -> {
                val firstEventOfPackage = seen.add(pkg)
                val interval = open[pkg]
                if (interval == null) {
                    // Pausa sin reanudación previa: la app ya estaba abierta al empezar
                    // el día (cruzó la medianoche), así que cuenta desde dayStart.
                    if (firstEventOfPackage && event.type == ACTIVITY_PAUSED) {
                        add(pkg, event.timestamp - dayStart)
                    }
                } else {
                    interval.activities -= event.className.orEmpty()
                    if (interval.activities.isEmpty()) {
                        add(pkg, event.timestamp - interval.start)
                        open -= pkg
                    }
                }
            }

            // Con la pantalla apagada o el dispositivo apagándose nada está "en uso".
            SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> closeAll(event.timestamp)

            // Arranque sin apagado limpio previo (batería agotada, cuelgue): lo que
            // siguiera abierto terminó, como muy tarde, en el último evento registrado.
            DEVICE_STARTUP -> closeAll(lastTimestamp)
        }
        lastTimestamp = event.timestamp
    }

    /** App que el usuario tiene delante ahora mismo, o null si no hay ninguna. */
    fun foregroundPackage(): String? {
        lastResumedPackage?.let { if (it in open) return it }
        return open.maxByOrNull { it.value.start }?.key
    }

    /** Tiempo de uso acumulado por paquete, incluyendo los intervalos aún abiertos. */
    fun usageByPackage(now: Long): Map<String, Long> {
        val result = HashMap(totals)
        for ((pkg, interval) in open) {
            result[pkg] = (result[pkg] ?: 0L) + (now - interval.start).coerceAtLeast(0L)
        }
        return result
    }

    private fun closeAll(timestamp: Long) {
        for ((pkg, interval) in open) add(pkg, timestamp - interval.start)
        open.clear()
    }

    private fun add(pkg: String, durationMs: Long) {
        if (durationMs > 0) totals[pkg] = (totals[pkg] ?: 0L) + durationMs
    }

    companion object {
        // Valores de UsageEvents.Event. Se copian porque varios se añadieron en la API 29
        // y minSdk es 26 (en 26-28 los tipos 1 y 2 se llamaban MOVE_TO_FOREGROUND/BACKGROUND).
        const val ACTIVITY_RESUMED = 1
        const val ACTIVITY_PAUSED = 2
        const val SCREEN_NON_INTERACTIVE = 16
        const val ACTIVITY_STOPPED = 23
        const val DEVICE_SHUTDOWN = 26
        const val DEVICE_STARTUP = 27
    }
}
