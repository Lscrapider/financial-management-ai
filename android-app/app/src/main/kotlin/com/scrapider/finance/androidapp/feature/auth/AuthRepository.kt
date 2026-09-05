package com.scrapider.finance.androidapp.feature.auth

import com.scrapider.finance.androidapp.core.network.ApiConfig
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toEnvelope
import com.scrapider.finance.androidapp.core.session.UserSession
import org.json.JSONArray
import org.json.JSONObject

class AuthRepository(
    private val apiClient: FinanceApiClient,
) {
    suspend fun login(
        username: String,
        password: String,
        role: LoginRole,
    ): NetworkResult<UserSession> {
        val normalizedUsername = username.trim()
        val loginResponse = apiClient.postJson(
            path = ApiConfig.LOGIN_PATH,
            payload = JSONObject()
                .put("username", normalizedUsername)
                .put("password", password)
                .put("roleCode", role.roleCode),
        ).toEnvelope()

        val token = when (loginResponse) {
            is NetworkResult.Failure -> return loginResponse
            is NetworkResult.Success -> loginResponse.data
                .optJSONObject("data")
                ?.optString("accessToken", "")
                .orEmpty()
        }
        if (token.isBlank()) {
            return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        }

        apiClient.setAccessToken(token)
        val userResponse = apiClient.get(ApiConfig.USER_INFO_PATH).toEnvelope()
        val userData = when (userResponse) {
            is NetworkResult.Failure -> {
                apiClient.setAccessToken("")
                return userResponse
            }

            is NetworkResult.Success -> userResponse.data.optJSONObject("data")
                ?: run {
                    apiClient.setAccessToken("")
                    return NetworkResult.Failure(NetworkFailure.InvalidResponse)
                }
        }

        val session = UserSession(
            accessToken = userData.optString("token", token).ifBlank { token },
            username = userData.optString("username", normalizedUsername).ifBlank { normalizedUsername },
            realName = userData.optString("realName", ""),
            roles = userData.optJSONArray("roles").toRoleList(),
        )
        apiClient.setAccessToken(session.accessToken)
        return NetworkResult.Success(session)
    }
}

private fun JSONArray?.toRoleList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
