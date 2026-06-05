package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.StockKlinePO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockKlineVO {

    private String stockCode;
    private String stockName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private String periodType;
    private String adjustType;
    private LocalDate tradeDate;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal amplitude;
    private BigDecimal turnoverRate;
    private BigDecimal ma5;
    private BigDecimal ma10;
    private BigDecimal ma20;
    private LocalDateTime syncedAt;

    public static StockKlineVO fromPO(StockKlinePO po) {
        StockKlineVO vo = new StockKlineVO();
        vo.setStockCode(po.getStockCode());
        vo.setStockName(po.getStockName());
        vo.setSecid(po.getSecid());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        vo.setPeriodType(po.getPeriodType());
        vo.setAdjustType(po.getAdjustType());
        vo.setTradeDate(po.getTradeDate());
        vo.setOpenPrice(po.getOpenPrice());
        vo.setClosePrice(po.getClosePrice());
        vo.setHighPrice(po.getHighPrice());
        vo.setLowPrice(po.getLowPrice());
        vo.setChangeAmount(po.getChangeAmount());
        vo.setChangePercent(po.getChangePercent());
        vo.setVolume(po.getVolume());
        vo.setTurnoverAmount(po.getTurnoverAmount());
        vo.setAmplitude(po.getAmplitude());
        vo.setTurnoverRate(po.getTurnoverRate());
        vo.setMa5(po.getMa5());
        vo.setMa10(po.getMa10());
        vo.setMa20(po.getMa20());
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }
}
