package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.action.ScreenActionRepository;
import com.scrapider.finance.androidapp.admin.AdminRepository;
import com.scrapider.finance.androidapp.admin.AdminScreenBinder;
import com.scrapider.finance.androidapp.auth.AuthPageBinder;
import com.scrapider.finance.androidapp.auth.AuthPageSpecFactory;
import com.scrapider.finance.androidapp.knowledge.KnowledgeScreenBinder;
import com.scrapider.finance.androidapp.knowledge.KnowledgeTaskRepository;
import com.scrapider.finance.androidapp.market.MarketRepository;
import com.scrapider.finance.androidapp.market.MarketScreenBinder;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.profile.PsychProfileRepository;
import com.scrapider.finance.androidapp.profile.PsychProfileScreenBinder;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;
import com.scrapider.finance.androidapp.research.ResearchRepository;
import com.scrapider.finance.androidapp.research.ResearchScreenBinder;
import com.scrapider.finance.androidapp.research.ReportDetailRepository;
import com.scrapider.finance.androidapp.research.ReportDetailScreenBinder;
import com.scrapider.finance.androidapp.session.SessionController;
import com.scrapider.finance.androidapp.session.SessionState;
import com.scrapider.finance.androidapp.user.UserProfileScreenBinder;
import com.scrapider.finance.androidapp.watch.WatchRepository;
import com.scrapider.finance.androidapp.watch.WatchScreenBinder;
import com.scrapider.finance.androidapp.workbench.WorkbenchRepository;
import com.scrapider.finance.androidapp.workbench.WorkbenchScreenBinder;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class MainActivity extends CockpitActivity {
    private static final String HIDDEN_SCREEN_MARKET = "行情中心";

    private ScreenRegistry screenRegistry;
    private BlockRenderer blockRenderer;
    private DrawerController drawerController;
    private ShellController shellController;
    private ScreenPanelRenderer screenPanelRenderer;
    private LoginPageController loginPageController;
    private ApiClient apiClient;
    private SessionController sessionController;
    private ScreenActionRepository screenActionRepository;
    private ScreenActionCoordinator actionCoordinator;
    private ScreenRefreshCoordinator refreshCoordinator;
    private WorkbenchRepository workbenchRepository;
    private WorkbenchScreenBinder workbenchScreenBinder;
    private MarketRepository marketRepository;
    private MarketScreenBinder marketScreenBinder;
    private WatchRepository watchRepository;
    private WatchScreenBinder watchScreenBinder;
    private ResearchRepository researchRepository;
    private ResearchScreenBinder researchScreenBinder;
    private ReportDetailRepository reportDetailRepository;
    private ReportDetailScreenBinder reportDetailScreenBinder;
    private PsychProfileRepository psychProfileRepository;
    private PsychProfileScreenBinder psychProfileScreenBinder;
    private KnowledgeTaskRepository knowledgeTaskRepository;
    private KnowledgeScreenBinder knowledgeScreenBinder;
    private AdminRepository adminRepository;
    private AdminScreenBinder adminScreenBinder;
    private UserProfileScreenBinder userProfileScreenBinder;
    private AuthPageBinder authPageBinder;
    private FormStateStore formStateStore;
    private RuntimeValueStore runtimeValueStore;
    private String currentNav = "我的";
    private ScreenSpec currentScreen;
    private boolean adminMode = false;
    private boolean loginAsAdmin = true;
    private boolean appShellVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        screenRegistry = new ScreenRegistry();
        formStateStore = new FormStateStore();
        runtimeValueStore = new RuntimeValueStore();
        blockRenderer = new BlockRenderer(this, this::showDrawer, formStateStore, runtimeValueStore);
        apiClient = new ApiClient();
        sessionController = new SessionController(apiClient);
        screenActionRepository = new ScreenActionRepository(apiClient);
        workbenchRepository = new WorkbenchRepository(apiClient);
        workbenchScreenBinder = new WorkbenchScreenBinder();
        marketRepository = new MarketRepository(apiClient);
        marketScreenBinder = new MarketScreenBinder();
        watchRepository = new WatchRepository(apiClient);
        watchScreenBinder = new WatchScreenBinder();
        researchRepository = new ResearchRepository(apiClient);
        researchScreenBinder = new ResearchScreenBinder();
        reportDetailRepository = new ReportDetailRepository(apiClient);
        reportDetailScreenBinder = new ReportDetailScreenBinder();
        psychProfileRepository = new PsychProfileRepository(apiClient);
        psychProfileScreenBinder = new PsychProfileScreenBinder();
        knowledgeTaskRepository = new KnowledgeTaskRepository(apiClient);
        knowledgeScreenBinder = new KnowledgeScreenBinder();
        adminRepository = new AdminRepository(apiClient);
        adminScreenBinder = new AdminScreenBinder();
        userProfileScreenBinder = new UserProfileScreenBinder();
        authPageBinder = new AuthPageBinder();
        loginPageController = new LoginPageController(this, formStateStore, new LoginPageController.Listener() {
            @Override
            public void toggleRole() {
                MainActivity.this.toggleRole();
            }

            @Override
            public void submitLogin() {
                MainActivity.this.submitStandaloneLogin();
            }
        });
        refreshCoordinator = new ScreenRefreshCoordinator(new ScreenRefreshCoordinator.Host() {
            @Override
            public ScreenSpec currentScreen() {
                return MainActivity.this.currentScreen;
            }

            @Override
            public boolean adminMode() {
                return MainActivity.this.adminMode;
            }

            @Override
            public boolean isAuthenticated() {
                return MainActivity.this.isAuthenticated();
            }

            @Override
            public boolean isLoginScreen(ScreenSpec screen) {
                return MainActivity.this.isLoginScreen(screen);
            }

            @Override
            public void routeToLogin(String message) {
                MainActivity.this.routeToLogin(message);
            }

            @Override
            public void applySessionState(SessionState state) {
                MainActivity.this.applySessionState(state);
            }

            @Override
            public void setStatus(String message, int color) {
                MainActivity.this.setStatus(message, color);
            }

            @Override
            public void renderCurrentScreenPanel() {
                MainActivity.this.renderCurrentScreenPanel();
            }
        }, runtimeValueStore, sessionController,
                workbenchRepository, workbenchScreenBinder,
                marketRepository, marketScreenBinder,
                watchRepository, watchScreenBinder,
                researchRepository, researchScreenBinder,
                reportDetailRepository, reportDetailScreenBinder,
                psychProfileRepository, psychProfileScreenBinder,
                knowledgeTaskRepository, knowledgeScreenBinder,
                adminRepository, adminScreenBinder,
                userProfileScreenBinder);
        actionCoordinator = new ScreenActionCoordinator(new ScreenActionCoordinator.Host() {
            @Override
            public ScreenSpec currentScreen() {
                return MainActivity.this.currentScreen;
            }

            @Override
            public boolean adminMode() {
                return MainActivity.this.adminMode;
            }

            @Override
            public boolean loginAsAdmin() {
                return MainActivity.this.loginAsAdmin;
            }

            @Override
            public void applySessionState(SessionState state) {
                MainActivity.this.applySessionState(state);
            }

            @Override
            public void openDefaultWorkbench() {
                MainActivity.this.openDefaultWorkbench();
            }

            @Override
            public void navigateToScreen(String title) {
                MainActivity.this.navigateToScreen(title);
            }

            @Override
            public void setStatus(String message, int color) {
                MainActivity.this.setStatus(message, color);
            }

            @Override
            public void renderCurrentScreenPanel() {
                MainActivity.this.renderCurrentScreenPanel();
            }

            @Override
            public void hideDrawer() {
                MainActivity.this.hideDrawer();
            }
        }, sessionController, screenActionRepository, refreshCoordinator, formStateStore, runtimeValueStore);
        drawerController = new DrawerController(this, blockRenderer, actionCoordinator::handle);
        shellController = new ShellController(this, new ShellController.Listener() {
            @Override
            public void refreshCurrentScreen() {
                MainActivity.this.refreshCurrentScreen();
            }

            @Override
            public void selectNav(String nav) {
                MainActivity.this.selectNav(nav);
            }
        });
        screenPanelRenderer = new ScreenPanelRenderer(this, blockRenderer, formStateStore, runtimeValueStore, new ScreenPanelRenderer.Listener() {
            @Override
            public void openDrawer(String title, RowSpec row) {
                MainActivity.this.showDrawer(title, row);
            }

            @Override
            public void backToMine() {
                MainActivity.this.backToMine();
            }
        });
        currentScreen = loginScreen();
        showLoginPage("请登录后进入投资工作台。", MUTED);
    }

    private void buildAppShell() {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(BACKGROUND);
        shell.setLayoutParams(matchParent());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        shell.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(shellController.buildTopBar());
        root.addView(shellController.buildContentScroll());
        root.addView(shellController.buildBottomNav(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        shell.addView(drawerController.buildOverlay());

        setContentView(shell);
        appShellVisible = true;
        renderAll();
    }

    @Override
    protected void onDestroy() {
        if (apiClient != null) {
            apiClient.shutdown();
        }
        super.onDestroy();
    }

    private void renderAll() {
        if (!appShellVisible) {
            return;
        }
        blockRenderer.setAdminMode(adminMode);
        shellController.renderTop(currentScreen);
        applyLoginRoleUi();
        renderCurrentScreenPanel();
        shellController.renderBottomNav(currentNav);
    }

    private void renderCurrentScreenPanel() {
        if (!appShellVisible) {
            return;
        }
        screenPanelRenderer.render(shellController.content(), currentScreen, adminMode);
    }

    @Override
    protected void setStatus(String message, int color) {
        if (!appShellVisible && loginPageController != null) {
            loginPageController.setStatus(message, color);
            return;
        }
        if (shellController == null) {
            return;
        }
        shellController.setStatus(message, color);
    }

    private void showDrawer(String title, RowSpec row) {
        if (row.actionKey != null) {
            actionCoordinator.handle(row);
            return;
        }
        drawerController.show(currentScreen, title, row);
    }

    private void toggleRole() {
        if (!isAuthenticated() || !appShellVisible || isLoginScreen(currentScreen)) {
            loginAsAdmin = !loginAsAdmin;
            showLoginPage(loginAsAdmin ? "登录角色已选择管理员，登录后由后端角色确认权限。" : "登录角色已选择普通用户。",
                    loginAsAdmin ? AMBER : MUTED);
            return;
        }
        setStatus("当前权限由已登录账号决定，不能在本地切换角色。", adminMode ? AMBER : MUTED);
    }

    private void refreshCurrentScreen() {
        refreshCoordinator.refreshCurrentScreen();
    }

    private void renderAllAndRefreshCurrent() {
        renderAll();
        if (isAuthenticated() && !isLoginScreen(currentScreen)) {
            refreshCoordinator.refreshCurrentScreen();
        }
    }

    private void selectNav(String nav) {
        if (isHiddenNav(nav)) {
            setStatus("行情页已暂时隐藏，优先使用观察、研究和知识库功能。", MUTED);
            return;
        }
        if (!isAuthenticated() && !"我的".equals(nav)) {
            routeToLogin("请先登录，登录后进入：" + nav + "。");
            return;
        }
        currentNav = nav;
        currentScreen = !isAuthenticated() && "我的".equals(nav)
                ? loginScreen()
                : screenRegistry.defaultForNav(nav, adminMode, currentScreen);
        setStatus("已进入导航：" + nav + "。", BLUE);
        renderAllAndRefreshCurrent();
    }

    private void backToMine() {
        currentNav = "我的";
        currentScreen = screenRegistry.defaultForNav("我的", adminMode, currentScreen);
        renderAllAndRefreshCurrent();
    }

    private void applySessionState(SessionState state) {
        adminMode = state.isAdmin();
        if (!adminMode && currentScreen.admin) {
            currentScreen = screenRegistry.defaultForNav(currentNav, false, currentScreen);
        }
    }

    private void openDefaultWorkbench() {
        currentNav = "工作台";
        currentScreen = screenRegistry.defaultForNav(currentNav, adminMode, currentScreen);
        buildAppShell();
    }

    private void hideDrawer() {
        if (drawerController != null) {
            drawerController.hide();
        }
    }

    private void navigateToScreen(String title) {
        if (isHiddenScreenTitle(title)) {
            setStatus("行情页已暂时隐藏，优先使用观察、研究和知识库功能。", MUTED);
            return;
        }
        if (AuthPageSpecFactory.SCREEN_TITLE.equals(title)) {
            currentScreen = loginScreen();
            showLoginPage("已打开独立登录页，可重新提交账号。", AMBER);
            return;
        }
        for (ScreenSpec screen : screenRegistry.all()) {
            if (screen.title.equals(title)) {
                if (!canOpenScreen(screen)) {
                    return;
                }
                currentScreen = screen;
                currentNav = screen.nav;
                renderAllAndRefreshCurrent();
                return;
            }
        }
    }

    private boolean canOpenScreen(ScreenSpec screen) {
        if (screen == null) {
            return false;
        }
        if (isHiddenScreenTitle(screen.title)) {
            setStatus("行情页已暂时隐藏，优先使用观察、研究和知识库功能。", MUTED);
            return false;
        }
        if (!isAuthenticated() && !isLoginScreen(screen)) {
            routeToLogin("请先登录，登录后再访问：" + screen.title + "。");
            return false;
        }
        if (screen.admin && !adminMode) {
            setStatus("当前账号没有管理员权限，已隐藏该管理页面。", RED);
            currentNav = "我的";
            currentScreen = screenRegistry.defaultForNav("我的", false, currentScreen);
            renderAll();
            return false;
        }
        return true;
    }

    private boolean isHiddenNav(String nav) {
        return "行情".equals(nav);
    }

    private boolean isHiddenScreenTitle(String title) {
        return HIDDEN_SCREEN_MARKET.equals(title);
    }

    private void routeToLogin(String message) {
        appShellVisible = false;
        currentNav = "我的";
        currentScreen = loginScreen();
        showLoginPage(message, AMBER);
    }

    private void showLoginPage(String message, int color) {
        appShellVisible = false;
        setContentView(loginPageController.build(loginAsAdmin));
        loginPageController.setStatus(message, color);
    }

    private void submitStandaloneLogin() {
        actionCoordinator.handle(new RowSpec("登录按钮", "登录", "primary", false, null, true,
                AuthPageSpecFactory.ACTION_LOGIN));
    }

    private ScreenSpec loginScreen() {
        ScreenSpec login = screenRegistry.findByTitle(AuthPageSpecFactory.SCREEN_TITLE);
        return login == null ? screenRegistry.defaultForNav("我的", false, null) : login;
    }

    private boolean isLoginScreen(ScreenSpec screen) {
        return screen != null && AuthPageSpecFactory.SCREEN_TITLE.equals(screen.title);
    }

    private boolean isAuthenticated() {
        return sessionController != null && sessionController.state().authenticated;
    }

    private void applyLoginRoleUi() {
        if (!isLoginScreen(currentScreen)) {
            return;
        }
        authPageBinder.applyLoginRole(runtimeValueStore, loginAsAdmin);
    }
}
