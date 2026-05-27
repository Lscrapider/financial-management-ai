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
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.StockAlertService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final StockAlertService stockAlertService;

    @Value("${stock.sync.enabled:false}")
    private boolean enabled;

    @Value("${stock.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${stock.sync.start-time:09:29}")
    private String startTime;

    @Value("${stock.sync.end-time:16:00}")
    private String endTime;

    @Value("${stock.sync.timezone:Asia/Shanghai}")
    private String timezone;

    public StockMarketSyncTask(
            StockMarketApi stockMarketApi,
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            StockAlertService stockAlertService) {
        this.stockMarketApi = stockMarketApi;
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.stockAlertService = stockAlertService;
    }

    @Scheduled(
            initialDelayString = "${stock.sync.initial-delay-ms}",
            fixedDelayString = "${stock.sync.fixed-delay-ms}")
    public void syncStockMarketData() {
        if (!this.enabled) {
            log.debug("Stock market sync task is disabled.");
            return;
        }
        if (!this.isInSyncWindow()) {
            log.debug("Stock market sync task is outside sync window.");
            return;
        }

        List<StockConfigPO> stocks = this.stockConfigManage.listEnabledStocks();
        if (CollUtil.isEmpty(stocks)) {
            log.info("No enabled stock config found.");
            return;
        }

        log.info("Start syncing stock market data, stock count: {}", stocks.size());
        stocks.forEach(this::syncOneStock);
        this.stockAlertService.checkAlerts();
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
            StockMarketDataDTO trends = this.stockMarketApi.getTrends(stock.getSecid());
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
        String tencentSymbol = this.toTencentSymbol(stock.getSecid());
        JsonNode data = response.path("data").path(tencentSymbol).path("data");
        String tradeDate = data.path("date").asText();
        BigDecimal previousClosePrice = this.previousClosePrice(response, tencentSymbol);
        LocalDateTime syncedAt = LocalDateTime.now();
        List<StockIntradayTrendPO> trends = StreamSupport.stream(data.path("data").spliterator(), false)
                .map(JsonNode::asText)
                .map(line -> StockIntradayTrendPO.fromTrendLine(
                        stock,
                        tradeDate,
                        line,
                        previousClosePrice,
                        syncedAt,
                        syncBatchNo))
                .filter(Objects::nonNull)
                .toList();

        this.stockIntradayTrendInfluxManage.saveTrends(trends);
    }

    private void sleepForRateLimit() throws InterruptedException {
        if (this.requestIntervalMs > 0) {
            Thread.sleep(this.requestIntervalMs);
        }
    }

    private boolean isInSyncWindow() {
        LocalTime now = LocalTime.now(ZoneId.of(this.timezone));
        LocalTime start = LocalTime.parse(this.startTime);
        LocalTime end = LocalTime.parse(this.endTime);
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private BigDecimal previousClosePrice(JsonNode response, String tencentSymbol) {
        JsonNode quote = response.path("data").path(tencentSymbol).path("qt").path(tencentSymbol);
        return StockMarketJsonParser.decimal(quote.path(4).asText());
    }

    private String toTencentSymbol(String secid) {
        String[] parts = secid.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + secid);
        }
        return "1".equals(parts[0]) ? "sh" + parts[1] : "sz" + parts[1];
    }
}
