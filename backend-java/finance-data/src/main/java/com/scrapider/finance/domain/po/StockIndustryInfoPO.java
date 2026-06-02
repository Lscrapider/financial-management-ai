package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("stock_industry_info")
public class StockIndustryInfoPO {

    private Long id;
    private String stockCode;
    private String stockName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private String industryName;
    private String regionName;
    private String conceptNames;
    private String source;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockIndustryInfoPO fromValuationHistory(
            StockConfigPO stockConfig,
            StockValuationHistoryPO valuationHistory) {
        LocalDateTime now = LocalDateTime.now();

        StockIndustryInfoPO industry = new StockIndustryInfoPO();
        industry.setStockCode(stockConfig.getStockCode());
        industry.setStockName(stockConfig.getStockName());
        industry.setSecid(stockConfig.getSecid());
        industry.setMarketCode(stockConfig.getMarketCode());
        industry.setExchangeCode(stockConfig.getExchangeCode());
        industry.setIndustryName(valuationHistory.getBoardName());
        industry.setSource("eastmoney");
        industry.setRawResponse(valuationHistory.getRawResponse());
        industry.setSyncedAt(now);
        return industry;
    }
}
