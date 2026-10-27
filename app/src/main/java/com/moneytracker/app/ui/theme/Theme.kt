package com.moneytracker.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE5),
    onPrimaryContainer = Color(0xFF104E43),
    secondary = SecondaryGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4F0EC),
    onSecondaryContainer = DarkInk,
    tertiary = LendAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBCD),
    onTertiaryContainer = Color(0xFF654210),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFFFCE9E6),
    onErrorContainer = Color(0xFF842D27),
    background = WarmWhite,
    onBackground = DarkInk,
    surface = Color.White,
    onSurface = DarkInk,
    surfaceVariant = LightGray,
    onSurfaceVariant = Color(0xFF647770),
    outline = Color(0xFF96A8A0),
    outlineVariant = BorderSoft
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF89D7BE),
    onPrimary = Color(0xFF083A31),
    primaryContainer = Color(0xFF24594F),
    onPrimaryContainer = Color(0xFFD4F5E8),
    secondary = Color(0xFFA3CEBD),
    onSecondary = Color(0xFF15372D),
    secondaryContainer = Color(0xFF2C483F),
    onSecondaryContainer = Color(0xFFDEEEE5),
    tertiary = Color(0xFFF4C886),
    onTertiary = Color(0xFF553A12),
    tertiaryContainer = Color(0xFF60491D),
    onTertiaryContainer = Color(0xFFFFE7BD),
    error = Color(0xFFFFAAA0),
    onError = Color(0xFF6D211D),
    errorContainer = Color(0xFF74322C),
    onErrorContainer = Color(0xFFFFDAD5),
    background = DarkBackground,
    onBackground = Color(0xFFE5F1EB),
    surface = DarkSurface,
    onSurface = Color(0xFFE5F1EB),
    surfaceVariant = Color(0xFF253A35),
    onSurfaceVariant = Color(0xFFAABEB4),
    outline = Color(0xFF81968B),
    outlineVariant = Color(0xFF365047)
)

@Composable
fun MoneyTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Keep consistent finance colors by default across Android versions.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
