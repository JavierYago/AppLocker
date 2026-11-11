package com.example.applocker.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.example.applocker.R

/**
 * Pantalla "Límite de tiempo alcanzado" dibujada encima de la app bloqueada mediante una
 * ventana TYPE_APPLICATION_OVERLAY (permiso SYSTEM_ALERT_WINDOW). Ocupa toda la pantalla
 * y se queda con los toques, así que la app de debajo deja de ser utilizable.
 *
 * Todos los métodos deben llamarse desde el hilo principal.
 */
class BlockOverlayController(
    private val context: Context,
    private val onGoHome: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlay: View? = null

    val isShowing: Boolean get() = overlay != null

    fun show(appLabel: String) {
        val message = context.getString(R.string.block_message, appLabel)
        overlay?.let {
            it.findViewById<TextView>(R.id.block_message).text = message
            return
        }

        // Un Service no tiene tema visual: se le da uno para que el botón se vea bien.
        val themed = ContextThemeWrapper(context, android.R.style.Theme_Material_NoActionBar)
        val root = object : FrameLayout(themed) {
            // El botón Atrás no debe devolver al usuario a la app bloqueada.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onGoHome()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        LayoutInflater.from(themed).inflate(R.layout.overlay_block, root, true)
        root.findViewById<TextView>(R.id.block_message).text = message
        root.findViewById<Button>(R.id.block_go_home).setOnClickListener { onGoHome() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            windowManager.addView(root, params)
            overlay = root
        } catch (_: RuntimeException) {
            // Permiso revocado entre la comprobación y el addView: no hay overlay.
        }
    }

    fun hide() {
        val view = overlay ?: return
        overlay = null
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // La vista ya no estaba adjunta.
        }
    }
}
