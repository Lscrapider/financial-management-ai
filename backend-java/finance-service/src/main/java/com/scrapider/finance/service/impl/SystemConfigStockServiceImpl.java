package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.converter.SystemConfigConverter;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.param.StockConfigAddParam;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.vo.StockConfigAddResultVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.SystemConfigStockService;
import com.scrapider.finance.task.StockMarketSyncTask;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigStockServiceImpl implements SystemConfigStockService {

    private final StockMarketApi stockMarketApi;
    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockMarketSyncTask stockMarketSyncTask;

    public SystemConfigStockServiceImpl(
            StockMarketApi stockMarketApi,
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockMarketSyncTask stockMarketSyncTask) {
        this.stockMarketApi = stockMarketApi;
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockMarketSyncTask = stockMarketSyncTask;
    }

    @Override
    public StockConfigAddResultVO addStock(StockConfigAddParam param) {
        String stockCode = this.normalizeStockCode(param);
        String stockName = this.normalizeStockName(param);
        StockConfigPO stock = this.buildStockConfig(stockCode, stockName);

        StockQuoteSnapshotPO snapshot = this.fetchAndValidateQuote(stock, stockCode, stockName);
        this.stockConfigManage.saveConfig(stock);
        this.stockQuoteSnapshotManage.saveLatest(snapshot);

        boolean trendSynced = this.stockMarketSyncTask.syncStockTrend(stockCode);
        return StockConfigAddResultVO.of(StockQuoteVO.fromPO(snapshot), trendSynced);
    }

    private StockQuoteSnapshotPO fetchAndValidateQuote(
            StockConfigPO stock,
            String expectedCode,
            String expectedName) {
        StockMarketDataDTO quote = this.stockMarketApi.getQuote(stock.getSecid());
        String rawResponse = quote.data().asText();
        String[] fields = StockQuoteSnapshotPO.extractTencentFields(rawResponse);
        if (fields.length == 0) {
            throw new IllegalArgumentException("腾讯快照未返回有效股票数据: " + expectedCode);
        }

        StockQuoteSnapshotPO snapshot = StockQuoteSnapshotPO.fromApiResponse(stock, quote.data());
        if (!expectedCode.equals(snapshot.getStockCode())) {
            throw new IllegalArgumentException("股票代码校验失败，腾讯返回代码: " + snapshot.getStockCode());
        }
        if (!expectedName.equals(snapshot.getStockName())) {
            throw new IllegalArgumentException("股票名称校验失败，腾讯返回名称: " + snapshot.getStockName());
        }
        return snapshot;
    }

    private String normalizeStockCode(StockConfigAddParam param) {
        String stockCode = param == null ? null : StrUtil.trim(param.getStockCode());
        if (!StrUtil.isNumeric(stockCode) || stockCode.length() != 6) {
            throw new IllegalArgumentException("股票代码必须是 6 位数字");
        }
        return stockCode;
    }

    private String normalizeStockName(StockConfigAddParam param) {
        String stockName = param == null ? null : StrUtil.trim(param.getStockName());
        if (StrUtil.isBlank(stockName)) {
            throw new IllegalArgumentException("股票名称不能为空");
        }
        return stockName;
    }

    private StockConfigPO buildStockConfig(String stockCode, String stockName) {
        return SystemConfigConverter.toStockConfig(
                stockCode,
                stockName,
                this.exchangeCodeOf(stockCode),
                this.marketCodeOf(stockCode),
                this.secidOf(stockCode));
    }

    private String exchangeCodeOf(String stockCode) {
        if (stockCode.startsWith("6")) {
            return "SH";
        }
        if (stockCode.startsWith("0") || stockCode.startsWith("3")) {
            return "SZ";
        }
        throw new IllegalArgumentException("暂只支持沪深 A 股股票代码");
    }

    private String marketCodeOf(String stockCode) {
        // 与 stock_config 现有市场编码保持一致，避免新增股票影响行情筛选。
        if (stockCode.startsWith("688")) {
            return "STAR";
        }
        if (stockCode.startsWith("300") || stockCode.startsWith("301")) {
            return "CHINEXT";
        }
        if (stockCode.startsWith("6")) {
            return "SH_MAIN";
        }
        if (stockCode.startsWith("0")) {
            return "SZ_MAIN";
        }
        throw new IllegalArgumentException("暂只支持沪深 A 股股票代码");
    }

    private String secidOf(String stockCode) {
        return "SH".equals(this.exchangeCodeOf(stockCode))
                ? "1." + stockCode
                : "0." + stockCode;
    }
}
