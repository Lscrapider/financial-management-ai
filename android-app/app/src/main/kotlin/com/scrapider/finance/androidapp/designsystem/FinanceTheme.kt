package com.scrapider.finance.androidapp.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape

@Immutable
data class FinanceSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val section: Dp = 32.dp,
)

@Immutable
data class FinanceDimensions(
    val minTouchTarget: Dp = 48.dp,
    val controlHeight: Dp = 52.dp,
    val compactRowHeight: Dp = 56.dp,
    val listRowHeight: Dp = 76.dp,
    val toolRowHeight: Dp = 96.dp,
    val iconSize: Dp = 24.dp,
    val outlineWidth: Dp = 1.dp,
)

@Immutable
data class FinanceSemanticColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
)

val LocalFinanceSpacing = staticCompositionLocalOf { FinanceSpacing() }
val LocalFinanceDimensions = staticCompositionLocalOf { FinanceDimensions() }
val LocalFinanceSemanticColors = staticCompositionLocalOf {
    FinanceSemanticColors(
        positive = Color.Unspecified,
        negative = Color.Unspecified,
        warning = Color.Unspecified,
    )
}

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkFinanceColorScheme else LightFinanceColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    CompositionLocalProvider(
        LocalFinanceSpacing provides FinanceSpacing(),
        LocalFinanceDimensions provides FinanceDimensions(),
        LocalFinanceSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinanceTypography,
            shapes = FinanceShapes,
            content = content,
        )
    }
}

private val LightFinanceColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF006BE6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F1FF),
    onPrimaryContainer = Color(0xFF003E8E),
    secondary = Color(0xFF52657D),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111722),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111722),
    surfaceVariant = Color(0xFFF2F5F8),
    onSurfaceVariant = Color(0xFF6D788D),
    outline = Color(0xFFBAC3CF),
    outlineVariant = Color(0xFFE1E6EC),
    error = Color(0xFFC72C2C),
)

private val DarkFinanceColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004A92),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBBC8DB),
    onSecondary = Color(0xFF253241),
    background = Color(0xFF10141B),
    onBackground = Color(0xFFE8ECF3),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFE8ECF3),
    surfaceVariant = Color(0xFF202731),
    onSurfaceVariant = Color(0xFFC2C8D4),
    outline = Color(0xFF8C96A6),
    outlineVariant = Color(0xFF3D4653),
    error = Color(0xFFFFB4AB),
)

private val LightSemanticColors = FinanceSemanticColors(
    positive = Color(0xFF078A3E),
    negative = Color(0xFFC72C2C),
    warning = Color(0xFFA66200),
)

private val DarkSemanticColors = FinanceSemanticColors(
    positive = Color(0xFF69DB9D),
    negative = Color(0xFFFFB4AB),
    warning = Color(0xFFFFC56B),
)

private val FinanceTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

private val FinanceShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
