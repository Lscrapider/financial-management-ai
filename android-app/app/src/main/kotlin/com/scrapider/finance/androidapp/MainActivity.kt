package com.scrapider.finance.androidapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scrapider.finance.androidapp.data.ApiClient
import com.scrapider.finance.androidapp.data.AddWatchTargetFormState
import com.scrapider.finance.androidapp.data.FinanceRepository
import com.scrapider.finance.androidapp.data.MarketAssetType
import com.scrapider.finance.androidapp.data.MarketFilter
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
import com.scrapider.finance.androidapp.ui.LoginScreen
import com.scrapider.finance.androidapp.ui.MarketScreen
import com.scrapider.finance.androidapp.ui.ObservationRiskScreen
import com.scrapider.finance.androidapp.ui.ReportResearchScreen
import com.scrapider.finance.androidapp.ui.WorkbenchScreen

private enum class AppScreen {
    Login,
    Workbench,
    Market,
    Observation,
    Report,
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

    private val apiClient = ApiClient()
    private val repository = FinanceRepository(apiClient)
    private var state by mutableStateOf(AppUiState())

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
}
