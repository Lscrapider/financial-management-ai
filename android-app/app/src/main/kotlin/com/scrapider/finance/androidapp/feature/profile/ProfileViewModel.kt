package com.scrapider.finance.androidapp.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.session.UserSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class ProfileViewModel(
    private val repository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var currentSession: UserSession? = null
    private var sessionToken = ""
    private var sessionGeneration = 0L
    private var requestGeneration = 0L
    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    /** 每次进入页面都刷新；切换账号时取消并隔离上一会话的异步结果。 */
    fun loadForSession(session: UserSession) {
        val sessionChanged = session.accessToken != sessionToken
        currentSession = session
        if (sessionChanged) {
            sessionToken = session.accessToken
            sessionGeneration++
            requestGeneration++
            loadJob?.cancel()
            mutationJob?.cancel()
            loadJob = null
            mutationJob = null
            _uiState.value = ProfileUiState()
        }
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        if (sessionToken.isBlank() || state.isLoading || state.hasPendingSave()) return
        val expectedSessionGeneration = sessionGeneration
        val generation = ++requestGeneration
        loadJob = viewModelScope.launch {
            if (!isCurrentSession(expectedSessionGeneration)) return@launch
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = "")
            when (val result = repository.load()) {
                is NetworkResult.Success -> {
                    if (isCurrentRequest(expectedSessionGeneration, generation)) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            profile = result.data,
                            errorMessage = "",
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    if (isCurrentRequest(expectedSessionGeneration, generation)) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.reason.userMessage,
                        )
                        notifyFailure(result.reason, expectedSessionGeneration)
                    }
                }
            }
        }
    }

    fun saveBasic(
        realName: String,
        introduction: String,
    ) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.hasPendingSave()) return
        val expectedSessionGeneration = sessionGeneration
        _uiState.value = state.copy(isSavingBasic = true)
        mutationJob = viewModelScope.launch {
            when (val result = repository.updateBasic(realName, introduction)) {
                is NetworkResult.Success -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    val accessToken = currentAccessToken(expectedSessionGeneration) ?: return@launch
                    val updated = profile.copy(realName = realName, introduction = introduction)
                    _uiState.value = _uiState.value
                        .copy(profile = updated, isSavingBasic = false)
                        .completed(ProfileDialog.Basic)
                    publishSessionUpdate(updated, expectedSessionGeneration)
                    eventChannel.send(
                        ProfileEvent.Saved(
                            accessToken = accessToken,
                            dialog = ProfileDialog.Basic,
                            message = "基本资料已保存",
                        ),
                    )
                }

                is NetworkResult.Failure -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    _uiState.value = _uiState.value.copy(isSavingBasic = false)
                    notifyFailure(result.reason, expectedSessionGeneration)
                }
            }
        }
    }

    fun saveContacts(
        email: String,
        phone: String,
    ) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.hasPendingSave()) return
        val expectedSessionGeneration = sessionGeneration
        _uiState.value = state.copy(isSavingContacts = true)
        mutationJob = viewModelScope.launch {
            when (val result = repository.updateContacts(email, phone)) {
                is NetworkResult.Success -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    val accessToken = currentAccessToken(expectedSessionGeneration) ?: return@launch
                    _uiState.value = _uiState.value.copy(
                        profile = profile.copy(email = email, phone = phone),
                        isSavingContacts = false,
                    ).completed(ProfileDialog.Contacts)
                    eventChannel.send(
                        ProfileEvent.Saved(
                            accessToken = accessToken,
                            dialog = ProfileDialog.Contacts,
                            message = "联系方式已保存",
                        ),
                    )
                }

                is NetworkResult.Failure -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    _uiState.value = _uiState.value.copy(isSavingContacts = false)
                    notifyFailure(result.reason, expectedSessionGeneration)
                }
            }
        }
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
    ) {
        val state = _uiState.value
        if (state.hasPendingSave()) return
        val expectedSessionGeneration = sessionGeneration
        _uiState.value = state.copy(isChangingPassword = true)
        mutationJob = viewModelScope.launch {
            when (val result = repository.changePassword(oldPassword, newPassword, confirmPassword)) {
                is NetworkResult.Success -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    val accessToken = currentAccessToken(expectedSessionGeneration) ?: return@launch
                    _uiState.value = _uiState.value
                        .copy(isChangingPassword = false)
                        .completed(ProfileDialog.Password)
                    eventChannel.send(
                        ProfileEvent.Saved(
                            accessToken = accessToken,
                            dialog = ProfileDialog.Password,
                            message = "密码已修改",
                        ),
                    )
                }

                is NetworkResult.Failure -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    _uiState.value = _uiState.value.copy(isChangingPassword = false)
                    notifyFailure(result.reason, expectedSessionGeneration)
                }
            }
        }
    }

    fun updateEmailNotification(enabled: Boolean) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.hasPendingSave()) return
        val expectedSessionGeneration = sessionGeneration
        _uiState.value = state.copy(
            profile = profile.copy(emailNotification = enabled),
            isUpdatingNotification = true,
        )
        mutationJob = viewModelScope.launch {
            when (val result = repository.updateEmailNotification(enabled)) {
                is NetworkResult.Success -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    val accessToken = currentAccessToken(expectedSessionGeneration) ?: return@launch
                    _uiState.value = _uiState.value.copy(isUpdatingNotification = false)
                    eventChannel.send(
                        ProfileEvent.Saved(
                            accessToken = accessToken,
                            dialog = null,
                            message = "邮件通知已更新",
                        ),
                    )
                }

                is NetworkResult.Failure -> {
                    if (!isCurrentSession(expectedSessionGeneration)) return@launch
                    _uiState.value = _uiState.value.copy(
                        profile = _uiState.value.profile?.copy(emailNotification = profile.emailNotification),
                        isUpdatingNotification = false,
                    )
                    notifyFailure(result.reason, expectedSessionGeneration)
                }
            }
        }
    }

    private suspend fun notifyFailure(
        failure: NetworkFailure,
        expectedSessionGeneration: Long,
    ) {
        val accessToken = currentAccessToken(expectedSessionGeneration) ?: return
        if (failure == NetworkFailure.Unauthorized) {
            eventChannel.send(ProfileEvent.SessionExpired(accessToken))
        } else {
            eventChannel.send(ProfileEvent.Notice(accessToken, failure.userMessage))
        }
    }

    private suspend fun publishSessionUpdate(
        profile: ProfileInfo,
        expectedSessionGeneration: Long,
    ) {
        if (currentAccessToken(expectedSessionGeneration) == null) return
        val session = currentSession ?: return
        val updatedSession = session.copy(
            username = profile.username.ifBlank { session.username },
            realName = profile.realName,
        )
        currentSession = updatedSession
        eventChannel.send(ProfileEvent.SessionUpdated(updatedSession))
    }

    private fun isCurrentRequest(
        expectedSessionGeneration: Long,
        expectedRequestGeneration: Long,
    ): Boolean = isCurrentSession(expectedSessionGeneration) && expectedRequestGeneration == requestGeneration

    private fun isCurrentSession(expectedSessionGeneration: Long): Boolean =
        expectedSessionGeneration == sessionGeneration && sessionToken.isNotBlank()

    private fun currentAccessToken(expectedSessionGeneration: Long): String? =
        sessionToken.takeIf { isCurrentSession(expectedSessionGeneration) }

    override fun onCleared() {
        loadJob?.cancel()
        mutationJob?.cancel()
        eventChannel.close()
        super.onCleared()
    }

    class Factory(
        apiClient: FinanceApiClient,
    ) : ViewModelProvider.Factory {
        private val repository = ProfileRepository(apiClient)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                return ProfileViewModel(repository) as T
            }
            throw IllegalArgumentException("未知的个人中心 ViewModel：" + modelClass.name)
        }
    }
}

private fun ProfileUiState.hasPendingSave(): Boolean =
    isSavingBasic || isSavingContacts || isChangingPassword || isUpdatingNotification

private fun ProfileUiState.completed(dialog: ProfileDialog): ProfileUiState = copy(
    completedOperationId = completedOperationId + 1,
    completedDialog = dialog,
)
