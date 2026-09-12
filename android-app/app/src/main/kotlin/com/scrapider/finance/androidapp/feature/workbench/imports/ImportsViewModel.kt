package com.scrapider.finance.androidapp.feature.workbench.imports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ImportsViewModel(
    private val repository: ImportsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportsUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<ImportsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var accessToken = ""
    private var adminSession = false
    private var active = false
    private var sessionEpoch = 0L
    private var fileListJob: Job? = null
    private var manualListJob: Job? = null
    private var pollingJob: Job? = null
    private var detailJob: Job? = null
    private var reviewJob: Job? = null
    private var mutationJob: Job? = null
    private var previewJob: Job? = null
    private var filePrepareJob: Job? = null
    private var preparedFile: ImportsRepository.PreparedImportFile? = null

    fun belongsToSession(accessToken: String): Boolean = this.accessToken == accessToken

    fun loadForSession(accessToken: String, isAdmin: Boolean) {
        if (this.accessToken == accessToken && adminSession == isAdmin && accessToken.isNotBlank()) {
            refreshAll()
            if (active) startPolling()
            return
        }
        viewModelScope.coroutineContext.cancelChildren()
        repository.clearDraftTemplate()
        while (eventChannel.tryReceive().isSuccess) {
            // 会话切换时丢弃上一账号尚未消费的导航或登录失效事件。
        }
        preparedFile?.close()
        preparedFile = null
        this.accessToken = accessToken
        adminSession = isAdmin
        sessionEpoch += 1
        _uiState.value = ImportsUiState()
        if (!isAdmin) {
            _uiState.value = _uiState.value.copy(
                error = NetworkFailure.Forbidden,
                notice = NetworkFailure.Forbidden.userMessage,
            )
            return
        }
        refreshAll()
        if (active) startPolling()
    }

    fun setActive(active: Boolean) {
        this.active = active
        if (active) startPolling() else stopPolling()
    }

    fun refresh(category: ImportCategory? = null) {
        if (category == null) {
            refreshAll()
            return
        }
        if (!requireAdmin()) return
        val epoch = sessionEpoch
        val job = viewModelScope.launch {
            reload(category, epoch, showLoading = true)
        }
        setListJob(category, job)
    }

    fun refreshAll() {
        if (!requireAdmin()) return
        refresh(ImportCategory.File)
        refresh(ImportCategory.Text)
    }

    fun selectCategory(category: ImportCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        if (!listState(category).hasLoaded && adminSession) refresh(category)
    }

    fun loadMore(category: ImportCategory) {
        if (!requireAdmin()) return
        val current = listState(category)
        if (!current.canLoadMore || current.isLoading || current.isLoadingMore) return
        val epoch = sessionEpoch
        updateList(category) { it.copy(isLoadingMore = true, moreError = null) }
        val job = viewModelScope.launch {
            val result = when (category) {
                ImportCategory.File -> repository.observeFileTasks(current.pageNum + 1, current.pageSize)
                ImportCategory.Text -> repository.observeManualTasks(current.pageNum + 1, current.pageSize)
            }
            if (epoch != sessionEpoch) return@launch
            when (result) {
                is NetworkResult.Failure -> {
                    updateList(category) { it.copy(isLoadingMore = false, moreError = result.reason) }
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    val old = listState(category)
                    updateList(category) {
                        it.copy(
                            records = (old.records + result.data.records).distinctBy(ImportTask::taskNo),
                            total = result.data.total,
                            pageNum = result.data.pageNum,
                            pageSize = result.data.pageSize,
                            pages = result.data.pages,
                            isLoadingMore = false,
                            moreError = null,
                            hasLoaded = true,
                        )
                    }
                    syncSelectedTask(result.data.records)
                }
            }
        }
        setListJob(category, job)
    }

    fun startPolling(intervalMillis: Long = DEFAULT_IMPORT_POLL_INTERVAL_MS) {
        if (!adminSession || !active || pollingJob?.isActive == true) return
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(isPolling = true)
        pollingJob = viewModelScope.launch {
            try {
                while (isActive && epoch == sessionEpoch) {
                    val selected = _uiState.value.selectedTask
                    if (selected != null && selected.status in setOf(ImportTaskStatus.Pending, ImportTaskStatus.Processing)) {
                        refreshSelectedTask(selected, epoch)
                    }
                    delay(intervalMillis.coerceAtLeast(MIN_IMPORT_POLL_INTERVAL_MS))
                }
            } finally {
                if (epoch == sessionEpoch) _uiState.value = _uiState.value.copy(isPolling = false)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.value = _uiState.value.copy(isPolling = false)
    }

    fun selectTask(task: ImportTask) {
        detailJob?.cancel()
        reviewJob?.cancel()
        previewJob?.cancel()
        repository.clearDraftTemplate()
        _uiState.value = _uiState.value.copy(
            selectedCategory = task.category,
            selectedTask = task,
            stages = emptyList(),
            review = null,
            draft = null,
            dirty = false,
            submissionUnconfirmed = false,
            error = null,
            notice = null,
            preview = null,
            selectedPreviewPageNo = null,
            previewError = null,
            isLoadingDetail = false,
            isLoadingReview = false,
        )
        loadStages(task.taskNo)
    }

    fun clearSelection() {
        detailJob?.cancel()
        reviewJob?.cancel()
        previewJob?.cancel()
        repository.clearDraftTemplate()
        _uiState.value = _uiState.value.copy(
            selectedTask = null,
            stages = emptyList(),
            review = null,
            draft = null,
            dirty = false,
            preview = null,
            selectedPreviewPageNo = null,
            previewError = null,
            isLoadingDetail = false,
            isLoadingReview = false,
        )
    }

    fun loadStages(taskNo: String) {
        if (!requireAdmin() || taskNo.isBlank()) return
        detailJob?.cancel()
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(isLoadingDetail = true, error = null)
        detailJob = viewModelScope.launch {
            when (val result = repository.getStages(taskNo)) {
                is NetworkResult.Failure -> {
                    if (epoch != sessionEpoch) return@launch
                    _uiState.value = _uiState.value.copy(isLoadingDetail = false, error = result.reason)
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    if (epoch != sessionEpoch || _uiState.value.selectedTask?.taskNo != taskNo) return@launch
                    _uiState.value = _uiState.value.copy(isLoadingDetail = false, stages = result.data, error = null)
                }
            }
        }
    }

    /** 仅在用户点击 OCR 复核入口时调用。 */
    fun loadOcrReview(taskNo: String) {
        loadReview(taskNo, ImportCategory.File)
    }

    fun loadManualReview(taskNo: String) {
        loadReview(taskNo, ImportCategory.Text)
    }

    fun updateParagraphText(paragraphNo: Int, text: String) {
        if (!_uiState.value.canEditDraft) return
        val draft = _uiState.value.draft ?: return
        if (draft.paragraphs.none { it.paragraphNo == paragraphNo }) return
        _uiState.value = _uiState.value.copy(
            draft = draft.copy(paragraphs = draft.paragraphs.map {
                if (it.paragraphNo == paragraphNo) it.copy(text = text) else it
            }),
            dirty = true,
            notice = null,
        )
    }

    fun addParagraph() {
        if (!_uiState.value.canEditDraft || _uiState.value.selectedCategory != ImportCategory.Text) return
        val draft = _uiState.value.draft ?: return
        val nextNo = (draft.paragraphs.maxOfOrNull { it.paragraphNo } ?: 0) + 1
        _uiState.value = _uiState.value.copy(
            draft = draft.copy(paragraphs = draft.paragraphs + ImportParagraphDraft(nextNo, "")),
            dirty = true,
            notice = null,
        )
    }

    fun removeParagraph(paragraphNo: Int) {
        if (!_uiState.value.canEditDraft || _uiState.value.selectedCategory != ImportCategory.Text) return
        val draft = _uiState.value.draft ?: return
        if (draft.paragraphs.size <= 1) return
        _uiState.value = _uiState.value.copy(
            draft = draft.copy(paragraphs = draft.paragraphs.filterNot { it.paragraphNo == paragraphNo }),
            dirty = true,
            notice = null,
        )
    }

    fun updateManualTitle(title: String) {
        if (!_uiState.value.canEditDraft || _uiState.value.selectedCategory != ImportCategory.Text) return
        val draft = _uiState.value.draft ?: return
        _uiState.value = _uiState.value.copy(draft = draft.copy(title = title), dirty = true, notice = null)
    }

    fun beginManualDraft() {
        if (!requireAdmin() || _uiState.value.isUploading || _uiState.value.isPreparingFile ||
            _uiState.value.isSaving || _uiState.value.isSubmitting || _uiState.value.isCreatingManual ||
            _uiState.value.submissionUnconfirmed
        ) return
        _uiState.value = _uiState.value.copy(
            selectedCategory = ImportCategory.Text,
            selectedTask = null,
            stages = emptyList(),
            review = null,
            draft = ImportDraft("", listOf(ImportParagraphDraft(1, ""))),
            dirty = false,
            error = null,
            notice = null,
        )
    }

    fun saveDraft() {
        val current = _uiState.value
        val draft = current.draft ?: return invalidOperation("当前没有可保存的草稿。")
        if (!requireAdmin() || !current.canEditDraft || mutationJob?.isActive == true) return
        val selected = current.selectedTask
        if (selected == null) {
            if (current.selectedCategory != ImportCategory.Text) return invalidOperation("没有选中的导入任务。")
            startManualCreate(draft, submitAfter = false)
            return
        }
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(isSaving = true, error = null, notice = null)
        mutationJob = viewModelScope.launch {
            val result = when (selected.category) {
                ImportCategory.File -> repository.saveOcrDraft(selected.taskNo, draft)
                ImportCategory.Text -> repository.saveManualDraft(selected.taskNo, draft)
            }
            if (epoch != sessionEpoch) return@launch
            when (result) {
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = result.reason)
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        dirty = false,
                        error = null,
                        notice = "草稿已保存。",
                        review = _uiState.value.review?.copy(status = ImportReviewStatus.Saved),
                    )
                }
            }
        }
    }

    fun submit() {
        val current = _uiState.value
        val draft = current.draft ?: return invalidOperation("当前没有可提交的草稿。")
        if (!requireAdmin() || !current.canEditDraft || mutationJob?.isActive == true) return
        val selected = current.selectedTask
        if (selected == null) {
            if (current.selectedCategory != ImportCategory.Text) return invalidOperation("没有选中的导入任务。")
            startManualCreate(draft, submitAfter = true)
            return
        }
        val epoch = sessionEpoch
        _uiState.value = current.copy(isSubmitting = true, error = null, notice = null)
        mutationJob = viewModelScope.launch {
            val result = when (selected.category) {
                ImportCategory.File -> repository.submitOcr(selected.taskNo, draft)
                ImportCategory.Text -> repository.submitManual(selected.taskNo, draft)
            }
            if (epoch != sessionEpoch) return@launch
            applySubmissionResult(selected, result, epoch)
        }
    }

    fun prepareFile(uri: Uri) {
        if (!requireAdmin() || _uiState.value.submissionUnconfirmed || _uiState.value.isUploading ||
            _uiState.value.isPreparingFile || _uiState.value.isSaving || _uiState.value.isSubmitting ||
            _uiState.value.isCreatingManual
        ) return
        filePrepareJob?.cancel()
        preparedFile?.close()
        preparedFile = null
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(
            isPreparingFile = true,
            preparedFileName = null,
            preparedFileDescription = "",
            fileError = null,
        )
        filePrepareJob = viewModelScope.launch {
            try {
                val prepared = repository.prepareFile(uri)
                if (epoch != sessionEpoch) {
                    prepared.close()
                    return@launch
                }
                preparedFile?.close()
                preparedFile = prepared
                _uiState.value = _uiState.value.copy(
                    isPreparingFile = false,
                    preparedFileName = prepared.filename,
                    preparedFileDescription = prepared.description,
                    fileError = null,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ImportFileException) {
                if (epoch != sessionEpoch) return@launch
                _uiState.value = _uiState.value.copy(
                    isPreparingFile = false,
                    preparedFileName = null,
                    preparedFileDescription = "",
                    fileError = exception.message ?: "无法读取所选文件。",
                )
            } catch (_: Exception) {
                if (epoch != sessionEpoch) return@launch
                _uiState.value = _uiState.value.copy(
                    isPreparingFile = false,
                    preparedFileName = null,
                    preparedFileDescription = "",
                    fileError = "无法读取所选文件。",
                )
            }
        }
    }

    fun discardPreparedFile() {
        if (_uiState.value.isUploading) return
        filePrepareJob?.cancel()
        filePrepareJob = null
        preparedFile?.close()
        preparedFile = null
        _uiState.value = _uiState.value.copy(
            isPreparingFile = false,
            preparedFileName = null,
            preparedFileDescription = "",
            fileError = null,
        )
    }

    fun uploadPreparedFile() {
        val prepared = preparedFile ?: run {
            _uiState.value = _uiState.value.copy(fileError = "请先选择文件。")
            return
        }
        if (!requireAdmin() || _uiState.value.submissionUnconfirmed || _uiState.value.isUploading ||
            _uiState.value.isPreparingFile || _uiState.value.isCreatingManual || mutationJob?.isActive == true
        ) return
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(isUploading = true, fileError = null, error = null, notice = null)
        mutationJob = viewModelScope.launch {
            try {
                val result = repository.uploadPreparedFile(prepared)
                preparedFile = null
                when (result) {
                    is NetworkResult.Failure -> {
                        if (epoch != sessionEpoch) return@launch
                        val unknown = result.reason.needsSubmissionConfirmation()
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            submissionUnconfirmed = unknown,
                            preparedFileName = null,
                            preparedFileDescription = "",
                            fileError = result.reason.userMessage,
                            error = result.reason,
                            notice = if (unknown) "上传结果尚未确认，请先查看处理记录，避免重复导入。" else result.reason.userMessage,
                        )
                        handleFailure(result.reason)
                    }
                    is NetworkResult.Success -> {
                        if (epoch != sessionEpoch) return@launch
                        preparedFile = null
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            preparedFileName = null,
                            preparedFileDescription = "",
                            submissionUnconfirmed = false,
                            fileError = null,
                            error = null,
                            notice = "资料已提交，后台正在识别和整理。",
                        )
                        eventChannel.trySend(ImportsEvent.Imported(result.data.map { it.taskNo }))
                        refresh(ImportCategory.File)
                    }
                }
            } catch (exception: CancellationException) {
                preparedFile = null
                throw exception
            } catch (_: Exception) {
                if (epoch != sessionEpoch) return@launch
                preparedFile = null
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    submissionUnconfirmed = true,
                    preparedFileName = null,
                    preparedFileDescription = "",
                    fileError = NetworkFailure.Unavailable.userMessage,
                    error = NetworkFailure.Unavailable,
                    notice = "上传结果尚未确认，请先查看处理记录，避免重复导入。",
                )
            }
        }
    }

    private fun startManualCreate(draft: ImportDraft, submitAfter: Boolean) {
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(
            selectedCategory = ImportCategory.Text,
            isCreatingManual = true,
            error = null,
            notice = null,
        )
        mutationJob = viewModelScope.launch {
            when (val result = repository.createManualDraft(draft.title, draft.paragraphs.map { it.text })) {
                is NetworkResult.Failure -> {
                    if (epoch != sessionEpoch) return@launch
                    val unconfirmed = result.reason.needsSubmissionConfirmation()
                    _uiState.value = _uiState.value.copy(
                        isCreatingManual = false,
                        submissionUnconfirmed = unconfirmed,
                        error = result.reason,
                        notice = if (unconfirmed) "草稿创建结果待确认，请先查看文本处理记录，输入内容会保留。" else null,
                    )
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    if (epoch != sessionEpoch) return@launch
                    val task = result.data
                    _uiState.value = _uiState.value.copy(
                        isCreatingManual = false,
                        selectedCategory = ImportCategory.Text,
                        selectedTask = task,
                        stages = emptyList(),
                        review = null,
                        draft = draft,
                        dirty = false,
                        error = null,
                        notice = "文本草稿已创建。",
                    )
                    eventChannel.trySend(ImportsEvent.Created(task.taskNo))
                    if (!submitAfter) {
                        refresh(ImportCategory.Text)
                    } else {
                        _uiState.value = _uiState.value.copy(isSubmitting = true, notice = null)
                        when (val submitResult = repository.submitManual(task.taskNo, draft)) {
                            is NetworkResult.Failure -> applySubmissionResult(task, submitResult, epoch)
                            is NetworkResult.Success -> applySubmissionResult(task, submitResult, epoch)
                        }
                    }
                }
            }
        }
    }

    private fun applySubmissionResult(
        selected: ImportTask,
        result: NetworkResult<Unit>,
        epoch: Long,
    ) {
        if (epoch != sessionEpoch) return
        when (result) {
            is NetworkResult.Failure -> {
                val unknown = result.reason.needsSubmissionConfirmation()
                _uiState.value = _uiState.value.copy(
                    isCreatingManual = false,
                    isSubmitting = false,
                    submissionUnconfirmed = unknown,
                    error = result.reason,
                    notice = if (unknown) "提交结果尚未确认，请先查看任务记录，避免重复提交。" else result.reason.userMessage,
                )
                handleFailure(result.reason)
            }
            is NetworkResult.Success -> {
                repository.clearDraftTemplate(selected.taskNo)
                _uiState.value = _uiState.value.copy(
                    isCreatingManual = false,
                    isSubmitting = false,
                    submissionUnconfirmed = false,
                    dirty = false,
                    error = null,
                    notice = "已提交，后台正在继续处理。",
                    selectedTask = selected.copy(status = ImportTaskStatus.Processing),
                    review = _uiState.value.review?.copy(status = ImportReviewStatus.Approved),
                )
                eventChannel.trySend(ImportsEvent.Submitted(selected.taskNo))
                refresh(selected.category)
            }
        }
    }

    fun previewPage(pageNo: Int, targetWidthPx: Int) {
        val task = _uiState.value.selectedTask
        if (!requireAdmin() || task == null || task.category != ImportCategory.File) return
        previewJob?.cancel()
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(
            selectedPreviewPageNo = pageNo,
            isLoadingPreview = true,
            preview = null,
            previewError = null,
        )
        previewJob = viewModelScope.launch {
            when (val result = repository.previewPage(task.taskNo, pageNo, targetWidthPx)) {
                is NetworkResult.Failure -> {
                    if (epoch != sessionEpoch || _uiState.value.selectedTask?.taskNo != task.taskNo) return@launch
                    _uiState.value = _uiState.value.copy(isLoadingPreview = false, previewError = result.reason)
                    if (result.reason == NetworkFailure.Unauthorized) eventChannel.trySend(ImportsEvent.SessionExpired)
                }
                is NetworkResult.Success -> {
                    if (epoch != sessionEpoch || _uiState.value.selectedTask?.taskNo != task.taskNo) return@launch
                    _uiState.value = _uiState.value.copy(isLoadingPreview = false, preview = result.data, previewError = null)
                }
            }
        }
    }

    fun closePagePreview() {
        previewJob?.cancel()
        _uiState.value = _uiState.value.copy(
            preview = null,
            selectedPreviewPageNo = null,
            isLoadingPreview = false,
            previewError = null,
        )
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun consumeSubmissionUnconfirmed() {
        // 只有对应分类的更新成功后才解除未知结果锁，避免再次点击立即重复提交。
        refresh(_uiState.value.selectedCategory)
    }

    override fun onCleared() {
        preparedFile?.close()
        eventChannel.close()
        super.onCleared()
    }

    private suspend fun reload(category: ImportCategory, epoch: Long, showLoading: Boolean) {
        if (epoch != sessionEpoch) return
        if (showLoading) updateList(category) { it.copy(isLoading = true, isLoadingMore = false, error = null, moreError = null) }
        val current = listState(category)
        val result = when (category) {
            ImportCategory.File -> repository.observeFileTasks(1, current.pageSize)
            ImportCategory.Text -> repository.observeManualTasks(1, current.pageSize)
        }
        if (epoch != sessionEpoch) return
        when (result) {
            is NetworkResult.Failure -> {
                updateList(category) { it.copy(isLoading = false, hasLoaded = true, error = result.reason) }
                handleFailure(result.reason)
            }
            is NetworkResult.Success -> {
                updateList(category) {
                    it.copy(
                        records = result.data.records,
                        total = result.data.total,
                        pageNum = result.data.pageNum,
                        pageSize = result.data.pageSize,
                        pages = result.data.pages,
                        isLoading = false,
                        isLoadingMore = false,
                        hasLoaded = true,
                        error = null,
                        moreError = null,
                    )
                }
                syncSelectedTask(result.data.records)
                val selected = _uiState.value.selectedTask
                val matchesConfirmedCategory = (selected?.category ?: if (_uiState.value.draft != null) ImportCategory.Text else ImportCategory.File) == category
                if (showLoading && _uiState.value.submissionUnconfirmed && matchesConfirmedCategory) {
                    _uiState.value = _uiState.value.copy(
                        submissionUnconfirmed = false,
                        notice = "资料记录已更新，请确认后继续。",
                    )
                }
            }
        }
    }

    private suspend fun refreshStagesSilently(taskNo: String, epoch: Long) {
        when (val result = repository.getStages(taskNo)) {
            is NetworkResult.Failure -> if (epoch == sessionEpoch && result.reason == NetworkFailure.Unauthorized) {
                eventChannel.trySend(ImportsEvent.SessionExpired)
            }
            is NetworkResult.Success -> if (epoch == sessionEpoch && _uiState.value.selectedTask?.taskNo == taskNo) {
                _uiState.value = _uiState.value.copy(stages = result.data)
            }
        }
    }

    private suspend fun refreshSelectedTask(task: ImportTask, epoch: Long) {
        if (epoch != sessionEpoch) return
        val current = listState(task.category)
        val index = current.records.indexOfFirst { it.taskNo == task.taskNo }
        val page = if (index >= 0) (index / current.pageSize.coerceAtLeast(1)) + 1 else current.pageNum
        val result = when (task.category) {
            ImportCategory.File -> repository.observeFileTasks(page, current.pageSize)
            ImportCategory.Text -> repository.observeManualTasks(page, current.pageSize)
        }
        if (epoch != sessionEpoch) return
        when (result) {
            is NetworkResult.Failure -> if (result.reason == NetworkFailure.Unauthorized) {
                eventChannel.trySend(ImportsEvent.SessionExpired)
            }
            is NetworkResult.Success -> {
                val refreshed = result.data.records
                updateList(task.category) { old ->
                    val byTaskNo = refreshed.associateBy(ImportTask::taskNo)
                    old.copy(
                        records = old.records.map { byTaskNo[it.taskNo] ?: it },
                        total = maxOf(old.total, result.data.total),
                        pages = maxOf(old.pages, result.data.pages),
                    )
                }
                syncSelectedTask(refreshed)
                val selected = _uiState.value.selectedTask
                if (selected?.taskNo == task.taskNo && selected.status in setOf(ImportTaskStatus.Pending, ImportTaskStatus.Processing)) {
                    refreshStagesSilently(task.taskNo, epoch)
                }
            }
        }
    }

    private fun loadReview(taskNo: String, category: ImportCategory) {
        if (!requireAdmin() || taskNo.isBlank()) return
        val selected = _uiState.value.selectedTask ?: return
        if (selected.taskNo != taskNo || selected.category != category || !_uiState.value.canEditSelectedTask) return
        reviewJob?.cancel()
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(isLoadingReview = true, error = null, review = null, draft = null)
        reviewJob = viewModelScope.launch {
            val result = when (category) {
                ImportCategory.File -> repository.getOcrReview(taskNo)
                ImportCategory.Text -> repository.getManualReview(taskNo)
            }
            if (epoch != sessionEpoch || _uiState.value.selectedTask?.taskNo != taskNo) return@launch
            when (result) {
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoadingReview = false, error = result.reason)
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    val taskTitle = _uiState.value.selectedTask?.originalFilename.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        isLoadingReview = false,
                        review = result.data,
                        draft = ImportDraft(
                            title = taskTitle,
                            paragraphs = result.data.paragraphs.map { ImportParagraphDraft(it.paragraphNo, it.text) },
                        ),
                        dirty = false,
                        error = null,
                    )
                }
            }
        }
    }

    private fun requireAdmin(): Boolean {
        if (adminSession) return true
        _uiState.value = _uiState.value.copy(error = NetworkFailure.Forbidden, notice = NetworkFailure.Forbidden.userMessage)
        return false
    }

    private fun invalidOperation(message: String) {
        _uiState.value = _uiState.value.copy(error = NetworkFailure.InvalidResponse, notice = message)
    }

    private fun handleFailure(reason: NetworkFailure) {
        if (reason == NetworkFailure.Unauthorized) eventChannel.trySend(ImportsEvent.SessionExpired)
    }

    private fun syncSelectedTask(records: List<ImportTask>) {
        val selected = _uiState.value.selectedTask ?: return
        val current = records.firstOrNull { it.taskNo == selected.taskNo } ?: return
        val clearUnconfirmed = _uiState.value.submissionUnconfirmed && current.status != ImportTaskStatus.ReviewRequired
        _uiState.value = _uiState.value.copy(
            selectedTask = current,
            submissionUnconfirmed = if (clearUnconfirmed) false else _uiState.value.submissionUnconfirmed,
        )
    }

    private fun listState(category: ImportCategory): ImportTaskListState = when (category) {
        ImportCategory.File -> _uiState.value.fileTasks
        ImportCategory.Text -> _uiState.value.manualTasks
    }

    private fun updateList(category: ImportCategory, update: (ImportTaskListState) -> ImportTaskListState) {
        val state = _uiState.value
        _uiState.value = when (category) {
            ImportCategory.File -> state.copy(fileTasks = update(state.fileTasks))
            ImportCategory.Text -> state.copy(manualTasks = update(state.manualTasks))
        }
    }

    private fun setListJob(category: ImportCategory, job: Job) {
        when (category) {
            ImportCategory.File -> {
                fileListJob?.cancel()
                fileListJob = job
            }
            ImportCategory.Text -> {
                manualListJob?.cancel()
                manualListJob = job
            }
        }
    }

    private companion object {
        const val MIN_IMPORT_POLL_INTERVAL_MS = 1_000L
    }
}

private fun NetworkFailure.needsSubmissionConfirmation(): Boolean = when (this) {
    NetworkFailure.Unavailable,
    NetworkFailure.InvalidResponse,
    NetworkFailure.Service -> true
    NetworkFailure.Unauthorized,
    NetworkFailure.Forbidden -> false
}

internal class ImportsViewModelFactory(
    private val repository: ImportsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ImportsViewModel::class.java))
        return ImportsViewModel(repository) as T
    }
}
