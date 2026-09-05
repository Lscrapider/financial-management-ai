package com.scrapider.finance.androidapp.feature.auth

import androidx.compose.runtime.Immutable
import com.scrapider.finance.androidapp.core.session.UserSession

enum class LoginRole(
    val roleCode: String,
    val label: String,
) {
    User("USER", "普通用户"),
    Admin("ADMIN", "管理员"),
}

@Immutable
data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val selectedRole: LoginRole = LoginRole.User,
    val rememberAccount: Boolean = true,
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String = "",
)

sealed interface AuthEvent {
    data class Authenticated(
        val session: UserSession,
        val rememberAccount: Boolean,
    ) : AuthEvent
}
