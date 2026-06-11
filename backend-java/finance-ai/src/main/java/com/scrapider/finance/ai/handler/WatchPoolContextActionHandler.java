package com.scrapider.finance.ai.handler;

import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.domain.enums.WatchTargetTypeEnum;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.WatchGroupItemPO;
import com.scrapider.finance.domain.po.WatchGroupPO;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.manage.WatchGroupItemManage;
import com.scrapider.finance.manage.WatchGroupManage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WatchPoolContextActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "watch_pool.context";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final WatchGroupManage watchGroupManage;
    private final WatchGroupItemManage watchGroupItemManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;

    public WatchPoolContextActionHandler(
            WatchGroupManage watchGroupManage,
            WatchGroupItemManage watchGroupItemManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage) {
        this.watchGroupManage = watchGroupManage;
        this.watchGroupItemManage = watchGroupItemManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "正在读取观察池上下文";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        int limit = this.normalizeLimit(param);
        List<WatchGroupPO> groups = this.watchGroupManage.listByUserId(session.userId());
        List<Long> groupIds = groups.stream().map(WatchGroupPO::getId).toList();
        List<WatchGroupItemPO> items = this.watchGroupItemManage.listByGroupIds(groupIds).stream()
                .filter(item -> Objects.equals(item.getUserId(), session.userId()))
                .limit(limit)
                .toList();
        Map<String, StockQuoteSnapshotPO> stockQuotes = this.stockQuoteMap(items);
        Map<String, IndexQuoteSnapshotPO> indexQuotes = this.indexQuoteMap(items);
        Map<String, BondQuoteSnapshotPO> bondQuotes = this.bondQuoteMap(items);

        List<Map<String, Object>> data = groups.stream()
                .map(group -> this.groupToMap(group, items, stockQuotes, indexQuotes, bondQuotes))
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queriedAt", OffsetDateTime.now().toString());
        metadata.put("groupCount", groups.size());
        metadata.put("itemCount", items.size());
        metadata.put("limit", limit);
        return new AgentDataGatewayResponseVO(
                param.action(),
                true,
                data,
                metadata,
                null);
    }

    private Map<String, Object> groupToMap(
            WatchGroupPO group,
            List<WatchGroupItemPO> allItems,
            Map<String, StockQuoteSnapshotPO> stockQuotes,
            Map<String, IndexQuoteSnapshotPO> indexQuotes,
            Map<String, BondQuoteSnapshotPO> bondQuotes) {
        List<Map<String, Object>> items = allItems.stream()
                .filter(item -> Objects.equals(item.getGroupId(), group.getId()))
                .map(item -> this.itemToMap(item, stockQuotes, indexQuotes, bondQuotes))
                .toList();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("groupName", group.getGroupName());
        row.put("items", items);
        return row;
    }

    private Map<String, Object> itemToMap(
            WatchGroupItemPO item,
            Map<String, StockQuoteSnapshotPO> stockQuotes,
            Map<String, IndexQuoteSnapshotPO> indexQuotes,
            Map<String, BondQuoteSnapshotPO> bondQuotes) {
        QuoteSnapshot quote = this.quoteSnapshot(item, stockQuotes, indexQuotes, bondQuotes);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("targetType", this.compactTargetType(item.getTargetType()));
        row.put("targetCode", item.getTargetCode());
        row.put("targetName", item.getTargetName());
        row.put("remark", item.getRemark());
        row.put("buyPrice", item.getBuyPrice());
        row.put("position", item.getPosition());
        row.put("latestPrice", quote.latestPrice());
        row.put("changePercent", quote.changePercent());
        return row;
    }

    private QuoteSnapshot quoteSnapshot(
            WatchGroupItemPO item,
            Map<String, StockQuoteSnapshotPO> stockQuotes,
            Map<String, IndexQuoteSnapshotPO> indexQuotes,
            Map<String, BondQuoteSnapshotPO> bondQuotes) {
        if (WatchTargetTypeEnum.INDEX.name().equals(item.getTargetType())) {
            IndexQuoteSnapshotPO quote = indexQuotes.get(item.getTargetCode());
            if (quote == null) {
                return QuoteSnapshot.empty();
            }
            return new QuoteSnapshot(quote.getLatestPrice(), quote.getChangePercent());
        }
        if (WatchTargetTypeEnum.BOND.name().equals(item.getTargetType())) {
            BondQuoteSnapshotPO quote = bondQuotes.get(item.getTargetCode());
            if (quote == null) {
                return QuoteSnapshot.empty();
            }
            return new QuoteSnapshot(quote.getLatestPrice(), quote.getChangePercent());
        }
        StockQuoteSnapshotPO quote = stockQuotes.get(item.getTargetCode());
        if (quote == null) {
            return QuoteSnapshot.empty();
        }
        return new QuoteSnapshot(quote.getLatestPrice(), quote.getChangePercent());
    }

    private Map<String, StockQuoteSnapshotPO> stockQuoteMap(List<WatchGroupItemPO> items) {
        List<String> stockCodes = items.stream()
                .filter(item -> WatchTargetTypeEnum.STOCK.name().equals(item.getTargetType()))
                .map(WatchGroupItemPO::getTargetCode)
                .distinct()
                .toList();
        if (stockCodes.isEmpty()) {
            return Map.of();
        }
        return this.stockQuoteSnapshotManage.listByStockCodes(stockCodes).stream()
                .collect(Collectors.toMap(StockQuoteSnapshotPO::getStockCode, Function.identity()));
    }

    private Map<String, IndexQuoteSnapshotPO> indexQuoteMap(List<WatchGroupItemPO> items) {
        List<String> indexCodes = items.stream()
                .filter(item -> WatchTargetTypeEnum.INDEX.name().equals(item.getTargetType()))
                .map(WatchGroupItemPO::getTargetCode)
                .distinct()
                .toList();
        if (indexCodes.isEmpty()) {
            return Map.of();
        }
        return this.indexQuoteSnapshotManage.listByIndexCodes(indexCodes).stream()
                .collect(Collectors.toMap(IndexQuoteSnapshotPO::getIndexCode, Function.identity()));
    }

    private Map<String, BondQuoteSnapshotPO> bondQuoteMap(List<WatchGroupItemPO> items) {
        List<String> bondCodes = items.stream()
                .filter(item -> WatchTargetTypeEnum.BOND.name().equals(item.getTargetType()))
                .map(WatchGroupItemPO::getTargetCode)
                .distinct()
                .toList();
        if (bondCodes.isEmpty()) {
            return Map.of();
        }
        return this.bondQuoteSnapshotManage.listByBondCodes(bondCodes).stream()
                .collect(Collectors.toMap(BondQuoteSnapshotPO::getBondCode, Function.identity()));
    }

    private String compactTargetType(String targetType) {
        if (targetType == null) {
            return null;
        }
        return targetType.toLowerCase(Locale.ROOT);
    }

    private int normalizeLimit(AgentDataQueryParam param) {
        Integer limit = param == null ? null : param.limit();
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private record QuoteSnapshot(BigDecimal latestPrice, BigDecimal changePercent) {

        private static QuoteSnapshot empty() {
            return new QuoteSnapshot(null, null);
        }
    }
}
