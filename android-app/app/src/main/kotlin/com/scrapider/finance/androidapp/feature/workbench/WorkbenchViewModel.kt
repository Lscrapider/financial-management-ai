package com.scrapider.finance.androidapp.feature.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkbenchViewModel(
    private val repository: WorkbenchRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkbenchUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<WorkbenchEvent>()
    val events = _events.asSharedFlow()

    private var sessionToken: String = ""
    private var requestGeneration: Long = 0L

    fun loadForSession(accessToken: String) {
        if (accessToken == sessionToken) return
        sessionToken = accessToken
        _uiState.value = WorkbenchUiState()
        refresh()
    }

    fun refresh() {
        if (sessionToken.isBlank() || _uiState.value.isLoading) return
        val currentRequest = ++requestGeneration
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, syncMessage = "")
            when (val result = repository.load()) {
                is NetworkResult.Failure -> {
                    if (currentRequest == requestGeneration) {
                        _uiState.value = WorkbenchUiState(
                            isLoading = false,
                            syncMessage = result.reason.userMessage,
                        )
                        if (result.reason == NetworkFailure.Unauthorized) {
                            _events.emit(WorkbenchEvent.SessionExpired)
                        }
                    }
                }

                is NetworkResult.Success -> {
                    if (currentRequest == requestGeneration) {
                        _uiState.value = WorkbenchUiState(
                            isLoading = false,
                            focusItems = result.data.focusItems,
                            reportItems = result.data.reportItems,
                            syncMessage = result.data.partialFailure?.userMessage.orEmpty(),
                        )
                        if (result.data.partialFailure == NetworkFailure.Unauthorized) {
                            _events.emit(WorkbenchEvent.SessionExpired)
                        }
                    }
                }
            }
        }
    }

    class Factory(
        apiClient: FinanceApiClient,
    ) : ViewModelProvider.Factory {
        private val repository = WorkbenchRepository(apiClient)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkbenchViewModel::class.java)) {
                return WorkbenchViewModel(repository) as T
            }
            throw IllegalArgumentException("未知的工作台 ViewModel：" + modelClass.name)
        }
    }
}
