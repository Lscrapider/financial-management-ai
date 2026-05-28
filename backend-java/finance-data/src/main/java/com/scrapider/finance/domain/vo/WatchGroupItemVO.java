package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.WatchGroupItemPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private BigDecimal changePercent;
    private BigDecimal turnoverAmount;
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
        this.changePercent = quote.getChangePercent();
        this.turnoverAmount = quote.getTurnoverAmount();
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
        this.changePercent = quote.getChangePercent();
        this.turnoverAmount = quote.getTurnoverAmount();
        this.syncedAt = quote.getSyncedAt();
    }
}
