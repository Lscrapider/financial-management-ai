package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.action.ActionResult;
import com.scrapider.finance.androidapp.action.ScreenActionRepository;
import com.scrapider.finance.androidapp.auth.AuthLoginAction;
import com.scrapider.finance.androidapp.auth.AuthPageSpecFactory;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;
import com.scrapider.finance.androidapp.session.SessionController;
import com.scrapider.finance.androidapp.session.SessionState;

import org.json.JSONException;
import org.json.JSONObject;

final class ScreenActionCoordinator {
    interface Host {
        ScreenSpec currentScreen();

        boolean adminMode();

        boolean loginAsAdmin();

        void applySessionState(SessionState state);

        void openDefaultWorkbench();

        void navigateToScreen(String title);

        void setStatus(String message, int color);

        void renderCurrentScreenPanel();

        void hideDrawer();
    }

    private final Host host;
    private final SessionController sessionController;
    private final ScreenActionRepository screenActionRepository;
    private final ScreenRefreshCoordinator refreshCoordinator;
    private final FormStateStore formStateStore;
    private final RuntimeValueStore runtimeValueStore;
    private final AuthLoginAction authLoginAction = new AuthLoginAction();

    ScreenActionCoordinator(
            Host host,
            SessionController sessionController,
            ScreenActionRepository screenActionRepository,
            ScreenRefreshCoordinator refreshCoordinator,
            FormStateStore formStateStore,
            RuntimeValueStore runtimeValueStore) {
        this.host = host;
        this.sessionController = sessionController;
        this.screenActionRepository = screenActionRepository;
        this.refreshCoordinator = refreshCoordinator;
        this.formStateStore = formStateStore;
        this.runtimeValueStore = runtimeValueStore;
    }

    void handle(RowSpec row) {
        if (ScreenActionRouter.isAction(row, AuthPageSpecFactory.ACTION_LOGIN)) {
            host.hideDrawer();
            performLogin();
            return;
        }
        if (ScreenActionRouter.isAction(row, "refresh")) {
            host.hideDrawer();
            refreshCoordinator.refreshCurrentScreen();
            return;
        }
        if (ScreenActionRouter.isAction(row, "checkAlerts")) {
            host.hideDrawer();
            performCheckAlerts();
            return;
        }
        if (ScreenActionRouter.isAction(row, "saveWatchItem")) {
            host.hideDrawer();
            performSaveWatchItem();
            return;
        }
        if (ScreenActionRouter.isAction(row, "saveStockAlert")) {
            host.hideDrawer();
            performSaveStockAlert();
            return;
        }
        if (ScreenActionRouter.isAction(row, "regenerateReport")) {
            host.hideDrawer();
            performRegenerateReport();
            return;
        }
        if (ScreenActionRouter.isAction(row, "submitKnowledgeMaterial")) {
            host.hideDrawer();
            performSubmitKnowledgeMaterial();
            return;
        }
        String target = ScreenActionRouter.targetScreenFor(row);
        if (target != null) {
            host.navigateToScreen(target);
            host.setStatus("已跳转到：" + target + "。", CockpitActivity.BLUE);
            host.hideDrawer();
            return;
        }
        host.setStatus("已完成操作：" + row.label + "。", CockpitActivity.GREEN);
        host.hideDrawer();
    }

    private void performLogin() {
        String username = formStateStore.value(AuthPageSpecFactory.SCREEN_TITLE, AuthPageSpecFactory.FIELD_USERNAME);
        String password = formStateStore.value(AuthPageSpecFactory.SCREEN_TITLE, AuthPageSpecFactory.FIELD_PASSWORD);
        authLoginAction.execute(sessionController, username, password, host.loginAsAdmin(), new AuthLoginAction.Host() {
            @Override
            public void showLoginProgress(String message) {
                host.setStatus(message, CockpitActivity.AMBER);
            }

            @Override
            public void showLoginSuccess(String message) {
                host.setStatus(message, CockpitActivity.GREEN);
            }

            @Override
            public void showLoginFailure(String message, ApiResult result) {
                host.setStatus(message, refreshCoordinator.statusColor(result));
            }

            @Override
            public void applySessionState(SessionState state) {
                host.applySessionState(state);
            }

            @Override
            public void openDefaultWorkbench() {
                host.openDefaultWorkbench();
            }

            @Override
            public void refreshWorkbenchData() {
                refreshCoordinator.refreshWorkbenchData();
            }

            @Override
            public void renderCurrentScreenPanel() {
                host.renderCurrentScreenPanel();
            }
        });
    }

    private void performCheckAlerts() {
        if (!host.adminMode()) {
            host.setStatus("当前账号没有管理员权限，不能触发布控提醒手动检查。", CockpitActivity.RED);
            return;
        }
        host.setStatus("正在触发布控提醒手动检查。", CockpitActivity.AMBER);
        screenActionRepository.checkAlerts(result -> {
            host.setStatus(result.message, resultColor(result));
            if (result.success) {
                refreshCoordinator.refreshWatchData();
            }
        });
    }

    private void performSaveWatchItem() {
        String groupName = formValue("所属分组", "");
        String groupId = groupIdFor(groupName);
        TargetInput target = parseTarget(formValue("搜索选择", ""));
        if (groupName.isEmpty()) {
            host.setStatus("请选择或输入观察分组。", CockpitActivity.RED);
            return;
        }
        if (!target.valid()) {
            host.setStatus("请输入标的名称和代码，例如：宁德时代 300750。", CockpitActivity.RED);
            return;
        }
        if (groupId.isEmpty()) {
            host.setStatus("正在创建观察分组：" + groupName + "。", CockpitActivity.AMBER);
            screenActionRepository.saveWatchGroup(groupName, result -> {
                if (!result.success) {
                    host.setStatus(result.message, resultColor(result));
                    return;
                }
                submitWatchItem(result.value, target);
            });
            return;
        }
        submitWatchItem(groupId, target);
    }

    private void submitWatchItem(String groupId, TargetInput target) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("groupId", groupId);
            payload.put("targetType", normalizeTargetType(formValue("标的类型", ""), target.code));
            payload.put("targetCode", target.code);
            payload.put("targetName", target.name);
            payload.put("buyPrice", parseDecimal(formValue("买入价", "0")));
            payload.put("position", parseDecimal(formValue("持仓数量", "0")));
            payload.put("remark", formValue("备注", ""));
        } catch (JSONException exception) {
            host.setStatus("观察池保存参数组装失败。", CockpitActivity.RED);
            return;
        }
        host.setStatus("正在保存观察池标的。", CockpitActivity.AMBER);
        screenActionRepository.saveWatchItem(payload, result -> {
            host.setStatus(result.message, resultColor(result));
            if (result.success) {
                refreshCoordinator.refreshWatchData();
            }
        });
    }

    private void performSaveStockAlert() {
        TargetInput target = parseTarget(formValue("提醒标的", ""));
        if (!target.valid()) {
            host.setStatus("请输入提醒标的名称和代码，例如：宁德时代 300750。", CockpitActivity.RED);
            return;
        }
        double threshold = parseDecimal(formValue("阈值编辑", "0"));
        if (threshold <= 0) {
            host.setStatus("请输入大于 0 的涨跌幅阈值。", CockpitActivity.RED);
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("targetType", normalizeTargetType(formValue("提醒类型", ""), target.code));
            payload.put("stockCode", target.code);
            payload.put("thresholdPercent", threshold);
            payload.put("enabled", parseEnabled(formValue("启用状态", "已开启提醒")));
            payload.put("emailNotification", parseEnabled(formValue("邮箱通知", "未开启邮箱提醒")));
        } catch (JSONException exception) {
            host.setStatus("布控提醒保存参数组装失败。", CockpitActivity.RED);
            return;
        }
        host.setStatus("正在保存布控提醒。", CockpitActivity.AMBER);
        screenActionRepository.saveStockAlert(payload, result -> {
            host.setStatus(result.message, resultColor(result));
            if (result.success) {
                refreshCoordinator.refreshWatchData();
            }
        });
    }

    private void performRegenerateReport() {
        host.setStatus("正在基于最新报告任务提交重新生成。", CockpitActivity.AMBER);
        screenActionRepository.regenerateLatestReport(result -> {
            host.setStatus(result.message, resultColor(result));
            if (!result.success) {
                return;
            }
            runtimeValueStore.put("报告研究", "任务轮询状态", "生成中", "1 项");
            runtimeValueStore.putTone("报告研究", "任务轮询状态", "生成中", "amber");
            if ("报告详情工作台".equals(host.currentScreen().title)) {
                refreshCoordinator.refreshReportDetailData();
            } else {
                refreshCoordinator.refreshResearchData();
            }
        });
    }

    private void performSubmitKnowledgeMaterial() {
        String queryText = formStateStore.value("知识库材料", "查询输入");
        int totalChunks = parsePositiveInt(formStateStore.value("知识库材料", "召回条数"), 12);
        host.setStatus("正在提交知识库材料检索任务。", CockpitActivity.AMBER);
        screenActionRepository.submitKnowledgeMaterial(queryText, totalChunks, result -> {
            host.setStatus(result.message, resultColor(result));
            if (!result.success) {
                return;
            }
            runtimeValueStore.put("知识库材料", "模式与配置", "任务状态", "处理中");
            runtimeValueStore.putTone("知识库材料", "模式与配置", "任务状态", "amber");
            runtimeValueStore.put("知识库材料", "任务状态", "轮询状态",
                    result.value.isEmpty() ? "材料检索任务已提交，稍后手动刷新。" : "任务 " + result.value + " 已提交，稍后手动刷新。");
            host.renderCurrentScreenPanel();
        });
    }

    private int resultColor(ActionResult result) {
        if (result.success) {
            return CockpitActivity.GREEN;
        }
        if (result.statusCode == 0 || result.statusCode == 403) {
            return CockpitActivity.RED;
        }
        return result.statusCode > 0 ? CockpitActivity.AMBER : CockpitActivity.MUTED;
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String formValue(String label, String fallback) {
        ScreenSpec screen = host.currentScreen();
        String screenTitle = screen == null ? "" : screen.title;
        if (formStateStore.hasValue(screenTitle, label)) {
            return safe(formStateStore.value(screenTitle, label), fallback);
        }
        return safe(runtimeValueStore.valueFor(screen, new RowSpec(label, fallback, "field")), fallback);
    }

    private String groupIdFor(String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return "";
        }
        ScreenSpec screen = host.currentScreen();
        return safe(runtimeValueStore.valueFor(screen, new RowSpec("分组ID:" + groupName.trim(), "", "field")), "");
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return fallback;
        }
        return value.trim();
    }

    private double parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        String normalized = value.replace("%", "").replace(",", "").trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private boolean parseEnabled(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.contains("已") || normalized.equalsIgnoreCase("true") || normalized.equals("1");
    }

    private String normalizeTargetType(String value, String code) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.contains("BOND") || normalized.contains("债") || code.startsWith("11") || code.startsWith("12")) {
            return "BOND";
        }
        if (normalized.contains("INDEX") || normalized.contains("指数")) {
            return "INDEX";
        }
        if (normalized.contains("FUND") || normalized.contains("基金")) {
            return "FUND";
        }
        if (normalized.contains("SECTOR") || normalized.contains("板块")) {
            return "SECTOR";
        }
        return "STOCK";
    }

    private TargetInput parseTarget(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return new TargetInput("", "");
        }
        String[] parts = text.split("\\s+");
        String code = "";
        if (parts.length > 1) {
            code = parts[parts.length - 1].replaceAll("[^A-Za-z0-9.]", "");
        }
        if (code.isEmpty()) {
            code = text.replaceAll("[^0-9A-Za-z.]", "");
        }
        String name = text;
        if (!code.isEmpty() && name.endsWith(code)) {
            name = name.substring(0, name.length() - code.length()).trim();
        }
        return new TargetInput(name, code);
    }

    private static final class TargetInput {
        private final String name;
        private final String code;

        private TargetInput(String name, String code) {
            this.name = name == null ? "" : name;
            this.code = code == null ? "" : code;
        }

        private boolean valid() {
            return !name.isEmpty() && !code.isEmpty();
        }
    }
}
