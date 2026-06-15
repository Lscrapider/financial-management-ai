package com.scrapider.finance.androidapp

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scrapider.finance.androidapp.data.ApiClient
import com.scrapider.finance.androidapp.data.AddWatchTargetFormState
import com.scrapider.finance.androidapp.data.FinanceRepository
import com.scrapider.finance.androidapp.data.KnowledgeMaterialFormState
import com.scrapider.finance.androidapp.data.KnowledgeMaterialTask
import com.scrapider.finance.androidapp.data.KnowledgeMaterialUiState
import com.scrapider.finance.androidapp.data.KnowledgeSection
import com.scrapider.finance.androidapp.data.MarketAssetType
import com.scrapider.finance.androidapp.data.MarketFilter
import com.scrapider.finance.androidapp.data.OcrUploadFile
import com.scrapider.finance.androidapp.data.MarketUiState
import com.scrapider.finance.androidapp.data.ObservationRiskUiState
import com.scrapider.finance.androidapp.data.CreateReportFormState
import com.scrapider.finance.androidapp.data.ReportConfigProfileOption
import com.scrapider.finance.androidapp.data.ReportResearchUiState
import com.scrapider.finance.androidapp.data.ReportStatusFilter
import com.scrapider.finance.androidapp.data.ReportTargetOption
import com.scrapider.finance.androidapp.data.ReportTargetSummary
import com.scrapider.finance.androidapp.data.ReportTargetType
import com.scrapider.finance.androidapp.data.SessionState
import com.scrapider.finance.androidapp.data.WatchTargetType
import com.scrapider.finance.androidapp.data.WorkbenchSummary
import com.scrapider.finance.androidapp.ui.FinanceTheme
import com.scrapider.finance.androidapp.ui.KnowledgeMaterialScreen
import com.scrapider.finance.androidapp.ui.LoginScreen
import com.scrapider.finance.androidapp.ui.MarketScreen
import com.scrapider.finance.androidapp.ui.ObservationRiskScreen
import com.scrapider.finance.androidapp.ui.ReportResearchScreen
import com.scrapider.finance.androidapp.ui.WorkbenchScreen
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class AppScreen {
    Login,
    Workbench,
    Market,
    Observation,
    Report,
    Knowledge,
}

private data class AppUiState(
    val screen: AppScreen = AppScreen.Login,
    val username: String = "research_user_01",
    val password: String = "",
    val loginAsAdmin: Boolean = false,
    val rememberAccount: Boolean = true,
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String = "",
    val session: SessionState = SessionState(),
    val workbench: WorkbenchSummary = WorkbenchSummary(),
    val market: MarketUiState = MarketUiState(),
    val observation: ObservationRiskUiState = ObservationRiskUiState(),
    val report: ReportResearchUiState = ReportResearchUiState(),
    val knowledge: KnowledgeMaterialUiState = KnowledgeMaterialUiState(),
)

class MainActivity : ComponentActivity() {
    private companion object {
        const val PREFS_NAME = "finance_android_session"
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
        const val KEY_REAL_NAME = "real_name"
        const val KEY_ROLES = "roles"
        const val KEY_REMEMBER_ACCOUNT = "remember_account"
        const val KEY_REMEMBERED_USERNAME = "remembered_username"
        const val ROLE_SEPARATOR = "\u001F"
    }

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        importOcrUris(uris, "PDF")
    }
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        importOcrUris(uris, "图库")
    }
    private val cameraPicker = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        importOcrCameraBitmap(bitmap)
    }
    private val apiClient = ApiClient()
    private val repository = FinanceRepository(apiClient)
    private val materialPollHandler = Handler(Looper.getMainLooper())
    private var state by mutableStateOf(AppUiState())
    private var materialPollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(20, 22, 26)
        window.navigationBarColor = android.graphics.Color.rgb(20, 22, 26)
        state = savedUiState()

        setContent {
            FinanceTheme {
                when (state.screen) {
                    AppScreen.Login -> LoginScreen(
                        username = state.username,
                        password = state.password,
                        loginAsAdmin = state.loginAsAdmin,
                        rememberAccount = state.rememberAccount,
                        passwordVisible = state.passwordVisible,
                        loading = state.loading,
                        errorMessage = state.errorMessage,
                        onUsernameChange = { state = state.copy(username = it, errorMessage = "") },
                        onPasswordChange = { state = state.copy(password = it, errorMessage = "") },
                        onRoleChange = { state = state.copy(loginAsAdmin = it) },
                        onRememberChange = { state = state.copy(rememberAccount = it) },
                        onPasswordVisibleChange = { state = state.copy(passwordVisible = it) },
                        onLogin = ::submitLogin,
                    )

                    AppScreen.Workbench -> WorkbenchScreen(
                        displayName = state.session.displayName,
                        loading = state.loading,
                        statusMessage = state.statusMessage,
                        summary = state.workbench,
                        onRefresh = ::refreshWorkbench,
                        onMarketSelected = ::showMarket,
                        onObservationSelected = ::showObservation,
                        onReportSelected = ::showReport,
                        onKnowledgeSelected = ::showKnowledge,
                    )

                    AppScreen.Market -> MarketScreen(
                        loading = state.loading,
                        statusMessage = state.statusMessage,
                        market = state.market,
                        onRefresh = ::refreshMarket,
                        onAssetTypeChange = ::selectMarketAssetType,
                        onMarketFilterChange = ::selectMarketFilter,
                        onSortByChangePercent = ::toggleMarketChangePercentSort,
                        onKeywordChange = { state = state.copy(market = state.market.copy(keyword = it)) },
                        onWorkbenchSelected = ::showWorkbench,
                        onObservationSelected = ::showObservation,
                        onReportSelected = ::showReport,
                        onKnowledgeSelected = ::showKnowledge,
                    )

                    AppScreen.Observation -> ObservationRiskScreen(
                        loading = state.loading,
                        statusMessage = state.statusMessage,
                        observation = state.observation,
                        onRefresh = ::refreshObservation,
                        onGroupSelected = ::selectObservationGroup,
                        onTypeFilterSelected = ::selectObservationTypeFilter,
                        onItemSelected = ::selectObservationItem,
                        onDismissDetailSheet = ::dismissObservationItem,
                        onAlertSettingChange = ::saveObservationAlertSetting,
                        onDeleteItem = ::deleteObservationItem,
                        onOpenAddSheet = ::openAddWatchTargetSheet,
                        onDismissAddSheet = ::dismissAddWatchTargetSheet,
                        onFormChange = ::updateAddWatchTargetForm,
                        onTargetTypeChange = ::selectAddWatchTargetType,
                        onSaveTarget = ::saveObservationTarget,
                        onWorkbenchSelected = ::showWorkbench,
                        onMarketSelected = ::showMarket,
                        onReportSelected = ::showReport,
                        onKnowledgeSelected = ::showKnowledge,
                    )

                    AppScreen.Report -> ReportResearchScreen(
                        loading = state.loading,
                        report = state.report,
                        onRefresh = ::refreshReportResearch,
                        onTargetTypeChange = ::selectReportTargetType,
                        onStatusFilterChange = ::selectReportStatusFilter,
                        onKeywordChange = { state = state.copy(report = state.report.copy(keyword = it)) },
                        onReportSelected = ::openReportDetail,
                        onRegenerate = ::regenerateReport,
                        onDismissDetailSheet = ::dismissReportDetail,
                        onOpenCreateSheet = ::openCreateReportSheet,
                        onDismissCreateSheet = ::dismissCreateReportSheet,
                        onCreateFormChange = ::updateCreateReportForm,
                        onCreateTargetTypeChange = ::selectCreateReportTargetType,
                        onCreateTargetKeywordChange = ::changeCreateReportTargetKeyword,
                        onCreateTargetSelected = ::selectCreateReportTarget,
                        onCreateProfileSelected = ::selectCreateReportProfile,
                        onSubmitCreateReport = ::submitCreateReport,
                        onWorkbenchSelected = ::showWorkbench,
                        onMarketSelected = ::showMarket,
                        onObservationSelected = ::showObservation,
                        onKnowledgeSelected = ::showKnowledge,
                    )

                    AppScreen.Knowledge -> KnowledgeMaterialScreen(
                        loading = state.loading,
                        statusMessage = state.statusMessage,
                        knowledge = state.knowledge,
                        onRefresh = ::refreshKnowledge,
                        onSectionChange = ::selectKnowledgeSection,
                        onTargetTypeChange = ::selectKnowledgeTargetType,
                        onTargetKeywordChange = ::changeKnowledgeTargetKeyword,
                        onTargetSelected = ::selectKnowledgeTarget,
                        onProfileSelected = ::selectKnowledgeProfile,
                        onFormChange = ::updateKnowledgeForm,
                        onSubmitTarget = ::submitKnowledgeTarget,
                        onSubmitNaturalLanguage = ::submitKnowledgeNaturalLanguage,
                        onSceneFilterChange = ::selectKnowledgeSceneFilter,
                        onTagFilterChange = ::selectKnowledgeTagFilter,
                        onSourceKeywordChange = ::changeKnowledgeSourceKeyword,
                        onResetFilters = ::resetKnowledgeFilters,
                        onPickOcrPdf = { pdfPicker.launch("application/pdf") },
                        onTakeOcrPhoto = { cameraPicker.launch(null) },
                        onPickOcrGallery = { galleryPicker.launch("image/*") },
                        onRemoveOcrFile = ::removeOcrUploadFile,
                        onClearOcrFiles = ::clearOcrUploadFiles,
                        onSubmitOcrFiles = ::submitOcrFiles,
                        onSelectOcrTask = ::selectOcrTask,
                        onOpenOcrReview = ::openOcrReview,
                        onDismissOcrReview = ::dismissOcrReview,
                        onOcrReviewParagraphChange = ::updateOcrReviewParagraph,
                        onMoveOcrReviewParagraph = ::moveOcrReviewParagraph,
                        onMergeOcrReviewParagraph = ::mergeOcrReviewParagraph,
                        onCopyOcrReviewParagraph = ::copyOcrReviewParagraph,
                        onDeleteOcrReviewParagraph = ::deleteOcrReviewParagraph,
                        onSaveOcrReviewDraft = ::saveOcrReviewDraft,
                        onSubmitOcrReview = ::submitOcrReview,
                        onManualTitleChange = ::changeManualKnowledgeTitle,
                        onManualChunkChange = ::changeManualKnowledgeChunk,
                        onAddManualChunk = ::addManualKnowledgeChunk,
                        onRemoveManualChunk = ::removeManualKnowledgeChunk,
                        onNewManualDraft = ::newManualKnowledgeDraft,
                        onSaveManualDraft = ::saveManualKnowledgeDraft,
                        onSubmitManualDraft = ::submitManualKnowledgeDraft,
                        onSelectManualTask = ::selectManualKnowledgeTask,
                        onOpenManualTask = ::openManualKnowledgeTask,
                        onDeleteManualTask = ::deleteManualKnowledgeTask,
                        onWorkbenchSelected = ::showWorkbench,
                        onMarketSelected = ::showMarket,
                        onObservationSelected = ::showObservation,
                        onReportSelected = ::showReport,
                    )
                }
            }
        }

        if (state.session.authenticated) {
            apiClient.setAccessToken(state.session.accessToken)
            refreshMarket()
        }
    }

    override fun onDestroy() {
        stopMaterialPolling()
        apiClient.shutdown()
        super.onDestroy()
    }

    private fun submitLogin() {
        if (state.loading) return
        state = state.copy(
            loading = true,
            errorMessage = "",
            statusMessage = "正在登录：${state.username}，角色 ${if (state.loginAsAdmin) "管理员" else "普通用户"}。",
        )
        repository.login(
            username = state.username,
            password = state.password,
            roleCode = if (state.loginAsAdmin) "ADMIN" else "USER",
        ) { result, session ->
            if (!result.success || !session.authenticated) {
                state = state.copy(
                    loading = false,
                    errorMessage = result.message.ifBlank { "用户名或密码错误" },
                    statusMessage = result.message.ifBlank { "登录失败，请检查账号和密码。" },
                )
                return@login
            }
            state = state.copy(
                screen = AppScreen.Market,
                loading = false,
                session = session,
                statusMessage = "登录成功：${session.displayName}，正在同步行情中心。",
                errorMessage = "",
            )
            saveLoginState(session)
            refreshMarket()
        }
    }

    private fun refreshMarket() {
        if (!state.session.authenticated) {
            state = state.copy(screen = AppScreen.Login, statusMessage = "请先登录，登录后进入行情中心。")
            return
        }
        val currentMarket = state.market
        state = state.copy(loading = true, statusMessage = "正在同步${currentMarket.assetType.label}行情。")
        repository.loadMarketQuotes(currentMarket.assetType, currentMarket.marketFilter.marketCode, currentMarket.sortOrder) { result, market ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadMarketQuotes
            }
            val nextMarket = if (result.success || market.hasAnyData) {
                market.copy(keyword = currentMarket.keyword)
            } else {
                currentMarket
            }
            state = state.copy(
                loading = false,
                market = nextMarket,
                statusMessage = when {
                    result.success -> "行情中心已同步：${nextMarket.assetType.label} ${nextMarket.quotes.size} 条，上涨 ${nextMarket.upCount}，下跌 ${nextMarket.downCount}。"
                    market.hasAnyData -> "部分同步完成：${market.assetType.label} ${market.quotes.size} 条。${result.message}"
                    else -> result.message
                },
            )
            if (!result.success && !market.hasAnyData) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectMarketAssetType(assetType: MarketAssetType) {
        if (state.market.assetType == assetType) return
        state = state.copy(
            screen = AppScreen.Market,
            market = MarketUiState(assetType = assetType),
            statusMessage = "正在切换到${assetType.label}行情。",
        )
        refreshMarket()
    }

    private fun selectMarketFilter(filter: MarketFilter) {
        if (state.market.marketFilter.marketCode == filter.marketCode) return
        state = state.copy(
            screen = AppScreen.Market,
            market = state.market.copy(marketFilter = filter),
            statusMessage = "正在筛选${filter.label}行情。",
        )
        refreshMarket()
    }

    private fun toggleMarketChangePercentSort() {
        val nextOrder = if (state.market.sortOrder == "desc") "asc" else "desc"
        state = state.copy(
            screen = AppScreen.Market,
            market = state.market.copy(sortOrder = nextOrder),
            statusMessage = "正在按涨跌幅${if (nextOrder == "desc") "从高到低" else "从低到高"}排序。",
        )
        refreshMarket()
    }

    private fun showWorkbench() {
        state = state.copy(screen = AppScreen.Workbench)
        if (!state.workbench.hasAnyData) {
            refreshWorkbench()
        }
    }

    private fun showMarket() {
        state = state.copy(screen = AppScreen.Market)
        if (!state.market.hasAnyData) {
            refreshMarket()
        }
    }

    private fun showObservation() {
        state = state.copy(screen = AppScreen.Observation)
        if (state.observation.groups.isEmpty()) {
            refreshObservation()
        }
    }

    private fun showReport() {
        state = state.copy(screen = AppScreen.Report)
        if (!state.report.hasAnyData) {
            refreshReportResearch()
        }
    }

    private fun showKnowledge() {
        state = state.copy(screen = AppScreen.Knowledge)
        if (state.knowledge.reportTypes.isEmpty() || state.knowledge.configProfiles.isEmpty()) {
            loadKnowledgeMaterialOptions()
        }
        if (state.knowledge.targetOptions.isEmpty()) {
            loadKnowledgeTargetOptions(state.knowledge.form.targetType, state.knowledge.form.targetKeyword)
        }
        if (state.knowledge.activeTask?.terminal == false) {
            refreshKnowledgeMaterialTask()
            ensureMaterialPolling()
        }
        if (state.knowledge.section == KnowledgeSection.OcrImport && state.knowledge.ocr.tasks.isEmpty()) {
            loadOcrTasks()
        }
        if (state.knowledge.section == KnowledgeSection.ManualImport && state.knowledge.manual.tasks.isEmpty()) {
            loadManualKnowledgeTasks()
        }
    }

    private fun refreshKnowledge() {
        when (state.knowledge.section) {
            KnowledgeSection.Materials -> refreshKnowledgeMaterialTask()
            KnowledgeSection.OcrImport -> loadOcrTasks()
            KnowledgeSection.ManualImport -> loadManualKnowledgeTasks()
        }
    }

    private fun selectKnowledgeSection(section: KnowledgeSection) {
        state = state.copy(
            screen = AppScreen.Knowledge,
            knowledge = state.knowledge.copy(section = section),
            statusMessage = when (section) {
                KnowledgeSection.Materials -> "已切换到知识库材料。"
                KnowledgeSection.OcrImport -> "已切换到知识库OCR导入。"
                KnowledgeSection.ManualImport -> "已切换到知识库手动导入。"
            },
        )
        if (section == KnowledgeSection.OcrImport && state.knowledge.ocr.tasks.isEmpty()) {
            loadOcrTasks()
        }
        if (section == KnowledgeSection.ManualImport && state.knowledge.manual.tasks.isEmpty()) {
            loadManualKnowledgeTasks()
        }
    }

    private fun refreshReportResearch() {
        if (!state.session.authenticated) {
            state = state.copy(screen = AppScreen.Login, statusMessage = "请先登录，登录后进入报告研究。")
            return
        }
        val current = state.report
        state = state.copy(loading = true, statusMessage = "正在同步${current.targetType.label}报告列表。")
        repository.loadReportResearch(current.targetType) { result, report ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadReportResearch
            }
            val nextReport = if (result.success || report.hasAnyData) {
                report.copy(
                    statusFilter = current.statusFilter,
                    keyword = current.keyword,
                    selectedDetail = current.selectedDetail,
                    showCreateSheet = current.showCreateSheet,
                    createForm = current.createForm,
                    targetOptions = current.targetOptions,
                    reportTypes = current.reportTypes,
                    configProfiles = current.configProfiles,
                )
            } else {
                current
            }
            state = state.copy(
                loading = false,
                report = nextReport,
                statusMessage = when {
                    result.success -> "报告研究已同步：${nextReport.targets.size} 个标的。"
                    report.hasAnyData -> "部分同步完成：${result.message}"
                    else -> result.message
                },
            )
            if (!result.success && !report.hasAnyData) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectReportTargetType(targetType: ReportTargetType) {
        if (state.report.targetType == targetType) return
        state = state.copy(
            screen = AppScreen.Report,
            report = state.report.copy(
                targetType = targetType,
                keyword = "",
                targetOptions = emptyList(),
                createForm = state.report.createForm.copy(targetType = targetType),
            ),
            statusMessage = "正在切换到${targetType.label}报告列表。",
        )
        refreshReportResearch()
    }

    private fun selectReportStatusFilter(filter: ReportStatusFilter) {
        state = state.copy(screen = AppScreen.Report, report = state.report.copy(statusFilter = filter))
    }

    private fun openReportDetail(report: ReportTargetSummary) {
        val reportId = report.latestReportId
        if (reportId == null || reportId <= 0L) {
            state = state.copy(statusMessage = "该标的暂无可查看报告详情。")
            return
        }
        if (state.loading) return
        state = state.copy(loading = true, statusMessage = "正在加载 ${report.targetName} 报告详情。")
        repository.loadReportDetail(reportId) { result, detail ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadReportDetail
            }
            if (!result.success || detail == null) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@loadReportDetail
            }
            state = state.copy(
                loading = false,
                report = state.report.copy(selectedDetail = detail),
                statusMessage = "${detail.targetName} 报告详情已加载。",
            )
        }
    }

    private fun dismissReportDetail() {
        state = state.copy(report = state.report.copy(selectedDetail = null))
    }

    private fun regenerateReport(taskNo: String) {
        if (state.loading) return
        state = state.copy(loading = true, statusMessage = "正在提交重新生成任务。")
        repository.regenerateReport(taskNo) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@regenerateReport
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@regenerateReport
            }
            state = state.copy(
                loading = false,
                report = state.report.copy(selectedDetail = null),
                statusMessage = "重新生成任务已提交，正在刷新报告列表。",
            )
            refreshReportResearch()
        }
    }

    private fun openCreateReportSheet() {
        val currentType = state.report.targetType
        val form = applyCreateReportProfile(
            state.report.createForm.copy(
                targetType = currentType,
                targetKeyword = "",
                targetCode = "",
                targetName = "",
            ),
            preferredProfileId = state.report.createForm.configProfileId,
        )
        state = state.copy(report = state.report.copy(showCreateSheet = true, createForm = form, targetOptions = emptyList()))
        loadReportCreateOptions()
        loadCreateReportTargetOptions(currentType, "")
    }

    private fun dismissCreateReportSheet() {
        state = state.copy(report = state.report.copy(showCreateSheet = false))
    }

    private fun updateCreateReportForm(form: CreateReportFormState) {
        state = state.copy(report = state.report.copy(createForm = form))
    }

    private fun selectCreateReportTargetType(targetType: ReportTargetType) {
        val form = applyCreateReportProfile(
            state.report.createForm.copy(
                targetType = targetType,
                targetKeyword = "",
                targetCode = "",
                targetName = "",
            ),
            preferredProfileId = null,
        )
        state = state.copy(
            report = state.report.copy(
                createForm = form,
                targetOptions = emptyList(),
            ),
        )
        loadCreateReportTargetOptions(targetType, "")
    }

    private fun changeCreateReportTargetKeyword(keyword: String) {
        val form = state.report.createForm.copy(
            targetKeyword = keyword,
            targetCode = "",
            targetName = "",
        )
        state = state.copy(report = state.report.copy(createForm = form))
        loadCreateReportTargetOptions(form.targetType, keyword)
    }

    private fun selectCreateReportTarget(option: ReportTargetOption) {
        state = state.copy(
            report = state.report.copy(
                createForm = state.report.createForm.copy(
                    targetType = option.targetType,
                    targetKeyword = "${option.targetName} ${option.targetCode}",
                    targetCode = option.targetCode,
                    targetName = option.targetName,
                ),
            ),
        )
    }

    private fun selectCreateReportProfile(profileId: Long) {
        val form = applyCreateReportProfile(state.report.createForm, preferredProfileId = profileId)
        state = state.copy(report = state.report.copy(createForm = form))
    }

    private fun submitCreateReport() {
        if (state.loading) return
        val form = state.report.createForm
        state = state.copy(loading = true, statusMessage = "正在创建 ${form.targetName.ifBlank { form.targetCode }} 报告任务。")
        repository.submitReportTask(form) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@submitReportTask
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@submitReportTask
            }
            state = state.copy(
                loading = false,
                report = state.report.copy(showCreateSheet = false),
                statusMessage = "报告任务已创建，正在刷新列表。",
            )
            refreshReportResearch()
        }
    }

    private fun loadReportCreateOptions() {
        repository.loadReportCreateOptions { result, reportTypes, profiles ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadReportCreateOptions
            }
            val nextReport = state.report.copy(
                reportTypes = reportTypes,
                configProfiles = profiles,
            )
            state = state.copy(
                report = nextReport.copy(createForm = applyCreateReportProfile(nextReport.createForm, nextReport.createForm.configProfileId, profiles)),
                statusMessage = result.message,
            )
        }
    }

    private fun loadCreateReportTargetOptions(targetType: ReportTargetType, keyword: String) {
        repository.loadReportTargetOptions(targetType, keyword) { result, options ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadReportTargetOptions
            }
            if (state.report.createForm.targetType != targetType) {
                return@loadReportTargetOptions
            }
            state = state.copy(
                report = state.report.copy(targetOptions = options),
                statusMessage = result.message,
            )
        }
    }

    private fun applyCreateReportProfile(
        form: CreateReportFormState,
        preferredProfileId: Long?,
        profiles: List<ReportConfigProfileOption> = state.report.configProfiles,
    ): CreateReportFormState {
        val availableProfiles = profiles.filter { it.targetType == null || it.targetType == form.targetType }.ifEmpty { profiles }
        val profile = availableProfiles.firstOrNull { it.id == preferredProfileId }
            ?: availableProfiles.firstOrNull { it.configProfile == "system_recommended" }
            ?: availableProfiles.firstOrNull()
        return if (profile == null) {
            form
        } else {
            form.copy(
                configProfileId = profile.id,
                configProfile = profile.configProfile,
                reportType = profile.reportType,
                totalChunks = profile.totalChunks,
                dailyKlineLimit = profile.dailyKlineLimit,
                weeklyKlineLimit = profile.weeklyKlineLimit,
                monthlyKlineLimit = profile.monthlyKlineLimit,
            )
        }
    }

    private fun loadKnowledgeMaterialOptions() {
        repository.loadKnowledgeMaterialOptions { result, reportTypes, profiles ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadKnowledgeMaterialOptions
            }
            val nextKnowledge = state.knowledge.copy(
                reportTypes = reportTypes,
                configProfiles = profiles,
            )
            state = state.copy(
                knowledge = nextKnowledge.copy(
                    form = applyKnowledgeMaterialProfile(
                        nextKnowledge.form,
                        nextKnowledge.form.configProfileId,
                        profiles,
                    ),
                ),
                statusMessage = activeKnowledgeTaskMessageOr(result.message),
            )
        }
    }

    private fun selectKnowledgeTargetType(targetType: ReportTargetType) {
        val form = applyKnowledgeMaterialProfile(
            state.knowledge.form.copy(
                targetType = targetType,
                targetKeyword = "",
                targetCode = "",
                targetName = "",
            ),
            preferredProfileId = null,
        )
        state = state.copy(
            screen = AppScreen.Knowledge,
            knowledge = state.knowledge.copy(form = form, targetOptions = emptyList()),
            statusMessage = "正在切换到${targetType.label}材料检索。",
        )
        loadKnowledgeTargetOptions(targetType, "")
    }

    private fun changeKnowledgeTargetKeyword(keyword: String) {
        val form = state.knowledge.form.copy(
            targetKeyword = keyword,
            targetCode = "",
            targetName = "",
        )
        state = state.copy(knowledge = state.knowledge.copy(form = form))
        loadKnowledgeTargetOptions(form.targetType, keyword)
    }

    private fun selectKnowledgeTarget(option: ReportTargetOption) {
        state = state.copy(
            knowledge = state.knowledge.copy(
                form = state.knowledge.form.copy(
                    targetType = option.targetType,
                    targetKeyword = "${option.targetName} ${option.targetCode}",
                    targetCode = option.targetCode,
                    targetName = option.targetName,
                ),
            ),
        )
    }

    private fun selectKnowledgeProfile(profileId: Long) {
        val form = applyKnowledgeMaterialProfile(state.knowledge.form, preferredProfileId = profileId)
        state = state.copy(knowledge = state.knowledge.copy(form = form))
    }

    private fun updateKnowledgeForm(form: KnowledgeMaterialFormState) {
        state = state.copy(knowledge = state.knowledge.copy(form = form))
    }

    private fun loadKnowledgeTargetOptions(targetType: ReportTargetType, keyword: String) {
        repository.loadKnowledgeMaterialTargetOptions(targetType, keyword) { result, options ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadKnowledgeMaterialTargetOptions
            }
            if (state.knowledge.form.targetType != targetType) {
                return@loadKnowledgeMaterialTargetOptions
            }
            state = state.copy(
                knowledge = state.knowledge.copy(targetOptions = options),
                statusMessage = activeKnowledgeTaskMessageOr(result.message),
            )
        }
    }

    private fun submitKnowledgeTarget() {
        if (state.loading) return
        val form = state.knowledge.form
        state = state.copy(
            loading = true,
            screen = AppScreen.Knowledge,
            knowledge = state.knowledge.copy(
                activeTask = null,
                sceneFilter = "",
                tagFilter = "",
                sourceKeyword = "",
                updatedAt = "--:--",
            ),
            statusMessage = "正在提交 ${form.targetName.ifBlank { form.targetCode }} 的知识库材料检索任务。",
        )
        repository.submitKnowledgeMaterialTarget(form) { result, task ->
            handleKnowledgeSubmitResult(result, task)
        }
    }

    private fun submitKnowledgeNaturalLanguage() {
        if (state.loading) return
        val form = state.knowledge.form
        state = state.copy(
            loading = true,
            screen = AppScreen.Knowledge,
            knowledge = state.knowledge.copy(
                activeTask = null,
                sceneFilter = "",
                tagFilter = "",
                sourceKeyword = "",
                updatedAt = "--:--",
            ),
            statusMessage = "正在按自然语言提交 ${form.totalChunks} 条知识库材料召回任务。",
        )
        repository.submitKnowledgeMaterialNaturalLanguage(form.queryText, form.totalChunks) { result, task ->
            handleKnowledgeSubmitResult(result, task)
        }
    }

    private fun handleKnowledgeSubmitResult(result: com.scrapider.finance.androidapp.data.ApiResult, task: KnowledgeMaterialTask?) {
        if (result.statusCode == 401) {
            handleExpiredSession("登录态已失效，请重新登录。")
            return
        }
        if (!result.success || task == null) {
            state = state.copy(loading = false, statusMessage = result.message)
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            return
        }
        state = state.copy(
            loading = false,
            knowledge = state.knowledge.copy(
                activeTask = task,
                sceneFilter = "",
                tagFilter = "",
                sourceKeyword = "",
                updatedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            ),
            statusMessage = knowledgeTaskMessage(task),
        )
        refreshKnowledgeMaterialTask()
        ensureMaterialPolling()
    }

    private fun refreshKnowledgeMaterialTask() {
        val taskNo = state.knowledge.activeTask?.taskNo.orEmpty()
        if (taskNo.isBlank()) {
            loadKnowledgeMaterialOptions()
            return
        }
        repository.loadKnowledgeMaterialTask(taskNo) { result, task ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadKnowledgeMaterialTask
            }
            if (!result.success || task == null) {
                state = state.copy(loading = false, statusMessage = result.message)
                return@loadKnowledgeMaterialTask
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    activeTask = task,
                    updatedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                ),
                statusMessage = knowledgeTaskMessage(task),
            )
            if (task.terminal) {
                stopMaterialPolling()
            } else {
                ensureMaterialPolling()
            }
        }
    }

    private fun ensureMaterialPolling() {
        val task = state.knowledge.activeTask
        if (task == null || task.terminal || state.screen != AppScreen.Knowledge || materialPollRunnable != null) {
            return
        }
        startMaterialPolling()
    }

    private fun startMaterialPolling() {
        stopMaterialPolling()
        materialPollRunnable = object : Runnable {
            override fun run() {
                val task = state.knowledge.activeTask
                if (task == null || task.terminal || state.screen != AppScreen.Knowledge) {
                    stopMaterialPolling()
                    return
                }
                refreshKnowledgeMaterialTask()
                materialPollHandler.postDelayed(this, 1800L)
            }
        }
        materialPollHandler.postDelayed(materialPollRunnable!!, 1800L)
    }

    private fun stopMaterialPolling() {
        materialPollRunnable?.let { materialPollHandler.removeCallbacks(it) }
        materialPollRunnable = null
    }

    private fun activeKnowledgeTaskMessageOr(fallback: String): String {
        val task = state.knowledge.activeTask
        return when {
            task != null && !task.terminal -> knowledgeTaskMessage(task)
            state.loading && state.screen == AppScreen.Knowledge -> state.statusMessage
            else -> fallback
        }
    }

    private fun knowledgeTaskMessage(task: KnowledgeMaterialTask): String = when (task.status) {
        "success" -> "材料召回完成：${task.chunks.size} 条。"
        "failed" -> task.errorMessage.ifBlank { "材料召回失败。" }
        "processing_current_scenes" -> "正在计算标的场景，完成后会自动召回知识库材料。"
        "current_scenes_ready" -> "标的场景已计算，正在准备知识库召回。"
        "retrieving_knowledge" -> "正在检索知识库材料，请稍候。"
        "pending" -> "材料任务已提交，等待后端处理。"
        else -> "材料任务${materialStatusText(task.status)}，请稍候。"
    }

    private fun selectKnowledgeSceneFilter(scene: String) {
        state = state.copy(knowledge = state.knowledge.copy(sceneFilter = scene, tagFilter = ""))
    }

    private fun selectKnowledgeTagFilter(tag: String) {
        state = state.copy(knowledge = state.knowledge.copy(tagFilter = tag))
    }

    private fun changeKnowledgeSourceKeyword(keyword: String) {
        state = state.copy(knowledge = state.knowledge.copy(sourceKeyword = keyword))
    }

    private fun resetKnowledgeFilters() {
        state = state.copy(knowledge = state.knowledge.copy(sceneFilter = "", tagFilter = "", sourceKeyword = ""))
    }

    private fun importOcrUris(uris: List<Uri>, sourceLabel: String) {
        if (uris.isEmpty()) return
        val files = uris.mapNotNull { uri -> readOcrUploadFile(uri) }
        if (files.isEmpty()) {
            state = state.copy(statusMessage = "${sourceLabel}文件读取失败，请重新选择。")
            Toast.makeText(this, "${sourceLabel}文件读取失败", Toast.LENGTH_SHORT).show()
            return
        }
        appendOcrUploadFiles(files, "已选择 ${files.size} 个${sourceLabel}文件。")
    }

    private fun importOcrCameraBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            state = state.copy(statusMessage = "未获取到相机照片。")
            return
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        val filename = "camera_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.jpg"
        appendOcrUploadFiles(
            listOf(
                OcrUploadFile(
                    name = filename,
                    contentType = "image/jpeg",
                    sizeBytes = output.size().toLong(),
                    bytes = output.toByteArray(),
                ),
            ),
            "已添加相机照片。",
        )
    }

    private fun readOcrUploadFile(uri: Uri): OcrUploadFile? = runCatching {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val name = queryDisplayName(uri).ifBlank {
            "ocr_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}"
        }
        OcrUploadFile(
            name = name,
            contentType = contentResolver.getType(uri).orEmpty().ifBlank { guessContentType(name) },
            sizeBytes = bytes.size.toLong(),
            bytes = bytes,
        )
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String {
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0).orEmpty() else ""
        }.orEmpty()
    }

    private fun guessContentType(filename: String): String = when {
        filename.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        filename.endsWith(".png", ignoreCase = true) -> "image/png"
        filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }

    private fun appendOcrUploadFiles(files: List<OcrUploadFile>, message: String) {
        val existing = state.knowledge.ocr.selectedFiles.associateBy { "${it.name}-${it.sizeBytes}" }.toMutableMap()
        files.forEach { file -> existing["${file.name}-${file.sizeBytes}"] = file }
        state = state.copy(
            screen = AppScreen.Knowledge,
            knowledge = state.knowledge.copy(
                section = KnowledgeSection.OcrImport,
                ocr = state.knowledge.ocr.copy(selectedFiles = existing.values.toList()),
            ),
            statusMessage = message,
        )
    }

    private fun removeOcrUploadFile(index: Int) {
        state = state.copy(
            knowledge = state.knowledge.copy(
                ocr = state.knowledge.ocr.copy(
                    selectedFiles = state.knowledge.ocr.selectedFiles.filterIndexed { itemIndex, _ -> itemIndex != index },
                ),
            ),
        )
    }

    private fun clearOcrUploadFiles() {
        state = state.copy(knowledge = state.knowledge.copy(ocr = state.knowledge.ocr.copy(selectedFiles = emptyList())))
    }

    private fun submitOcrFiles() {
        if (state.loading) return
        val files = state.knowledge.ocr.selectedFiles
        state = state.copy(loading = true, statusMessage = "正在提交 ${files.size} 个 OCR 导入任务。")
        repository.submitOcrFiles(files) { result, tasks ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@submitOcrFiles
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@submitOcrFiles
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    section = KnowledgeSection.OcrImport,
                    ocr = state.knowledge.ocr.copy(
                        selectedFiles = emptyList(),
                        tasks = tasks.ifEmpty { state.knowledge.ocr.tasks },
                        selectedTaskNo = tasks.firstOrNull()?.taskNo ?: state.knowledge.ocr.selectedTaskNo,
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    ),
                ),
                statusMessage = "OCR任务已提交，正在刷新队列。",
            )
            loadOcrTasks()
        }
    }

    private fun loadOcrTasks() {
        if (!state.session.authenticated) {
            state = state.copy(screen = AppScreen.Login, statusMessage = "请先登录，登录后进入 OCR 导入。")
            return
        }
        state = state.copy(loading = true, statusMessage = "正在同步 OCR 导入队列。")
        repository.loadOcrTasks { result, tasks ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadOcrTasks
            }
            if (!result.success && tasks.isEmpty()) {
                state = state.copy(loading = false, statusMessage = result.message)
                return@loadOcrTasks
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    section = KnowledgeSection.OcrImport,
                    ocr = state.knowledge.ocr.copy(
                        tasks = tasks,
                        selectedTaskNo = state.knowledge.ocr.selectedTaskNo.takeIf { taskNo -> tasks.any { it.taskNo == taskNo } }
                            ?: tasks.firstOrNull()?.taskNo.orEmpty(),
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    ),
                ),
                statusMessage = "OCR队列已同步：${tasks.size} 个任务。",
            )
        }
    }

    private fun selectOcrTask(taskNo: String) {
        state = state.copy(knowledge = state.knowledge.copy(ocr = state.knowledge.ocr.copy(selectedTaskNo = taskNo)))
    }

    private fun openOcrReview(taskNo: String) {
        if (taskNo.isBlank() || state.loading) return
        state = state.copy(loading = true, statusMessage = "正在进入 OCR 人工复核。")
        repository.loadOcrReview(taskNo) { result, review ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadOcrReview
            }
            if (!result.success || review == null) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@loadOcrReview
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    section = KnowledgeSection.OcrImport,
                    ocr = state.knowledge.ocr.copy(selectedReview = review),
                ),
                statusMessage = "已进入 ${review.taskNo} 的人工复核。",
            )
        }
    }

    private fun dismissOcrReview() {
        state = state.copy(knowledge = state.knowledge.copy(ocr = state.knowledge.ocr.copy(selectedReview = null)))
    }

    private fun updateOcrReviewParagraph(paragraphNo: Int, text: String) {
        val review = state.knowledge.ocr.selectedReview ?: return
        val draft = review.draftContent
        val nextParagraphs = draft.paragraphs.map { paragraph ->
            if (paragraph.paragraphNo == paragraphNo) paragraph.copy(text = text) else paragraph
        }
        updateSelectedOcrReviewParagraphs(nextParagraphs)
    }

    private fun moveOcrReviewParagraph(paragraphNo: Int, offset: Int) {
        val review = state.knowledge.ocr.selectedReview ?: return
        val paragraphs = review.draftContent.paragraphs.toMutableList()
        val currentIndex = paragraphs.indexOfFirst { it.paragraphNo == paragraphNo }
        val targetIndex = currentIndex + offset
        if (currentIndex !in paragraphs.indices || targetIndex !in paragraphs.indices) return
        val item = paragraphs.removeAt(currentIndex)
        paragraphs.add(targetIndex, item)
        updateSelectedOcrReviewParagraphs(paragraphs)
    }

    private fun mergeOcrReviewParagraph(paragraphNo: Int) {
        val review = state.knowledge.ocr.selectedReview ?: return
        val paragraphs = review.draftContent.paragraphs.toMutableList()
        val currentIndex = paragraphs.indexOfFirst { it.paragraphNo == paragraphNo }
        if (currentIndex !in paragraphs.indices || currentIndex >= paragraphs.lastIndex) return
        val current = paragraphs[currentIndex]
        val next = paragraphs[currentIndex + 1]
        paragraphs[currentIndex] = current.copy(
            text = listOf(current.text, next.text).joinToString("\n").trim(),
            sourcePages = (current.sourcePages + next.sourcePages).distinct().sorted(),
            avgConfidence = ((current.avgConfidence + next.avgConfidence) / 2.0),
            warnings = current.warnings + next.warnings,
        )
        paragraphs.removeAt(currentIndex + 1)
        updateSelectedOcrReviewParagraphs(paragraphs)
    }

    private fun copyOcrReviewParagraph(paragraphNo: Int) {
        val review = state.knowledge.ocr.selectedReview ?: return
        val paragraphs = review.draftContent.paragraphs.toMutableList()
        val currentIndex = paragraphs.indexOfFirst { it.paragraphNo == paragraphNo }
        if (currentIndex !in paragraphs.indices) return
        paragraphs.add(currentIndex + 1, paragraphs[currentIndex])
        updateSelectedOcrReviewParagraphs(paragraphs)
    }

    private fun deleteOcrReviewParagraph(paragraphNo: Int) {
        val review = state.knowledge.ocr.selectedReview ?: return
        val paragraphs = review.draftContent.paragraphs.toMutableList()
        if (paragraphs.size <= 1) return
        val removed = paragraphs.removeAll { it.paragraphNo == paragraphNo }
        if (removed) {
            updateSelectedOcrReviewParagraphs(paragraphs)
        }
    }

    private fun updateSelectedOcrReviewParagraphs(paragraphs: List<com.scrapider.finance.androidapp.data.OcrReviewParagraph>) {
        val review = state.knowledge.ocr.selectedReview ?: return
        val draft = review.draftContent
        val nextParagraphs = paragraphs.mapIndexed { index, paragraph ->
            paragraph.copy(paragraphNo = index + 1)
        }
        state = state.copy(
            knowledge = state.knowledge.copy(
                ocr = state.knowledge.ocr.copy(
                    selectedReview = review.copy(
                        paragraphCount = nextParagraphs.size,
                        warningCount = nextParagraphs.sumOf { it.warnings.size },
                        draftContent = draft.copy(
                            paragraphs = nextParagraphs,
                            paragraphCount = nextParagraphs.size,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun saveOcrReviewDraft() {
        val review = state.knowledge.ocr.selectedReview ?: return
        if (state.loading) return
        state = state.copy(loading = true, statusMessage = "正在保存 OCR 复核草稿。")
        repository.saveOcrReviewDraft(review.taskNo, review.draftContent) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@saveOcrReviewDraft
            }
            state = state.copy(loading = false, statusMessage = result.message)
            if (!result.success) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitOcrReview() {
        val review = state.knowledge.ocr.selectedReview ?: return
        if (state.loading) return
        state = state.copy(loading = true, statusMessage = "正在提交 OCR 复核结果。")
        repository.submitOcrReview(review.taskNo, review.draftContent) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@submitOcrReview
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@submitOcrReview
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(ocr = state.knowledge.ocr.copy(selectedReview = null)),
                statusMessage = "OCR复核结果已提交，正在刷新队列。",
            )
            loadOcrTasks()
        }
    }

    private fun changeManualKnowledgeTitle(title: String) {
        state = state.copy(knowledge = state.knowledge.copy(manual = state.knowledge.manual.copy(title = title)))
    }

    private fun changeManualKnowledgeChunk(index: Int, text: String) {
        val chunks = state.knowledge.manual.chunks.toMutableList()
        if (index !in chunks.indices) return
        chunks[index] = text
        state = state.copy(knowledge = state.knowledge.copy(manual = state.knowledge.manual.copy(chunks = chunks)))
    }

    private fun addManualKnowledgeChunk() {
        val manual = state.knowledge.manual
        if (manual.readonly) return
        state = state.copy(knowledge = state.knowledge.copy(manual = manual.copy(chunks = manual.chunks + "")))
    }

    private fun removeManualKnowledgeChunk(index: Int) {
        val manual = state.knowledge.manual
        if (manual.readonly) return
        val chunks = manual.chunks.filterIndexed { itemIndex, _ -> itemIndex != index }.ifEmpty { listOf("") }
        state = state.copy(knowledge = state.knowledge.copy(manual = manual.copy(chunks = chunks)))
    }

    private fun newManualKnowledgeDraft() {
        state = state.copy(
            screen = AppScreen.Knowledge,
            knowledge = state.knowledge.copy(
                section = KnowledgeSection.ManualImport,
                manual = state.knowledge.manual.copy(
                    taskNo = "",
                    title = "",
                    chunks = listOf(""),
                    selectedTaskNo = "",
                    readonly = false,
                ),
            ),
            statusMessage = "已新建手动知识草稿。",
        )
    }

    private fun saveManualKnowledgeDraft() {
        if (state.loading) return
        val manual = state.knowledge.manual
        if (!manual.canSubmit) {
            state = state.copy(statusMessage = if (manual.readonly) "该任务不可编辑。" else "至少需要一个非空文本分段。")
            return
        }
        state = state.copy(loading = true, statusMessage = "正在保存手动知识草稿。")
        repository.saveManualKnowledgeDraft(manual.taskNo, manual.title, manual.chunks) { result, task ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@saveManualKnowledgeDraft
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@saveManualKnowledgeDraft
            }
            val nextTaskNo = task?.taskNo?.ifBlank { manual.taskNo } ?: manual.taskNo
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    section = KnowledgeSection.ManualImport,
                    manual = state.knowledge.manual.copy(
                        taskNo = nextTaskNo,
                        selectedTaskNo = nextTaskNo.ifBlank { state.knowledge.manual.selectedTaskNo },
                        readonly = false,
                    ),
                ),
                statusMessage = "手动知识草稿已保存，正在刷新队列。",
            )
            loadManualKnowledgeTasks()
        }
    }

    private fun submitManualKnowledgeDraft() {
        if (state.loading) return
        val manual = state.knowledge.manual
        if (!manual.canSubmit) {
            state = state.copy(statusMessage = if (manual.readonly) "该任务不可编辑。" else "至少需要一个非空文本分段。")
            return
        }
        state = state.copy(loading = true, statusMessage = "正在提交手动知识，进入打标入库流程。")
        repository.submitManualKnowledgeDraft(manual.taskNo, manual.title, manual.chunks) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@submitManualKnowledgeDraft
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@submitManualKnowledgeDraft
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    section = KnowledgeSection.ManualImport,
                    manual = state.knowledge.manual.copy(
                        taskNo = "",
                        title = "",
                        chunks = listOf(""),
                        selectedTaskNo = "",
                        readonly = false,
                    ),
                ),
                statusMessage = "手动知识已提交，正在刷新队列。",
            )
            loadManualKnowledgeTasks()
        }
    }

    private fun loadManualKnowledgeTasks() {
        if (!state.session.authenticated) {
            state = state.copy(screen = AppScreen.Login, statusMessage = "请先登录，登录后进入手动导入。")
            return
        }
        state = state.copy(loading = true, statusMessage = "正在同步手动导入队列。")
        repository.loadManualKnowledgeTasks { result, tasks ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadManualKnowledgeTasks
            }
            if (!result.success && tasks.isEmpty()) {
                state = state.copy(loading = false, statusMessage = result.message)
                return@loadManualKnowledgeTasks
            }
            val selectedTaskNo = state.knowledge.manual.selectedTaskNo
                .takeIf { taskNo -> tasks.any { it.taskNo == taskNo } }
                ?: tasks.firstOrNull()?.taskNo.orEmpty()
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    section = KnowledgeSection.ManualImport,
                    manual = state.knowledge.manual.copy(
                        tasks = tasks,
                        selectedTaskNo = selectedTaskNo,
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    ),
                ),
                statusMessage = "手动导入队列已同步：${tasks.size} 个任务。",
            )
        }
    }

    private fun selectManualKnowledgeTask(taskNo: String) {
        state = state.copy(knowledge = state.knowledge.copy(manual = state.knowledge.manual.copy(selectedTaskNo = taskNo)))
    }

    private fun openManualKnowledgeTask(taskNo: String) {
        if (taskNo.isBlank() || state.loading) return
        val task = state.knowledge.manual.tasks.firstOrNull { it.taskNo == taskNo }
        state = state.copy(loading = true, statusMessage = "正在加载手动知识内容。")
        repository.loadManualKnowledgeDetail(taskNo) { result, detail ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadManualKnowledgeDetail
            }
            if (!result.success || detail == null) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@loadManualKnowledgeDetail
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(
                    section = KnowledgeSection.ManualImport,
                    manual = state.knowledge.manual.copy(
                        taskNo = taskNo,
                        title = task?.originalFilename.orEmpty(),
                        chunks = detail.draftContent.paragraphs.map { it.text }.ifEmpty { listOf("") },
                        selectedTaskNo = taskNo,
                        readonly = task?.needsReview == false,
                    ),
                ),
                statusMessage = if (task?.needsReview == false) "已打开手动知识查看模式。" else "已打开手动知识编辑模式。",
            )
        }
    }

    private fun deleteManualKnowledgeTask(taskNo: String) {
        if (taskNo.isBlank() || state.loading) return
        state = state.copy(loading = true, statusMessage = "正在删除手动导入任务。")
        repository.deleteManualKnowledgeTask(taskNo) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@deleteManualKnowledgeTask
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@deleteManualKnowledgeTask
            }
            val currentManual = state.knowledge.manual
            val nextManual = if (currentManual.taskNo == taskNo) {
                currentManual.copy(taskNo = "", title = "", chunks = listOf(""), selectedTaskNo = "", readonly = false)
            } else {
                currentManual.copy(selectedTaskNo = "")
            }
            state = state.copy(
                loading = false,
                knowledge = state.knowledge.copy(section = KnowledgeSection.ManualImport, manual = nextManual),
                statusMessage = "手动导入任务已删除，正在刷新队列。",
            )
            loadManualKnowledgeTasks()
        }
    }

    private fun applyKnowledgeMaterialProfile(
        form: KnowledgeMaterialFormState,
        preferredProfileId: Long?,
        profiles: List<ReportConfigProfileOption> = state.knowledge.configProfiles,
    ): KnowledgeMaterialFormState {
        val availableProfiles = profiles.filter { it.targetType == null || it.targetType == form.targetType }.ifEmpty { profiles }
        val profile = availableProfiles.firstOrNull { it.id == preferredProfileId }
            ?: availableProfiles.firstOrNull { it.configProfile == "system_recommended" }
            ?: availableProfiles.firstOrNull()
        return if (profile == null) {
            form
        } else {
            form.copy(
                configProfileId = profile.id,
                configProfile = profile.configProfile,
                reportType = profile.reportType,
                totalChunks = profile.totalChunks,
                dailyKlineLimit = profile.dailyKlineLimit,
                weeklyKlineLimit = profile.weeklyKlineLimit,
                monthlyKlineLimit = profile.monthlyKlineLimit,
            )
        }
    }

    private fun refreshWorkbench() {
        if (!state.session.authenticated) {
            state = state.copy(screen = AppScreen.Login, statusMessage = "请先登录，登录后进入投资工作台。")
            return
        }
        state = state.copy(loading = true, statusMessage = "正在同步投资工作台：观察池、布控提醒和报告动态。")
        repository.loadWorkbench { result, summary ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadWorkbench
            }
            state = state.copy(
                loading = false,
                workbench = if (result.success || summary.hasAnyData) summary else state.workbench,
                statusMessage = when {
                    result.success -> "投资工作台已同步：观察池 ${summary.watchItemCount} 个标的，关注 ${summary.focusCount} 项。"
                    summary.hasAnyData -> "部分同步完成：观察池 ${summary.watchItemCount} 个标的，关注 ${summary.focusCount} 项。${result.message}"
                    else -> result.message
                },
            )
            if (!result.success && !summary.hasAnyData) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshObservation() {
        if (!state.session.authenticated) {
            state = state.copy(screen = AppScreen.Login, statusMessage = "请先登录，登录后进入观察风控。")
            return
        }
        val current = state.observation
        state = state.copy(loading = true, statusMessage = "正在同步观察池分组、标的和风险预警。")
        repository.loadObservationRisk { result, observation ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadObservationRisk
            }
            val selectedGroupId = current.selectedGroupId
                .takeIf { id -> observation.groups.any { it.id == id } }
                ?: observation.selectedGroupId
            val formGroupId = current.addForm.selectedGroupId
                .takeIf { id -> observation.groups.any { it.id == id } }
                ?: selectedGroupId
            state = state.copy(
                loading = false,
                observation = observation.copy(
                    selectedGroupId = selectedGroupId,
                    selectedItemId = current.selectedItemId
                        .takeIf { id -> observation.groups.any { group -> group.items.any { it.id == id } } }
                        .orEmpty(),
                    typeFilter = current.typeFilter,
                    targetOptions = current.targetOptions,
                    showAddSheet = current.showAddSheet,
                    addForm = current.addForm.copy(selectedGroupId = formGroupId),
                ),
                statusMessage = when {
                    result.success -> "观察风控已同步：观察池 ${observation.watchItemCount} 个标的，启用预警 ${observation.enabledAlertCount} 项。"
                    observation.groups.isNotEmpty() || observation.alerts.isNotEmpty() -> "部分同步完成：${result.message}"
                    else -> result.message
                },
            )
            if (!result.success && observation.groups.isEmpty() && observation.alerts.isEmpty()) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectObservationGroup(groupId: String) {
        state = state.copy(
            screen = AppScreen.Observation,
            observation = state.observation.copy(selectedGroupId = groupId, selectedItemId = ""),
        )
    }

    private fun selectObservationTypeFilter(targetType: WatchTargetType?) {
        state = state.copy(
            screen = AppScreen.Observation,
            observation = state.observation.copy(typeFilter = targetType, selectedItemId = ""),
        )
    }

    private fun selectObservationItem(itemId: String) {
        state = state.copy(
            screen = AppScreen.Observation,
            observation = state.observation.copy(selectedItemId = itemId),
        )
    }

    private fun dismissObservationItem() {
        state = state.copy(observation = state.observation.copy(selectedItemId = ""))
    }

    private fun saveObservationAlertSetting(
        itemId: String,
        enabled: Boolean,
        thresholdPercent: Double,
    ) {
        if (state.loading) return
        val item = state.observation.groups
            .asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.id == itemId }
            ?: return
        if (!item.targetType.supportsAlert) {
            state = state.copy(statusMessage = "${item.targetType.label}暂不支持风险预警。")
            return
        }
        val alert = state.observation.alerts.find {
            it.targetType == item.targetType && it.stockCode == item.targetCode
        }
        state = state.copy(loading = true, statusMessage = "正在保存 ${item.targetName} 的风险预警设置。")
        repository.saveStockAlert(
            targetType = item.targetType,
            stockCode = item.targetCode,
            thresholdPercent = thresholdPercent,
            enabled = enabled,
            id = alert?.id.orEmpty(),
        ) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@saveStockAlert
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@saveStockAlert
            }
            state = state.copy(loading = false, statusMessage = "${item.targetName} 风险预警设置已保存。")
            refreshObservation()
        }
    }

    private fun deleteObservationItem(itemId: String) {
        if (state.loading) return
        val item = state.observation.groups
            .asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.id == itemId }
            ?: return
        val alert = state.observation.alerts.find {
            it.targetType == item.targetType && it.stockCode == item.targetCode
        }
        state = state.copy(loading = true, statusMessage = "正在删除 ${item.targetName} 的观察和预警配置。")
        repository.deleteWatchTargetWithAlert(item, alert) { result ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@deleteWatchTargetWithAlert
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@deleteWatchTargetWithAlert
            }
            state = state.copy(
                loading = false,
                observation = state.observation.copy(selectedItemId = ""),
                statusMessage = "${item.targetName} 已从观察池移除。",
            )
            refreshObservation()
        }
    }

    private fun openAddWatchTargetSheet() {
        if (state.observation.groups.isEmpty()) {
            refreshObservation()
            return
        }
        val groupId = state.observation.selectedGroup?.id.orEmpty()
        state = state.copy(
            observation = state.observation.copy(
                showAddSheet = true,
                addForm = AddWatchTargetFormState(selectedGroupId = groupId),
            ),
        )
        loadAddTargetOptions(WatchTargetType.Stock)
    }

    private fun dismissAddWatchTargetSheet() {
        state = state.copy(observation = state.observation.copy(showAddSheet = false))
    }

    private fun updateAddWatchTargetForm(form: AddWatchTargetFormState) {
        state = state.copy(observation = state.observation.copy(addForm = form))
    }

    private fun selectAddWatchTargetType(targetType: WatchTargetType) {
        val form = state.observation.addForm.copy(
            targetType = targetType,
            targetKeyword = "",
            selectedTargetCode = "",
            selectedTargetName = "",
            selectedSecid = "",
            alertEnabled = targetType.supportsAlert,
        )
        state = state.copy(
            observation = state.observation.copy(
                addForm = form,
                targetOptions = emptyList(),
            ),
        )
        loadAddTargetOptions(targetType)
    }

    private fun loadAddTargetOptions(targetType: WatchTargetType) {
        repository.loadAlertTargetOptions(targetType) { result, options ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@loadAlertTargetOptions
            }
            if (state.observation.addForm.targetType != targetType) {
                return@loadAlertTargetOptions
            }
            state = state.copy(
                observation = state.observation.copy(targetOptions = options),
                statusMessage = if (result.success) result.message else result.message,
            )
        }
    }

    private fun saveObservationTarget() {
        if (state.loading) return
        val form = state.observation.addForm
        state = state.copy(loading = true, statusMessage = "正在保存观察标的。")
        repository.saveWatchTarget(form) { result, item ->
            if (result.statusCode == 401) {
                handleExpiredSession("登录态已失效，请重新登录。")
                return@saveWatchTarget
            }
            if (!result.success) {
                state = state.copy(loading = false, statusMessage = result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return@saveWatchTarget
            }
            val targetCode = item?.targetCode ?: form.selectedTargetCode.ifBlank { form.targetKeyword.trim() }
            if (!form.effectiveAlertEnabled) {
                finishObservationSave("观察标的已保存。")
                return@saveWatchTarget
            }
            state = state.copy(statusMessage = "观察标的已保存，正在绑定风险预警。")
            repository.saveStockAlert(
                targetType = form.targetType,
                stockCode = targetCode,
                thresholdPercent = form.alertThresholdPercent,
                enabled = true,
            ) { alertResult ->
                if (alertResult.statusCode == 401) {
                    handleExpiredSession("登录态已失效，请重新登录。")
                    return@saveStockAlert
                }
                if (alertResult.success) {
                    finishObservationSave("观察标的和风险预警已保存。")
                } else {
                    state = state.copy(
                        loading = false,
                        observation = state.observation.copy(showAddSheet = false),
                        statusMessage = "观察标的已保存，但风险预警保存失败：${alertResult.message}",
                    )
                    Toast.makeText(this, alertResult.message, Toast.LENGTH_SHORT).show()
                    refreshObservation()
                }
            }
        }
    }

    private fun finishObservationSave(message: String) {
        state = state.copy(
            loading = false,
            observation = state.observation.copy(showAddSheet = false),
            statusMessage = message,
        )
        refreshObservation()
    }

    private fun savedUiState(): AppUiState {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val rememberAccount = preferences.getBoolean(KEY_REMEMBER_ACCOUNT, true)
        val token = preferences.getString(KEY_TOKEN, "").orEmpty()
        val rememberedUsername = preferences.getString(KEY_REMEMBERED_USERNAME, "").orEmpty()
        val sessionUsername = preferences.getString(KEY_USERNAME, "").orEmpty()
        if (token.isBlank()) {
            return AppUiState(
                username = if (rememberAccount) rememberedUsername.ifBlank { "research_user_01" } else "",
                rememberAccount = rememberAccount,
            )
        }
        val session = SessionState(
            authenticated = true,
            accessToken = token,
            username = sessionUsername.ifBlank { rememberedUsername },
            realName = preferences.getString(KEY_REAL_NAME, "").orEmpty(),
            roles = preferences.getString(KEY_ROLES, "").orEmpty().split(ROLE_SEPARATOR).filter { it.isNotBlank() },
        )
        return AppUiState(
            screen = AppScreen.Market,
            username = if (rememberAccount) session.username.ifBlank { rememberedUsername } else "",
            rememberAccount = rememberAccount,
            session = session,
            statusMessage = "正在恢复登录态并同步行情中心。",
        )
    }

    private fun saveLoginState(session: SessionState) {
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_REAL_NAME, session.realName)
            .putString(KEY_ROLES, session.roles.joinToString(ROLE_SEPARATOR))
            .putBoolean(KEY_REMEMBER_ACCOUNT, state.rememberAccount)
        if (state.rememberAccount) {
            editor.putString(KEY_REMEMBERED_USERNAME, session.username.ifBlank { state.username })
        } else {
            editor.remove(KEY_REMEMBERED_USERNAME)
        }
        editor.apply()
    }

    private fun clearLoginState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_REAL_NAME)
            .remove(KEY_ROLES)
            .apply()
    }

    private fun handleExpiredSession(message: String) {
        clearLoginState()
        state = state.copy(
            screen = AppScreen.Login,
            loading = false,
            session = SessionState(),
            statusMessage = message,
            errorMessage = "登录态已失效",
        )
    }

    private fun materialStatusText(status: String): String = when (status) {
        "pending" -> "等待中"
        "processing_current_scenes" -> "正在计算场景"
        "current_scenes_ready" -> "场景已计算"
        "retrieving_knowledge" -> "正在召回知识库材料"
        "success" -> "已完成"
        "failed" -> "失败"
        else -> status.ifBlank { "处理中" }
    }
}
