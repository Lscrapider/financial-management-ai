package com.scrapider.finance.androidapp.network;

public final class ApiConfig {
    public static final String DEFAULT_BASE_URL = "http://192.168.0.107:8081";
    public static final String LOGIN_PATH = "/api/auth/login";
    public static final String USER_INFO_PATH = "/api/user/info";
    public static final String WATCH_GROUPS_PATH = "/api/watch-pool/groups";
    public static final String WATCH_ITEMS_PATH = "/api/watch-pool/items";
    public static final String STOCK_ALERTS_PATH = "/api/stock-alerts";
    public static final String STOCK_ALERT_CHECK_PATH = "/api/stock-alerts/check";
    public static final String REPORT_TARGETS_PATH = "/api/ai/scene-analysis/tasks/reports/targets?pageNum=1&pageSize=4";
    public static final String REPORT_TARGETS_LATEST_PATH = "/api/ai/scene-analysis/tasks/reports/targets?pageNum=1&pageSize=1";
    public static final String REPORT_DETAIL_PATH_PREFIX = "/api/ai/scene-analysis/tasks/reports/";
    public static final String REPORT_REGENERATE_PATH_SUFFIX = "/report/regenerate";
    public static final String PSYCH_PROFILE_QUESTIONNAIRE_PATH = "/api/ai/investor-psych-profile/questionnaire";
    public static final String PSYCH_PROFILE_PATH = "/api/ai/investor-psych-profile";
    public static final String OCR_TASK_PAGE_PATH = "/api/ai/ocr/tasks/page";
    public static final String KNOWLEDGE_MATERIAL_TASKS_PATH = "/api/ai/knowledge-material/tasks";
    public static final String MANUAL_KNOWLEDGE_TASK_PAGE_PATH = "/api/ai/manual-knowledge/tasks/page";
    public static final String AI_CONSOLE_OVERVIEW_PATH = "/api/ai/console/overview";
    public static final String AI_TOKEN_USAGE_OVERVIEW_PATH = "/api/ai/token-usage/overview";
    public static final String MARKET_SYNC_LATEST_FULL_PATH = "/api/market-sync/jobs/latest-full";
    public static final String STOCK_QUOTES_PATH = "/api/stocks/quotes?limit=2&sortField=changePercent&sortOrder=desc";
    public static final String INDEX_QUOTES_PATH = "/api/indices/quotes?limit=1&sortField=changePercent&sortOrder=desc";
    public static final String BOND_QUOTES_PATH = "/api/bonds/quotes?limit=1&sortField=changePercent&sortOrder=desc";
    public static final int CONNECT_TIMEOUT_MS = 3500;
    public static final int READ_TIMEOUT_MS = 5000;

    private ApiConfig() {
    }
}
