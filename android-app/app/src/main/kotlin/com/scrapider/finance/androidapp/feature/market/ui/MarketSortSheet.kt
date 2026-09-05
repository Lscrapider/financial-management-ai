package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import com.scrapider.finance.androidapp.feature.market.MarketSortOption
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MarketSortSheet(
    selectedOption: MarketSortOption,
    onDismiss: () -> Unit,
    onSelectOption: (MarketSortOption) -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "排序方式",
        onDismissRequest = onDismiss,
    ) {
        MarketSortOption.entries.forEach { option ->
            BasicComponent(
                title = option.label,
                onClick = { onSelectOption(option) },
                onClickLabel = "选择${option.label}",
                endActions = if (option == selectedOption) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "当前排序方式",
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
