package com.example.applocker.data

import android.content.Context
import androidx.core.content.edit

/**
 * Recuerda qué avisos se han lanzado ya hoy para no repetirlos en cada comprobación.
 * Se guarda en disco (SharedPreferences) para sobrevivir a reinicios del servicio, y se
 * vacía solo al cambiar de día.
 */
class AlertStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("alert_state", Context.MODE_PRIVATE)

    @Synchronized
    fun firedThresholds(dayStart: Long, packageName: String): Set<Int> {
        resetIfNewDay(dayStart)
        return prefs.getString(key(packageName), null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            .orEmpty()
    }

    @Synchronized
    fun markFired(dayStart: Long, packageName: String, thresholds: Collection<Int>) {
        if (thresholds.isEmpty()) return
        val updated = firedThresholds(dayStart, packageName) + thresholds
        prefs.edit { putString(key(packageName), updated.joinToString(",")) }
    }

    /** Al cambiar el límite de una app, sus avisos vuelven a estar pendientes. */
    @Synchronized
    fun clear(packageName: String) {
        prefs.edit { remove(key(packageName)) }
    }

    private fun resetIfNewDay(dayStart: Long) {
        if (prefs.getLong(KEY_DAY, -1L) != dayStart) {
            prefs.edit {
                clear()
                putLong(KEY_DAY, dayStart)
            }
        }
    }

    private fun key(packageName: String) = "fired:$packageName"

    private companion object {
        const val KEY_DAY = "day"
    }
}
