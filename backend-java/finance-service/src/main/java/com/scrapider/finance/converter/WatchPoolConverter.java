package com.scrapider.finance.converter;

import com.scrapider.finance.domain.enums.WatchTargetTypeEnum;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.WatchGroupItemPO;
import com.scrapider.finance.domain.po.WatchGroupPO;
import com.scrapider.finance.domain.vo.WatchGroupItemVO;
import com.scrapider.finance.domain.vo.WatchGroupVO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class WatchPoolConverter {

    private WatchPoolConverter() {
    }

    public static List<WatchGroupVO> toGroupVOList(
            List<WatchGroupPO> groups,
            List<WatchGroupItemPO> items,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap,
            Map<String, IndexQuoteSnapshotPO> indexQuoteMap,
            Map<String, BondQuoteSnapshotPO> bondQuoteMap) {
        Map<Long, List<WatchGroupItemPO>> itemMap = items.stream()
                .collect(Collectors.groupingBy(WatchGroupItemPO::getGroupId));
        Map<Long, WatchGroupItemVO> itemVOMap = toItemVOList(items, stockQuoteMap, indexQuoteMap, bondQuoteMap).stream()
                .collect(Collectors.toMap(item -> Long.valueOf(item.getId()), Function.identity()));
        return groups.stream()
                .map(group -> WatchGroupVO.fromPO(group, itemMap.getOrDefault(group.getId(), List.of()).stream()
                        .map(item -> itemVOMap.get(item.getId()))
                        .filter(Objects::nonNull)
                        .toList()))
                .toList();
    }

    public static List<WatchGroupItemVO> toItemVOList(
            List<WatchGroupItemPO> items,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap,
            Map<String, IndexQuoteSnapshotPO> indexQuoteMap,
            Map<String, BondQuoteSnapshotPO> bondQuoteMap) {
        return items.stream()
                .map(item -> toItemVO(item, stockQuoteMap, indexQuoteMap, bondQuoteMap))
                .toList();
    }

    private static WatchGroupItemVO toItemVO(
            WatchGroupItemPO item,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap,
            Map<String, IndexQuoteSnapshotPO> indexQuoteMap,
            Map<String, BondQuoteSnapshotPO> bondQuoteMap) {
        WatchGroupItemVO vo = WatchGroupItemVO.fromPO(item);
        if (WatchTargetTypeEnum.STOCK.name().equals(item.getTargetType())) {
            vo.fillStockQuote(stockQuoteMap.get(item.getTargetCode()));
        }
        if (WatchTargetTypeEnum.INDEX.name().equals(item.getTargetType())) {
            vo.fillIndexQuote(indexQuoteMap.get(item.getTargetCode()));
        }
        if (WatchTargetTypeEnum.BOND.name().equals(item.getTargetType())) {
            vo.fillBondQuote(bondQuoteMap.get(item.getTargetCode()));
        }
        return vo;
    }
}
