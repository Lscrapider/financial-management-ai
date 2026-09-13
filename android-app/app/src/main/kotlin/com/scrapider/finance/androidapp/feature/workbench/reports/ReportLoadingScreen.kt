package com.scrapider.finance.androidapp.feature.workbench.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing

/** 首次绑定会话时保留工具上下文，只显示轻量的加载反馈。 */
@Composable
internal fun ReportLoadingScreen(
    title: String,
    text: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Column(modifier = modifier.fillMaxSize()) {
        ReportTopBar(title = title, onBack = onBack)
        ReportLoadingState(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.md),
        )
    }
}
