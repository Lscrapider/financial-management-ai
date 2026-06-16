package com.scrapider.finance.androidapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions

private val WorkspaceNavItems = listOf("工作台", "行情", "观察", "研究", "知识")

data class CompactFilterItem(
    val label: String,
    val value: String,
    val onClick: () -> Unit,
)

data class FilterOptionItem(
    val key: String,
    val label: String,
    val description: String = "",
)

@Composable
fun ScreenTopBar(
    title: String,
    loading: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleAccessory: @Composable (() -> Unit)? = null,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    avatarText: String = "研",
    onAvatarClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1E27), Color(0xFF171A20), WorkspaceBackground),
                ),
            )
            .drawBehind {
                drawLine(
                    color = WorkspaceBorder.copy(alpha = 0.72f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    color = WorkspaceForeground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                titleAccessory?.invoke()
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = WorkspaceMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onRefresh != null) {
                TextButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "刷新中" else "刷新", color = WorkspaceMuted, fontSize = 13.sp)
                }
            }
            if (primaryActionText != null && onPrimaryAction != null) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(
                            Brush.horizontalGradient(listOf(CommandBlue, Color(0xFF1C7DFF))),
                            RoundedCornerShape(8.dp),
                        )
                        .border(1.dp, PrimaryFixedDim.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                        .clickable(enabled = !loading) { onPrimaryAction() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = primaryActionText,
                        color = Color(0xFFF4F5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            TopBarAvatar(avatarText, onAvatarClick)
        }
    }
}

@Composable
fun CompactFilterBar(
    items: List<CompactFilterItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder.copy(alpha = 0.84f), RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1D212A), Color(0xFF171A20))),
                RoundedCornerShape(8.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .background(WorkspaceSurfaceElevated.copy(alpha = 0.54f), RoundedCornerShape(6.dp))
                    .border(1.dp, WorkspaceBorder.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
                    .clickable { item.onClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = item.label,
                        color = WorkspaceMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.value,
                        color = WorkspaceForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "⌄",
                    color = PrimaryFixedDim,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterOptionSheet(
    title: String,
    options: List<FilterOptionItem>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WorkspaceSurface,
        contentColor = WorkspaceForeground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            options.forEach { option ->
                val selected = option.key == selectedKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (option.description.isBlank()) 44.dp else 54.dp)
                        .background(
                            if (selected) CommandBlueSoft.copy(alpha = 0.58f) else WorkspaceSurfaceElevated,
                            RoundedCornerShape(8.dp),
                        )
                        .border(
                            1.dp,
                            if (selected) PrimaryFixedDim.copy(alpha = 0.36f) else WorkspaceBorder,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable {
                            onSelected(option.key)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = option.label,
                            color = if (selected) PrimaryFixedDim else WorkspaceForeground,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (option.description.isNotBlank()) {
                            Text(
                                text = option.description,
                                color = WorkspaceMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (selected) {
                        Text("✓", color = PrimaryFixedDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AiPulseBadge(onClick: (() -> Unit)? = null) {
    val transition = rememberInfiniteTransition(label = "aiBadge")
    val pulse = transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aiPulse",
    )
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 26.dp)
            .border(1.dp, PrimaryFixedDim.copy(alpha = 0.38f + pulse.value * 0.24f), RoundedCornerShape(8.dp))
            .background(CommandBlueSoft.copy(alpha = 0.55f + pulse.value * 0.18f), RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AI",
            color = PrimaryFixed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
                .size(5.dp)
                .background(PrimaryFixedDim.copy(alpha = pulse.value), CircleShape),
        )
    }
}

@Composable
private fun TopBarAvatar(text: String, onAvatarClick: (() -> Unit)?) {
    val avatarLabel = text.trim().take(2).ifBlank { "研" }
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                Brush.radialGradient(listOf(CommandBlueSoft.copy(alpha = 0.96f), Color(0xFF171F2E))),
                CircleShape,
            )
            .border(1.dp, PrimaryFixedDim.copy(alpha = 0.42f), CircleShape)
            .then(if (onAvatarClick != null) Modifier.clickable { onAvatarClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarLabel,
            color = PrimaryFixedDim,
            fontSize = if (avatarLabel.length > 1) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun WorkspaceBottomNav(
    selectedItem: String,
    showKnowledge: Boolean = true,
    onWorkbenchSelected: () -> Unit = {},
    onMarketSelected: () -> Unit = {},
    onObservationSelected: () -> Unit = {},
    onReportSelected: () -> Unit = {},
    onKnowledgeSelected: () -> Unit = {},
) {
    NavigationBar(
        containerColor = Color(0xFF181B21),
        contentColor = WorkspaceMuted,
        tonalElevation = 0.dp,
        modifier = Modifier
            .drawBehind {
                drawLine(
                    color = WorkspaceBorder.copy(alpha = 0.72f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .navigationBarsPadding(),
    ) {
        WorkspaceNavItems
            .filter { showKnowledge || it != "知识" }
            .forEach { item ->
            val active = item == selectedItem
            NavigationBarItem(
                selected = active,
                onClick = {
                    if (!active) {
                        when (item) {
                            "工作台" -> onWorkbenchSelected()
                            "行情" -> onMarketSelected()
                            "观察" -> onObservationSelected()
                            "研究" -> onReportSelected()
                            "知识" -> onKnowledgeSelected()
                        }
                    }
                },
                icon = {
                    Text(
                        text = workspaceNavIcon(item),
                        fontSize = 24.sp,
                        lineHeight = 24.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
                label = {
                    Text(
                        text = item,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryFixedDim,
                    selectedTextColor = PrimaryFixedDim,
                    indicatorColor = CommandBlueSoft.copy(alpha = 0.82f),
                    unselectedIconColor = WorkspaceMuted,
                    unselectedTextColor = WorkspaceMuted,
                ),
            )
        }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WorkspaceBorder.copy(alpha = 0.86f)),
        colors = CardDefaults.cardColors(containerColor = WorkspaceSurfaceElevated.copy(alpha = 0.58f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        },
    )
}

private fun workspaceNavIcon(item: String): String = when (item) {
    "工作台" -> "▦"
    "行情" -> "⌁"
    "观察" -> "◉"
    "研究" -> "▤"
    "知识" -> "▣"
    else -> "●"
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(5.dp))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun MetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xFF242833), Color(0xFF1A1D24))),
                RoundedCornerShape(8.dp),
            )
            .border(1.dp, WorkspaceBorder.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp, maxLines = 1)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun FinanceTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = WorkspaceForeground,
                unfocusedTextColor = WorkspaceForeground,
                focusedContainerColor = WorkspaceSurfaceElevated,
                unfocusedContainerColor = WorkspaceSurfaceElevated,
                errorContainerColor = WorkspaceSurfaceElevated,
                focusedBorderColor = CommandBlue,
                unfocusedBorderColor = WorkspaceBorder,
                errorBorderColor = SignalRed,
                cursorColor = PrimaryFixedDim,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CommandBlue,
            contentColor = Color(0xFFF4F5FF),
            disabledContainerColor = WorkspaceSurfaceMuted,
            disabledContentColor = WorkspaceMuted,
        ),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null) {
            Text(action, color = PrimaryFixedDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TinyBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(4.dp),
        color = PrimaryFixedDim,
        trackColor = WorkspaceSurfaceMuted.copy(alpha = 0.72f),
    )
}

@Composable
fun DividerLine() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WorkspaceBorder),
    )
}

@Composable
fun IconBadge(
    text: String,
    color: Color = PrimaryFixedDim,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                Brush.verticalGradient(listOf(WorkspaceSurfaceElevated, Color(0xFF1A1D24))),
                RoundedCornerShape(8.dp),
            )
            .border(1.dp, WorkspaceBorder.copy(alpha = 0.84f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
