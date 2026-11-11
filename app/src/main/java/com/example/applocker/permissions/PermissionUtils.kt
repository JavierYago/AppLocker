package com.example.applocker.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionState(
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
) {
    val allGranted: Boolean get() = usageAccess && notifications && overlay
}

object PermissionUtils {

    fun currentState(context: Context) = PermissionState(
        usageAccess = hasUsageAccess(context),
        notifications = canPostNotifications(context),
        overlay = canDrawOverlays(context),
    )

    /**
     * PACKAGE_USAGE_STATS es un permiso "especial" (nivel signature|appop): no se puede
     * pedir con el diálogo normal ni comprobar con checkSelfPermission(). El usuario lo
     * concede en Ajustes y nosotros consultamos el resultado a través de AppOps.
     */
    @Suppress("DEPRECATION") // cada variante está obsoleta en un rango distinto de APIs
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canPostNotifications(context: Context): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        // En Android < 13 no hay permiso, pero el usuario puede haberlas silenciado en Ajustes.
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Abre Ajustes > Acceso al uso (directamente en nuestra app si el fabricante lo permite). */
    fun openUsageAccessSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(context))
        if (!context.tryStart(direct)) {
            context.tryStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    fun openOverlaySettings(context: Context) {
        val direct = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))
        if (!context.tryStart(direct)) {
            context.tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.tryStart(intent)
    }

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    private fun Context.tryStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
