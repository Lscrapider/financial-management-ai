package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.WatchGroupItemPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class WatchGroupItemVO {

    private String id;
    private String groupId;
    private String targetType;
    private String targetCode;
    private String targetName;
    private String secid;
    private String remark;
    private BigDecimal buyPrice;
    private BigDecimal position;
    private Integer sortOrder;
    private BigDecimal latestPrice;
    private BigDecimal averagePrice;
    private BigDecimal changePercent;
    private Long volume;
    private Long externalVolume;
    private Long internalVolume;
    private Long currentVolume;
    private BigDecimal turnoverAmount;
    private BigDecimal turnoverRate;
    private BigDecimal amplitude;
    private BigDecimal volumeRatio;
    private BigDecimal limitUpPrice;
    private BigDecimal limitDownPrice;
    private BigDecimal totalMarketValue;
    private BigDecimal floatMarketValue;
    private BigDecimal peTtm;
    private BigDecimal peDynamic;
    private BigDecimal peStatic;
    private BigDecimal pbRatio;
    private List<StockQuoteDetailVO> quoteDetails;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WatchGroupItemVO fromPO(WatchGroupItemPO item) {
        WatchGroupItemVO vo = new WatchGroupItemVO();
        vo.setId(String.valueOf(item.getId()));
        vo.setGroupId(String.valueOf(item.getGroupId()));
        vo.setTargetType(item.getTargetType());
        vo.setTargetCode(item.getTargetCode());
        vo.setTargetName(item.getTargetName());
        vo.setSecid(item.getSecid());
        vo.setRemark(item.getRemark());
        vo.setBuyPrice(item.getBuyPrice());
        vo.setPosition(item.getPosition());
        vo.setSortOrder(item.getSortOrder());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    public void fillStockQuote(StockQuoteSnapshotPO quote) {
        if (quote == null) {
            return;
        }
        this.latestPrice = quote.getLatestPrice();
        this.averagePrice = quote.getAveragePrice();
        this.changePercent = quote.getChangePercent();
        this.volume = quote.getVolume();
        this.externalVolume = quote.getExternalVolume();
        this.internalVolume = quote.getInternalVolume();
        this.currentVolume = quote.getCurrentVolume();
        this.turnoverAmount = quote.getTurnoverAmount();
        this.turnoverRate = quote.getTurnoverRate();
        this.amplitude = quote.getAmplitude();
        this.volumeRatio = quote.getVolumeRatio();
        this.limitUpPrice = quote.getLimitUpPrice();
        this.limitDownPrice = quote.getLimitDownPrice();
        this.totalMarketValue = quote.getTotalMarketValue();
        this.floatMarketValue = quote.getFloatMarketValue();
        this.peTtm = quote.getPeTtm();
        this.peDynamic = quote.getPeDynamic();
        this.peStatic = quote.getPeStatic();
        this.pbRatio = quote.getPbRatio();
        this.quoteDetails = StockQuoteVO.fromPO(quote).getQuoteDetails();
        this.syncedAt = quote.getSyncedAt();
    }

    public void fillIndexQuote(IndexQuoteSnapshotPO quote) {
        if (quote == null) {
            return;
        }
        this.latestPrice = quote.getLatestPrice();
        this.changePercent = quote.getChangePercent();
        this.turnoverAmount = quote.getTurnoverAmount();
        this.syncedAt = quote.getSyncedAt();
    }

    public void fillBondQuote(BondQuoteSnapshotPO quote) {
        if (quote == null) {
            return;
        }
        this.latestPrice = quote.getLatestPrice();
        this.averagePrice = quote.getAveragePrice();
        this.changePercent = quote.getChangePercent();
        this.volume = quote.getVolume();
        this.currentVolume = quote.getCurrentVolume();
        this.turnoverAmount = quote.getTurnoverAmount();
        this.turnoverRate = quote.getTurnoverRate();
        this.amplitude = quote.getAmplitude();
        this.quoteDetails = BondQuoteVO.fromPO(quote).getQuoteDetails();
        this.syncedAt = quote.getSyncedAt();
    }
}
