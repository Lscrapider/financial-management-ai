package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scrapider.finance.domain.param.TargetDeleteParam;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.domain.po.WatchGroupItemPO;
import com.scrapider.finance.domain.vo.TargetDeleteResultVO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondIntradayTrendInfluxManage;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.ConvertibleBondShareManage;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexIntradayTrendInfluxManage;
import com.scrapider.finance.manage.IndexKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.SceneAnalysisReportManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import com.scrapider.finance.manage.StockAlertConfigManage;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockDividendHistoryManage;
import com.scrapider.finance.manage.StockFinancialIndicatorManage;
import com.scrapider.finance.manage.StockIndustryInfoManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockKlineManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.manage.StockValuationHistoryManage;
import com.scrapider.finance.manage.WatchGroupItemManage;
import com.scrapider.finance.service.SystemConfigTargetDeleteService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigTargetDeleteServiceImpl implements SystemConfigTargetDeleteService {

    private static final String TYPE_STOCK = "STOCK";
    private static final String TYPE_INDEX = "INDEX";
    private static final String TYPE_BOND = "BOND";
    private static final String TYPE_CONVERTIBLE_BOND = "CONVERTIBLE_BOND";

    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockKlineManage stockKlineManage;
    private final StockIndustryInfoManage stockIndustryInfoManage;
    private final StockValuationHistoryManage stockValuationHistoryManage;
    private final StockFinancialIndicatorManage stockFinancialIndicatorManage;
    private final StockDividendHistoryManage stockDividendHistoryManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final IndexConfigManage indexConfigManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexKlineManage indexKlineManage;
    private final IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage;
    private final BondConfigManage bondConfigManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondKlineManage bondKlineManage;
    private final BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage;
    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;
    private final ConvertibleBondShareManage convertibleBondShareManage;
    private final StockAlertConfigManage stockAlertConfigManage;
    private final WatchGroupItemManage watchGroupItemManage;
    private final SceneAnalysisReportManage sceneAnalysisReportManage;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;

    public SystemConfigTargetDeleteServiceImpl(
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockKlineManage stockKlineManage,
            StockIndustryInfoManage stockIndustryInfoManage,
            StockValuationHistoryManage stockValuationHistoryManage,
            StockFinancialIndicatorManage stockFinancialIndicatorManage,
            StockDividendHistoryManage stockDividendHistoryManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            IndexConfigManage indexConfigManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexKlineManage indexKlineManage,
            IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage,
            BondConfigManage bondConfigManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondKlineManage bondKlineManage,
            BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage,
            ConvertibleBondBasicManage convertibleBondBasicManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage,
            ConvertibleBondShareManage convertibleBondShareManage,
            StockAlertConfigManage stockAlertConfigManage,
            WatchGroupItemManage watchGroupItemManage,
            SceneAnalysisReportManage sceneAnalysisReportManage,
            SceneAnalysisTaskManage sceneAnalysisTaskManage) {
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockKlineManage = stockKlineManage;
        this.stockIndustryInfoManage = stockIndustryInfoManage;
        this.stockValuationHistoryManage = stockValuationHistoryManage;
        this.stockFinancialIndicatorManage = stockFinancialIndicatorManage;
        this.stockDividendHistoryManage = stockDividendHistoryManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.indexConfigManage = indexConfigManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexKlineManage = indexKlineManage;
        this.indexIntradayTrendInfluxManage = indexIntradayTrendInfluxManage;
        this.bondConfigManage = bondConfigManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondKlineManage = bondKlineManage;
        this.bondIntradayTrendInfluxManage = bondIntradayTrendInfluxManage;
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
        this.convertibleBondShareManage = convertibleBondShareManage;
        this.stockAlertConfigManage = stockAlertConfigManage;
        this.watchGroupItemManage = watchGroupItemManage;
        this.sceneAnalysisReportManage = sceneAnalysisReportManage;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
    }

    @Override
    @Transactional
    public TargetDeleteResultVO deleteTarget(TargetDeleteParam param) {
        String targetType = this.normalizeTargetType(param);
        String targetCode = this.normalizeTargetCode(param);
        switch (targetType) {
            case TYPE_STOCK -> this.deleteStock(targetCode);
            case TYPE_INDEX -> this.deleteIndex(targetCode);
            case TYPE_BOND -> this.deleteBond(targetCode);
            default -> throw new IllegalArgumentException("targetType must be one of STOCK, INDEX, BOND.");
        }
        return TargetDeleteResultVO.of(targetType, targetCode);
    }

    private void deleteStock(String stockCode) {
        this.deleteCommonTargetData(List.of(TYPE_STOCK), stockCode);
        this.stockQuoteSnapshotManage.remove(new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                .eq(StockQuoteSnapshotPO::getStockCode, stockCode));
        this.stockKlineManage.remove(new LambdaQueryWrapper<StockKlinePO>()
                .eq(StockKlinePO::getStockCode, stockCode));
        this.stockIndustryInfoManage.remove(new LambdaQueryWrapper<StockIndustryInfoPO>()
                .eq(StockIndustryInfoPO::getStockCode, stockCode));
        this.stockValuationHistoryManage.remove(new LambdaQueryWrapper<StockValuationHistoryPO>()
                .eq(StockValuationHistoryPO::getStockCode, stockCode));
        this.stockFinancialIndicatorManage.remove(new LambdaQueryWrapper<StockFinancialIndicatorPO>()
                .eq(StockFinancialIndicatorPO::getStockCode, stockCode));
        this.stockDividendHistoryManage.remove(new LambdaQueryWrapper<StockDividendHistoryPO>()
                .eq(StockDividendHistoryPO::getStockCode, stockCode));
        this.stockConfigManage.remove(new LambdaQueryWrapper<StockConfigPO>()
                .eq(StockConfigPO::getStockCode, stockCode));
        this.stockIntradayTrendInfluxManage.deleteByStockCode(stockCode);
    }

    private void deleteIndex(String indexCode) {
        this.deleteCommonTargetData(List.of(TYPE_INDEX), indexCode);
        this.indexQuoteSnapshotManage.remove(new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                .eq(IndexQuoteSnapshotPO::getIndexCode, indexCode));
        this.indexKlineManage.remove(new LambdaQueryWrapper<IndexKlinePO>()
                .eq(IndexKlinePO::getIndexCode, indexCode));
        this.indexConfigManage.remove(new LambdaQueryWrapper<IndexConfigPO>()
                .eq(IndexConfigPO::getIndexCode, indexCode));
        this.indexIntradayTrendInfluxManage.deleteByIndexCode(indexCode);
    }

    private void deleteBond(String bondCode) {
        this.deleteCommonTargetData(List.of(TYPE_BOND, TYPE_CONVERTIBLE_BOND), bondCode);
        this.bondQuoteSnapshotManage.remove(new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                .eq(BondQuoteSnapshotPO::getBondCode, bondCode));
        this.bondKlineManage.remove(new LambdaQueryWrapper<BondKlinePO>()
                .eq(BondKlinePO::getBondCode, bondCode));
        this.convertibleBondBasicManage.remove(new LambdaQueryWrapper<ConvertibleBondBasicPO>()
                .eq(ConvertibleBondBasicPO::getBondCode, bondCode));
        this.convertibleBondDailyValuationManage.remove(new LambdaQueryWrapper<ConvertibleBondDailyValuationPO>()
                .eq(ConvertibleBondDailyValuationPO::getBondCode, bondCode));
        this.convertibleBondShareManage.remove(new LambdaQueryWrapper<ConvertibleBondSharePO>()
                .eq(ConvertibleBondSharePO::getBondCode, bondCode));
        this.bondConfigManage.remove(new LambdaQueryWrapper<BondConfigPO>()
                .eq(BondConfigPO::getBondCode, bondCode));
        this.bondIntradayTrendInfluxManage.deleteByBondCode(bondCode);
    }

    private void deleteCommonTargetData(List<String> targetTypes, String targetCode) {
        this.stockAlertConfigManage.remove(new LambdaQueryWrapper<StockAlertConfigPO>()
                .in(StockAlertConfigPO::getTargetType, targetTypes)
                .eq(StockAlertConfigPO::getStockCode, targetCode));
        this.watchGroupItemManage.remove(new LambdaQueryWrapper<WatchGroupItemPO>()
                .in(WatchGroupItemPO::getTargetType, targetTypes)
                .eq(WatchGroupItemPO::getTargetCode, targetCode));
        this.sceneAnalysisReportManage.remove(new LambdaQueryWrapper<SceneAnalysisReportPO>()
                .in(SceneAnalysisReportPO::getTargetType, targetTypes)
                .eq(SceneAnalysisReportPO::getTargetCode, targetCode));
        this.sceneAnalysisTaskManage.remove(new LambdaQueryWrapper<SceneAnalysisTaskPO>()
                .in(SceneAnalysisTaskPO::getTargetType, targetTypes)
                .eq(SceneAnalysisTaskPO::getTargetCode, targetCode));
    }

    private String normalizeTargetType(TargetDeleteParam param) {
        String targetType = param == null ? null : StrUtil.trim(param.getTargetType());
        if (StrUtil.isBlank(targetType)) {
            throw new IllegalArgumentException("targetType must not be blank.");
        }
        String normalized = targetType.toUpperCase();
        if (TYPE_CONVERTIBLE_BOND.equals(normalized)) {
            return TYPE_BOND;
        }
        return normalized;
    }

    private String normalizeTargetCode(TargetDeleteParam param) {
        String targetCode = param == null ? null : StrUtil.trim(param.getTargetCode());
        if (StrUtil.isBlank(targetCode)) {
            throw new IllegalArgumentException("targetCode must not be blank.");
        }
        return targetCode;
    }
}
