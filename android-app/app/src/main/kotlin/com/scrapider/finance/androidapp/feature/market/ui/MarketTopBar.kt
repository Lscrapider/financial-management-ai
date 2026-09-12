package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketHomeTopBar(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = LocalFinanceDimensions.current
    TopAppBar(
        title = { Text(text = "行情", style = MiuixTheme.textStyles.title1) },
        modifier = modifier,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MiuixTheme.colorScheme.background),
        actions = {
            IconButton(
                onClick = onSearch,
                modifier = Modifier.sizeIn(
                    minWidth = dimensions.minTouchTarget,
                    minHeight = dimensions.minTouchTarget,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "搜索行情",
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketPageTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = LocalFinanceDimensions.current
    TopAppBar(
        title = { Text(text = title, style = MiuixTheme.textStyles.title3) },
        modifier = modifier,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MiuixTheme.colorScheme.background),
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.sizeIn(
                    minWidth = dimensions.minTouchTarget,
                    minHeight = dimensions.minTouchTarget,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
    )
}
