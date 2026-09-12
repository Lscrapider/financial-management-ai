package com.scrapider.finance.androidapp.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private const val CHROME_PRIMARY_FRACTION = 0.35f

/** A「雾蓝视界」的工具栏表面；从既有主题派生，正文仍使用 surface。 */
@Composable
internal fun financeChromeColor(): Color = lerp(
    MaterialTheme.colorScheme.surface,
    MaterialTheme.colorScheme.primaryContainer,
    CHROME_PRIMARY_FRACTION,
)
