package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockIntradayTrendManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockMarketSyncTask {

    private final StockMarketApi stockMarketApi;
    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockIntradayTrendManage stockIntradayTrendManage;

    @Value("${stock.sync.enabled:false}")
    private boolean enabled;

    @Value("${stock.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${stock.sync.trend-days:1}")
    private int trendDays;

    public StockMarketSyncTask(
            StockMarketApi stockMarketApi,
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendManage stockIntradayTrendManage) {
        this.stockMarketApi = stockMarketApi;
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendManage = stockIntradayTrendManage;
    }

    @Scheduled(
            initialDelayString = "${stock.sync.initial-delay-ms:10000}",
            fixedDelayString = "${stock.sync.fixed-delay-ms:300000}")
    public void syncStockMarketData() {
        if (!this.enabled) {
            log.debug("Stock market sync task is disabled.");
            return;
        }

        List<StockConfigPO> stocks = this.stockConfigManage.listEnabledStocks();
        if (CollUtil.isEmpty(stocks)) {
            log.info("No enabled stock config found.");
            return;
        }

        log.info("Start syncing stock market data, stock count: {}", stocks.size());
        stocks.forEach(this::syncOneStock);
        log.info("Finished syncing stock market data.");
    }

    private void syncOneStock(StockConfigPO stock) {
        if (StrUtil.isBlank(stock.getSecid())) {
            log.warn("Skip stock without secid, code: {}", stock.getStockCode());
            return;
        }

        try {
            String syncBatchNo = UUID.randomUUID().toString();
            StockMarketDataDTO quote = this.stockMarketApi.getQuote(stock.getSecid());
            this.stockQuoteSnapshotManage.saveLatest(StockQuoteSnapshotPO.fromApiResponse(stock, quote.data()));
            this.sleepForRateLimit();

            StockMarketDataDTO trends = this.stockMarketApi.getTrends(stock.getSecid(), this.normalizeTrendDays());
            this.saveTrends(stock, trends.data(), syncBatchNo);
            this.sleepForRateLimit();
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync stock market data, code: {}, name: {}",
                    stock.getStockCode(),
                    stock.getStockName(),
                    ex);
        }
    }

    private void saveTrends(StockConfigPO stock, JsonNode response, String syncBatchNo) {
        JsonNode data = response.path("data");
        BigDecimal previousClosePrice = StockMarketJsonParser.decimal(data, "preClose");
        LocalDateTime syncedAt = LocalDateTime.now();
        List<StockIntradayTrendPO> trends = StreamSupport.stream(data.path("trends").spliterator(), false)
                .map(JsonNode::asText)
                .map(line -> StockIntradayTrendPO.fromTrendLine(
                        stock,
                        line,
                        previousClosePrice,
                        syncedAt,
                        syncBatchNo))
                .filter(Objects::nonNull)
                .toList();

        this.stockIntradayTrendManage.saveTrends(trends);
    }

    private void sleepForRateLimit() throws InterruptedException {
        if (this.requestIntervalMs > 0) {
            Thread.sleep(this.requestIntervalMs);
        }
    }

    private int normalizeTrendDays() {
        if (this.trendDays < 1) {
            return 1;
        }
        return Math.min(this.trendDays, 5);
    }
}
