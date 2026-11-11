package com.example.applocker.util

/** "1 h 30 min", "45 min", "2 h" o "< 1 min". */
fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes <= 0L -> if (durationMs > 0L) "< 1 min" else "0 min"
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h $minutes min"
    }
}

fun formatMinutes(minutes: Int): String = formatDuration(minutes * 60_000L)
