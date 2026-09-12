package com.scrapider.finance.androidapp.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

private const val LIGHT_SIGNAL_TINT_ALPHA = 0.08f
private const val DARK_SIGNAL_TINT_ALPHA = 0.14f
private const val SIGNAL_FOREGROUND_ALPHA = 0.84f

@Immutable
internal data class FinanceSignalColors(
    val positiveContainer: Color = Color.Unspecified,
    val onPositiveContainer: Color = Color.Unspecified,
    val negativeContainer: Color = Color.Unspecified,
    val onNegativeContainer: Color = Color.Unspecified,
    val warningContainer: Color = Color.Unspecified,
    val onWarningContainer: Color = Color.Unspecified,
    val neutralContainer: Color = Color.Unspecified,
    val onNeutralContainer: Color = Color.Unspecified,
)

/** 派生现有主题的信号色，不安装新主题，也不依赖行情的局部 UI 库。 */
@Composable
internal fun rememberFinanceSignalColors(): FinanceSignalColors {
    val colors = MaterialTheme.colorScheme
    val semanticColors = LocalFinanceSemanticColors.current
    val isDark = isSystemInDarkTheme()
    return remember(colors, semanticColors, isDark) {
        val tintAlpha = if (isDark) DARK_SIGNAL_TINT_ALPHA else LIGHT_SIGNAL_TINT_ALPHA
        // 合成为不透明颜色，避免底层容器改变标签对比度；涨跌语义沿用全局主题。
        FinanceSignalColors(
            positiveContainer = semanticColors.positive.copy(alpha = tintAlpha).compositeOver(colors.surface),
            onPositiveContainer = semanticColors.positive.copy(alpha = SIGNAL_FOREGROUND_ALPHA)
                .compositeOver(colors.onSurface),
            negativeContainer = semanticColors.negative.copy(alpha = tintAlpha).compositeOver(colors.surface),
            onNegativeContainer = semanticColors.negative.copy(alpha = SIGNAL_FOREGROUND_ALPHA)
                .compositeOver(colors.onSurface),
            warningContainer = semanticColors.warning.copy(alpha = tintAlpha).compositeOver(colors.surface),
            onWarningContainer = semanticColors.warning.copy(alpha = SIGNAL_FOREGROUND_ALPHA)
                .compositeOver(colors.onSurface),
            neutralContainer = colors.surfaceVariant,
            onNeutralContainer = colors.onSurfaceVariant.copy(alpha = SIGNAL_FOREGROUND_ALPHA)
                .compositeOver(colors.onSurface),
        )
    }
}
