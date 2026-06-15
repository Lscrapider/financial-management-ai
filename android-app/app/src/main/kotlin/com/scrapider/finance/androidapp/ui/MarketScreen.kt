package com.scrapider.finance.androidapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrapider.finance.androidapp.data.MarketAssetType
import com.scrapider.finance.androidapp.data.MarketFilter
import com.scrapider.finance.androidapp.data.MarketQuote
import com.scrapider.finance.androidapp.data.MarketUiState
import com.scrapider.finance.androidapp.data.marketFilters
import java.text.DecimalFormat

@Composable
fun MarketScreen(
    loading: Boolean,
    statusMessage: String,
    market: MarketUiState,
    onRefresh: () -> Unit,
    onAssetTypeChange: (MarketAssetType) -> Unit,
    onMarketFilterChange: (MarketFilter) -> Unit,
    onSortByChangePercent: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onWorkbenchSelected: () -> Unit,
    onObservationSelected: () -> Unit,
    onReportSelected: () -> Unit,
) {
    Scaffold(
        containerColor = WorkspaceBackground,
        topBar = {
            MarketTopBar(
                updatedAt = market.updatedAt,
                loading = loading,
                onRefresh = onRefresh,
            )
        },
        bottomBar = {
            MarketBottomNav(
                onWorkbenchSelected = onWorkbenchSelected,
                onObservationSelected = onObservationSelected,
                onReportSelected = onReportSelected,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WorkspaceBackground)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssetSegmentedTabs(
                selected = market.assetType,
                onSelected = onAssetTypeChange,
            )
            MarketSearchBar(
                keyword = market.keyword,
                onKeywordChange = onKeywordChange,
            )
            MarketFilterChips(
                assetType = market.assetType,
                selected = market.marketFilter,
                onSelected = onMarketFilterChange,
            )
            MarketListCard(
                loading = loading,
                quotes = market.visibleQuotes,
                totalCount = market.quotes.size,
                sortOrder = market.sortOrder,
                onSortByChangePercent = onSortByChangePercent,
            )
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = WorkspaceMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MarketTopBar(
    updatedAt: String,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(WorkspaceSurface)
            .border(BorderStroke(1.dp, WorkspaceBorder.copy(alpha = 0.65f)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("☰", color = PrimaryFixedDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("行情中心", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${updatedAt} 更新", color = WorkspaceMuted, fontSize = 12.sp)
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "同步中" else "↻", color = WorkspaceMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun AssetSegmentedTabs(
    selected: MarketAssetType,
    onSelected: (MarketAssetType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkspaceSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MarketAssetType.entries.forEach { item ->
            val active = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(if (active) WorkspaceSurfaceElevated else Color.Transparent, RoundedCornerShape(6.dp))
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = if (active) WorkspaceBorder else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelected(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    color = if (active) WorkspaceForeground else WorkspaceMuted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MarketSearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("搜索股票、指数、可转债", color = WorkspaceMuted, fontSize = 13.sp) },
        leadingIcon = { Text("⌕", color = WorkspaceMuted, fontSize = 16.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(color = WorkspaceForeground, fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = WorkspaceForeground,
            unfocusedTextColor = WorkspaceForeground,
            focusedContainerColor = Color(0xFF191B23),
            unfocusedContainerColor = Color(0xFF191B23),
            focusedBorderColor = PrimaryFixedDim,
            unfocusedBorderColor = WorkspaceBorder,
            cursorColor = PrimaryFixedDim,
        ),
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun MarketFilterChips(
    assetType: MarketAssetType,
    selected: MarketFilter,
    onSelected: (MarketFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        marketFilters(assetType).forEach { filter ->
            val active = filter.marketCode == selected.marketCode
            Text(
                text = filter.label,
                modifier = Modifier
                    .background(if (active) CommandBlueSoft else Color(0xFF1D1F27), RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (active) CommandBlueSoft else WorkspaceBorder,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelected(filter) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                color = if (active) PrimaryFixedDim else WorkspaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MarketListCard(
    loading: Boolean,
    quotes: List<MarketQuote>,
    totalCount: Int,
    sortOrder: String,
    onSortByChangePercent: () -> Unit,
) {
    var visibleCount by remember { mutableIntStateOf(12) }
    val displayed = quotes.take(visibleCount)

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WorkspaceBorder),
        colors = CardDefaults.cardColors(containerColor = WorkspaceSurface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        MarketTableHeader(sortOrder = sortOrder, onSortByChangePercent = onSortByChangePercent)
        when {
            loading -> EmptyMarketRow("正在同步行情数据。")
            quotes.isEmpty() -> EmptyMarketRow(if (totalCount > 0) "当前搜索没有匹配标的。" else "后端暂无行情快照，刷新后会展示最新行情。")
            else -> displayed.forEach { quote -> MarketQuoteRow(quote) }
        }
        if (quotes.size > visibleCount) {
            Text(
                text = "展开更多行情",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF191B23))
                    .clickable { visibleCount += 20 }
                    .padding(vertical = 9.dp),
                color = PrimaryFixedDim,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MarketTableHeader(
    sortOrder: String,
    onSortByChangePercent: () -> Unit,
) {
    val arrow = if (sortOrder == "asc") "↑" else "↓"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkspaceSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("名称/代码", 1.25f, TextAlign.Start)
        HeaderCell("最新价", 1f, TextAlign.End)
        HeaderCell("涨跌幅 $arrow", 1f, TextAlign.End, PrimaryFixedDim, onClick = onSortByChangePercent)
        HeaderCell("成交额", 1f, TextAlign.End)
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: TextAlign,
    color: Color = WorkspaceMuted,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick == null) {
        Modifier.weight(weight)
    } else {
        Modifier.weight(weight).clickable { onClick() }
    }
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = align,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun MarketQuoteRow(quote: MarketQuote) {
    val directionColor = if (quote.changePercent >= 0.0) SignalRed else SignalGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1.25f)) {
            Text(
                text = quote.name,
                color = WorkspaceForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quote.code.ifBlank { quote.assetType.label },
                color = WorkspaceMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Text(
            text = formatMarketPrice(quote.latestPrice),
            color = directionColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMarketPercent(quote.changePercent),
            color = directionColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatTurnover(quote.turnoverAmount),
            color = WorkspaceOnSurface,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
    }
    DividerLine()
}

@Composable
private fun EmptyMarketRow(text: String) {
    Text(
        text = text,
        color = WorkspaceMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 22.dp),
    )
}

@Composable
private fun MarketBottomNav(
    onWorkbenchSelected: () -> Unit,
    onObservationSelected: () -> Unit,
    onReportSelected: () -> Unit,
) {
    NavigationBar(
        containerColor = Color(0xFF1D1F27),
        contentColor = WorkspaceMuted,
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        listOf("工作台", "行情", "观察", "研究", "我的").forEach { item ->
            NavigationBarItem(
                selected = item == "行情",
                onClick = {
                    when (item) {
                        "工作台" -> onWorkbenchSelected()
                        "观察" -> onObservationSelected()
                        "研究" -> onReportSelected()
                    }
                },
                icon = { Text(navIcon(item), fontSize = 17.sp) },
                label = { Text(item, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryFixedDim,
                    selectedTextColor = PrimaryFixedDim,
                    indicatorColor = CommandBlueSoft,
                    unselectedIconColor = WorkspaceMuted,
                    unselectedTextColor = WorkspaceMuted,
                ),
            )
        }
    }
}

private fun navIcon(item: String): String = when (item) {
    "工作台" -> "▦"
    "行情" -> "⌁"
    "观察" -> "◉"
    "研究" -> "▤"
    else -> "●"
}

private fun formatMarketPrice(value: Double): String =
    if (value == 0.0) "--" else DecimalFormat("#,##0.###").format(value)

private fun formatMarketPercent(value: Double): String {
    val sign = if (value >= 0.0) "+" else ""
    return "$sign${DecimalFormat("0.00").format(value)}%"
}

private fun formatTurnover(value: Double): String = when {
    value >= 100_000_000.0 -> "${DecimalFormat("0.#").format(value / 100_000_000.0)}亿"
    value >= 10_000.0 -> "${DecimalFormat("0.#").format(value / 10_000.0)}万"
    value > 0.0 -> DecimalFormat("#,##0").format(value)
    else -> "--"
}
