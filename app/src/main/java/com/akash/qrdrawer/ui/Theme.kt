package com.akash.qrdrawer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SignalBlue = Color(0xFF2166E8)
private val Ink = Color(0xFF1B2029)
private val Paper = Color(0xFFF6F7F9)
private val Night = Color(0xFF121821)

@Composable
fun QrDrawerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF8DB4FF),
            onPrimary = Color(0xFF08295E),
            background = Night,
            surface = Color(0xFF1B2430),
            surfaceVariant = Color(0xFF243140),
            onBackground = Color(0xFFE8EDF5),
            onSurface = Color(0xFFE8EDF5),
            onSurfaceVariant = Color(0xFF9AA7B6),
            outline = Color(0xFF536171),
            error = Color(0xFFFF8A80),
        )
    } else {
        lightColorScheme(
            primary = SignalBlue,
            onPrimary = Color.White,
            background = Paper,
            surface = Color.White,
            surfaceVariant = Color(0xFFF1F4F8),
            onBackground = Ink,
            onSurface = Ink,
            onSurfaceVariant = Color(0xFF626B78),
            outline = Color(0xFFD4D9E0),
            error = Color(0xFFC62828),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
