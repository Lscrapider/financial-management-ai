package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockIntradayTrendVO {

    private String stockCode;
    private String stockName;
    private String secid;
    private String syncBatchNo;
    private LocalDateTime trendTime;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal averagePrice;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal previousClosePrice;
    private LocalDateTime syncedAt;

    public static StockIntradayTrendVO fromPO(StockIntradayTrendPO po) {
        StockIntradayTrendVO vo = new StockIntradayTrendVO();
        vo.setStockCode(po.getStockCode());
        vo.setStockName(po.getStockName());
        vo.setSecid(po.getSecid());
        vo.setSyncBatchNo(po.getSyncBatchNo());
        vo.setTrendTime(po.getTrendTime());
        vo.setOpenPrice(po.getOpenPrice());
        vo.setClosePrice(po.getClosePrice());
        vo.setHighPrice(po.getHighPrice());
        vo.setLowPrice(po.getLowPrice());
        vo.setAveragePrice(po.getAveragePrice());
        vo.setVolume(po.getVolume());
        vo.setTurnoverAmount(po.getTurnoverAmount());
        vo.setPreviousClosePrice(po.getPreviousClosePrice());
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }
}
