package com.example.applocker.domain.model

/**
 * Límite diario configurado por el usuario para una app.
 *
 * @param alertThresholdsMinutes minutos restantes a los que se avisa (p. ej. [30, 10]).
 */
data class AppLimit(
    val packageName: String,
    val dailyLimitMinutes: Int,
    val alertThresholdsMinutes: List<Int> = emptyList(),
    val enabled: Boolean = true,
) {
    val dailyLimitMs: Long get() = dailyLimitMinutes * 60_000L

    companion object {
        const val MAX_DAILY_LIMIT_MINUTES = 24 * 60
    }
}
