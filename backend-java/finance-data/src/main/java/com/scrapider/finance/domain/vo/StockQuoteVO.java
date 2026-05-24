package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockQuoteVO {

    private String stockCode;
    private String stockName;
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
    private BigDecimal turnoverRate;
    private BigDecimal amplitude;
    private BigDecimal totalMarketValue;
    private BigDecimal floatMarketValue;
    private LocalDateTime syncedAt;

    public static StockQuoteVO fromPO(StockQuoteSnapshotPO po) {
        StockQuoteVO vo = new StockQuoteVO();
        vo.setStockCode(po.getStockCode());
        vo.setStockName(po.getStockName());
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
        vo.setTurnoverRate(po.getTurnoverRate());
        vo.setAmplitude(po.getAmplitude());
        vo.setTotalMarketValue(po.getTotalMarketValue());
        vo.setFloatMarketValue(po.getFloatMarketValue());
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }
}
