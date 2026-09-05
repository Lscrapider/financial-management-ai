package com.scrapider.finance.androidapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrapider.finance.androidapp.core.network.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    initialUsername: String,
    initialRememberAccount: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AuthUiState(
            username = initialUsername,
            rememberAccount = initialRememberAccount,
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events = _events.asSharedFlow()

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, errorMessage = "")
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = "")
    }

    fun selectRole(role: LoginRole) {
        _uiState.value = _uiState.value.copy(selectedRole = role, errorMessage = "")
    }

    fun updateRememberAccount(rememberAccount: Boolean) {
        _uiState.value = _uiState.value.copy(rememberAccount = rememberAccount)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
    }

    fun submit() {
        val currentState = _uiState.value
        if (currentState.isSubmitting) return
        if (currentState.username.isBlank() || currentState.password.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "请输入用户名和密码。")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isSubmitting = true, errorMessage = "")
            when (
                val result = repository.login(
                    username = currentState.username,
                    password = currentState.password,
                    role = currentState.selectedRole,
                )
            ) {
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = result.reason.userMessage,
                    )
                }

                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, password = "")
                    _events.emit(
                        AuthEvent.Authenticated(
                            session = result.data,
                            rememberAccount = currentState.rememberAccount,
                        ),
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: AuthRepository,
        private val initialUsername: String,
        private val initialRememberAccount: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                return AuthViewModel(
                    repository = repository,
                    initialUsername = initialUsername,
                    initialRememberAccount = initialRememberAccount,
                ) as T
            }
            throw IllegalArgumentException("未知的登录 ViewModel：" + modelClass.name)
        }
    }
}
