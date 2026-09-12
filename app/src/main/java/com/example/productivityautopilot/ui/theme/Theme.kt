package com.example.productivityautopilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.productivityautopilot.notification.AppTheme

private val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF80F3FF),
    secondary = MidnightSecondary,
    onSecondary = Color(0xFF003251),
    secondaryContainer = Color(0xFF004975),
    onSecondaryContainer = Color(0xFFCBE6FF),
    tertiary = MidnightTertiary,
    background = MidnightBackground,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineColorDark,
    error = ErrorRed
)

private val OceanColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = OceanSecondary,
    onSecondary = Color(0xFF00344D),
    secondaryContainer = Color(0xFF004C6D),
    onSecondaryContainer = Color(0xFFCBE6FF),
    tertiary = OceanTertiary,
    background = OceanBackground,
    onBackground = TextPrimaryDark,
    surface = OceanSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF223348),
    error = ErrorRed
)

private val ForestColorScheme = darkColorScheme(
    primary = ForestPrimary,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005230),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = ForestSecondary,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005230),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = ForestTertiary,
    background = ForestBackground,
    onBackground = TextPrimaryDark,
    surface = ForestSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ForestSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF223B2A),
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = LightTertiary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = OutlineColorLight,
    error = Color(0xFFDC2626)
)

@Composable
fun ProductivityAutopilotTheme(
    theme: AppTheme = AppTheme.MIDNIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        AppTheme.MIDNIGHT -> MidnightColorScheme
        AppTheme.OCEAN -> OceanColorScheme
        AppTheme.FOREST -> ForestColorScheme
        AppTheme.LIGHT -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
