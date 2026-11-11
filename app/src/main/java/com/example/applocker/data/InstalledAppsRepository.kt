package com.example.applocker.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.example.applocker.domain.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lista las apps "lanzables": las que declaran una actividad MAIN/LAUNCHER, es decir,
 * exactamente las que el usuario ve en su cajón de aplicaciones. Esto descarta de forma
 * natural los servicios y componentes internos del sistema, pero mantiene apps de sistema
 * que sí se usan (Chrome, YouTube, Ajustes...).
 *
 * Requiere el bloque <queries> del AndroidManifest para funcionar en Android 11+.
 */
class InstalledAppsRepository(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        queryActivities(launcherIntent)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName } // no nos limitamos a nosotros mismos
            .distinctBy { it.packageName } // una app puede tener varios iconos de launcher
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.applicationInfo.loadLabel(packageManager).toString(),
                    icon = info.applicationInfo.loadIcon(packageManager)
                        .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Nombre visible de un paquete, o el propio nombre de paquete si ya no está instalado. */
    fun getLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
            .toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

    private companion object {
        const val ICON_SIZE_PX = 96
    }
}
