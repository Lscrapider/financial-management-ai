package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.BondDailyKlinePO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BondDailyKlineVO {

    private String bondCode;
    private String bondName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
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
    private LocalDateTime syncedAt;

    public static BondDailyKlineVO fromPO(BondDailyKlinePO po) {
        BondDailyKlineVO vo = new BondDailyKlineVO();
        vo.setBondCode(po.getBondCode());
        vo.setBondName(po.getBondName());
        vo.setSecid(po.getSecid());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
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
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }
}
