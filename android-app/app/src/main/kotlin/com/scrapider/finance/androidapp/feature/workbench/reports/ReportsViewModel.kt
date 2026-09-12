package com.scrapider.finance.androidapp.feature.workbench.reports

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.feature.workbench.ReportStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class ReportsViewModel(private val repository: ReportsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState = _uiState.asStateFlow()
    private val eventChannel = Channel<ReportsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var token = ""
    private var lastEntryKey = ""
    private var listJob: Job? = null
    private var historyJob: Job? = null
    private var detailJob: Job? = null
    private var metadataJob: Job? = null
    private var searchJob: Job? = null
    private var pollJob: Job? = null
    private var active = false
    private var pageSize: Int? = null
    private var appliedQuery = ""
    private var appliedType = ""
    private var detailReturnPage = ReportPage.List
    private var detailRequestId: Long? = null
    private var followLatest = false
    private var directEntry = false

    fun belongsToSession(accessToken: String): Boolean = token == accessToken

    fun enter(accessToken: String, entryKey: String, entryId: String?) {
        if (token != accessToken) {
            viewModelScope.coroutineContext.cancelChildren()
            while (eventChannel.tryReceive().isSuccess) { /* 清除上一账号尚未消费的提示。 */ }
            token = accessToken
            lastEntryKey = ""
            pageSize = null
            _uiState.value = ReportsUiState()
        }
        // 同一次入口在旋转屏幕后重建 Route，保留阅读位置与表单状态。
        if (lastEntryKey == entryKey) return
        lastEntryKey = entryKey
        directEntry = entryId != null
        _uiState.value = _uiState.value.copy(
            page = ReportPage.List,
            query = if (entryId != null) "" else _uiState.value.query,
            targetType = if (entryId != null) "" else _uiState.value.targetType,
        )
        refresh(entryId?.takeIf { it.toLongOrNull() == null })
        entryId?.toLongOrNull()?.let { openDetail(it, ReportPage.List, latest = true, fromWorkbench = true) }
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) startPolling() else pollJob?.cancel()
    }

    fun updateQuery(query: String) { _uiState.value = _uiState.value.copy(query = query) }

    fun selectType(type: String) {
        if (reportTargetTypes.none { it.value == type }) return
        _uiState.value = _uiState.value.copy(targetType = type)
        refresh()
    }

    fun refresh(entryId: String? = null) {
        listJob?.cancel()
        appliedQuery = _uiState.value.query.trim()
        appliedType = _uiState.value.targetType
        val query = appliedQuery
        val type = appliedType
        _uiState.value = _uiState.value.copy(isLoading = true, isLoadingMore = false, errorMessage = "", moreError = "")
        listJob = viewModelScope.launch {
            when (val result = repository.targets(query, type)) {
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, hasLoaded = true, errorMessage = result.reason.userMessage)
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    pageSize = result.data.pageSize
                    _uiState.value = _uiState.value.copy(isLoading = false, hasLoaded = true, targets = result.data.items, nextPage = result.data.nextPage)
                    if (_uiState.value.page == ReportPage.List && _uiState.value.create.submissionUnconfirmed) {
                        // 列表成功更新后，用户需要重新选择标的，才能有意发起下一次研究。
                        updateCreate { copy(submissionUnconfirmed = false, selectedTarget = null, errorMessage = "") }
                    }
                    if (entryId != null && _uiState.value.page == ReportPage.List) {
                        val target = result.data.items.find { it.taskNo == entryId }
                        if (target != null) {
                            openTarget(target)
                            directEntry = true
                        }
                        else eventChannel.send(ReportsEvent.Notice("已打开报告列表，请选择要查看的报告。"))
                    }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val next = state.nextPage ?: return
        if (state.isLoading || state.isLoadingMore) return
        _uiState.value = state.copy(isLoadingMore = true, moreError = "")
        listJob = viewModelScope.launch {
            when (val result = repository.targets(appliedQuery, appliedType, next, pageSize)) {
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, moreError = result.reason.userMessage)
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    targets = (_uiState.value.targets + result.data.items).distinctBy(ReportTarget::key),
                    nextPage = result.data.nextPage,
                )
            }
        }
    }

    fun openTarget(target: ReportTarget) {
        directEntry = false
        detailReturnPage = ReportPage.List
        followLatest = target.taskNo.isNotBlank()
        detailRequestId = target.reportId
        pollJob?.cancel()
        _uiState.value = _uiState.value.copy(page = ReportPage.Detail, document = target.asDocument(), documentError = "")
        refreshDocument()
    }

    fun openHistory(target: ReportTarget) {
        pollJob?.cancel()
        _uiState.value = _uiState.value.copy(page = ReportPage.History, historyTarget = target, history = emptyList())
        refreshHistory()
    }

    fun refreshHistory() {
        val target = _uiState.value.historyTarget ?: return
        historyJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoadingHistory = true, historyError = "")
        historyJob = viewModelScope.launch {
            when (val result = repository.history(target)) {
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoadingHistory = false, historyError = result.reason.userMessage)
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(isLoadingHistory = false, history = result.data)
            }
        }
    }

    fun openRecord(reportId: Long) { openDetail(reportId, ReportPage.History, latest = false) }

    private fun openDetail(id: Long, returnPage: ReportPage, latest: Boolean, fromWorkbench: Boolean = false) {
        directEntry = fromWorkbench
        detailReturnPage = returnPage
        detailRequestId = id
        followLatest = latest
        pollJob?.cancel()
        _uiState.value = _uiState.value.copy(page = ReportPage.Detail, document = null, documentError = "")
        refreshDocument()
    }

    fun refreshDocument() {
        detailJob?.cancel()
        pollJob?.cancel()
        val document = _uiState.value.document
        val id = detailRequestId
        if ((document == null || document.taskNo.isBlank()) && id == null) {
            _uiState.value = _uiState.value.copy(documentError = "该记录暂时没有可读取的报告，请返回列表后稍候再试。")
            return
        }
        _uiState.value = _uiState.value.copy(isLoadingDocument = true, documentError = "")
        detailJob = viewModelScope.launch {
            val result = if (followLatest && document != null && document.taskNo.isNotBlank()) repository.task(document)
                else repository.detail(requireNotNull(id))
            applyDocument(result)
            if (result is NetworkResult.Success) {
                _uiState.value = _uiState.value.copy(
                    unconfirmedRegenerationTasks = _uiState.value.unconfirmedRegenerationTasks - result.data.taskNo,
                )
                startPolling()
            }
        }
    }

    private suspend fun applyDocument(result: NetworkResult<ReportDocument>) {
        when (result) {
            is NetworkResult.Failure -> {
                _uiState.value = _uiState.value.copy(isLoadingDocument = false, documentError = result.reason.userMessage)
                handleFailure(result.reason)
            }
            is NetworkResult.Success -> {
                val wasWorking = _uiState.value.document?.status.isWorking()
                _uiState.value = _uiState.value.copy(isLoadingDocument = false, documentError = "", document = result.data)
                if (wasWorking && !result.data.status.isWorking()) eventChannel.send(ReportsEvent.ReportsChanged)
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        if (!active || _uiState.value.page != ReportPage.Detail || !_uiState.value.document?.status.isWorking()) return
        val startedAt = SystemClock.elapsedRealtime()
        pollJob = viewModelScope.launch {
            while (active && _uiState.value.page == ReportPage.Detail && _uiState.value.document?.status.isWorking()) {
                // 与 Web report-polling.ts 一致：开始 20 秒内间隔 10 秒，之后间隔 5 秒。
                delay(if (SystemClock.elapsedRealtime() - startedAt < FIRST_WINDOW_MS) FIRST_INTERVAL_MS else NORMAL_INTERVAL_MS)
                val document = _uiState.value.document ?: break
                val result = if (followLatest && document.taskNo.isNotBlank()) repository.task(document)
                    else document.reportId?.let { repository.detail(it) } ?: break
                applyDocument(result)
                if (result is NetworkResult.Failure) break
            }
        }
    }

    fun openCreate() {
        directEntry = false
        pollJob?.cancel()
        _uiState.value = _uiState.value.copy(page = ReportPage.Create)
        if (_uiState.value.create.profiles.isEmpty()) loadMetadata()
    }

    fun loadMetadata() {
        metadataJob?.cancel()
        updateCreate { copy(isLoading = true, errorMessage = "") }
        metadataJob = viewModelScope.launch {
            when (val result = repository.metadata()) {
                is NetworkResult.Failure -> {
                    updateCreate { copy(isLoading = false, errorMessage = result.reason.userMessage) }
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    val profile = result.data.profiles.find { it.recommended } ?: result.data.profiles.firstOrNull()
                    updateCreate { copy(isLoading = false, profiles = result.data.profiles, reportTypes = result.data.reportTypes) }
                    profile?.let { selectProfile(it.id) }
                }
            }
        }
    }

    fun selectProfile(id: Long) {
        if (_uiState.value.create.isSubmitting || _uiState.value.create.submissionUnconfirmed) return
        if (_uiState.value.create.selectedProfileId == id) return
        val profile = _uiState.value.create.profiles.find { it.id == id } ?: return
        searchJob?.cancel()
        updateCreate { copy(
            selectedProfileId = id, selectedReportType = profile.reportType,
            // 通用配置不指定标的类型；沿用已有 Web 表单和后端搜索的 STOCK 默认值。
            targetType = profile.targetType.ifBlank { DEFAULT_TARGET_TYPE },
            targetQuery = "", selectedTarget = null, targetOptions = emptyList(), hasSearched = false,
            isSearching = false, searchError = "", errorMessage = "",
        ) }
    }

    fun selectReportType(type: String) {
        if (_uiState.value.create.isSubmitting || _uiState.value.create.submissionUnconfirmed || _uiState.value.create.reportTypes.none { it.value == type }) return
        updateCreate { copy(selectedReportType = type) }
    }

    fun selectTargetType(type: String) {
        if (_uiState.value.create.isSubmitting || _uiState.value.create.submissionUnconfirmed || reportTargetTypes.none { it.value == type && type.isNotBlank() }) return
        if (_uiState.value.create.targetType == type) return
        searchJob?.cancel()
        updateCreate { copy(targetType = type, targetQuery = "", selectedTarget = null, targetOptions = emptyList(), hasSearched = false, isSearching = false, searchError = "") }
    }

    fun updateTargetQuery(query: String) {
        if (_uiState.value.create.isSubmitting || _uiState.value.create.submissionUnconfirmed) return
        if (_uiState.value.create.targetQuery == query) return
        searchJob?.cancel()
        updateCreate { copy(targetQuery = query, selectedTarget = null, targetOptions = emptyList(), isSearching = false, hasSearched = false, searchError = "") }
    }

    fun searchTargets() {
        val create = _uiState.value.create
        if (create.isSubmitting || create.submissionUnconfirmed || create.targetType.isBlank()) return
        searchJob?.cancel()
        updateCreate { copy(isSearching = true, searchError = "", hasSearched = false) }
        searchJob = viewModelScope.launch {
            when (val result = repository.searchTargets(create.targetType, create.targetQuery)) {
                is NetworkResult.Failure -> {
                    updateCreate { copy(isSearching = false, hasSearched = true, searchError = result.reason.userMessage) }
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> updateCreate { copy(isSearching = false, hasSearched = true, targetOptions = result.data) }
            }
        }
    }

    fun selectTarget(target: ReportTargetOption) {
        if (_uiState.value.create.isSubmitting || _uiState.value.create.submissionUnconfirmed || _uiState.value.create.targetOptions.none { it.key == target.key }) return
        updateCreate { copy(selectedTarget = target) }
    }

    fun submit() {
        val create = _uiState.value.create
        if (!create.canSubmit) return
        val target = requireNotNull(create.selectedTarget)
        val profileId = requireNotNull(create.selectedProfileId)
        updateCreate { copy(isSubmitting = true, errorMessage = "") }
        viewModelScope.launch {
            when (val result = repository.submit(profileId, create.selectedReportType, target)) {
                is NetworkResult.Failure -> {
                    val unconfirmed = result.reason.needsSubmissionConfirmation()
                    val message = if (unconfirmed) "提交结果未确认，请先查看报告列表，避免重复生成。" else result.reason.userMessage
                    updateCreate { copy(isSubmitting = false, submissionUnconfirmed = unconfirmed, errorMessage = message) }
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    updateCreate { copy(isSubmitting = false, targetQuery = "", selectedTarget = null, targetOptions = emptyList(), hasSearched = false) }
                    eventChannel.send(ReportsEvent.ReportsChanged)
                    eventChannel.send(ReportsEvent.Notice("生成任务已提交。"))
                    refresh()
                    if (_uiState.value.page == ReportPage.Create) {
                        detailReturnPage = ReportPage.List
                        detailRequestId = null
                        followLatest = true
                        _uiState.value = _uiState.value.copy(page = ReportPage.Detail, documentError = "", document = ReportDocument(
                            reportId = null, taskNo = result.data.taskNo, targetName = target.targetName,
                            targetCode = target.targetCode, targetTypeLabel = target.typeLabel,
                            reportTypeLabel = create.reportTypes.find { it.value == create.selectedReportType }?.label.orEmpty(),
                            timeLabel = "暂无时间", versionLabel = "", status = result.data.status, blocks = emptyList(),
                        ))
                        refreshDocument()
                    }
                }
            }
        }
    }

    fun regenerate() {
        val document = _uiState.value.document ?: return
        if (document.taskNo.isBlank() || _uiState.value.isRegenerating || document.status.isWorking() ||
            document.taskNo in _uiState.value.unconfirmedRegenerationTasks) return
        _uiState.value = _uiState.value.copy(isRegenerating = true)
        viewModelScope.launch {
            when (val result = repository.regenerate(document.taskNo)) {
                is NetworkResult.Failure -> {
                    val unconfirmed = result.reason.needsSubmissionConfirmation()
                    _uiState.value = _uiState.value.copy(
                        isRegenerating = false,
                        unconfirmedRegenerationTasks = if (unconfirmed)
                            _uiState.value.unconfirmedRegenerationTasks + document.taskNo
                        else _uiState.value.unconfirmedRegenerationTasks,
                    )
                    handleFailure(result.reason)
                    eventChannel.send(ReportsEvent.Notice(if (unconfirmed) "提交结果未确认，请先核对报告记录，避免重复生成。" else result.reason.userMessage))
                }
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isRegenerating = false)
                    eventChannel.send(ReportsEvent.ReportsChanged)
                    eventChannel.send(ReportsEvent.Notice("已提交新的报告版本。"))
                    refresh()
                    if (_uiState.value.page == ReportPage.Detail && _uiState.value.document?.taskNo == document.taskNo) {
                        followLatest = true
                        refreshDocument()
                    }
                }
            }
        }
    }

    fun shouldCloseOnBack(): Boolean = _uiState.value.page == ReportPage.List ||
        (_uiState.value.page == ReportPage.Detail && directEntry)

    fun reviewSubmission() {
        if (!_uiState.value.create.submissionUnconfirmed) return
        _uiState.value = _uiState.value.copy(page = ReportPage.List, query = "", targetType = "")
        refresh()
    }

    fun back() {
        pollJob?.cancel()
        detailJob?.cancel()
        _uiState.value = _uiState.value.copy(page = if (_uiState.value.page == ReportPage.Detail) detailReturnPage else ReportPage.List)
        if (_uiState.value.page == ReportPage.History) refreshHistory()
    }

    private inline fun updateCreate(update: ReportCreateState.() -> ReportCreateState) {
        _uiState.value = _uiState.value.copy(create = _uiState.value.create.update())
    }

    private suspend fun handleFailure(failure: NetworkFailure) {
        if (failure == NetworkFailure.Unauthorized) eventChannel.send(ReportsEvent.SessionExpired)
    }

    class Factory(apiClient: FinanceApiClient) : ViewModelProvider.Factory {
        private val repository = ReportsRepository(apiClient)
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReportsViewModel::class.java)) return ReportsViewModel(repository) as T
            throw IllegalArgumentException("未知的报告 ViewModel")
        }
    }

    private companion object {
        const val DEFAULT_TARGET_TYPE = "STOCK"
        const val FIRST_WINDOW_MS = 20_000L
        const val FIRST_INTERVAL_MS = 10_000L
        const val NORMAL_INTERVAL_MS = 5_000L
    }
}

private fun ReportStatus?.isWorking(): Boolean = this == ReportStatus.Pending || this == ReportStatus.Generating

// 通用网络层未保留服务端错误详情，无法证明失败响应之前没有产生任务。
private fun NetworkFailure.needsSubmissionConfirmation(): Boolean =
    this == NetworkFailure.Unavailable || this == NetworkFailure.InvalidResponse || this == NetworkFailure.Service
