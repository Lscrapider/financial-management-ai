package com.scrapider.finance.domain.po;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
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

    public static StockIndustryInfoPO fromEastMoneyStockGetResponse(StockConfigPO stockConfig, JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return null;
        }
        JsonNode row = response.path("data");
        if (row.isMissingNode() || row.isNull()) {
            return null;
        }
        String industryName = StockMarketJsonParser.text(row, "f127", null);
        String regionName = StockMarketJsonParser.text(row, "f128", null);
        String conceptNames = StockMarketJsonParser.text(row, "f129", null);
        if (StrUtil.isBlank(industryName) && StrUtil.isBlank(regionName) && StrUtil.isBlank(conceptNames)) {
            return null;
        }

        StockIndustryInfoPO industry = new StockIndustryInfoPO();
        industry.setStockCode(StockMarketJsonParser.text(row, "f57", stockConfig.getStockCode()));
        industry.setStockName(StockMarketJsonParser.text(row, "f58", stockConfig.getStockName()));
        industry.setSecid(stockConfig.getSecid());
        industry.setMarketCode(stockConfig.getMarketCode());
        industry.setExchangeCode(stockConfig.getExchangeCode());
        industry.setIndustryName(industryName);
        industry.setRegionName(regionName);
        industry.setConceptNames(conceptNames);
        industry.setSource("eastmoney");
        industry.setRawResponse(response.toString());
        industry.setSyncedAt(LocalDateTime.now());
        return industry;
    }

    public static StockIndustryInfoPO fromTushareStockBasicRow(StockConfigPO stockConfig, JsonNode row) {
        if (row == null || row.isMissingNode() || row.isNull()) {
            return null;
        }
        String industryName = StockMarketJsonParser.text(row, "industry", null);
        String regionName = StockMarketJsonParser.text(row, "area", null);
        if ((industryName == null || industryName.isBlank()) && (regionName == null || regionName.isBlank())) {
            return null;
        }

        StockIndustryInfoPO industry = new StockIndustryInfoPO();
        industry.setStockCode(stockConfig.getStockCode());
        industry.setStockName(StockMarketJsonParser.text(row, "name", stockConfig.getStockName()));
        industry.setSecid(stockConfig.getSecid());
        industry.setMarketCode(stockConfig.getMarketCode());
        industry.setExchangeCode(stockConfig.getExchangeCode());
        industry.setIndustryName(industryName);
        industry.setRegionName(regionName);
        industry.setSource("tushare");
        industry.setRawResponse(row.toString());
        industry.setSyncedAt(LocalDateTime.now());
        return industry;
    }
}
