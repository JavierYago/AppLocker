package com.example.applocker.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.applocker.MainActivity
import com.example.applocker.R
import com.example.applocker.permissions.PermissionUtils
import com.example.applocker.util.formatDuration

class NotificationHelper(private val context: Context) {

    /**
     * Desde Android 8.0 (API 26) toda notificación debe ir en un canal. Crear un canal que
     * ya existe no hace nada, así que se llama sin miedo en cada arranque de la app.
     * Como minSdk es 26 no hace falta comprobar la versión.
     */
    fun createChannels() {
        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.channel_monitor_name),
            NotificationManager.IMPORTANCE_LOW, // silenciosa: es la notificación permanente
        ).apply {
            description = context.getString(R.string.channel_monitor_description)
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH, // aparece en pantalla (heads-up) y suena
        ).apply {
            description = context.getString(R.string.channel_alerts_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(monitor, alerts))
    }

    /** Notificación permanente obligatoria para el servicio en primer plano. */
    fun buildMonitorNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.monitor_notification_title))
            .setContentText(context.getString(R.string.monitor_notification_text))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun showRemainingAlert(packageName: String, appLabel: String, remainingMs: Long, limitMs: Long) {
        // Se redondea hacia arriba para no decir "quedan 29 min" justo al cruzar los 30.
        val remaining = formatDuration(remainingMs + 59_999L)
        val text = context.getString(
            R.string.alert_notification_text,
            formatDuration(limitMs - remainingMs),
            formatDuration(limitMs),
        )
        notify(
            packageName,
            alertBuilder()
                .setContentTitle(context.getString(R.string.alert_notification_title, remaining, appLabel))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    fun showLimitReached(packageName: String, appLabel: String, limitMs: Long) {
        val text = context.getString(R.string.limit_reached_notification_text, formatDuration(limitMs))
        notify(
            packageName,
            alertBuilder()
                .setContentTitle(context.getString(R.string.limit_reached_notification_title, appLabel))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    private fun alertBuilder() = NotificationCompat.Builder(context, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_timer)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(openAppIntent())
        .setAutoCancel(true)

    /**
     * Una notificación por app (el id sale del paquete): el aviso de "quedan 10 min"
     * sustituye al de "quedan 30 min" en vez de apilarse.
     */
    @SuppressLint("MissingPermission") // comprobado en canPostNotifications()
    private fun notify(packageName: String, notification: Notification) {
        if (!PermissionUtils.canPostNotifications(context)) return
        NotificationManagerCompat.from(context).notify(packageName, ALERT_NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERTS = "alerts"
        const val MONITOR_NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2
    }
}
