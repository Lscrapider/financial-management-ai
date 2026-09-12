package com.scrapider.finance.androidapp.feature.market

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarketViewModel(
    private val repository: MarketRepository,
    private val detailRepository: MarketDetailRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(MarketDetailState())
    val detailState = _detailState.asStateFlow()

    private val _events = MutableSharedFlow<MarketEvent>()
    val events = _events.asSharedFlow()

    private var sessionToken: String = ""
    private var requestGeneration: Long = 0L
    private var hasLoadedContent = false

    fun loadForSession(accessToken: String) {
        if (accessToken == sessionToken) return
        sessionToken = accessToken
        requestGeneration += 1
        hasLoadedContent = false
        _uiState.value = MarketUiState()
        _detailState.value = MarketDetailState()
    }

    fun refresh() {
        viewModelScope.launch { refreshContent() }
    }

    private suspend fun refreshContent(keepPreviousOnPartialFailure: Boolean = false) {
        if (sessionToken.isBlank() || _uiState.value.isLoading) return
        val currentRequest = ++requestGeneration
        _uiState.value = _uiState.value.copy(isLoading = true)
        try {
            when (val result = withContext(Dispatchers.Default) { repository.load() }) {
                is NetworkResult.Failure -> onContentLoadFailure(currentRequest, result.reason)
                is NetworkResult.Success -> {
                    val failure = result.data.partialFailure
                    if (keepPreviousOnPartialFailure && hasLoadedContent && failure != null) {
                        onContentLoadFailure(currentRequest, failure)
                    } else {
                        onContentLoaded(currentRequest, result.data)
                    }
                }
            }
        } finally {
            if (currentRequest == requestGeneration) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** 调用方的页面与前台生命周期拥有本次请求，离开页面即取消。 */
    suspend fun updateVisibleContent(destination: MarketDestination) {
        if (sessionToken.isBlank() || _uiState.value.isSaving || _uiState.value.destination != destination) return
        when (destination) {
            MarketDestination.List -> refreshContent(keepPreviousOnPartialFailure = true)
            is MarketDestination.TargetDetail -> {
                if (_uiState.value.findTargetSnapshot(destination.targetType, destination.targetCode, destination.watchItemId) != null) {
                    loadDetail(destination)
                }
            }
            else -> Unit
        }
    }

    fun selectChartPeriod(period: MarketChartPeriod) {
        if (_detailState.value.period == period) return
        _detailState.value = _detailState.value.copy(period = period, points = emptyList(), chartError = "", chartLoading = true)
    }

    fun selectChartAdjust(adjust: MarketChartAdjust) {
        if (_detailState.value.adjust == adjust) return
        _detailState.value = _detailState.value.copy(adjust = adjust, points = emptyList(), chartError = "", chartLoading = true)
    }

    private suspend fun loadDetail(destination: MarketDestination.TargetDetail) = coroutineScope {
        val key = "${destination.targetType}:${destination.targetCode}"
        if (_detailState.value.targetKey != key) _detailState.value = MarketDetailState(targetKey = key)
        val token = sessionToken
        val request = _detailState.value
        _detailState.value = request.copy(quoteLoading = request.quote == null, chartLoading = request.points.isEmpty())
        try {
            val quote = async { detailRepository.quote(destination.targetType, destination.targetCode) }
            val chart = async { detailRepository.chart(destination.targetType, destination.targetCode, request.period, request.adjust) }
            val quoteResult = quote.await()
            val chartResult = chart.await()
            val current = _detailState.value
            if (token != sessionToken || current.targetKey != key || current.period != request.period || current.adjust != request.adjust) return@coroutineScope
            _detailState.value = request.copy(
                quote = (quoteResult as? NetworkResult.Success)?.data ?: request.quote,
                points = (chartResult as? NetworkResult.Success)?.data ?: request.points,
                quoteUnavailable = quoteResult is NetworkResult.Success && quoteResult.data == null,
                quoteError = (quoteResult as? NetworkResult.Failure)?.reason?.userMessage.orEmpty(),
                chartError = (chartResult as? NetworkResult.Failure)?.reason?.userMessage.orEmpty(),
            )
            if ((quoteResult as? NetworkResult.Failure)?.reason == NetworkFailure.Unauthorized ||
                (chartResult as? NetworkResult.Failure)?.reason == NetworkFailure.Unauthorized) publishSessionExpired()
        } finally {
            val current = _detailState.value
            if (token == sessionToken && current.targetKey == key && current.period == request.period && current.adjust == request.adjust) {
                _detailState.value = current.copy(quoteLoading = false, chartLoading = false)
            }
        }
    }

    fun selectGroup(groupId: String?) {
        _uiState.value = _uiState.value.copy(selectedGroupId = groupId)
    }

    fun selectTargetTypeFilter(filter: MarketTargetTypeFilter) {
        _uiState.value = _uiState.value.copy(targetTypeFilter = filter)
    }

    fun selectSortOption(option: MarketSortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
    }

    fun openSearch() {
        navigateTo(MarketDestination.Search, clearSearchQuery = true)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun openTargetDetail(
        targetType: String,
        targetCode: String,
        watchItemId: String? = null,
    ) {
        val key = "$targetType:$targetCode"
        if (_detailState.value.targetKey != key) _detailState.value = MarketDetailState(targetKey = key)
        navigateTo(
            MarketDestination.TargetDetail(
                targetType = targetType,
                targetCode = targetCode,
                watchItemId = watchItemId,
            ),
        )
    }

    fun openManageGroups() {
        navigateTo(MarketDestination.ManageGroups)
    }

    fun openAddTargets(groupId: String? = _uiState.value.selectedGroupId) {
        val selectedGroupId = groupId ?: _uiState.value.groups.firstOrNull()?.id
        if (selectedGroupId == null) {
            publishNotice("请先新建自选池")
            return
        }
        navigateTo(MarketDestination.AddTargets(selectedGroupId))
        _uiState.value = _uiState.value.copy(targetOptions = emptyList())
        loadTargetOptions()
    }

    fun selectAddTargetGroup(groupId: String) {
        val destination = _uiState.value.destination as? MarketDestination.AddTargets ?: return
        if (destination.groupId == groupId) return
        _uiState.value = _uiState.value.copy(
            destination = MarketDestination.AddTargets(groupId),
        )
    }

    fun openTargetSettings(itemId: String) {
        navigateTo(MarketDestination.TargetSettings(itemId))
    }

    fun navigateBack() {
        val currentState = _uiState.value
        val previousDestination = currentState.backStack.lastOrNull()
        if (previousDestination == null) {
            if (currentState.destination == MarketDestination.List) return
            _uiState.value = currentState.copy(
                destination = MarketDestination.List,
                targetOptions = emptyList(),
                isLoadingTargetOptions = false,
            )
            return
        }
        _uiState.value = currentState.copy(
            destination = previousDestination,
            backStack = currentState.backStack.dropLast(1),
            targetOptions = if (previousDestination is MarketDestination.AddTargets) {
                currentState.targetOptions
            } else {
                emptyList()
            },
            isLoadingTargetOptions = if (previousDestination is MarketDestination.AddTargets) {
                currentState.isLoadingTargetOptions
            } else {
                false
            },
        )
    }

    fun saveGroup(
        groupId: String?,
        name: String,
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            publishNotice("请输入自选池名称")
            return
        }
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, syncMessage = "")
            when (val result = repository.saveGroup(groupId, normalizedName)) {
                is NetworkResult.Failure -> onOperationFailure(result.reason)
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        selectedGroupId = result.data.id,
                    )
                    _events.emit(MarketEvent.GroupSaved)
                    _events.emit(MarketEvent.Notice(if (groupId == null) "已创建自选池" else "已更新自选池"))
                    refresh()
                }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, syncMessage = "")
            when (val result = repository.deleteGroup(groupId)) {
                is NetworkResult.Failure -> onOperationFailure(result.reason)
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        selectedGroupId = _uiState.value.selectedGroupId.takeUnless { it == groupId },
                    )
                    _events.emit(MarketEvent.Notice("已删除自选池"))
                    refresh()
                }
            }
        }
    }

    fun addTarget(target: MarketTargetOption) {
        val destination = _uiState.value.destination as? MarketDestination.AddTargets ?: return
        val group = _uiState.value.groups.firstOrNull { it.id == destination.groupId } ?: return
        val targetKey = target.targetType + ":" + target.targetCode
        if (group.items.any { it.targetKey == targetKey }) {
            publishNotice("该标的已在当前自选池")
            return
        }
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, syncMessage = "")
            when (val result = repository.addTarget(destination.groupId, target)) {
                is NetworkResult.Failure -> onOperationFailure(result.reason)
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.emit(MarketEvent.Notice("已添加 ${target.targetName}"))
                    refresh()
                }
            }
        }
    }

    fun deleteTarget(itemId: String) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, syncMessage = "")
            when (val result = repository.deleteTarget(itemId)) {
                is NetworkResult.Failure -> onOperationFailure(result.reason)
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.emit(MarketEvent.Notice("已从自选池移除标的"))
                    refresh()
                }
            }
        }
    }

    fun saveTargetSettings(input: MarketTargetSettingsInput) {
        if (_uiState.value.isSaving) return
        val requiresAlertValue = input.item.targetType.supportsAlert() &&
            (input.alert != null || input.alertEnabled)
        if (requiresAlertValue && !isValidThreshold(input.thresholdPercent)) {
            publishNotice(
                "提醒阈值需在 $MIN_ALERT_THRESHOLD_PERCENT% 至 $MAX_ALERT_THRESHOLD_PERCENT% 之间",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, syncMessage = "")
            when (val result = repository.saveTargetSettings(input)) {
                is NetworkResult.Failure -> onOperationFailure(result.reason)
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.emit(MarketEvent.Notice("设置已保存"))
                    refresh()
                }
            }
        }
    }

    private fun loadTargetOptions() {
        if (_uiState.value.isLoadingTargetOptions) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTargetOptions = true)
            when (val result = repository.loadTargetOptions()) {
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoadingTargetOptions = false)
                    onOperationFailure(result.reason)
                }

                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingTargetOptions = false,
                        targetOptions = result.data,
                    )
                }
            }
        }
    }

    private fun navigateTo(
        destination: MarketDestination,
        clearSearchQuery: Boolean = false,
    ) {
        val currentState = _uiState.value
        if (currentState.destination == destination) return
        _uiState.value = currentState.copy(
            destination = destination,
            backStack = currentState.backStack + currentState.destination,
            searchQuery = if (clearSearchQuery) "" else currentState.searchQuery,
        )
    }

    private fun onContentLoadFailure(
        currentRequest: Long,
        reason: NetworkFailure,
    ) {
        if (currentRequest != requestGeneration) return
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            syncMessage = reason.userMessage,
        )
        if (reason == NetworkFailure.Unauthorized) {
            publishSessionExpired()
        }
    }

    private fun onContentLoaded(
        currentRequest: Long,
        content: MarketContent,
    ) {
        if (currentRequest != requestGeneration) return
        val currentState = _uiState.value
        val selectedGroupId = if (hasLoadedContent && currentState.selectedGroupId == null) {
            null
        } else {
            currentState.selectedGroupId
                ?.takeIf { currentId -> content.groups.any { it.id == currentId } }
                ?: content.groups.firstOrNull()?.id
        }
        hasLoadedContent = true
        _uiState.value = currentState.copy(
            isLoading = false,
            groups = content.groups,
            systemTargets = content.systemTargets,
            marketOverview = content.marketOverview,
            alerts = content.alerts,
            selectedGroupId = selectedGroupId,
            syncMessage = content.partialFailure?.userMessage.orEmpty(),
        )
        if (content.partialFailure == NetworkFailure.Unauthorized) {
            publishSessionExpired()
        }
    }

    private suspend fun onOperationFailure(reason: NetworkFailure) {
        _uiState.value = _uiState.value.copy(isSaving = false, syncMessage = reason.userMessage)
        if (reason == NetworkFailure.Unauthorized) {
            _events.emit(MarketEvent.SessionExpired)
        } else {
            _events.emit(MarketEvent.Notice(reason.userMessage))
        }
    }

    private fun publishNotice(message: String) {
        viewModelScope.launch {
            _events.emit(MarketEvent.Notice(message))
        }
    }

    private fun publishSessionExpired() {
        viewModelScope.launch {
            _events.emit(MarketEvent.SessionExpired)
        }
    }

    private fun isValidThreshold(value: Double?): Boolean =
        value != null && value >= MIN_ALERT_THRESHOLD_PERCENT && value <= MAX_ALERT_THRESHOLD_PERCENT

    class Factory(
        apiClient: FinanceApiClient,
    ) : ViewModelProvider.Factory {
        private val repository = MarketRepository(apiClient)
        private val detailRepository = MarketDetailRepository(apiClient)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MarketViewModel::class.java)) {
                return MarketViewModel(repository, detailRepository) as T
            }
            throw IllegalArgumentException("未知的行情 ViewModel：" + modelClass.name)
        }
    }
}
