package com.scrapider.finance.androidapp.feature.workbench.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTargetOption
import com.scrapider.finance.androidapp.feature.workbench.reports.reportTargetTypes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class KnowledgeViewModel(
    private val repository: KnowledgeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<KnowledgeEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var sessionToken = ""
    private var sessionGeneration = 0L
    private var active = false
    private var activeTaskNo = ""
    private var metadataJob: Job? = null
    private var targetSearchJob: Job? = null
    private var taskJob: Job? = null
    private var pollJob: Job? = null

    fun belongsToSession(accessToken: String): Boolean = accessToken.isNotBlank() && accessToken == sessionToken

    fun enter(accessToken: String) {
        if (accessToken == sessionToken && sessionToken.isNotBlank()) return

        sessionGeneration += 1
        while (eventChannel.tryReceive().isSuccess) {
            // 清除上一账号残留的一次性提示，避免切换账号后误消费旧事件。
        }
        stopPolling()
        metadataJob?.cancel()
        targetSearchJob?.cancel()
        taskJob?.cancel()
        sessionToken = accessToken
        activeTaskNo = ""
        _uiState.value = KnowledgeUiState()
        loadMetadata()
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) startPollingIfNeeded() else stopPolling()
    }

    fun loadMetadata() {
        if (sessionToken.isBlank() || _uiState.value.isLoadingMetadata) return
        val generation = sessionGeneration
        _uiState.value = _uiState.value.copy(isLoadingMetadata = true, errorMessage = "")
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            when (val result = repository.metadata()) {
                is NetworkResult.Failure -> {
                    if (generation != sessionGeneration) return@launch
                    _uiState.value = _uiState.value.copy(isLoadingMetadata = false, errorMessage = result.reason.userMessage)
                    handleFailure(result.reason)
                }

                is NetworkResult.Success -> {
                    if (generation != sessionGeneration) return@launch
                    val profiles = result.data.profiles
                    _uiState.value = _uiState.value.copy(
                        isLoadingMetadata = false,
                        errorMessage = "",
                        profiles = profiles,
                    )
                    profiles.firstOrNull { it.recommended }?.let { selectProfile(it.id) }
                        ?: profiles.firstOrNull()?.let { selectProfile(it.id) }
                }
            }
        }
    }

    fun selectMode(mode: KnowledgeSearchMode) {
        if (_uiState.value.isSubmitting || _uiState.value.submissionUnconfirmed) return
        if (_uiState.value.searchMode == mode) return
        _uiState.value = _uiState.value.copy(
            searchMode = mode,
            targetQuery = "",
            targetOptions = emptyList(),
            selectedTarget = null,
            isSearchingTargets = false,
            hasSearchedTargets = false,
            targetSearchError = "",
            queryText = if (mode == KnowledgeSearchMode.Target) "" else _uiState.value.queryText,
        )
    }

    fun updateQueryText(value: String) {
        if (_uiState.value.isSubmitting || _uiState.value.submissionUnconfirmed) return
        _uiState.value = _uiState.value.copy(queryText = value, errorMessage = "")
    }

    fun selectProfile(id: Long) {
        val state = _uiState.value
        if (state.isSubmitting || state.submissionUnconfirmed) return
        val profile = state.profiles.firstOrNull { it.id == id } ?: return
        targetSearchJob?.cancel()
        _uiState.value = state.copy(
            selectedProfileId = id,
            targetType = profile.targetType,
            targetQuery = "",
            targetOptions = emptyList(),
            selectedTarget = null,
            isSearchingTargets = false,
            hasSearchedTargets = false,
            targetSearchError = "",
            errorMessage = "",
        )
    }

    fun selectTargetType(value: String) {
        val state = _uiState.value
        if (state.isSubmitting || state.submissionUnconfirmed) return
        if (reportTargetTypes.none { it.value == value && it.value.isNotBlank() }) return
        targetSearchJob?.cancel()
        _uiState.value = state.copy(
            targetType = value,
            targetQuery = "",
            targetOptions = emptyList(),
            selectedTarget = null,
            isSearchingTargets = false,
            hasSearchedTargets = false,
            targetSearchError = "",
        )
    }

    fun updateTargetQuery(value: String) {
        val state = _uiState.value
        if (state.isSubmitting || state.submissionUnconfirmed) return
        targetSearchJob?.cancel()
        _uiState.value = state.copy(
            targetQuery = value,
            targetOptions = emptyList(),
            selectedTarget = null,
            isSearchingTargets = false,
            hasSearchedTargets = false,
            targetSearchError = "",
        )
    }

    fun searchTargets() {
        val state = _uiState.value
        if (state.isSubmitting || state.submissionUnconfirmed || state.targetType.isBlank()) return
        val generation = sessionGeneration
        val targetType = state.targetType
        val query = state.targetQuery
        targetSearchJob?.cancel()
        _uiState.value = state.copy(
            isSearchingTargets = true,
            hasSearchedTargets = false,
            targetOptions = emptyList(),
            selectedTarget = null,
            targetSearchError = "",
        )
        targetSearchJob = viewModelScope.launch {
            when (val result = repository.searchTargets(targetType, query)) {
                is NetworkResult.Failure -> {
                    if (generation != sessionGeneration) return@launch
                    _uiState.value = _uiState.value.copy(
                        isSearchingTargets = false,
                        hasSearchedTargets = true,
                        targetSearchError = result.reason.userMessage,
                    )
                    handleFailure(result.reason)
                }

                is NetworkResult.Success -> {
                    if (generation != sessionGeneration) return@launch
                    _uiState.value = _uiState.value.copy(
                        isSearchingTargets = false,
                        hasSearchedTargets = true,
                        targetOptions = result.data,
                    )
                }
            }
        }
    }

    fun selectTarget(target: ReportTargetOption) {
        val state = _uiState.value
        if (state.isSubmitting || state.submissionUnconfirmed) return
        if (state.targetOptions.none { it.key == target.key }) return
        _uiState.value = state.copy(selectedTarget = target, errorMessage = "")
    }

    fun submit() {
        val state = _uiState.value
        val profile = state.selectedProfile ?: return
        if (!state.canSubmit || state.isSubmitting || state.submissionUnconfirmed) return
        val target = state.selectedTarget
        val generation = sessionGeneration
        _uiState.value = state.copy(isSubmitting = true, errorMessage = "")
        viewModelScope.launch {
            when (val result = repository.submit(state.searchMode, profile, state.targetType, target, state.queryText)) {
                is NetworkResult.Failure -> {
                    if (generation != sessionGeneration) return@launch
                    val unconfirmed = result.reason == NetworkFailure.Unavailable ||
                        result.reason == NetworkFailure.Service ||
                        result.reason == NetworkFailure.InvalidResponse
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        submissionUnconfirmed = unconfirmed,
                        errorMessage = if (unconfirmed) {
                            "提交结果未确认，请勿立即重复检索，避免产生重复任务。"
                        } else {
                            result.reason.userMessage
                        },
                    )
                    handleFailure(result.reason)
                    if (unconfirmed) eventChannel.send(KnowledgeEvent.Notice("提交结果未确认，请稍后确认任务状态。"))
                }

                is NetworkResult.Success -> {
                    if (generation != sessionGeneration) return@launch
                    if (result.data.taskNo.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            submissionUnconfirmed = true,
                            errorMessage = NetworkFailure.InvalidResponse.userMessage,
                        )
                        eventChannel.send(KnowledgeEvent.Notice("提交响应缺少任务编号，请确认后再决定是否重新检索。"))
                        return@launch
                    }
                    activeTaskNo = result.data.taskNo
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        submissionUnconfirmed = false,
                        errorMessage = "",
                        taskErrorMessage = "",
                        task = result.data.asTask(),
                    )
                    eventChannel.send(KnowledgeEvent.Notice("已提交材料检索。"))
                    refreshTask()
                }
            }
        }
    }

    /**
     * 网络结果未知或响应缺少任务编号时，必须由用户明确确认后才允许再次创建任务。
     */
    fun retryAfterUnconfirmed() {
        val state = _uiState.value
        if (!state.submissionUnconfirmed || state.isSubmitting) return
        _uiState.value = state.copy(
            submissionUnconfirmed = false,
            errorMessage = "",
        )
        submit()
    }

    fun refreshTask() {
        val taskNo = activeTaskNo
        if (taskNo.isBlank() || _uiState.value.isLoadingTask) return
        val generation = sessionGeneration
        _uiState.value = _uiState.value.copy(isLoadingTask = true, taskErrorMessage = "")
        taskJob?.cancel()
        taskJob = viewModelScope.launch {
            fetchTask(generation, taskNo)
        }
    }

    private suspend fun fetchTask(generation: Long, taskNo: String) {
        when (val result = repository.task(taskNo)) {
            is NetworkResult.Failure -> {
                if (generation != sessionGeneration || activeTaskNo != taskNo) return
                _uiState.value = _uiState.value.copy(
                    isLoadingTask = false,
                    taskErrorMessage = result.reason.userMessage,
                )
                stopPolling()
                handleFailure(result.reason)
            }

            is NetworkResult.Success -> {
                if (generation != sessionGeneration || activeTaskNo != taskNo) return
                _uiState.value = _uiState.value.copy(
                    isLoadingTask = false,
                    taskErrorMessage = "",
                    task = result.data,
                )
                if (result.data.status.isWorking) startPollingIfNeeded() else stopPolling()
            }
        }
    }

    private fun startPollingIfNeeded() {
        pollJob?.cancel()
        val task = _uiState.value.task ?: return
        val taskNo = activeTaskNo
        if (!active || taskNo.isBlank() || !task.status.isWorking) return
        val generation = sessionGeneration
        pollJob = viewModelScope.launch {
            while (isActive && active && generation == sessionGeneration && taskNo == activeTaskNo) {
                delay(KNOWLEDGE_POLL_INTERVAL_MS)
                if (!isActive || generation != sessionGeneration || taskNo != activeTaskNo) break
                if (_uiState.value.isLoadingTask) continue
                fetchTask(generation, taskNo)
                if (!_uiState.value.task?.status?.isWorking.orFalse()) break
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun handleFailure(failure: NetworkFailure) {
        if (failure == NetworkFailure.Unauthorized) {
            eventChannel.trySend(KnowledgeEvent.SessionExpired)
        }
    }

    class Factory(apiClient: FinanceApiClient) : ViewModelProvider.Factory {
        private val repository = KnowledgeRepository(apiClient)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(KnowledgeViewModel::class.java)) {
                return KnowledgeViewModel(repository) as T
            }
            throw IllegalArgumentException("未知的知识检索 ViewModel")
        }
    }

    private companion object {
        // 与 frontend-vue 材料页现有前台轮询节奏一致；它是 UI 刷新节奏，不是业务阈值。
        const val KNOWLEDGE_POLL_INTERVAL_MS = 1_600L
    }
}

private fun KnowledgeSubmitResult.asTask(): KnowledgeTask = KnowledgeTask(
    taskNo = taskNo,
    searchMode = searchMode,
    targetType = targetType,
    targetCode = targetCode,
    targetName = targetName,
    queryText = queryText,
    rewrittenQuery = rewrittenQuery,
    status = status,
    errorMessage = "",
    submittedAt = "",
    finishedAt = "",
    chunks = emptyList(),
)

private fun Boolean?.orFalse(): Boolean = this == true
