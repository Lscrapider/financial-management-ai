package com.scrapider.finance.androidapp.feature.workbench.imports

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportInfoState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingScreen
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportsRoute(
    session: UserSession,
    apiClient: FinanceApiClient,
    onClose: () -> Unit,
    onSessionExpired: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val contentModifier = modifier.fillMaxSize().imePadding()
    if (!session.isAdmin) {
        BackHandler(onBack = onClose)
        Column(contentModifier) {
            ReportTopBar(title = "资料导入", onBack = onClose)
            ReportInfoState("当前账号没有访问该功能的权限。", Modifier.padding(spacing.xl))
        }
        return
    }
    val context = LocalContext.current.applicationContext
    val factory = remember(apiClient, context) {
        ImportsViewModelFactory(ImportsRepository(apiClient, ImportFileStore(context.contentResolver, File(context.cacheDir, "research-imports"))))
    }
    val viewModel: ImportsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSessionExpired by rememberUpdatedState(onSessionExpired)
    val currentOnNotice by rememberUpdatedState(onNotice)
    var initialized by remember(session.accessToken, session.isAdmin) { mutableStateOf(false) }
    var page by rememberSaveable(session.accessToken) { mutableStateOf(ImportScreenPage.List) }
    var editorManual by rememberSaveable(session.accessToken) { mutableStateOf(false) }
    var showMethodSheet by rememberSaveable { mutableStateOf(false) }
    var discardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTaskNo by rememberSaveable { mutableStateOf<String?>(null) }
    var removeParagraph by rememberSaveable { mutableStateOf<Int?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val dismissKeyboard = { keyboard?.hide(); focus.clearFocus() }
    val mutating = state.isUploading || state.isSubmitting || state.isSaving || state.isCreatingManual
    val navigateBack = {
        dismissKeyboard()
        when {
            page == ImportScreenPage.Upload && !mutating -> {
                viewModel.discardPreparedFile()
                page = ImportScreenPage.List
            }
            page == ImportScreenPage.Editor && state.dirty && !mutating -> {
                pendingTaskNo = null
                discardDialog = true
            }
            page == ImportScreenPage.Editor && state.selectedTask != null && !mutating -> page = ImportScreenPage.Detail
            page != ImportScreenPage.List -> page = ImportScreenPage.List
            else -> onClose()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            page = ImportScreenPage.Upload
            viewModel.prepareFile(uri)
        }
    }
    val chooseFile: () -> Unit = {
        dismissKeyboard()
        try {
            picker.launch(IMPORT_MIME_TYPES)
        } catch (_: android.content.ActivityNotFoundException) {
            onNotice("当前设备没有可用的文件选择器。")
        }
    }
    val showRecords: () -> Unit = {
        dismissKeyboard()
        page = ImportScreenPage.List
        // 结果未确认时保留编辑内容；记录更新失败也不能让长文本丢失。
        if (state.draft == null) viewModel.clearSelection()
        viewModel.refresh(state.selectedCategory)
    }
    val reloadReview: () -> Unit = {
        state.selectedTask?.let { task ->
            if (task.category == ImportCategory.File) viewModel.loadOcrReview(task.taskNo)
            else viewModel.loadManualReview(task.taskNo)
        }
    }
    val retryDetail: () -> Unit = {
        state.selectedTask?.let { task ->
            viewModel.refresh(task.category)
            viewModel.loadStages(task.taskNo)
        }
    }
    val previewWidth = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    val previewPage: (Int) -> Unit = { number ->
        dismissKeyboard()
        viewModel.previewPage(number, previewWidth)
    }

    LaunchedEffect(session.accessToken, session.isAdmin) {
        viewModel.loadForSession(session.accessToken, session.isAdmin)
        initialized = true
        // 进程重建后不恢复一个已没有内容的详情或编辑位置；配置变化由 ViewModel 保留内容。
        val restored = viewModel.uiState.value
        if ((page == ImportScreenPage.Detail && restored.selectedTask == null) ||
            (page == ImportScreenPage.Editor && restored.draft == null && restored.selectedTask == null)) {
            page = ImportScreenPage.List
        }
    }
    LifecycleStartEffect(viewModel, page) {
        viewModel.setActive(page == ImportScreenPage.List || page == ImportScreenPage.Detail)
        onStopOrDispose { viewModel.setActive(false) }
    }
    LaunchedEffect(viewModel, session.accessToken) {
        viewModel.events.collect { event ->
            when (event) {
                ImportsEvent.SessionExpired -> currentOnSessionExpired()
                is ImportsEvent.Imported -> if (page == ImportScreenPage.Upload) {
                    viewModel.selectCategory(ImportCategory.File)
                    page = ImportScreenPage.List
                }
                is ImportsEvent.Created -> Unit
                is ImportsEvent.Submitted -> if (page == ImportScreenPage.Editor) {
                    page = ImportScreenPage.Detail
                    viewModel.loadStages(event.taskNo)
                }
            }
        }
    }
    LaunchedEffect(session.accessToken, state.notice) {
        if (viewModel.belongsToSession(session.accessToken) && viewModel.uiState.value.notice == state.notice) {
            state.notice?.let { currentOnNotice(it); viewModel.clearNotice() }
        }
    }
    BackHandler(onBack = navigateBack)

    if (!initialized || !viewModel.belongsToSession(session.accessToken)) {
        ReportLoadingScreen(
            title = "资料导入",
            text = "正在加载资料导入",
            onBack = navigateBack,
            modifier = contentModifier,
        )
        return
    }
    val savedPages = rememberSaveableStateHolder()
    val pageKey = "${page.name}:${if (page == ImportScreenPage.List) state.selectedCategory.name else state.selectedTask?.taskNo.orEmpty()}"
    savedPages.SaveableStateProvider(pageKey) {
        when (page) {
            ImportScreenPage.List -> ImportListScreen(
                state, viewModel::selectCategory,
                onOpen = { task ->
                    when {
                        state.dirty && state.selectedTask?.taskNo == task.taskNo -> {
                            editorManual = task.category == ImportCategory.Text
                            page = ImportScreenPage.Editor
                        }
                        state.dirty -> { pendingTaskNo = task.taskNo; discardDialog = true }
                        else -> { viewModel.selectTask(task); page = ImportScreenPage.Detail }
                    }
                },
                onRetry = { viewModel.refresh(state.selectedCategory) },
                onLoadMore = { viewModel.loadMore(state.selectedCategory) },
                onImport = {
                    if (!mutating) {
                        if (state.draft != null && (state.dirty || state.submissionUnconfirmed)) {
                            editorManual = state.selectedTask?.category != ImportCategory.File
                            viewModel.selectCategory(state.selectedTask?.category ?: ImportCategory.Text)
                            page = ImportScreenPage.Editor
                        }
                        else showMethodSheet = true
                    }
                },
                onBack = navigateBack, modifier = contentModifier,
            )
            ImportScreenPage.Upload -> ImportUploadScreen(
                fileName = state.preparedFileName,
                fileDescription = state.preparedFileDescription,
                requirements = "支持 PDF、PNG、JPG、JPEG、WEBP。单个文件不超过 ${MAX_IMPORT_FILE_SIZE_BYTES / (1024 * 1024)} MB，PDF 不超过 $MAX_IMPORT_PDF_PAGE_COUNT 页。",
                isPreparing = state.isPreparingFile,
                isUploading = state.isUploading,
                error = state.fileError.orEmpty(),
                unconfirmed = state.submissionUnconfirmed,
                onChoose = chooseFile,
                onUpload = viewModel::uploadPreparedFile,
                onReview = showRecords,
                onBack = navigateBack,
                modifier = contentModifier,
            )
            ImportScreenPage.Detail -> ImportDetailScreen(
                state = state,
                canEdit = state.canEditSelectedTask,
                onEdit = { editorManual = state.selectedTask?.category == ImportCategory.Text; reloadReview(); page = ImportScreenPage.Editor },
                onRetry = retryDetail,
                onBack = navigateBack,
                modifier = contentModifier,
            )
            ImportScreenPage.Editor -> ImportEditorScreen(
                state = state,
                isManual = editorManual,
                onTitleChanged = viewModel::updateManualTitle,
                onParagraphChanged = viewModel::updateParagraphText,
                onAddParagraph = viewModel::addParagraph,
                onRemoveParagraph = { removeParagraph = it },
                onPreviewPage = previewPage,
                onSave = { dismissKeyboard(); viewModel.saveDraft() },
                onSubmit = { dismissKeyboard(); viewModel.submit() },
                onReload = reloadReview,
                onReviewUnconfirmed = showRecords,
                onBack = navigateBack,
                modifier = contentModifier,
            )
        }
    }
    if (showMethodSheet) ModalBottomSheet(onDismissRequest = { showMethodSheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.padding(horizontal = spacing.xl, vertical = spacing.md)) {
            item { ImportHeading("导入资料") }
            item {
                ImportActionRow("从文件导入", "识别 PDF 或图片中的研究材料", R.drawable.ic_phosphor_upload_simple_duotone, {
                    showMethodSheet = false
                    viewModel.clearSelection()
                    viewModel.selectCategory(ImportCategory.File)
                    page = ImportScreenPage.Upload
                    chooseFile()
                }, enabled = !mutating && !state.submissionUnconfirmed)
            }
            item {
                ImportActionRow("录入文本", "分段整理笔记，保存草稿后确认入库", R.drawable.ic_phosphor_file_text_duotone, {
                    showMethodSheet = false
                    viewModel.beginManualDraft()
                    editorManual = true
                    page = ImportScreenPage.Editor
                }, enabled = !mutating && !state.submissionUnconfirmed)
            }
        }
    }
    if (discardDialog) AlertDialog(
        onDismissRequest = { discardDialog = false },
        title = { Text("放弃未保存的修改？", style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                "本次修改还没有保存。放弃后，已保存的内容不受影响。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = {
            val task = (state.fileTasks.records + state.manualTasks.records).find { it.taskNo == pendingTaskNo }
            discardDialog = false
            pendingTaskNo = null
            viewModel.clearSelection()
            if (task == null) page = ImportScreenPage.List
            else { viewModel.selectTask(task); page = ImportScreenPage.Detail }
        }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discardDialog = false }) { Text("继续编辑") } },
    )
    removeParagraph?.let { number ->
        AlertDialog(onDismissRequest = { removeParagraph = null }, title = { Text("移除此段？", style = MaterialTheme.typography.titleMedium) },
            text = { Text("移除后，可在保存前重新补充内容。", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { viewModel.removeParagraph(number); removeParagraph = null }) { Text("移除") } },
            dismissButton = { TextButton(onClick = { removeParagraph = null }) { Text("保留") } })
    }
    state.selectedPreviewPageNo?.let { number ->
        ImportPagePreview(number, state.preview?.bitmap, state.isLoadingPreview, state.previewError?.userMessage.orEmpty(),
            onClose = viewModel::closePagePreview, onRetry = { previewPage(number) })
    }
}

private enum class ImportScreenPage { List, Upload, Detail, Editor }
private val IMPORT_MIME_TYPES = arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")
