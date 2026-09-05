package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
internal fun MarketHomeTopBar(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = "行情",
        modifier = modifier,
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "搜索行情",
                )
            }
        },
    )
}

@Composable
internal fun MarketPageTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmallTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
    )
}

@Immutable
data class MarketNavigationItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MarketNavigationBar(
    items: List<MarketNavigationItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.id == selectedItemId,
                onClick = { onItemSelected(item.id) },
                icon = item.icon,
                label = item.label,
            )
        }
    }
}
