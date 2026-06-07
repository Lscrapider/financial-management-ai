package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.converter.SystemConfigConverter;
import com.scrapider.finance.domain.param.BondConfigAddParam;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.vo.BondConfigAddResultVO;
import com.scrapider.finance.domain.vo.StockConfigAddResultVO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.service.ConvertibleBondDataProvider;
import com.scrapider.finance.service.SystemConfigBondService;
import com.scrapider.finance.service.SystemConfigStockService;
import com.scrapider.finance.task.BondMarketSyncTask;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigBondServiceImpl implements SystemConfigBondService {

    private final ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider;
    private final BondConfigManage bondConfigManage;
    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final SystemConfigStockService systemConfigStockService;
    private final BondMarketSyncTask bondMarketSyncTask;

    public SystemConfigBondServiceImpl(
            ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider,
            BondConfigManage bondConfigManage,
            ConvertibleBondBasicManage convertibleBondBasicManage,
            SystemConfigStockService systemConfigStockService,
            BondMarketSyncTask bondMarketSyncTask) {
        this.convertibleBondDataProvider = convertibleBondDataProvider;
        this.bondConfigManage = bondConfigManage;
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.systemConfigStockService = systemConfigStockService;
        this.bondMarketSyncTask = bondMarketSyncTask;
    }

    @Override
    public BondConfigAddResultVO addBond(BondConfigAddParam param) {
        String bondCode = this.normalizeBondCode(param);
        String bondName = this.normalizeBondName(param);
        BondConfigPO bond = this.buildBondConfig(bondCode, bondName);

        ConvertibleBondBasicPO basic = this.fetchAndValidateBasic(bond, bondCode, bondName);
        this.bondConfigManage.saveConfig(bond);
        this.convertibleBondBasicManage.saveBasic(basic);

        StockConfigAddResultVO underlyingStock = this.syncUnderlyingStock(basic);
        boolean marketDataSynced = this.bondMarketSyncTask.syncMarketDataForBond(bondCode);
        boolean dailyValuationSynced = this.bondMarketSyncTask.syncConvertibleDailyDataForBond(bondCode);
        return this.toResult(bond, basic, underlyingStock, marketDataSynced, dailyValuationSynced);
    }

    private ConvertibleBondBasicPO fetchAndValidateBasic(
            BondConfigPO bond,
            String expectedCode,
            String expectedName) {
        ConvertibleBondDataProvider provider = this.convertibleBondDataProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalArgumentException("可转债 Tushare 数据源未启用");
        }
        ConvertibleBondBasicPO basic = provider.getBasic(bond);
        if (basic == null) {
            throw new IllegalArgumentException("Tushare 未返回可转债基础资料: " + expectedCode);
        }
        if (!expectedCode.equals(basic.getBondCode())) {
            throw new IllegalArgumentException("可转债代码校验失败，Tushare 返回代码: " + basic.getBondCode());
        }
        if (!expectedName.equals(basic.getBondName())) {
            throw new IllegalArgumentException("可转债名称校验失败，Tushare 返回名称: " + basic.getBondName());
        }
        return basic;
    }

    private StockConfigAddResultVO syncUnderlyingStock(ConvertibleBondBasicPO basic) {
        String stockCode = this.normalizeStockCode(basic.getUnderlyingStockCode());
        String stockName = StrUtil.trim(basic.getUnderlyingStockName());
        if (StrUtil.isBlank(stockCode) || StrUtil.isBlank(stockName)) {
            throw new IllegalArgumentException("可转债基础资料缺少正股代码或名称");
        }
        return this.systemConfigStockService.addStock(SystemConfigConverter.toStockConfigAddParam(stockCode, stockName));
    }

    private BondConfigAddResultVO toResult(
            BondConfigPO bond,
            ConvertibleBondBasicPO basic,
            StockConfigAddResultVO underlyingStock,
            boolean marketDataSynced,
            boolean dailyValuationSynced) {
        return SystemConfigConverter.toBondConfigAddResult(
                bond,
                basic,
                this.normalizeStockCode(basic.getUnderlyingStockCode()),
                underlyingStock,
                marketDataSynced,
                dailyValuationSynced);
    }

    private String normalizeBondCode(BondConfigAddParam param) {
        String bondCode = param == null ? null : StrUtil.trim(param.getBondCode());
        if (!StrUtil.isNumeric(bondCode) || bondCode.length() != 6) {
            throw new IllegalArgumentException("可转债代码必须是 6 位数字");
        }
        return bondCode;
    }

    private String normalizeBondName(BondConfigAddParam param) {
        String bondName = param == null ? null : StrUtil.trim(param.getBondName());
        if (StrUtil.isBlank(bondName)) {
            throw new IllegalArgumentException("可转债名称不能为空");
        }
        return bondName;
    }

    private BondConfigPO buildBondConfig(String bondCode, String bondName) {
        return SystemConfigConverter.toBondConfig(
                bondCode,
                bondName,
                this.exchangeCodeOf(bondCode),
                this.secidOf(bondCode));
    }

    private String exchangeCodeOf(String bondCode) {
        if (bondCode.startsWith("110")
                || bondCode.startsWith("111")
                || bondCode.startsWith("113")
                || bondCode.startsWith("118")) {
            return "SH";
        }
        if (bondCode.startsWith("123")
                || bondCode.startsWith("127")
                || bondCode.startsWith("128")) {
            return "SZ";
        }
        throw new IllegalArgumentException("暂只支持沪深可转债代码");
    }

    private String secidOf(String bondCode) {
        return "SH".equals(this.exchangeCodeOf(bondCode))
                ? "1." + bondCode
                : "0." + bondCode;
    }

    private String normalizeStockCode(String stockCode) {
        if (StrUtil.isBlank(stockCode)) {
            return null;
        }
        String trimmed = stockCode.trim();
        int dotIndex = trimmed.indexOf('.');
        return dotIndex > 0 ? trimmed.substring(0, dotIndex) : trimmed;
    }
}
