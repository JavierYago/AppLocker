package com.example.applocker.domain.model

import android.graphics.Bitmap

/** App instalada que el usuario puede abrir desde el launcher. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
)
