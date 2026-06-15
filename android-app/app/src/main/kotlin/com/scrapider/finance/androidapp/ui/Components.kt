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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions

private val WorkspaceNavItems = listOf("工作台", "行情", "观察", "研究", "知识")

@Composable
fun ScreenTopBar(
    title: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleAccessory: @Composable (() -> Unit)? = null,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    avatarText: String = "研",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(WorkspaceBackground)
            .border(BorderStroke(1.dp, WorkspaceBorder.copy(alpha = 0.65f)))
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
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "同步中" else "刷新", color = WorkspaceMuted, fontSize = 13.sp)
            }
            if (primaryActionText != null && onPrimaryAction != null) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(CommandBlue, RoundedCornerShape(8.dp))
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
            TopBarAvatar(avatarText)
        }
    }
}

@Composable
fun AiPulseBadge() {
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
            .background(CommandBlueSoft.copy(alpha = 0.55f + pulse.value * 0.18f), RoundedCornerShape(8.dp)),
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
private fun TopBarAvatar(text: String) {
    val avatarLabel = text.trim().take(2).ifBlank { "研" }
    Box(
        modifier = Modifier
            .size(34.dp)
            .border(1.dp, PrimaryFixedDim.copy(alpha = 0.35f), CircleShape)
            .background(CommandBlueSoft.copy(alpha = 0.72f), CircleShape),
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
    onWorkbenchSelected: () -> Unit = {},
    onMarketSelected: () -> Unit = {},
    onObservationSelected: () -> Unit = {},
    onReportSelected: () -> Unit = {},
    onKnowledgeSelected: () -> Unit = {},
) {
    NavigationBar(
        containerColor = Color(0xFF1D1F27),
        contentColor = WorkspaceMuted,
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        WorkspaceNavItems.forEach { item ->
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
                    indicatorColor = CommandBlueSoft,
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
        border = BorderStroke(1.dp, WorkspaceBorder),
        colors = CardDefaults.cardColors(containerColor = WorkspaceSurface),
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
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF1D1F27), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
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
        trackColor = Color(0xFF272A32),
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
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(6.dp))
            .background(WorkspaceSurfaceElevated, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
