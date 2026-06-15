package com.scrapider.finance.androidapp.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrapider.finance.androidapp.data.AppFontScale
import com.scrapider.finance.androidapp.data.SessionState

@Composable
fun UserCenterScreen(
    session: SessionState,
    loading: Boolean,
    statusMessage: String,
    showKnowledge: Boolean = true,
    fontScale: AppFontScale,
    onFontScaleChange: (AppFontScale) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onAddStockConfig: (String, String) -> Unit,
    onAddBondConfig: (String, String) -> Unit,
    onDeleteTargetConfig: (String, String) -> Unit,
    onLogout: () -> Unit,
    onWorkbenchSelected: () -> Unit,
    onMarketSelected: () -> Unit,
    onObservationSelected: () -> Unit,
    onReportSelected: () -> Unit,
    onKnowledgeSelected: () -> Unit,
) {
    Scaffold(
        containerColor = WorkspaceBackground,
        topBar = {
            ScreenTopBar(
                title = "个人中心",
                subtitle = "账户、安全和提醒偏好",
                avatarText = session.displayName,
                loading = loading,
                onRefresh = null,
                primaryActionText = "工作台",
                onPrimaryAction = onWorkbenchSelected,
            )
        },
        bottomBar = {
            WorkspaceBottomNav(
                selectedItem = "",
                showKnowledge = showKnowledge,
                onWorkbenchSelected = onWorkbenchSelected,
                onMarketSelected = onMarketSelected,
                onObservationSelected = onObservationSelected,
                onReportSelected = onReportSelected,
                onKnowledgeSelected = onKnowledgeSelected,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccountHeader(session)
            InfoPanel(
                title = "基本设置",
                rows = listOf(
                    "登录账号" to session.username.ifBlank { "未记录" },
                    "显示名称" to session.realName.ifBlank { session.displayName },
                    "账户角色" to roleText(session),
                ),
            )
            InfoPanel(
                title = "安全设置",
                rows = listOf(
                    "登录状态" to if (session.authenticated) "已登录" else "未登录",
                    "访问令牌" to if (session.accessToken.isBlank()) "未保存" else "本机已保存",
                    "权限范围" to session.roles.joinToString("、").ifBlank { "普通用户" },
                ),
            )
            DisplaySettingPanel(
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
            )
            PasswordPanel(
                loading = loading,
                onChangePassword = onChangePassword,
            )
            if (session.isAdmin) {
                TargetConfigPanel(
                    loading = loading,
                    onAddStockConfig = onAddStockConfig,
                    onAddBondConfig = onAddBondConfig,
                    onDeleteTargetConfig = onDeleteTargetConfig,
                )
            }
            InfoPanel(
                title = "新消息提醒",
                rows = listOf(
                    "行情提醒" to "跟随观察风控阈值",
                    "报告提醒" to "报告生成状态在研究页查看",
                ),
            )
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed),
            ) {
                Text("退出登录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun AccountHeader(session: SessionState) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, PrimaryFixedDim.copy(alpha = 0.42f), CircleShape)
                    .background(CommandBlueSoft.copy(alpha = 0.78f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = session.displayName.trim().take(2).ifBlank { "研" },
                    color = PrimaryFixedDim,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = session.displayName,
                    color = WorkspaceForeground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = session.username.ifBlank { "research_user_01" },
                    color = WorkspaceMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusChip(
                text = if (session.isAdmin) "管理员" else "研究员",
                color = if (session.isAdmin) SignalAmber else PrimaryFixedDim,
            )
        }
    }
}

@Composable
private fun InfoPanel(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Panel {
        Text(title, color = WorkspaceForeground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = label,
                        color = WorkspaceMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.34f),
                        maxLines = 1,
                    )
                    Text(
                        text = value,
                        color = WorkspaceOnSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(0.66f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplaySettingPanel(
    fontScale: AppFontScale,
    onFontScaleChange: (AppFontScale) -> Unit,
) {
    Panel {
        Text("显示设置", color = WorkspaceForeground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "按比例缩放全局字号，保留标题、正文和标签的层级关系。",
            color = WorkspaceMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AppFontScale.entries.forEach { option ->
                TargetTypeChip(
                    text = option.label,
                    active = option == fontScale,
                    modifier = Modifier.weight(1f),
                    onClick = { onFontScaleChange(option) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "预览标题",
                color = WorkspaceForeground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = "正文和标签会同步按比例变化",
                color = WorkspaceMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PasswordPanel(
    loading: Boolean,
    onChangePassword: (String, String, String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val canSubmit = oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank() && !loading
    Panel {
        Text("修改密码", color = WorkspaceForeground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        FinanceTextField(
            label = "旧密码",
            value = oldPassword,
            onValueChange = { oldPassword = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        FinanceTextField(
            label = "新密码",
            value = newPassword,
            onValueChange = { newPassword = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        FinanceTextField(
            label = "确认新密码",
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            isError = confirmPassword.isNotBlank() && newPassword != confirmPassword,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        PrimaryActionButton(
            text = if (loading) "提交中" else "修改密码",
            enabled = canSubmit,
            onClick = { onChangePassword(oldPassword, newPassword, confirmPassword) },
        )
    }
}

@Composable
private fun TargetConfigPanel(
    loading: Boolean,
    onAddStockConfig: (String, String) -> Unit,
    onAddBondConfig: (String, String) -> Unit,
    onDeleteTargetConfig: (String, String) -> Unit,
) {
    var stockCode by remember { mutableStateOf("") }
    var stockName by remember { mutableStateOf("") }
    var bondCode by remember { mutableStateOf("") }
    var bondName by remember { mutableStateOf("") }
    var deleteType by remember { mutableStateOf("STOCK") }
    var deleteCode by remember { mutableStateOf("") }
    val codeOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    Panel {
        Text("标的配置", color = WorkspaceForeground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "新增股票、可转债，或按类型物理删除标的配置和关联数据。",
            color = WorkspaceMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        FinanceTextField(
            label = "股票代码",
            value = stockCode,
            onValueChange = { stockCode = it.take(6) },
            keyboardOptions = codeOptions,
        )
        FinanceTextField(
            label = "股票名称",
            value = stockName,
            onValueChange = { stockName = it },
        )
        PrimaryActionButton(
            text = if (loading) "同步中" else "新增股票并同步",
            enabled = !loading && stockCode.matches(Regex("\\d{6}")) && stockName.isNotBlank(),
            onClick = { onAddStockConfig(stockCode, stockName) },
        )
        DividerLine()
        FinanceTextField(
            label = "可转债代码",
            value = bondCode,
            onValueChange = { bondCode = it.take(6) },
            keyboardOptions = codeOptions,
        )
        FinanceTextField(
            label = "可转债名称",
            value = bondName,
            onValueChange = { bondName = it },
        )
        PrimaryActionButton(
            text = if (loading) "同步中" else "新增可转债并同步",
            enabled = !loading && bondCode.matches(Regex("\\d{6}")) && bondName.isNotBlank(),
            onClick = { onAddBondConfig(bondCode, bondName) },
        )
        DividerLine()
        Text("删除标的", color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TargetTypeChip("股票", deleteType == "STOCK", Modifier.weight(1f)) { deleteType = "STOCK" }
            TargetTypeChip("可转债", deleteType == "BOND", Modifier.weight(1f)) { deleteType = "BOND" }
            TargetTypeChip("指数", deleteType == "INDEX", Modifier.weight(1f)) { deleteType = "INDEX" }
        }
        FinanceTextField(
            label = "标的代码",
            value = deleteCode,
            onValueChange = { deleteCode = it.take(6) },
            keyboardOptions = codeOptions,
        )
        OutlinedButton(
            onClick = { onDeleteTargetConfig(deleteType, deleteCode) },
            enabled = !loading && deleteCode.matches(Regex("\\d{6}")),
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed),
        ) {
            Text(if (loading) "删除中" else "物理删除标的", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TargetTypeChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .border(1.dp, if (active) PrimaryFixedDim else WorkspaceBorder, RoundedCornerShape(6.dp))
            .background(if (active) CommandBlueSoft.copy(alpha = 0.66f) else WorkspaceSurfaceElevated, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) PrimaryFixedDim else WorkspaceMuted,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun roleText(session: SessionState): String =
    if (session.isAdmin) "管理员" else "普通用户"
