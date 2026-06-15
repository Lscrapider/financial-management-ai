package com.scrapider.finance.androidapp.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    username: String,
    password: String,
    loginAsAdmin: Boolean,
    rememberAccount: Boolean,
    passwordVisible: Boolean,
    loading: Boolean,
    errorMessage: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRoleChange: (Boolean) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WorkspaceBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 28.dp, bottom = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge("A")
        Spacer(Modifier.height(16.dp))
        Text("金融管理研究助手", color = WorkspaceForeground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("个人投资研究驾驶舱", color = WorkspaceMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, WorkspaceBorder),
            colors = CardDefaults.cardColors(containerColor = WorkspaceSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RoleSelector(loginAsAdmin = loginAsAdmin, onRoleChange = onRoleChange)
                if (errorMessage.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SignalRed.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .background(SignalRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("!", color = SignalRed, fontWeight = FontWeight.Bold)
                        Text(errorMessage, color = SignalRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                FinanceTextField(
                    label = "用户名",
                    value = username,
                    onValueChange = onUsernameChange,
                    isError = errorMessage.isNotBlank(),
                )
                FinanceTextField(
                    label = "密码",
                    value = password,
                    onValueChange = onPasswordChange,
                    isError = errorMessage.isNotBlank(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                            Text(if (passwordVisible) "隐藏" else "显示", color = WorkspaceMuted, fontSize = 12.sp)
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberAccount,
                            onCheckedChange = onRememberChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryFixedDim,
                                uncheckedColor = WorkspaceBorder,
                                checkmarkColor = WorkspaceBackground,
                            ),
                        )
                        Text("记住账号", color = WorkspaceMuted, fontSize = 12.sp)
                    }
                    Text("创建账号", color = PrimaryFixedDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                PrimaryActionButton(
                    text = if (loading) "登录中..." else "登录并返回工作台",
                    enabled = !loading,
                    onClick = onLogin,
                )
            }
        }
    }
}

@Composable
private fun RoleSelector(
    loginAsAdmin: Boolean,
    onRoleChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF32353D), RoundedCornerShape(6.dp))
            .padding(4.dp),
    ) {
        RoleButton("管理员", selected = loginAsAdmin, onClick = { onRoleChange(true) }, modifier = Modifier.weight(1f))
        RoleButton("普通用户", selected = !loginAsAdmin, onClick = { onRoleChange(false) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RoleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(if (selected) CommandBlue else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color(0xFFF4F5FF) else WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
