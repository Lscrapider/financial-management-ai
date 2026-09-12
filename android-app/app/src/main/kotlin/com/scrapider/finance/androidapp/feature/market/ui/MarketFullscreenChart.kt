package com.scrapider.finance.androidapp.feature.market.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketChartAdjust
import com.scrapider.finance.androidapp.feature.market.MarketChartPeriod
import com.scrapider.finance.androidapp.feature.market.MarketDetailState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 仅图表展开期间覆盖应用的竖屏方向；旋转重建时由恢复的页面状态接管。 */
@Composable
internal fun MarketChartOrientation(expanded: Boolean) {
    val activity = LocalContext.current.findActivity()
    val originalOrientation = rememberSaveable {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(activity, expanded, originalOrientation) {
        activity?.requestedOrientation = if (expanded) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else originalOrientation
        onDispose {
            if (expanded && activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}

@Composable
internal fun MarketFullscreenChart(
    targetName: String,
    targetCode: String,
    targetType: String,
    detail: MarketDetailState,
    interaction: MarketChartInteraction,
    onPeriodSelected: (MarketChartPeriod) -> Unit,
    onAdjustSelected: (MarketChartAdjust) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = spacing.lg, vertical = spacing.xs),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_phosphor_arrow_right), "退出全屏图表",
                            Modifier.size(dimensions.iconSize).rotate(180f),
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(targetName, Modifier.weight(1f), style = MiuixTheme.textStyles.title2,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(targetCode, style = MiuixTheme.textStyles.body2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MarketChartSection(
                    targetName = targetName, targetType = targetType, detail = detail,
                    interaction = interaction, onPeriodSelected = onPeriodSelected,
                    onAdjustSelected = onAdjustSelected,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fullscreen = true,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
