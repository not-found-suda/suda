package com.ssafy.mobile.core.ui.theme

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = SudaPrimaryDark,
        onPrimary = SudaOnPrimaryDark,
        primaryContainer = SudaPrimaryContainerDark,
        onPrimaryContainer = SudaOnPrimaryContainerDark,
        secondary = SudaSecondaryDark,
        onSecondary = SudaOnSecondaryDark,
        secondaryContainer = SudaSecondaryContainerDark,
        onSecondaryContainer = SudaOnSecondaryContainerDark,
        tertiary = SudaTertiaryDark,
        onTertiary = SudaOnTertiaryDark,
        tertiaryContainer = SudaTertiaryContainerDark,
        onTertiaryContainer = SudaOnTertiaryContainerDark,
        background = SudaBackgroundDark,
        onBackground = SudaOnBackgroundDark,
        surface = SudaSurfaceDark,
        onSurface = SudaOnSurfaceDark,
        surfaceVariant = SudaSurfaceVariantDark,
        onSurfaceVariant = SudaOnSurfaceVariantDark,
        outline = SudaOutlineDark,
        outlineVariant = SudaOutlineVariantDark,
        error = SudaErrorDark,
        onError = SudaOnErrorDark,
        errorContainer = SudaErrorContainerDark,
        onErrorContainer = SudaOnErrorContainerDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = SudaPrimaryLight,
        onPrimary = SudaOnPrimaryLight,
        primaryContainer = SudaPrimaryContainerLight,
        onPrimaryContainer = SudaOnPrimaryContainerLight,
        secondary = SudaSecondaryLight,
        onSecondary = SudaOnSecondaryLight,
        secondaryContainer = SudaSecondaryContainerLight,
        onSecondaryContainer = SudaOnSecondaryContainerLight,
        tertiary = SudaTertiaryLight,
        onTertiary = SudaOnTertiaryLight,
        tertiaryContainer = SudaTertiaryContainerLight,
        onTertiaryContainer = SudaOnTertiaryContainerLight,
        background = SudaBackgroundLight,
        onBackground = SudaOnBackgroundLight,
        surface = SudaSurfaceLight,
        onSurface = SudaOnSurfaceLight,
        surfaceVariant = SudaSurfaceVariantLight,
        onSurfaceVariant = SudaOnSurfaceVariantLight,
        outline = SudaOutlineLight,
        outlineVariant = SudaOutlineVariantLight,
        error = SudaErrorLight,
        onError = SudaOnErrorLight,
        errorContainer = SudaErrorContainerLight,
        onErrorContainer = SudaOnErrorContainerLight,
    )

@Composable
fun MobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
