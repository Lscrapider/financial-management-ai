package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.admin.AdminRepository;
import com.scrapider.finance.androidapp.admin.AdminScreenBinder;
import com.scrapider.finance.androidapp.knowledge.KnowledgeScreenBinder;
import com.scrapider.finance.androidapp.knowledge.KnowledgeTaskRepository;
import com.scrapider.finance.androidapp.market.MarketRepository;
import com.scrapider.finance.androidapp.market.MarketScreenBinder;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.profile.PsychProfileRepository;
import com.scrapider.finance.androidapp.profile.PsychProfileScreenBinder;
import com.scrapider.finance.androidapp.research.ReportDetailRepository;
import com.scrapider.finance.androidapp.research.ReportDetailScreenBinder;
import com.scrapider.finance.androidapp.research.ResearchRepository;
import com.scrapider.finance.androidapp.research.ResearchScreenBinder;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;
import com.scrapider.finance.androidapp.session.SessionController;
import com.scrapider.finance.androidapp.session.SessionState;
import com.scrapider.finance.androidapp.user.UserProfileScreenBinder;
import com.scrapider.finance.androidapp.watch.WatchRepository;
import com.scrapider.finance.androidapp.watch.WatchScreenBinder;
import com.scrapider.finance.androidapp.workbench.WorkbenchRepository;
import com.scrapider.finance.androidapp.workbench.WorkbenchScreenBinder;

final class ScreenRefreshCoordinator {
    interface Host {
        ScreenSpec currentScreen();

        boolean adminMode();

        boolean isAuthenticated();

        boolean isLoginScreen(ScreenSpec screen);

        void routeToLogin(String message);

        void applySessionState(SessionState state);

        void setStatus(String message, int color);

        void renderCurrentScreenPanel();
    }

    private final Host host;
    private final RuntimeValueStore runtimeValueStore;
    private final SessionController sessionController;
    private final WorkbenchRepository workbenchRepository;
    private final WorkbenchScreenBinder workbenchScreenBinder;
    private final MarketRepository marketRepository;
    private final MarketScreenBinder marketScreenBinder;
    private final WatchRepository watchRepository;
    private final WatchScreenBinder watchScreenBinder;
    private final ResearchRepository researchRepository;
    private final ResearchScreenBinder researchScreenBinder;
    private final ReportDetailRepository reportDetailRepository;
    private final ReportDetailScreenBinder reportDetailScreenBinder;
    private final PsychProfileRepository psychProfileRepository;
    private final PsychProfileScreenBinder psychProfileScreenBinder;
    private final KnowledgeTaskRepository knowledgeTaskRepository;
    private final KnowledgeScreenBinder knowledgeScreenBinder;
    private final AdminRepository adminRepository;
    private final AdminScreenBinder adminScreenBinder;
    private final UserProfileScreenBinder userProfileScreenBinder;

    ScreenRefreshCoordinator(
            Host host,
            RuntimeValueStore runtimeValueStore,
            SessionController sessionController,
            WorkbenchRepository workbenchRepository,
            WorkbenchScreenBinder workbenchScreenBinder,
            MarketRepository marketRepository,
            MarketScreenBinder marketScreenBinder,
            WatchRepository watchRepository,
            WatchScreenBinder watchScreenBinder,
            ResearchRepository researchRepository,
            ResearchScreenBinder researchScreenBinder,
            ReportDetailRepository reportDetailRepository,
            ReportDetailScreenBinder reportDetailScreenBinder,
            PsychProfileRepository psychProfileRepository,
            PsychProfileScreenBinder psychProfileScreenBinder,
            KnowledgeTaskRepository knowledgeTaskRepository,
            KnowledgeScreenBinder knowledgeScreenBinder,
            AdminRepository adminRepository,
            AdminScreenBinder adminScreenBinder,
            UserProfileScreenBinder userProfileScreenBinder) {
        this.host = host;
        this.runtimeValueStore = runtimeValueStore;
        this.sessionController = sessionController;
        this.workbenchRepository = workbenchRepository;
        this.workbenchScreenBinder = workbenchScreenBinder;
        this.marketRepository = marketRepository;
        this.marketScreenBinder = marketScreenBinder;
        this.watchRepository = watchRepository;
        this.watchScreenBinder = watchScreenBinder;
        this.researchRepository = researchRepository;
        this.researchScreenBinder = researchScreenBinder;
        this.reportDetailRepository = reportDetailRepository;
        this.reportDetailScreenBinder = reportDetailScreenBinder;
        this.psychProfileRepository = psychProfileRepository;
        this.psychProfileScreenBinder = psychProfileScreenBinder;
        this.knowledgeTaskRepository = knowledgeTaskRepository;
        this.knowledgeScreenBinder = knowledgeScreenBinder;
        this.adminRepository = adminRepository;
        this.adminScreenBinder = adminScreenBinder;
        this.userProfileScreenBinder = userProfileScreenBinder;
    }

    void refreshCurrentScreen() {
        ScreenSpec screen = host.currentScreen();
        if (!host.isAuthenticated() && !host.isLoginScreen(screen)) {
            host.routeToLogin("请先登录，登录后再访问：" + screen.title + "。");
            return;
        }
        if ("投资工作台".equals(screen.title)) {
            refreshWorkbenchData();
            return;
        }
        if ("行情中心".equals(screen.title)) {
            refreshMarketData();
            return;
        }
        if ("观察风控".equals(screen.title)) {
            refreshWatchData();
            return;
        }
        if ("报告研究".equals(screen.title)) {
            refreshResearchData();
            return;
        }
        if ("报告详情工作台".equals(screen.title)) {
            refreshReportDetailData();
            return;
        }
        if ("投资心理画像".equals(screen.title)) {
            refreshPsychProfileData();
            return;
        }
        if ("知识库材料".equals(screen.title)
                || "知识库管理".equals(screen.title)
                || "知识入库".equals(screen.title)) {
            refreshKnowledgeData();
            return;
        }
        if ("系统管理".equals(screen.title)) {
            refreshAdminData();
            return;
        }
        if ("我的与智能研究助手".equals(screen.title)) {
            refreshUserProfileData();
            return;
        }
        refreshUserInfoOnly();
    }

    void refreshWorkbenchData() {
        host.setStatus("正在同步投资工作台：观察池、布控提醒和报告动态。", CockpitActivity.AMBER);
        workbenchRepository.load((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                workbenchScreenBinder.apply(runtimeValueStore, summary);
            }
            host.setStatus(workbenchScreenBinder.statusMessage(result, summary), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshMarketData() {
        host.setStatus("正在同步行情中心：股票、指数和可转债报价。", CockpitActivity.AMBER);
        marketRepository.load((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                marketScreenBinder.apply(runtimeValueStore, summary);
            }
            host.setStatus(marketScreenBinder.statusMessage(result, summary), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshWatchData() {
        host.setStatus("正在同步观察风控：观察池分组、持仓标的和布控提醒。", CockpitActivity.AMBER);
        watchRepository.load((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                watchScreenBinder.apply(runtimeValueStore, summary);
            }
            host.setStatus(watchScreenBinder.statusMessage(result, summary), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshResearchData() {
        host.setStatus("正在同步报告研究：报告标的、任务状态和历史版本。", CockpitActivity.AMBER);
        researchRepository.load((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                researchScreenBinder.apply(runtimeValueStore, summary);
            }
            host.setStatus(researchScreenBinder.statusMessage(result, summary), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshReportDetailData() {
        host.setStatus("正在同步报告详情：最新报告正文、引用证据和组件状态。", CockpitActivity.AMBER);
        reportDetailRepository.loadLatest((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                reportDetailScreenBinder.apply(runtimeValueStore, summary);
            }
            host.setStatus(reportDetailScreenBinder.statusMessage(result, summary), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshPsychProfileData() {
        host.setStatus("正在同步投资心理画像：问卷、画像结果和助手建议口径。", CockpitActivity.AMBER);
        psychProfileRepository.load((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                psychProfileScreenBinder.apply(runtimeValueStore, summary);
            }
            host.setStatus(psychProfileScreenBinder.statusMessage(result, summary), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshKnowledgeData() {
        host.setStatus("正在同步知识库任务：文字识别、手动知识和入库状态。", CockpitActivity.AMBER);
        knowledgeTaskRepository.load((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                knowledgeScreenBinder.applyMaterials(runtimeValueStore, summary);
                knowledgeScreenBinder.applyManager(runtimeValueStore, summary);
                knowledgeScreenBinder.applyImport(runtimeValueStore, summary);
            }
            host.setStatus(knowledgeScreenBinder.statusMessage(result, summary, host.currentScreen().title),
                    statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshAdminData() {
        host.setStatus("正在同步系统管理：系统监控、调用用量和同步任务。", CockpitActivity.AMBER);
        adminRepository.load((result, summary) -> {
            if (result.success || summary.hasAnyData()) {
                adminScreenBinder.apply(runtimeValueStore, summary);
            }
            host.setStatus(adminScreenBinder.statusMessage(result, summary), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    void refreshUserProfileData() {
        host.setStatus("正在同步个人中心：用户信息、权限和助手上下文。", CockpitActivity.AMBER);
        sessionController.refreshUserInfo((result, state) -> {
            if (result.success) {
                host.applySessionState(state);
            }
            userProfileScreenBinder.apply(runtimeValueStore, state);
            host.setStatus(statusMessage(result), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    int statusColor(ApiResult result) {
        if (result.success) {
            return CockpitActivity.GREEN;
        }
        if (result.statusCode == 0 || result.statusCode == 403) {
            return CockpitActivity.RED;
        }
        return result.backendReachable() ? CockpitActivity.AMBER : CockpitActivity.MUTED;
    }

    private void refreshUserInfoOnly() {
        host.setStatus("正在连接后端：" + ApiConfig.DEFAULT_BASE_URL + ApiConfig.USER_INFO_PATH + "。",
                CockpitActivity.AMBER);
        sessionController.refreshUserInfo((result, state) -> {
            if (result.success) {
                host.applySessionState(state);
            }
            host.setStatus(statusMessage(result), statusColor(result));
            host.renderCurrentScreenPanel();
        });
    }

    private String statusMessage(ApiResult result) {
        if (result.success) {
            return "已刷新：" + host.currentScreen().title + "，后端用户信息同步完成。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }
}
