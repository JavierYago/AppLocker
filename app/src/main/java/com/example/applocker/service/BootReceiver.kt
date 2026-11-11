package com.example.applocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.applocker.AppLockerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Reanuda la vigilancia tras reiniciar el dispositivo o actualizar la app. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val container = (context.applicationContext as AppLockerApplication).container
        // goAsync() mantiene vivo el receiver mientras se consulta la base de datos.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (container.limitRepository.hasEnabledLimits()) {
                    UsageMonitorService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
