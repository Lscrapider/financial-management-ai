package com.scrapider.finance.androidapp.feature.workbench.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportErrorState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportInfoState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar

@Composable
internal fun ImportUploadScreen(
    fileName: String?,
    fileDescription: String,
    requirements: String,
    isPreparing: Boolean,
    isUploading: Boolean,
    error: String,
    unconfirmed: Boolean,
    onChoose: () -> Unit,
    onUpload: () -> Unit,
    onReview: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val busy = isPreparing || isUploading
    Column(modifier.fillMaxSize()) {
        ReportTopBar(title = "导入文件", onBack = onBack, actions = {
            if (fileName != null) TextButton(onClick = onChoose, enabled = !busy && !unconfirmed) { Text("更换") }
        })
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "upload-heading") {
                ImportHeading("把研究材料加入知识库")
                Text("选择文件后确认上传，识别和整理会在后台继续。", style = MaterialTheme.typography.bodyMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer)
            }
            if (isPreparing) {
                item(key = "upload-preparing") { ReportLoadingState("正在读取并检查文件") }
            } else if (fileName != null) {
                item(key = "upload-file") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_phosphor_file_text_duotone),
                            null,
                            Modifier.size(LocalFinanceDimensions.current.iconSize),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Text(fileName, style = MaterialTheme.typography.titleMedium)
                            Text(fileDescription, style = MaterialTheme.typography.bodySmall,
                                color = rememberFinanceSignalColors().onNeutralContainer)
                        }
                    }
                }
            } else {
                item(key = "upload-pick") {
                    ImportActionRow("选择研究文件", "从手机或文件服务中选取", R.drawable.ic_phosphor_upload_simple_duotone, onChoose,
                        enabled = !busy && !unconfirmed)
                }
            }
            item(key = "upload-requirements") {
                Text(requirements, style = MaterialTheme.typography.bodySmall,
                    color = rememberFinanceSignalColors().onNeutralContainer)
            }
            if (unconfirmed) {
                item(key = "upload-unconfirmed") {
                    ReportInfoState("上传结果尚未确认。请先查看处理记录，确认文件是否已接收，避免重复导入。")
                }
            } else if (error.isNotBlank()) {
                item(key = "upload-error") { ReportErrorState(error, onRetry = onChoose) }
            }
            if (isUploading) {
                item(key = "upload-working") { ReportLoadingState("正在上传，请稍候") }
            }
            item(key = "upload-next") {
                ImportHeading("上传之后")
                Text("处理记录会显示识别和入库状态。若有待核对的内容，可进入复核，修改段落并对照原页。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer)
            }
        }
        ImportActionBar(
            label = when {
                isPreparing -> "正在检查文件"
                isUploading -> "正在上传"
                unconfirmed -> "查看处理记录"
                fileName == null -> "选择文件"
                else -> "开始导入"
            },
            onClick = when {
                unconfirmed -> onReview
                fileName == null -> onChoose
                else -> onUpload
            },
            busy = busy,
        )
    }
}
