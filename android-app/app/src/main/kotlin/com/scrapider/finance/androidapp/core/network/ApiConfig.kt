package com.scrapider.finance.androidapp.core.network

object ApiConfig {
    const val DEFAULT_BASE_URL = "https://ali-finance.scrapider.cloud/finance-api"
    const val LOGIN_PATH = "/api/auth/login"
    const val REFRESH_PATH = "/api/auth/refresh"
    const val USER_INFO_PATH = "/api/user/info"
    const val USER_PASSWORD_PATH = "/api/user/password"
    const val USER_NOTIFICATION_PATH = "/api/user/notification"
    const val WATCH_GROUPS_PATH = "/api/watch-pool/groups"
    const val WATCH_ITEMS_PATH = "/api/watch-pool/items"
    const val STOCK_ALERTS_PATH = "/api/stock-alerts"
    const val STOCK_ALERT_TARGET_OPTIONS_PATH = "/api/stock-alerts/target-options"
    const val STOCK_QUOTES_PATH = "/api/stocks/quotes"
    const val INDEX_QUOTES_PATH = "/api/indices/quotes"
    const val BOND_QUOTES_PATH = "/api/bonds/quotes"
    const val REPORT_TARGETS_PATH = "/api/ai/scene-analysis/tasks/reports/targets"
    const val CONNECT_TIMEOUT_MS = 3_500L
    const val READ_TIMEOUT_MS = 5_000L
}
