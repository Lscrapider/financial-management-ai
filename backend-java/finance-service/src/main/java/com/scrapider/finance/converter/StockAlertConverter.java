package com.scrapider.finance.converter;

import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.vo.StockAlertConfigVO;
import java.util.List;
import java.util.Map;

public final class StockAlertConverter {

    private static final String TYPE_STOCK = "STOCK";
    private static final String TYPE_INDEX = "INDEX";
    private static final String TYPE_BOND = "BOND";

    private StockAlertConverter() {
    }

    public static List<StockAlertConfigVO> toVOList(
            List<StockAlertConfigPO> configs,
            Map<Long, AppUserPO> userMap,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap,
            Map<String, IndexQuoteSnapshotPO> indexQuoteMap,
            Map<String, BondQuoteSnapshotPO> bondQuoteMap) {
        return configs.stream()
                .map(config -> toVO(config, userMap, stockQuoteMap, indexQuoteMap, bondQuoteMap))
                .toList();
    }

    private static StockAlertConfigVO toVO(
            StockAlertConfigPO config,
            Map<Long, AppUserPO> userMap,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap,
            Map<String, IndexQuoteSnapshotPO> indexQuoteMap,
            Map<String, BondQuoteSnapshotPO> bondQuoteMap) {
        StockAlertConfigVO vo = StockAlertConfigVO.fromPO(config);
        fillQuote(vo, config, stockQuoteMap, indexQuoteMap, bondQuoteMap);
        AppUserPO user = userMap.get(config.getUserId());
        if (user != null) {
            vo.fillUser(user.getUsername(), user.getRealName(), user.getEmail(), user.getEmailNotification());
        }
        return vo;
    }

    private static void fillQuote(
            StockAlertConfigVO vo,
            StockAlertConfigPO config,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap,
            Map<String, IndexQuoteSnapshotPO> indexQuoteMap,
            Map<String, BondQuoteSnapshotPO> bondQuoteMap) {
        switch (config.getTargetType()) {
            case TYPE_STOCK -> {
                StockQuoteSnapshotPO quote = stockQuoteMap.get(config.getStockCode());
                if (quote != null) {
                    vo.fillQuote(quote.getLatestPrice(), quote.getChangePercent(), quote.getSyncedAt());
                }
            }
            case TYPE_INDEX -> {
                IndexQuoteSnapshotPO quote = indexQuoteMap.get(config.getStockCode());
                if (quote != null) {
                    vo.fillQuote(quote.getLatestPrice(), quote.getChangePercent(), quote.getSyncedAt());
                }
            }
            case TYPE_BOND -> {
                BondQuoteSnapshotPO quote = bondQuoteMap.get(config.getStockCode());
                if (quote != null) {
                    vo.fillQuote(quote.getLatestPrice(), quote.getChangePercent(), quote.getSyncedAt());
                }
            }
            default -> {
            }
        }
    }
}
