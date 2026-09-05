package com.scrapider.finance.androidapp.feature.market

import com.scrapider.finance.androidapp.core.network.ApiConfig
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toEnvelope
import com.scrapider.finance.androidapp.core.network.toJsonArrayPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

class MarketRepository(
    private val apiClient: FinanceApiClient,
) {
    suspend fun load(): NetworkResult<MarketContent> = coroutineScope {
        val groupsRequest = async { loadGroups() }
        val systemTargetsRequest = async { loadSystemTargets() }
        val overviewRequest = async { loadMarketOverview() }
        val alertsRequest = async { loadAlerts() }

        val groupsResult = groupsRequest.await()
        val systemTargetsResult = systemTargetsRequest.await()
        val overviewResult = overviewRequest.await()
        val alertsResult = alertsRequest.await()
        val failures = listOf(groupsResult, systemTargetsResult, overviewResult, alertsResult)
            .mapNotNull { result -> (result as? NetworkResult.Failure)?.reason }

        if (failures.size == MARKET_DATA_SOURCE_COUNT) {
            return@coroutineScope NetworkResult.Failure(failures.first())
        }

        NetworkResult.Success(
            MarketContent(
                groups = (groupsResult as? NetworkResult.Success)?.data.orEmpty(),
                systemTargets = (systemTargetsResult as? NetworkResult.Success)?.data.orEmpty(),
                marketOverview = (overviewResult as? NetworkResult.Success)?.data.orEmpty(),
                alerts = (alertsResult as? NetworkResult.Success)?.data.orEmpty(),
                partialFailure = failures.firstOrNull(),
            ),
        )
    }

    suspend fun loadTargetOptions(): NetworkResult<List<MarketTargetOption>> = coroutineScope {
        val requests = systemTargetTypes.map { type ->
            async { loadTargetOptions(type) }
        }
        val results = requests.map { request -> request.await() }
        val failures = results.mapNotNull { result -> (result as? NetworkResult.Failure)?.reason }
        if (failures.size == results.size) {
            return@coroutineScope NetworkResult.Failure(failures.first())
        }

        NetworkResult.Success(
            results.flatMap { result -> (result as? NetworkResult.Success)?.data.orEmpty() }
                .distinctBy { option -> option.targetType + ":" + option.targetCode },
        )
    }

    suspend fun saveGroup(
        groupId: String?,
        name: String,
    ): NetworkResult<MarketWatchGroup> {
        val payload = JSONObject().put("groupName", name)
        groupId?.toLongOrNull()?.let { payload.put("id", it) }
        return apiClient.postJson(ApiConfig.WATCH_GROUPS_PATH, payload)
            .toEnvelope()
            .mapPayload(::parseSavedGroup)
    }

    suspend fun deleteGroup(groupId: String): NetworkResult<Unit> =
        apiClient.delete(ApiConfig.WATCH_GROUPS_PATH + "/" + groupId)
            .toEnvelope()
            .toUnitResult()

    suspend fun addTarget(
        groupId: String,
        target: MarketTargetOption,
    ): NetworkResult<MarketWatchItem> =
        apiClient.postJson(
            ApiConfig.WATCH_ITEMS_PATH,
            JSONObject()
                .put("groupId", groupId.toLongOrNull() ?: groupId)
                .put("targetType", target.targetType)
                .put("targetCode", target.targetCode)
                .put("targetName", target.targetName),
        )
            .toEnvelope()
            .mapPayload(::parseSavedWatchItem)

    suspend fun deleteTarget(itemId: String): NetworkResult<Unit> =
        apiClient.delete(ApiConfig.WATCH_ITEMS_PATH + "/" + itemId)
            .toEnvelope()
            .toUnitResult()

    suspend fun saveTargetSettings(input: MarketTargetSettingsInput): NetworkResult<Unit> {
        val itemResult = saveWatchItem(input)
        if (itemResult is NetworkResult.Failure) return itemResult

        if (!input.item.targetType.supportsAlert() ||
            (input.alert == null && !input.alertEnabled)
        ) {
            return NetworkResult.Success(Unit)
        }

        val threshold = input.thresholdPercent
            ?: return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        val payload = JSONObject()
            .put("targetType", input.item.targetType)
            .put("stockCode", input.item.targetCode)
            .put("thresholdPercent", threshold)
            .put("enabled", input.alertEnabled)
        input.alert?.id?.toLongOrNull()?.let { payload.put("id", it) }
        return apiClient.postJson(ApiConfig.STOCK_ALERTS_PATH, payload)
            .toEnvelope()
            .toUnitResult()
    }

    private suspend fun loadGroups(): NetworkResult<List<MarketWatchGroup>> =
        apiClient.get(ApiConfig.WATCH_GROUPS_PATH)
            .toEnvelope()
            .mapPayload(::parseGroups)

    private suspend fun loadSystemTargets(): NetworkResult<List<MarketSystemTarget>> = coroutineScope {
        val optionsRequest = async { loadTargetOptions() }
        val stockQuotesRequest = async {
            loadSystemQuotes(
                path = ApiConfig.STOCK_QUOTES_PATH,
                targetType = "STOCK",
                targetCodeField = "stockCode",
            )
        }
        val indexQuotesRequest = async {
            loadSystemQuotes(
                path = ApiConfig.INDEX_QUOTES_PATH,
                targetType = "INDEX",
                targetCodeField = "indexCode",
            )
        }
        val bondQuotesRequest = async {
            loadSystemQuotes(
                path = ApiConfig.BOND_QUOTES_PATH,
                targetType = "BOND",
                targetCodeField = "bondCode",
            )
        }

        val optionsResult = optionsRequest.await()
        val quoteMap = listOf(
            stockQuotesRequest.await(),
            indexQuotesRequest.await(),
            bondQuotesRequest.await(),
        ).flatMap { result -> (result as? NetworkResult.Success)?.data.orEmpty() }
            .associateBy(SystemTargetQuote::targetKey)

        when (optionsResult) {
            is NetworkResult.Failure -> optionsResult
            is NetworkResult.Success -> NetworkResult.Success(
                optionsResult.data.map { option ->
                    val quote = quoteMap[option.targetKey]
                    MarketSystemTarget(
                        targetType = option.targetType,
                        targetCode = option.targetCode,
                        targetName = option.targetName,
                        latestPrice = quote?.latestPrice,
                        changePercent = quote?.changePercent,
                    )
                },
            )
        }
    }

    private suspend fun loadMarketOverview(): NetworkResult<List<MarketIndexQuote>> =
        apiClient.get(indexQuotesPath())
            .toJsonArrayPayload()
            .mapArrayPayload(::parseMarketOverview)

    private suspend fun loadSystemQuotes(
        path: String,
        targetType: String,
        targetCodeField: String,
    ): NetworkResult<List<SystemTargetQuote>> =
        apiClient.get(path + "?limit=" + SYSTEM_TARGET_QUOTE_REQUEST_LIMIT)
            .toJsonArrayPayload()
            .mapArrayPayload { quotes ->
                parseSystemQuotes(
                    quotes = quotes,
                    targetType = targetType,
                    targetCodeField = targetCodeField,
                )
            }

    private suspend fun loadAlerts(): NetworkResult<List<MarketAlert>> =
        apiClient.get(ApiConfig.STOCK_ALERTS_PATH)
            .toEnvelope()
            .mapPayload(::parseAlerts)

    private suspend fun loadTargetOptions(
        targetType: String,
    ): NetworkResult<List<MarketTargetOption>> =
        apiClient.get(ApiConfig.STOCK_ALERT_TARGET_OPTIONS_PATH + "?targetType=" + targetType)
            .toEnvelope()
            .mapPayload(::parseTargetOptions)

    private suspend fun saveWatchItem(input: MarketTargetSettingsInput): NetworkResult<MarketWatchItem> {
        val item = input.item
        val payload = JSONObject()
            .put("id", item.id.toLongOrNull() ?: item.id)
            .put("groupId", item.groupId.toLongOrNull() ?: item.groupId)
            .put("targetType", item.targetType)
            .put("targetCode", item.targetCode)
            .put("targetName", item.targetName)
            .putNullable("secid", item.secid)
            .putNullable("remark", input.remark)
            .putNullable("buyPrice", input.buyPrice)
            .putNullable("position", input.position)
        return apiClient.postJson(ApiConfig.WATCH_ITEMS_PATH, payload)
            .toEnvelope()
            .mapPayload(::parseSavedWatchItem)
    }

    private fun parseGroups(root: JSONObject): List<MarketWatchGroup> {
        val groups = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until groups.length()) {
                groups.optJSONObject(index)?.let { group -> add(parseGroup(group)) }
            }
        }
    }

    private fun parseGroup(group: JSONObject): MarketWatchGroup {
        val groupId = group.stringValue("id")
        val items = group.optJSONArray("items") ?: JSONArray()
        return MarketWatchGroup(
            id = groupId,
            name = group.stringValue("groupName").ifBlank { "未命名自选池" },
            items = buildList {
                for (index in 0 until items.length()) {
                    items.optJSONObject(index)?.let { item ->
                        add(parseWatchItem(item, groupId))
                    }
                }
            },
        )
    }

    private fun parseSavedGroup(root: JSONObject): MarketWatchGroup =
        parseGroup(requireNotNull(root.optJSONObject("data")))

    private fun parseSavedWatchItem(root: JSONObject): MarketWatchItem =
        parseWatchItem(requireNotNull(root.optJSONObject("data")))

    private fun parseWatchItem(
        item: JSONObject,
        fallbackGroupId: String = "",
    ): MarketWatchItem = MarketWatchItem(
        id = item.stringValue("id"),
        groupId = item.stringValue("groupId").ifBlank { fallbackGroupId },
        targetType = item.stringValue("targetType"),
        targetCode = item.stringValue("targetCode"),
        targetName = item.stringValue("targetName")
            .ifBlank { item.stringValue("targetCode") }
            .ifBlank { "未命名标的" },
        secid = item.nullableString("secid"),
        remark = item.nullableString("remark"),
        buyPrice = item.nullableDouble("buyPrice"),
        position = item.nullableDouble("position"),
        latestPrice = item.nullableDouble("latestPrice"),
        changePercent = item.nullableDouble("changePercent"),
    )

    private fun parseMarketOverview(array: JSONArray): List<MarketIndexQuote> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                MarketIndexQuote(
                    code = item.stringValue("indexCode"),
                    name = item.stringValue("indexName")
                        .ifBlank { item.stringValue("indexCode") }
                        .ifBlank { "未命名指数" },
                    latestPrice = item.nullableDouble("latestPrice"),
                    changePercent = item.nullableDouble("changePercent"),
                ),
            )
        }
    }

    private fun parseSystemQuotes(
        quotes: JSONArray,
        targetType: String,
        targetCodeField: String,
    ): List<SystemTargetQuote> = buildList {
        for (index in 0 until quotes.length()) {
            val quote = quotes.optJSONObject(index) ?: continue
            val targetCode = quote.stringValue(targetCodeField)
            if (targetCode.isBlank()) continue
            add(
                SystemTargetQuote(
                    targetType = targetType,
                    targetCode = targetCode,
                    latestPrice = quote.nullableDouble("latestPrice"),
                    changePercent = quote.nullableDouble("changePercent"),
                ),
            )
        }
    }

    private fun parseAlerts(root: JSONObject): List<MarketAlert> {
        val alerts = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until alerts.length()) {
                val item = alerts.optJSONObject(index) ?: continue
                val targetType = item.stringValue("targetType")
                val targetCode = item.stringValue("targetCode")
                    .ifBlank { item.stringValue("stockCode") }
                if (targetType.isBlank() || targetCode.isBlank()) continue
                add(
                    MarketAlert(
                        id = item.stringValue("id"),
                        targetType = targetType,
                        targetCode = targetCode,
                        thresholdPercent = item.nullableDouble("thresholdPercent"),
                        enabled = item.optBoolean("enabled", false),
                        outOfThreshold = item.optBoolean("outOfThreshold", false),
                    ),
                )
            }
        }
    }

    private fun parseTargetOptions(root: JSONObject): List<MarketTargetOption> {
        val options = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until options.length()) {
                val item = options.optJSONObject(index) ?: continue
                val targetType = item.stringValue("targetType")
                val targetCode = item.stringValue("targetCode")
                val targetName = item.stringValue("targetName")
                if (targetType.isBlank() || targetCode.isBlank() || targetName.isBlank()) continue
                add(
                    MarketTargetOption(
                        targetType = targetType,
                        targetCode = targetCode,
                        targetName = targetName,
                    ),
                )
            }
        }
    }

    private fun indexQuotesPath(): String =
        ApiConfig.INDEX_QUOTES_PATH + "?limit=" + MARKET_OVERVIEW_ITEM_LIMIT

    private companion object {
        const val MARKET_DATA_SOURCE_COUNT = 4
    }
}

private data class SystemTargetQuote(
    val targetType: String,
    val targetCode: String,
    val latestPrice: Double?,
    val changePercent: Double?,
) {
    val targetKey: String
        get() = targetType + ":" + targetCode
}

private fun <T> NetworkResult<JSONObject>.mapPayload(
    parser: (JSONObject) -> T,
): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(parser(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun <T> NetworkResult<JSONArray>.mapArrayPayload(
    parser: (JSONArray) -> T,
): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(parser(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun NetworkResult<JSONObject>.toUnitResult(): NetworkResult<Unit> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> NetworkResult.Success(Unit)
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.stringValue(key: String): String =
    optString(key, "").trim().takeUnless { it == "null" }.orEmpty()

private fun JSONObject.nullableString(key: String): String? =
    stringValue(key).ifBlank { null }

private fun JSONObject.nullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
