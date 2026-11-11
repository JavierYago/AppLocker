package com.example.applocker.usage

import com.example.applocker.domain.model.AppLimit

/**
 * Decide, para una app y su uso de hoy, si toca avisar o bloquear. Kotlin puro: toda la
 * regla de negocio de avisos vive aquí y se puede testear sin dispositivo.
 */
object LimitEvaluator {

    /** Marca usada junto a los umbrales para recordar que ya se notificó "límite alcanzado". */
    const val LIMIT_REACHED_MARKER = 0

    data class Result(
        val remainingMs: Long,
        val limitReached: Boolean,
        /** Umbral (minutos restantes) por el que hay que notificar ahora, o null. */
        val alertToFire: Int?,
        /** true si hay que notificar ahora que se ha alcanzado el límite. */
        val notifyLimitReached: Boolean,
        /** Marcas que hay que guardar como ya lanzadas. */
        val newlyFired: List<Int>,
    )

    fun evaluate(limit: AppLimit, usedMs: Long, alreadyFired: Set<Int>): Result {
        val remainingMs = limit.dailyLimitMs - usedMs
        val limitReached = remainingMs <= 0

        // Umbrales cruzados y aún no avisados. Un umbral >= límite no tiene sentido
        // (saltaría con 0 minutos de uso), así que se ignora.
        val crossed = limit.alertThresholdsMinutes.filter { threshold ->
            threshold in 1 until limit.dailyLimitMinutes &&
                remainingMs <= threshold * 60_000L &&
                threshold !in alreadyFired
        }
        val notifyLimitReached = limitReached && LIMIT_REACHED_MARKER !in alreadyFired

        return Result(
            remainingMs = remainingMs.coerceAtLeast(0L),
            limitReached = limitReached,
            // Si se cruzan varios a la vez (p. ej. el servicio arrancó tarde) solo se
            // avisa del más pequeño, y ninguno si ya no queda tiempo.
            alertToFire = if (limitReached) null else crossed.minOrNull(),
            notifyLimitReached = notifyLimitReached,
            newlyFired = if (notifyLimitReached) crossed + LIMIT_REACHED_MARKER else crossed,
        )
    }
}
