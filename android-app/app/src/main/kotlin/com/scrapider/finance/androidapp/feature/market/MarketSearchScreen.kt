package com.scrapider.finance.androidapp.feature.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MarketTopAppBar(title = "搜索行情", onNavigateBack = onNavigateBack) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = spacing.xl,
                top = spacing.lg,
                end = spacing.xl,
                bottom = spacing.section,
            ),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称或代码") },
                    singleLine = true,
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }
            when {
                query.isBlank() -> {
                    item { MarketSearchEmptyPanel(text = "输入名称或代码开始搜索") }
                }

                matchingTargets.isEmpty() -> {
                    item { MarketSearchEmptyPanel(text = "未找到匹配标的") }
                }

                else -> {
                    itemsIndexed(
                        items = matchingTargets,
                        key = { _, target -> target.targetKey },
                        contentType = { _, _ -> "market-search-target" },
                    ) { index, target ->
                        MarketSearchTargetRow(
                            target = target,
                            onClick = { onTargetSelected(target) },
                        )
                        if (index < matchingTargets.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val dimensions = LocalFinanceDimensions.current
    val semanticColors = LocalFinanceSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.listRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.targetName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = target.targetCode + " · " + target.targetType.marketTargetTypeLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = target.latestPrice.asPriceText(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = target.changePercent.asPercentText(),
                modifier = Modifier.widthIn(min = spacing.xxl * 3),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium,
                color = target.changePercent.marketChangeColor(
                    positive = semanticColors.positive,
                    negative = semanticColors.negative,
                    neutral = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun MarketSearchEmptyPanel(text: String) {
    val spacing = LocalFinanceSpacing.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.compactRowHeight),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(spacing.lg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
