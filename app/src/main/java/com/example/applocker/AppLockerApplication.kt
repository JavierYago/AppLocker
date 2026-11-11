package com.example.applocker

import android.app.Application
import android.content.Context
import com.example.applocker.data.AlertStateStore
import com.example.applocker.data.AppLimitRepository
import com.example.applocker.data.InstalledAppsRepository
import com.example.applocker.data.local.AppDatabase
import com.example.applocker.notifications.NotificationHelper

class AppLockerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.createChannels()
    }
}

/**
 * Inyección de dependencias manual: un único sitio donde se crean los objetos compartidos
 * por la UI y el servicio. Suficiente para este tamaño de app; si crece, se puede
 * sustituir por Hilt sin tocar las clases que consume.
 */
class AppContainer(context: Context) {
    private val database by lazy { AppDatabase.create(context) }

    val limitRepository by lazy { AppLimitRepository(database.appLimitDao()) }
    val installedAppsRepository by lazy { InstalledAppsRepository(context) }
    val alertStateStore by lazy { AlertStateStore(context) }
    val notificationHelper by lazy { NotificationHelper(context) }
}
