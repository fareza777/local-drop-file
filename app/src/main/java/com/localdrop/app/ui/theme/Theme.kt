package com.localdrop.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF3155CA), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EDFF), onPrimaryContainer = Color(0xFF203573),
    secondary = Color(0xFF526079), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EDF5), onSecondaryContainer = Color(0xFF29344B),
    tertiary = Color(0xFF526079), background = Color(0xFFF4F5F9), onBackground = Color(0xFF1B2233),
    surface = Color.White, onSurface = Color(0xFF1B2233),
    surfaceVariant = Color(0xFFEBEEF5), onSurfaceVariant = Color(0xFF58637A),
    outline = Color(0xFF8B94AA), outlineVariant = Color(0xFFDFE3EE)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFBFFF), onPrimary = Color(0xFF172759),
    primaryContainer = Color(0xFF26375C), onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFBBC5DC), onSecondary = Color(0xFF1F2C45),
    secondaryContainer = Color(0xFF2A3346), onSecondaryContainer = Color(0xFFDDE4F5),
    tertiary = Color(0xFFBBC5DC), background = Color(0xFF11151F), onBackground = Color(0xFFEEF1FA),
    surface = Color(0xFF1C2230), onSurface = Color(0xFFEEF1FA),
    surfaceVariant = Color(0xFF2A3142), onSurfaceVariant = Color(0xFFB9C2D8),
    outline = Color(0xFF8994AC), outlineVariant = Color(0xFF394357)
)
@Composable
fun LocalDropTheme(themeMode: String = "System", dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        val context = androidx.compose.ui.platform.LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
