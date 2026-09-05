package com.scrapider.finance.androidapp.feature.market

import java.util.Locale

/**
 * 将系统标的、自选标的和市场概览归并为一个可搜索、可进入详情的展示模型。
 *
 * 这里仅组合当前已加载的数据，不额外请求或补齐详情字段，避免改变现有接口契约。
 */
internal fun MarketUiState.availableTargetSnapshots(): List<MarketTargetSnapshot> {
    val targetLookup = createTargetLookup()
    return targetLookup.targetReferences.values.mapNotNull(targetLookup::snapshot)
}

internal fun MarketUiState.findTargetSnapshot(
    targetType: String,
    targetCode: String,
    watchItemId: String? = null,
): MarketTargetSnapshot? = createTargetLookup().snapshot(
    reference = MarketTargetReference(targetType = targetType, targetCode = targetCode),
    watchItemId = watchItemId,
)

private fun MarketUiState.createTargetLookup(): MarketTargetLookup {
    val targetReferences = linkedMapOf<String, MarketTargetReference>()
    val systemTargetsByKey = linkedMapOf<String, MarketSystemTarget>()
    val watchedTargetsByKey = linkedMapOf<String, MarketWatchedTarget>()
    val watchedTargetsByItemId = mutableMapOf<String, MarketWatchedTarget>()
    val overviewTargetsByKey = linkedMapOf<String, MarketIndexQuote>()
    val alertsByKey = linkedMapOf<String, MarketAlert>()

    systemTargets.forEach { target ->
        val key = targetIdentity(target.targetType, target.targetCode)
        targetReferences.putIfAbsent(key, MarketTargetReference(target.targetType, target.targetCode))
        systemTargetsByKey.putIfAbsent(key, target)
    }
    groups.forEach { group ->
        group.items.forEach { item ->
            val key = targetIdentity(item.targetType, item.targetCode)
            val watchedTarget = MarketWatchedTarget(item = item, groupName = group.name)
            targetReferences.putIfAbsent(key, MarketTargetReference(item.targetType, item.targetCode))
            watchedTargetsByKey.putIfAbsent(key, watchedTarget)
            if (item.id.isNotBlank()) {
                watchedTargetsByItemId.putIfAbsent(item.id, watchedTarget)
            }
        }
    }
    marketOverview.forEach { index ->
        val key = targetIdentity(MARKET_INDEX_TARGET_TYPE, index.code)
        targetReferences.putIfAbsent(
            key,
            MarketTargetReference(MARKET_INDEX_TARGET_TYPE, index.code),
        )
        overviewTargetsByKey.putIfAbsent(key, index)
    }
    alerts.forEach { alert ->
        alertsByKey.putIfAbsent(targetIdentity(alert.targetType, alert.targetCode), alert)
    }

    return MarketTargetLookup(
        targetReferences = targetReferences,
        systemTargetsByKey = systemTargetsByKey,
        watchedTargetsByKey = watchedTargetsByKey,
        watchedTargetsByItemId = watchedTargetsByItemId,
        overviewTargetsByKey = overviewTargetsByKey,
        alertsByKey = alertsByKey,
    )
}

private data class MarketTargetLookup(
    val targetReferences: Map<String, MarketTargetReference>,
    val systemTargetsByKey: Map<String, MarketSystemTarget>,
    val watchedTargetsByKey: Map<String, MarketWatchedTarget>,
    val watchedTargetsByItemId: Map<String, MarketWatchedTarget>,
    val overviewTargetsByKey: Map<String, MarketIndexQuote>,
    val alertsByKey: Map<String, MarketAlert>,
) {
    fun snapshot(
        reference: MarketTargetReference,
        watchItemId: String? = null,
    ): MarketTargetSnapshot? {
        val targetKey = targetIdentity(reference.targetType, reference.targetCode)
        val systemTarget = systemTargetsByKey[targetKey]
        val requestedWatchItemId = watchItemId?.takeIf(String::isNotBlank)
        val watchedTarget = if (requestedWatchItemId == null) {
            watchedTargetsByKey[targetKey]
        } else {
            watchedTargetsByItemId[requestedWatchItemId]
                ?.takeIf { watched ->
                    targetIdentity(watched.item.targetType, watched.item.targetCode) == targetKey
                }
        }
        val overviewTarget = overviewTargetsByKey[targetKey]
        if (systemTarget == null && watchedTarget == null && overviewTarget == null) return null

        val item = watchedTarget?.item
        val resolvedTargetType = systemTarget?.targetType
            ?: item?.targetType
            ?: if (overviewTarget != null) MARKET_INDEX_TARGET_TYPE else reference.targetType
        val resolvedTargetCode = systemTarget?.targetCode
            ?: item?.targetCode
            ?: overviewTarget?.code
            ?: reference.targetCode
        return MarketTargetSnapshot(
            targetType = resolvedTargetType,
            targetCode = resolvedTargetCode,
            targetName = item?.targetName
                ?.takeIf(String::isNotBlank)
                ?: systemTarget?.targetName?.takeIf(String::isNotBlank)
                ?: overviewTarget?.name?.takeIf(String::isNotBlank)
                ?: resolvedTargetCode,
            latestPrice = item?.latestPrice ?: systemTarget?.latestPrice ?: overviewTarget?.latestPrice,
            changePercent = item?.changePercent ?: systemTarget?.changePercent ?: overviewTarget?.changePercent,
            watchItemId = item?.id,
            watchGroupName = watchedTarget?.groupName,
            buyPrice = item?.buyPrice,
            position = item?.position,
            remark = item?.remark,
            alert = alertsByKey[targetKey],
        )
    }
}

private data class MarketTargetReference(
    val targetType: String,
    val targetCode: String,
)

internal fun <T> List<T>.marketSorted(
    option: MarketSortOption,
    targetCode: (T) -> String,
    latestPrice: (T) -> Double?,
    changePercent: (T) -> Double?,
): List<T> {
    if (option == MarketSortOption.Default) return this
    val value = when (option) {
        MarketSortOption.ChangeDescending,
        MarketSortOption.ChangeAscending,
        -> changePercent

        MarketSortOption.PriceDescending,
        MarketSortOption.PriceAscending,
        -> latestPrice

        MarketSortOption.Default -> return this
    }
    val ascending = option == MarketSortOption.ChangeAscending ||
        option == MarketSortOption.PriceAscending
    return sortedWith(
        Comparator { left, right ->
            val leftValue = value(left)
            val rightValue = value(right)
            val comparison = when {
                leftValue == null && rightValue == null -> 0
                leftValue == null -> 1
                rightValue == null -> -1
                ascending -> leftValue.compareTo(rightValue)
                else -> rightValue.compareTo(leftValue)
            }
            if (comparison != 0) comparison else targetCode(left).compareTo(targetCode(right))
        },
    )
}

private data class MarketWatchedTarget(
    val item: MarketWatchItem,
    val groupName: String,
)

private fun targetIdentity(
    targetType: String,
    targetCode: String,
): String = targetType.uppercase(Locale.ROOT) + ":" + targetCode.uppercase(Locale.ROOT)

private const val MARKET_INDEX_TARGET_TYPE = "INDEX"
