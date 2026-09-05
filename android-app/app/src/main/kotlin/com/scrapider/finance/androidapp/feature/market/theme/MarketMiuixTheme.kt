package com.scrapider.finance.androidapp.feature.market.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 行情模块的局部 Miuix 主题。
 *
 * FinanceTheme 仍是应用全局主题；这里仅把其金融蓝、语义色和排版映射给 Miuix。
 */
@Composable
fun MarketMiuixTheme(content: @Composable () -> Unit) {
    val materialColors = MaterialTheme.colorScheme
    val materialTypography = MaterialTheme.typography
    val semanticColors = LocalFinanceSemanticColors.current
    val isDark = isSystemInDarkTheme()
    val colors = remember(materialColors, semanticColors, isDark) {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = materialColors.primary,
            onPrimary = materialColors.onPrimary,
            primaryVariant = materialColors.primaryContainer,
            onPrimaryVariant = materialColors.onPrimaryContainer,
            primaryContainer = materialColors.primaryContainer,
            onPrimaryContainer = materialColors.onPrimaryContainer,
            error = materialColors.error,
            onError = materialColors.onError,
            errorContainer = materialColors.errorContainer,
            onErrorContainer = materialColors.onErrorContainer,
            secondary = materialColors.secondary,
            onSecondary = materialColors.onSecondary,
            secondaryVariant = materialColors.surfaceVariant,
            onSecondaryVariant = materialColors.onSurfaceVariant,
            secondaryContainer = materialColors.surfaceVariant,
            onSecondaryContainer = materialColors.onSurface,
            secondaryContainerVariant = materialColors.surfaceVariant,
            onSecondaryContainerVariant = materialColors.onSurfaceVariant,
            tertiaryContainer = materialColors.secondaryContainer,
            onTertiaryContainer = materialColors.onSecondaryContainer,
            tertiaryContainerVariant = materialColors.secondaryContainer,
            background = materialColors.background,
            onBackground = materialColors.onBackground,
            onBackgroundVariant = materialColors.onSurfaceVariant,
            surface = materialColors.surface,
            onSurface = materialColors.onSurface,
            surfaceVariant = materialColors.surfaceVariant,
            onSurfaceSecondary = materialColors.onSurfaceVariant,
            onSurfaceVariantSummary = materialColors.onSurfaceVariant,
            onSurfaceVariantActions = materialColors.primary,
            disabledOnSurface = materialColors.onSurfaceVariant,
            surfaceContainer = materialColors.surfaceVariant,
            onSurfaceContainer = materialColors.onSurface,
            onSurfaceContainerVariant = materialColors.onSurfaceVariant,
            surfaceContainerHigh = materialColors.surfaceVariant,
            onSurfaceContainerHigh = materialColors.onSurface,
            surfaceContainerHighest = materialColors.surfaceVariant,
            onSurfaceContainerHighest = materialColors.onSurface,
            outline = materialColors.outline,
            dividerLine = materialColors.outlineVariant,
            sliderKeyPoint = semanticColors.warning,
            sliderKeyPointForeground = materialColors.onPrimary,
            sliderBackground = materialColors.surfaceVariant,
        )
    }
    val textStyles = remember(materialTypography) {
        defaultTextStyles(
            main = materialTypography.bodyMedium,
            paragraph = materialTypography.bodyMedium,
            body1 = materialTypography.bodyMedium,
            body2 = materialTypography.bodySmall,
            button = materialTypography.labelLarge,
            footnote1 = materialTypography.labelMedium,
            footnote2 = materialTypography.bodySmall,
            headline1 = materialTypography.headlineSmall,
            headline2 = materialTypography.titleMedium,
            subtitle = materialTypography.bodyMedium,
            title1 = materialTypography.displaySmall,
            title2 = materialTypography.headlineSmall,
            title3 = materialTypography.titleMedium,
            title4 = materialTypography.labelLarge,
        )
    }
    MiuixTheme(colors = colors, textStyles = textStyles, content = content)
}
