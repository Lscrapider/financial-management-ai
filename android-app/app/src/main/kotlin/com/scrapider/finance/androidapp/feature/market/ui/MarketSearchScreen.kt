package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketTargetSnapshot
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MarketSearchScreen(
    targets: List<MarketTargetSnapshot>,
    query: String,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTargetSelected: (MarketTargetSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val onSearchExpandedChange: (Boolean) -> Unit = { isExpanded ->
        if (!isExpanded && expanded) {
            // Miuix 的 SearchBar 会优先处理系统返回；此处直接沿用行情的手工回退路径。
            onNavigateBack()
        } else {
            expanded = isExpanded
        }
    }
    val matchingTargets = remember(targets, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            targets.filter { target ->
                target.targetName.contains(normalizedQuery, ignoreCase = true) ||
                    target.targetCode.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { MarketPageTopBar(title = "搜索行情", onNavigateBack = onNavigateBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = spacing.xl, vertical = spacing.lg),
        ) {
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = { },
                        expanded = expanded,
                        onExpandedChange = onSearchExpandedChange,
                        label = "名称或代码",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = if (query.isNotBlank()) {
                            {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "清除搜索内容",
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                },
                expanded = expanded,
                onExpandedChange = onSearchExpandedChange,
            ) { }
            Spacer(Modifier.height(spacing.lg))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = spacing.section),
            ) {
                when {
                    query.isBlank() -> item {
                        MarketEmptyPanel(text = "输入名称或代码开始搜索")
                    }

                    matchingTargets.isEmpty() -> item {
                        MarketEmptyPanel(text = "未找到匹配标的")
                    }

                    else -> itemsIndexed(
                        items = matchingTargets,
                        key = { _, target -> target.targetKey },
                        contentType = { _, _ -> "market-search-target" },
                    ) { index, target ->
                        MarketSearchTargetRow(
                            target = target,
                            onClick = { onTargetSelected(target) },
                        )
                        if (index < matchingTargets.lastIndex) {
                            top.yukonga.miuix.kmp.basic.HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketSearchTargetRow(
    target: MarketTargetSnapshot,
    onClick: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    BasicComponent(
        title = target.targetName,
        summary = "${target.targetCode} · ${target.targetType.marketTargetTypeLabel()}",
        onClick = onClick,
        onClickLabel = "查看${target.targetName}详情",
        endActions = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = target.latestPrice.asPriceText(),
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = target.changePercent.asPercentText(),
                    modifier = Modifier.widthIn(min = spacing.xxl * 3),
                    textAlign = TextAlign.End,
                    style = MiuixTheme.textStyles.body2,
                    color = target.changePercent.marketChangeColor(
                        positive = semanticColors.positive,
                        negative = semanticColors.negative,
                        neutral = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                )
            }
        },
    )
}
