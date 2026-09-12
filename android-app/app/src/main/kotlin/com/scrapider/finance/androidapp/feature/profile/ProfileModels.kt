package com.scrapider.finance.androidapp.feature.profile

import androidx.compose.runtime.Immutable
import com.scrapider.finance.androidapp.core.session.UserSession

@Immutable
internal data class ProfileInfo(
    val username: String = "",
    val realName: String = "",
    val introduction: String = "",
    val email: String = "",
    val phone: String = "",
    val emailNotification: Boolean = false,
) {
    val displayName: String
        get() = realName.ifBlank { username }.ifBlank { "研究员" }
}

@Immutable
internal data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: ProfileInfo? = null,
    val errorMessage: String = "",
    val isSavingBasic: Boolean = false,
    val isSavingContacts: Boolean = false,
    val isChangingPassword: Boolean = false,
    val isUpdatingNotification: Boolean = false,
    val completedOperationId: Long = 0L,
    val completedDialog: ProfileDialog? = null,
)

internal enum class ProfileDialog {
    Basic,
    Contacts,
    Password,
}

internal sealed interface ProfileEvent {
    val accessToken: String

    data class SessionExpired(
        override val accessToken: String,
    ) : ProfileEvent

    data class Notice(
        override val accessToken: String,
        val message: String,
    ) : ProfileEvent

    data class Saved(
        override val accessToken: String,
        val dialog: ProfileDialog?,
        val message: String,
    ) : ProfileEvent

    data class SessionUpdated(val session: UserSession) : ProfileEvent {
        override val accessToken: String
            get() = session.accessToken
    }
}
