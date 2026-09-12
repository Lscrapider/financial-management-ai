package com.scrapider.finance.androidapp.feature.workbench.imports

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportErrorState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar

/** 原页独立展示，关闭后保留段落编辑位置；缩放按钮也可由 TalkBack 操作。 */
@Composable
internal fun ImportPagePreview(
    pageNo: Int,
    image: Bitmap?,
    isLoading: Boolean,
    error: String,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    var zoom by rememberSaveable(pageNo) { mutableIntStateOf(1) }
    val spacing = LocalFinanceSpacing.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ReportTopBar(title = "原页 · 第 $pageNo 页", onBack = onClose)
                when {
                    isLoading -> ReportLoadingState("正在加载原页", Modifier.padding(horizontal = spacing.xl))
                    error.isNotBlank() -> ReportErrorState(error, onRetry, Modifier.padding(spacing.xl))
                    image != null -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.xl)) {
                            TextButton(onClick = { zoom-- }, enabled = zoom > 1) { Text("缩小") }
                            TextButton(onClick = { zoom++ }, enabled = zoom < MAX_PAGE_ZOOM) { Text("放大") }
                            TextButton(onClick = { zoom = 1 }, enabled = zoom != 1) { Text("适应宽度") }
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val pageWidth = maxWidth
                            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                                Image(
                                    bitmap = image.asImageBitmap(),
                                    contentDescription = "第 $pageNo 页原始材料，可放大对照识别正文",
                                    modifier = Modifier.width(pageWidth * zoom)
                                        .aspectRatio(image.width.toFloat() / image.height),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_PAGE_ZOOM = 4
