package com.scrapider.finance.androidapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scrapider.finance.androidapp.data.ApiClient
import com.scrapider.finance.androidapp.data.FinanceRepository
import com.scrapider.finance.androidapp.data.SessionState
import com.scrapider.finance.androidapp.data.WorkbenchSummary
import com.scrapider.finance.androidapp.ui.FinanceTheme
import com.scrapider.finance.androidapp.ui.LoginScreen
import com.scrapider.finance.androidapp.ui.WorkbenchScreen

private enum class AppScreen {
    Login,
    Workbench,
}

private data class AppUiState(
    val screen: AppScreen = AppScreen.Login,
    val username: String = "research_user_01",
    val password: String = "",
    val loginAsAdmin: Boolean = false,
    val rememberAccount: Boolean = true,
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String = "",
    val session: SessionState = SessionState(),
    val workbench: WorkbenchSummary = WorkbenchSummary(),
)

class MainActivity : ComponentActivity() {
    private companion object {
        const val PREFS_NAME = "finance_android_session"
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
        const val KEY_REAL_NAME = "real_name"
        const val KEY_ROLES = "roles"
        const val KEY_REMEMBER_ACCOUNT = "remember_account"
        const val KEY_REMEMBERED_USERNAME = "remembered_username"
        const val ROLE_SEPARATOR = "\u001F"
    }

    private val apiClient = ApiClient()
    private val repository = FinanceRepository(apiClient)
    private var state by mutableStateOf(AppUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(20, 22, 26)
        window.navigationBarColor = android.graphics.Color.rgb(20, 22, 26)
        state = savedUiState()

        setContent {
            FinanceTheme {
                when (state.screen) {
                    AppScreen.Login -> LoginScreen(
                        username = state.username,
                        password = state.password,
                        loginAsAdmin = state.loginAsAdmin,
                        rememberAccount = state.rememberAccount,
                        passwordVisible = state.passwordVisible,
                        loading = state.loading,
                        errorMessage = state.errorMessage,
                        onUsernameChange = { state = state.copy(username = it, errorMessage = "") },
                        onPasswordChange = { state = state.copy(password = it, errorMessage = "") },
                        onRoleChange = { state = state.copy(loginAsAdmin = it) },
                        onRememberChange = { state = state.copy(rememberAccount = it) },
                        onPasswordVisibleChange = { state = state.copy(passwordVisible = it) },
                        onLogin = ::submitLogin,
                    )

                    AppScreen.Workbench -> WorkbenchScreen(
                        displayName = state.session.displayName,
                        loading = state.loading,
                        statusMessage = state.statusMessage,
                        summary = state.workbench,
                        onRefresh = ::refreshWorkbench,
                    )
                }
            }
        }

        if (state.session.authenticated) {
            apiClient.setAccessToken(state.session.accessToken)
            refreshWorkbench()
        }
    }

    override fun onDestroy() {
        apiClient.shutdown()
        super.onDestroy()
    }

    private fun submitLogin() {
        if (state.loading) return
        state = state.copy(
            loading = true,
            errorMessage = "",
            statusMessage = "正在登录：${state.username}，角色 ${if (state.loginAsAdmin) "管理员" else "普通用户"}。",
        )
        repository.login(
            username = state.username,
            password = state.password,
            roleCode = if (state.loginAsAdmin) "ADMIN" else "USER",
        ) { result, session ->
            if (!result.success || !session.authenticated) {
                state = state.copy(
                    loading = false,
                    errorMessage = result.message.ifBlank { "用户名或密码错误" },
                    statusMessage = result.message.ifBlank { "登录失败，请检查账号和密码。" },
                )
                return@login
            }
            state = state.copy(
                screen = AppScreen.Workbench,
                loading = false,
                session = session,
                statusMessage = "登录成功：${session.displayName}，正在同步投资工作台。",
                errorMessage = "",
            )
            saveLoginState(session)
            refreshWorkbench()
        }
    }

    private fun refreshWorkbench() {
        if (!state.session.authenticated) {
            state = state.copy(screen = AppScreen.Login, statusMessage = "请先登录，登录后进入投资工作台。")
            return
        }
        state = state.copy(loading = true, statusMessage = "正在同步投资工作台：观察池、布控提醒和报告动态。")
        repository.loadWorkbench { result, summary ->
            if (result.statusCode == 401) {
                clearLoginState()
                state = state.copy(
                    screen = AppScreen.Login,
                    loading = false,
                    session = SessionState(),
                    statusMessage = "登录态已失效，请重新登录。",
                    errorMessage = "登录态已失效",
                )
                return@loadWorkbench
            }
            state = state.copy(
                loading = false,
                workbench = if (result.success || summary.hasAnyData) summary else state.workbench,
                statusMessage = when {
                    result.success -> "投资工作台已同步：观察池 ${summary.watchItemCount} 个标的，关注 ${summary.focusCount} 项。"
                    summary.hasAnyData -> "部分同步完成：观察池 ${summary.watchItemCount} 个标的，关注 ${summary.focusCount} 项。${result.message}"
                    else -> result.message
                },
            )
            if (!result.success && !summary.hasAnyData) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savedUiState(): AppUiState {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val rememberAccount = preferences.getBoolean(KEY_REMEMBER_ACCOUNT, true)
        val token = preferences.getString(KEY_TOKEN, "").orEmpty()
        val rememberedUsername = preferences.getString(KEY_REMEMBERED_USERNAME, "").orEmpty()
        val sessionUsername = preferences.getString(KEY_USERNAME, "").orEmpty()
        if (token.isBlank()) {
            return AppUiState(
                username = if (rememberAccount) rememberedUsername.ifBlank { "research_user_01" } else "",
                rememberAccount = rememberAccount,
            )
        }
        val session = SessionState(
            authenticated = true,
            accessToken = token,
            username = sessionUsername.ifBlank { rememberedUsername },
            realName = preferences.getString(KEY_REAL_NAME, "").orEmpty(),
            roles = preferences.getString(KEY_ROLES, "").orEmpty().split(ROLE_SEPARATOR).filter { it.isNotBlank() },
        )
        return AppUiState(
            screen = AppScreen.Workbench,
            username = if (rememberAccount) session.username.ifBlank { rememberedUsername } else "",
            rememberAccount = rememberAccount,
            session = session,
            statusMessage = "正在恢复登录态并同步投资工作台。",
        )
    }

    private fun saveLoginState(session: SessionState) {
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_REAL_NAME, session.realName)
            .putString(KEY_ROLES, session.roles.joinToString(ROLE_SEPARATOR))
            .putBoolean(KEY_REMEMBER_ACCOUNT, state.rememberAccount)
        if (state.rememberAccount) {
            editor.putString(KEY_REMEMBERED_USERNAME, session.username.ifBlank { state.username })
        } else {
            editor.remove(KEY_REMEMBERED_USERNAME)
        }
        editor.apply()
    }

    private fun clearLoginState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_REAL_NAME)
            .remove(KEY_ROLES)
            .apply()
    }
}
