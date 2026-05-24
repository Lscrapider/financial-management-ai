package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IndexQuoteVO {

    private String indexCode;
    private String indexName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private BigDecimal latestPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClosePrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal amplitude;
    private LocalDateTime syncedAt;

    public static IndexQuoteVO fromPO(IndexQuoteSnapshotPO po) {
        IndexQuoteVO vo = new IndexQuoteVO();
        vo.setIndexCode(po.getIndexCode());
        vo.setIndexName(po.getIndexName());
        vo.setSecid(po.getSecid());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        vo.setLatestPrice(po.getLatestPrice());
        vo.setOpenPrice(po.getOpenPrice());
        vo.setHighPrice(po.getHighPrice());
        vo.setLowPrice(po.getLowPrice());
        vo.setPreviousClosePrice(po.getPreviousClosePrice());
        vo.setChangeAmount(po.getChangeAmount());
        vo.setChangePercent(po.getChangePercent());
        vo.setVolume(po.getVolume());
        vo.setTurnoverAmount(po.getTurnoverAmount());
        vo.setAmplitude(po.getAmplitude());
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }
}
