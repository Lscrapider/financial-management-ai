package com.scrapider.finance.androidapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color

val WorkspaceBackground = Color(0xFF14161A)
val WorkspaceSurface = Color(0xFF1C1E23)
val WorkspaceSurfaceElevated = Color(0xFF24272D)
val WorkspaceSurfaceMuted = Color(0xFF2E3033)
val WorkspaceBorder = Color(0xFF36363A)
val WorkspaceForeground = Color(0xFFF2F2F2)
val WorkspaceOnSurface = Color(0xFFE1E2EC)
val WorkspaceMuted = Color(0xFFAEB4BD)
val CommandBlue = Color(0xFF006BE6)
val CommandBlueSoft = Color(0xFF123A63)
val PrimaryFixed = Color(0xFFD8E2FF)
val PrimaryFixedDim = Color(0xFFAEC6FF)
val SignalRed = Color(0xFFDC4446)
val SignalGreen = Color(0xFF57D188)
val SignalAmber = Color(0xFFEFBD48)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = PrimaryFixedDim,
    onPrimary = Color(0xFF002E6A),
    primaryContainer = CommandBlue,
    onPrimaryContainer = Color(0xFFF4F5FF),
    secondary = SignalGreen,
    onSecondary = Color(0xFF00391D),
    tertiary = SignalAmber,
    background = WorkspaceBackground,
    onBackground = WorkspaceOnSurface,
    surface = WorkspaceSurface,
    onSurface = WorkspaceOnSurface,
    surfaceVariant = WorkspaceSurfaceMuted,
    onSurfaceVariant = WorkspaceMuted,
    error = SignalRed,
    outline = WorkspaceBorder,
)

@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * fontScale.coerceIn(1.0f, 1.30f))
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = DarkScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
