package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.IndexKlinePO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IndexKlineVO {

    private String indexCode;
    private String indexName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private String periodType;
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

    public static IndexKlineVO fromPO(IndexKlinePO po) {
        IndexKlineVO vo = new IndexKlineVO();
        vo.setIndexCode(po.getIndexCode());
        vo.setIndexName(po.getIndexName());
        vo.setSecid(po.getSecid());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        vo.setPeriodType(po.getPeriodType());
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
