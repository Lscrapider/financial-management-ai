package com.scrapider.finance.androidapp.core.session

import androidx.compose.runtime.Immutable

@Immutable
data class UserSession(
    val accessToken: String,
    val username: String,
    val realName: String,
    val roles: List<String>,
) {
    val displayName: String
        get() = realName.ifBlank { username.ifBlank { "研究员" } }

    val isAdmin: Boolean
        get() = roles.any { it.contains("admin", ignoreCase = true) || it.contains("管理员") }
}
