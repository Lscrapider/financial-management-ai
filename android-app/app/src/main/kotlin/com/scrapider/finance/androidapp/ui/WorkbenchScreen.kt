package com.scrapider.finance.androidapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrapider.finance.androidapp.data.AiChatMessage
import com.scrapider.finance.androidapp.data.AiChatProgressStep
import com.scrapider.finance.androidapp.data.AiChatRole
import com.scrapider.finance.androidapp.data.AiChatUiState
import com.scrapider.finance.androidapp.data.WorkbenchMovement
import com.scrapider.finance.androidapp.data.WorkbenchReportLine
import com.scrapider.finance.androidapp.data.WorkbenchSummary
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import java.text.DecimalFormat
import java.time.LocalTime

@Composable
fun WorkbenchScreen(
    displayName: String,
    loading: Boolean,
    statusMessage: String,
    summary: WorkbenchSummary,
    showKnowledge: Boolean = true,
    onRefresh: () -> Unit,
    onAiChatOpen: () -> Unit = {},
    onUserCenterSelected: () -> Unit = {},
    onMarketSelected: () -> Unit = {},
    onObservationSelected: () -> Unit = {},
    onReportSelected: () -> Unit = {},
    onKnowledgeSelected: () -> Unit = {},
) {
    Scaffold(
        containerColor = WorkspaceBackground,
        topBar = {
            TopBar(
                displayName = displayName,
                loading = loading,
                onRefresh = onRefresh,
                onAiChatOpen = onAiChatOpen,
                onUserCenterSelected = onUserCenterSelected,
            )
        },
        bottomBar = {
            BottomNav(
                onMarketSelected = onMarketSelected,
                onObservationSelected = onObservationSelected,
                onReportSelected = onReportSelected,
                onKnowledgeSelected = onKnowledgeSelected,
                showKnowledge = showKnowledge,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WorkspaceBackground)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CockpitStatus(summary = summary)
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            TodayActions(summary = summary)
            MovementTable(movements = summary.movements)
            AssetAndRisk(summary = summary)
            ReportSection(reports = summary.reports)
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun TopBar(
    displayName: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onAiChatOpen: () -> Unit,
    onUserCenterSelected: () -> Unit,
) {
    ScreenTopBar(
        title = "工作台",
        subtitle = greeting(displayName),
        titleAccessory = { AiPulseBadge(onClick = onAiChatOpen) },
        avatarText = displayName,
        loading = loading,
        onRefresh = onRefresh,
        onAvatarClick = onUserCenterSelected,
    )
}

@Composable
fun AiChatScreen(
    aiChat: AiChatUiState,
    onDismiss: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WorkspaceBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AiChatTopActions(onDismiss = onDismiss)
        if (aiChat.loading || aiChat.progressSteps.isNotEmpty()) {
            AiChatProgressPanel(aiChat = aiChat)
        }
        AiChatMessages(aiChat = aiChat, modifier = Modifier.weight(1f))
        AiChatInput(
            aiChat = aiChat,
            onInputChange = onInputChange,
            onSend = onSend,
            onClear = onClear,
        )
    }
}

@Composable
private fun AiChatTopActions(
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "收起",
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = PrimaryFixedDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AiChatProgressPanel(aiChat: AiChatUiState) {
    val latest = aiChat.progressSteps.lastOrNull()
        ?: AiChatProgressStep(0L, aiChat.progressText.ifBlank { "正在分析问题并准备上下文" }, "running", "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(WorkspaceSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiChatProgressDot(active = aiChat.loading)
        Text(
            text = latest.content,
            modifier = Modifier.weight(1f),
            color = if (aiChat.loading) WorkspaceForeground else WorkspaceMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (latest.createdAt.isNotBlank()) {
            Text(latest.createdAt, color = WorkspaceMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AiChatProgressDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "aiChatStep")
    val alpha = transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "aiChatStepAlpha",
    )
    Box(
        modifier = Modifier
            .size(if (active) 8.dp else 7.dp)
            .background(
                if (active) PrimaryFixedDim.copy(alpha = alpha.value) else WorkspaceMuted.copy(alpha = 0.45f),
                CircleShape,
            ),
    )
}

@Composable
private fun AiChatMessages(aiChat: AiChatUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val pendingRowCount = if (aiChat.loading && !aiChat.hasStreamingAnswer) 1 else 0
    val bottomAnchorIndex = aiChat.messages.size + pendingRowCount
    LaunchedEffect(aiChat.messages.size, aiChat.messages.lastOrNull()?.content, aiChat.loading, aiChat.hasStreamingAnswer) {
        if (aiChat.messages.isNotEmpty() || aiChat.loading) {
            withFrameNanos { }
            listState.scrollToItem(bottomAnchorIndex)
        }
    }
    if (aiChat.messages.isEmpty() && !aiChat.loading) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
                .background(WorkspaceSurface, RoundedCornerShape(8.dp))
                .padding(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, PrimaryFixedDim.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                        .background(CommandBlueSoft.copy(alpha = 0.72f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AI", color = PrimaryFixed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text("输入问题开始研究对话", color = WorkspaceForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("支持行情查询、复盘清单和 Markdown 回答", color = WorkspaceMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF151A22), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(aiChat.messages, key = { it.id }) { message ->
            AiChatMessageRow(message)
        }
        if (aiChat.loading && !aiChat.hasStreamingAnswer) {
            item {
                AiChatPendingRow(aiChat.progressText.ifBlank { "正在分析" })
            }
        }
        item(key = "ai-chat-bottom-anchor") {
            Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun AiChatMessageRow(message: AiChatMessage) {
    val isUser = message.role == AiChatRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.84f else 0.92f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!isUser) {
                AiChatAvatar()
                Spacer(Modifier.width(8.dp))
            }
            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isUser) "我" else "AI", color = WorkspaceMuted, fontSize = 12.sp)
                if (!isUser && message.model.isNotBlank()) {
                    StatusChip(message.model, PrimaryFixedDim)
                }
                Text(message.createdAt, color = WorkspaceMuted, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .border(
                        1.dp,
                        if (isUser) PrimaryFixedDim.copy(alpha = 0.35f) else WorkspaceBorder,
                        RoundedCornerShape(8.dp),
                    )
                    .background(if (isUser) CommandBlueSoft.copy(alpha = 0.62f) else WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (isUser) {
                    Text(message.content, color = WorkspaceForeground, fontSize = 13.sp, lineHeight = 20.sp)
                } else {
                    MarkdownAiChatText(markdown = message.content.ifBlank { "正在生成回答..." })
                }
            }
            }
        }
    }
}

@Composable
private fun AiChatAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(1.dp, PrimaryFixedDim.copy(alpha = 0.32f), RoundedCornerShape(7.dp))
            .background(CommandBlueSoft.copy(alpha = 0.64f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("AI", color = PrimaryFixedDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AiChatPendingRow(progressText: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
                .background(WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(progressText, color = WorkspaceMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AiChatInput(
    aiChat: AiChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedTextField(
            value = aiChat.input,
            onValueChange = { if (it.length <= 1000) onInputChange(it) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !aiChat.loading,
            minLines = 1,
            maxLines = 2,
            placeholder = { Text("输入你的问题", color = WorkspaceMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = WorkspaceForeground,
                unfocusedTextColor = WorkspaceForeground,
                focusedContainerColor = Color(0xFF191B23),
                unfocusedContainerColor = Color(0xFF191B23),
                disabledContainerColor = Color(0xFF191B23),
                focusedBorderColor = CommandBlue,
                unfocusedBorderColor = WorkspaceBorder,
                disabledBorderColor = WorkspaceBorder,
                cursorColor = PrimaryFixedDim,
            ),
            shape = RoundedCornerShape(8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("回答仅供研究参考", color = WorkspaceMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear, enabled = aiChat.messages.isNotEmpty() && !aiChat.loading) {
                    Text("清空", color = WorkspaceMuted, fontSize = 13.sp)
                }
                Button(
                    onClick = onSend,
                    enabled = aiChat.input.trim().isNotBlank() && !aiChat.loading,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CommandBlue,
                        contentColor = Color(0xFFF4F5FF),
                        disabledContainerColor = WorkspaceSurfaceMuted,
                        disabledContentColor = WorkspaceMuted,
                    ),
                ) {
                    Text(if (aiChat.loading) "生成中" else "发送", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MarkdownAiChatText(markdown: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    val textColor = WorkspaceOnSurface.toArgb()
    val linkColor = PrimaryFixedDim.toArgb()
    val platformFontScale = context.resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
    val appFontScale = density.fontScale / platformFontScale
    val textSizeSp = 13f * appFontScale
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setLineSpacing(0f, 1.2f)
                includeFontPadding = false
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Composable
private fun CockpitStatus(summary: WorkbenchSummary) {
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("今日驾驶舱", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            StatusChip(text = if (summary.focusCount > 0) "关注" else "平稳", color = if (summary.focusCount > 0) SignalAmber else SignalGreen)
        }
        Text(
            text = if (summary.focusCount > 0) "接近阈值或报告生成中" else "暂无高优先级异常",
            color = WorkspaceMuted,
            fontSize = 14.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCell("观察池", summary.watchItemCount.toString(), PrimaryFixedDim, Modifier.weight(1f))
            MetricCell("越界", summary.outAlertCount.toString(), SignalAmber, Modifier.weight(1f))
            MetricCell("报告生成中", summary.reportGeneratingCount.toString(), PrimaryFixedDim, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TodayActions(summary: WorkbenchSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("今日行动")
        ActionCard(summary.primaryAction, if (summary.outAlertCount + summary.nearAlertCount > 0) "接近阈值" else "观察池", "查看布控", SignalAmber, primary = true)
        ActionCard(summary.reportAction, if (summary.reportGeneratingCount > 0) "知识召回中" else "报告状态", "查看报告", PrimaryFixedDim)
        ActionCard(summary.marketAction, "待复核", "补充提醒", WorkspaceMuted)
    }
}

@Composable
private fun ActionCard(
    title: String,
    chip: String,
    action: String,
    color: Color,
    primary: Boolean = false,
) {
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = WorkspaceForeground, fontSize = 14.sp, lineHeight = 20.sp)
                StatusChip(chip, color)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {},
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (primary) CommandBlueSoft else WorkspaceSurfaceMuted,
                    contentColor = if (primary) PrimaryFixedDim else WorkspaceOnSurface,
                ),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(action, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MovementTable(movements: List<WorkbenchMovement>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("观察池异动", "更多 >")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, WorkspaceBorder),
            colors = CardDefaults.cardColors(containerColor = WorkspaceSurface),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF191B23))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("标的/代码", color = WorkspaceMuted, fontSize = 12.sp, modifier = Modifier.weight(2f))
                Text("最新价", color = WorkspaceMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text("涨跌幅", color = WorkspaceMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            if (movements.isEmpty()) {
                EmptyTableRow("后端暂无观察池异动，刷新后会展示涨跌幅最大的标的。")
            } else {
                movements.take(4).forEach { movement ->
                    MovementRow(movement)
                }
            }
        }
    }
}

@Composable
private fun EmptyTableRow(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 18.dp),
        color = WorkspaceMuted,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun MovementRow(movement: WorkbenchMovement) {
    val positive = movement.changePercent >= 0.0
    val color = if (positive) SignalRed else SignalGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(movement.name, color = WorkspaceForeground, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(movement.code.ifBlank { movement.type.ifBlank { "--" } }, color = WorkspaceMuted, fontSize = 12.sp)
        }
        Text(formatPrice(movement.latestPrice), color = color, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                formatPercent(movement.changePercent),
                modifier = Modifier
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    DividerLine()
}

@Composable
private fun AssetAndRisk(summary: WorkbenchSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel {
            Text("资产视图", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                AssetCount("股票", summary.stockItemCount)
                AssetCount("指数", summary.indexItemCount)
                AssetCount("可转债", summary.bondItemCount)
            }
            DividerLine()
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                LegendDot("上涨 ${summary.watchUpCount}", SignalRed)
                LegendDot("下跌 ${summary.watchDownCount}", SignalGreen)
                LegendDot("平盘 ${flatCount(summary)}", Color(0xFF8C90A0))
            }
        }
        Panel {
            Text("布控与风险", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("全域覆盖率", color = WorkspaceMuted, fontSize = 12.sp)
                Text(coverageText(summary), color = PrimaryFixedDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            TinyBar(coverage(summary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RiskCell("已触发", summary.outAlertCount.toString(), WorkspaceForeground, Modifier.weight(1f))
                RiskCell("临界", summary.nearAlertCount.toString(), SignalAmber, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReportSection(reports: List<WorkbenchReportLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("报告动态", "查看全部 >")
        Panel {
            if (reports.isEmpty()) {
                Text(
                    text = "后端暂无报告动态。生成报告后，这里会展示最新状态、模型和摘要。",
                    color = WorkspaceMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            } else {
                reports.take(4).forEachIndexed { index, report ->
                    if (index > 0) {
                        DividerLine()
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(report.title, color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(report.engine, color = WorkspaceMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        StatusChip(report.status, if (report.status == "失败") SignalRed else PrimaryFixedDim)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, WorkspaceBorder, RoundedCornerShape(4.dp))
                            .background(Color(0xFF191B23), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                    ) {
                        Text(report.detail, color = WorkspaceMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Text(report.eta, color = WorkspaceMuted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun BottomNav(
    showKnowledge: Boolean,
    onMarketSelected: () -> Unit,
    onObservationSelected: () -> Unit,
    onReportSelected: () -> Unit,
    onKnowledgeSelected: () -> Unit,
) {
    WorkspaceBottomNav(
        selectedItem = "工作台",
        showKnowledge = showKnowledge,
        onMarketSelected = onMarketSelected,
        onObservationSelected = onObservationSelected,
        onReportSelected = onReportSelected,
        onKnowledgeSelected = onKnowledgeSelected,
    )
}

@Composable
private fun AssetCount(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp)
        Text(value.toString(), color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, color = WorkspaceMuted, fontSize = 12.sp)
    }
}

@Composable
private fun RiskCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, if (color == SignalAmber) SignalAmber.copy(alpha = 0.35f) else WorkspaceBorder, RoundedCornerShape(4.dp))
            .background(Color(0xFF1D1F27), RoundedCornerShape(4.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = color, fontSize = 12.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun flatCount(summary: WorkbenchSummary): Int =
    (summary.watchItemCount - summary.watchUpCount - summary.watchDownCount).coerceAtLeast(0)

private fun coverage(summary: WorkbenchSummary): Float {
    if (summary.watchItemCount <= 0) return 0f
    val covered = summary.enabledAlertCount
    return (covered.toFloat() / summary.watchItemCount.toFloat()).coerceIn(0f, 1f)
}

private fun coverageText(summary: WorkbenchSummary): String =
    "${(coverage(summary) * 100).toInt()}%"

private fun formatPrice(value: Double): String = if (value == 0.0) "--" else DecimalFormat("#,##0.###").format(value)

private fun formatPercent(value: Double): String {
    val sign = if (value >= 0.0) "+" else ""
    return "$sign${DecimalFormat("0.00").format(value)}%"
}

private fun greeting(displayName: String): String {
    val hour = LocalTime.now().hour
    val prefix = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..23 -> "晚上好"
        else -> "夜深了"
    }
    return "$prefix，$displayName"
}
