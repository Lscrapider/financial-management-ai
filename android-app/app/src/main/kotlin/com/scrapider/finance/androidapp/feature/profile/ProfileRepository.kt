package com.scrapider.finance.androidapp.feature.profile

import com.scrapider.finance.androidapp.core.network.ApiConfig
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toEnvelope
import org.json.JSONObject

internal class ProfileRepository(
    private val apiClient: FinanceApiClient,
) {
    suspend fun load(): NetworkResult<ProfileInfo> = apiClient.get(ApiConfig.USER_INFO_PATH)
        .toEnvelope()
        .mapProfile(::parseProfile)

    suspend fun updateBasic(
        realName: String,
        introduction: String,
    ): NetworkResult<Unit> = updateUserInfo(
        JSONObject()
            .put("realName", realName)
            .put("introduction", introduction),
    )

    suspend fun updateContacts(
        email: String,
        phone: String,
    ): NetworkResult<Unit> = updateUserInfo(
        JSONObject()
            .put("email", email)
            .put("phone", phone),
    )

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
    ): NetworkResult<Unit> = apiClient.putJson(
        path = ApiConfig.USER_PASSWORD_PATH,
        payload = JSONObject()
            .put("oldPassword", oldPassword)
            .put("newPassword", newPassword)
            .put("confirmPassword", confirmPassword),
    ).toEnvelope().asUnit()

    suspend fun updateEmailNotification(enabled: Boolean): NetworkResult<Unit> = apiClient.putJson(
        path = ApiConfig.USER_NOTIFICATION_PATH,
        payload = JSONObject().put("emailNotification", enabled),
    ).toEnvelope().asUnit()

    private suspend fun updateUserInfo(payload: JSONObject): NetworkResult<Unit> = apiClient.putJson(
        path = ApiConfig.USER_INFO_PATH,
        payload = payload,
    ).toEnvelope().asUnit()
}

private fun NetworkResult<JSONObject>.mapProfile(
    parser: (JSONObject) -> ProfileInfo,
): NetworkResult<ProfileInfo> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(parser(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun NetworkResult<JSONObject>.asUnit(): NetworkResult<Unit> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> NetworkResult.Success(Unit)
}

private fun parseProfile(root: JSONObject): ProfileInfo {
    val data = root.optJSONObject("data") ?: error("缺少用户资料数据")
    return ProfileInfo(
        username = data.text("username"),
        realName = data.text("realName"),
        introduction = data.text("introduction").ifBlank { data.text("desc") },
        email = data.text("email"),
        phone = data.text("phone"),
        emailNotification = data.optBoolean("emailNotification", false),
    )
}

private fun JSONObject.text(key: String): String = optString(key, "").trim().takeUnless { it == "null" }.orEmpty()
