package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BondQuoteVO {

    private String bondCode;
    private String bondName;
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
    private String bondRating;
    private LocalDateTime syncedAt;

    public static BondQuoteVO fromPO(BondQuoteSnapshotPO po) {
        BondQuoteVO vo = new BondQuoteVO();
        vo.setBondCode(po.getBondCode());
        vo.setBondName(po.getBondName());
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
        vo.setBondRating(po.getBondRating());
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }
}
