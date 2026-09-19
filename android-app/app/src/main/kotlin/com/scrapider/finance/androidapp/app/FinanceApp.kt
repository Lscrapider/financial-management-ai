package com.scrapider.finance.androidapp.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.prefs.FontScaleMode
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.feature.auth.LoginRoute

@Composable
fun FinanceApp(
    state: AppUiState,
    apiClient: FinanceApiClient,
    onAuthenticated: (UserSession, Boolean) -> Unit,
    onSessionUpdated: (UserSession) -> Unit,
    onDestinationSelected: (AppDestination) -> Unit,
    onSignOut: () -> Unit,
    onFontScaleModeSelected: (FontScaleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showExitConfirm by remember { mutableStateOf(false) }

    // 内页返回由各 Route 自己的 BackHandler 优先接管；轮到这里说明已在主层级，
    // 弹出退出确认，避免误触系统返回直接退出应用。
    BackHandler { showExitConfirm = true }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出应用", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = "确定要退出" + stringResource(R.string.app_name) + "吗？",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { context.findActivity()?.finish() }) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        val session = state.session
        if (session == null) {
            LoginRoute(
                apiClient = apiClient,
                initialUsername = state.rememberedUsername,
                initialRememberAccount = state.rememberAccount,
                onAuthenticated = onAuthenticated,
            )
        } else {
            AppShell(
                session = session,
                selectedDestination = state.destination,
                fontScaleMode = state.fontScaleMode,
                apiClient = apiClient,
                onSessionUpdated = onSessionUpdated,
                onDestinationSelected = onDestinationSelected,
                onSignOut = onSignOut,
                onFontScaleModeSelected = onFontScaleModeSelected,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
