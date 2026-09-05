package com.scrapider.finance.androidapp.core.session

import android.content.Context

data class SessionSnapshot(
    val session: UserSession?,
    val username: String,
    val rememberAccount: Boolean,
)

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): SessionSnapshot {
        val rememberAccount = preferences.getBoolean(KEY_REMEMBER_ACCOUNT, true)
        val rememberedUsername = preferences.getString(KEY_REMEMBERED_USERNAME, "").orEmpty()
        val token = preferences.getString(KEY_TOKEN, "").orEmpty()
        val username = preferences.getString(KEY_USERNAME, "").orEmpty()
        val loginUsername = if (rememberAccount) {
            rememberedUsername.ifBlank { username }.ifBlank { DEFAULT_USERNAME }
        } else {
            ""
        }
        val session = token.takeIf { it.isNotBlank() }?.let {
            UserSession(
                accessToken = it,
                username = username.ifBlank { loginUsername },
                realName = preferences.getString(KEY_REAL_NAME, "").orEmpty(),
                roles = preferences.getString(KEY_ROLES, "").orEmpty()
                    .split(ROLE_SEPARATOR)
                    .filter(String::isNotBlank),
            )
        }
        return SessionSnapshot(
            session = session,
            username = loginUsername,
            rememberAccount = rememberAccount,
        )
    }

    fun save(session: UserSession, rememberAccount: Boolean) {
        preferences.edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_REAL_NAME, session.realName)
            .putString(KEY_ROLES, session.roles.joinToString(ROLE_SEPARATOR))
            .putBoolean(KEY_REMEMBER_ACCOUNT, rememberAccount)
            .apply {
                if (rememberAccount) {
                    putString(KEY_REMEMBERED_USERNAME, session.username)
                } else {
                    remove(KEY_REMEMBERED_USERNAME)
                }
            }
            .apply()
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_REAL_NAME)
            .remove(KEY_ROLES)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "finance_android_session"
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
        const val KEY_REAL_NAME = "real_name"
        const val KEY_ROLES = "roles"
        const val KEY_REMEMBER_ACCOUNT = "remember_account"
        const val KEY_REMEMBERED_USERNAME = "remembered_username"
        const val ROLE_SEPARATOR = "\u001F"
        const val DEFAULT_USERNAME = "research_user_01"
    }
}
