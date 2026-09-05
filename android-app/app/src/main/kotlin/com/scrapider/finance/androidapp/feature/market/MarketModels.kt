package com.scrapider.finance.androidapp.feature.market

import androidx.compose.runtime.Immutable
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import java.util.Locale

const val MARKET_OVERVIEW_ITEM_LIMIT = 3

// 后端行情查询接口当前支持的单类最大条数，用于补全系统标的的实时行情。
const val SYSTEM_TARGET_QUOTE_REQUEST_LIMIT = 500

// 与 Web 端已有的预警阈值输入约束保持一致。
const val DEFAULT_ALERT_THRESHOLD_PERCENT = 5.0
const val MIN_ALERT_THRESHOLD_PERCENT = 0.01
const val MAX_ALERT_THRESHOLD_PERCENT = 100.0
const val ALERT_THRESHOLD_STEP_PERCENT = 0.5

@Immutable
data class MarketUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val groups: List<MarketWatchGroup> = emptyList(),
    val systemTargets: List<MarketSystemTarget> = emptyList(),
    val marketOverview: List<MarketIndexQuote> = emptyList(),
    val alerts: List<MarketAlert> = emptyList(),
    val selectedGroupId: String? = null,
    val targetTypeFilter: MarketTargetTypeFilter = MarketTargetTypeFilter.All,
    val sortOption: MarketSortOption = MarketSortOption.Default,
    val destination: MarketDestination = MarketDestination.List,
    val backStack: List<MarketDestination> = emptyList(),
    val searchQuery: String = "",
    val isLoadingTargetOptions: Boolean = false,
    val targetOptions: List<MarketTargetOption> = emptyList(),
    val syncMessage: String = "",
)

sealed interface MarketDestination {
    data object List : MarketDestination

    data object ManageGroups : MarketDestination

    data class AddTargets(val groupId: String) : MarketDestination

    data class TargetSettings(val itemId: String) : MarketDestination

    data object Search : MarketDestination

    data class TargetDetail(
        val targetType: String,
        val targetCode: String,
        val watchItemId: String? = null,
    ) : MarketDestination
}

enum class MarketTargetTypeFilter(
    val label: String,
    private val targetType: String?,
) {
    All(label = "全部", targetType = null),
    Stock(label = "A股", targetType = "STOCK"),
    Index(label = "指数", targetType = "INDEX"),
    Bond(label = "可转债", targetType = "BOND"),
    ;

    fun includes(value: String): Boolean = targetType == null || targetType.equals(value, ignoreCase = true)
}

enum class MarketSortOption(
    val label: String,
) {
    Default(label = "默认"),
    ChangeDescending(label = "涨跌幅：高到低"),
    ChangeAscending(label = "涨跌幅：低到高"),
    PriceDescending(label = "最新价：高到低"),
    PriceAscending(label = "最新价：低到高"),
}

@Immutable
data class MarketContent(
    val groups: List<MarketWatchGroup>,
    val systemTargets: List<MarketSystemTarget>,
    val marketOverview: List<MarketIndexQuote>,
    val alerts: List<MarketAlert>,
    val partialFailure: NetworkFailure? = null,
)

@Immutable
data class MarketWatchGroup(
    val id: String,
    val name: String,
    val items: List<MarketWatchItem>,
)

@Immutable
data class MarketWatchItem(
    val id: String,
    val groupId: String,
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val secid: String?,
    val remark: String?,
    val buyPrice: Double?,
    val position: Double?,
    val latestPrice: Double?,
    val changePercent: Double?,
) {
    val targetKey: String
        get() = targetType + ":" + targetCode
}

/** 系统已启用的市场标的，不携带任何用户自选、提醒或个人记录信息。 */
@Immutable
data class MarketSystemTarget(
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val latestPrice: Double?,
    val changePercent: Double?,
) {
    val targetKey: String
        get() = targetType + ":" + targetCode
}

@Immutable
data class MarketIndexQuote(
    val code: String,
    val name: String,
    val latestPrice: Double?,
    val changePercent: Double?,
)

@Immutable
data class MarketAlert(
    val id: String,
    val targetType: String,
    val targetCode: String,
    val thresholdPercent: Double?,
    val enabled: Boolean,
    val outOfThreshold: Boolean,
) {
    val targetKey: String
        get() = "$targetType:$targetCode"
}

@Immutable
data class MarketTargetOption(
    val targetType: String,
    val targetCode: String,
    val targetName: String,
) {
    val targetKey: String
        get() = "$targetType:$targetCode"
}

@Immutable
data class MarketTargetSnapshot(
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val latestPrice: Double?,
    val changePercent: Double?,
    val watchItemId: String?,
    val watchGroupName: String?,
    val buyPrice: Double?,
    val position: Double?,
    val remark: String?,
    val alert: MarketAlert?,
) {
    val targetKey: String
        get() = targetType + ":" + targetCode
}

@Immutable
data class MarketTargetSettingsInput(
    val item: MarketWatchItem,
    val alert: MarketAlert?,
    val alertEnabled: Boolean,
    val thresholdPercent: Double?,
    val buyPrice: Double?,
    val position: Double?,
    val remark: String?,
)

sealed interface MarketEvent {
    data object SessionExpired : MarketEvent

    data class Notice(val message: String) : MarketEvent

    data object GroupSaved : MarketEvent
}

fun String.marketTargetTypeLabel(): String = when (uppercase(Locale.ROOT)) {
    "STOCK" -> "A股"
    "INDEX" -> "指数"
    "BOND" -> "可转债"
    "FUND" -> "基金"
    "SECTOR" -> "板块"
    else -> ifBlank { "标的" }
}

fun String.supportsAlert(): Boolean = uppercase(Locale.ROOT) in ALERT_SUPPORTED_TARGET_TYPES

val systemTargetTypes = listOf("STOCK", "INDEX", "BOND")

private val ALERT_SUPPORTED_TARGET_TYPES = systemTargetTypes.toSet()
