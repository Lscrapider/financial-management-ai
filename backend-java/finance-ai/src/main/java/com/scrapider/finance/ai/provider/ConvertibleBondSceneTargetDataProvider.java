package com.scrapider.finance.ai.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.SceneTargetDataConverter;
import com.scrapider.finance.ai.domain.dto.ConvertibleBondSceneDataDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.service.SceneAssetDataEnsureService;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConvertibleBondSceneTargetDataProvider extends AbstractSceneTargetDataProvider implements SceneTargetDataProvider {

    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondConfigManage bondConfigManage;
    private final BondKlineManage bondKlineManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final SceneAssetDataEnsureService sceneAssetDataEnsureService;

    public ConvertibleBondSceneTargetDataProvider(
            ObjectMapper objectMapper,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondConfigManage bondConfigManage,
            BondKlineManage bondKlineManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            SceneAssetDataEnsureService sceneAssetDataEnsureService) {
        super(objectMapper);
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondConfigManage = bondConfigManage;
        this.bondKlineManage = bondKlineManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.sceneAssetDataEnsureService = sceneAssetDataEnsureService;
    }

    @Override
    public boolean supports(String targetType) {
        return "CONVERTIBLE_BOND".equals(targetType);
    }

    @Override
    public SceneAnalysisMessageDTO buildMessage(String taskNo, String bondCode, SceneAnalysisSubmitParam param) {
        List<String> missing = new ArrayList<>();
        BondQuoteSnapshotPO quote = this.bondQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                        .eq(BondQuoteSnapshotPO::getBondCode, bondCode)
                        .last("LIMIT 1"));
        if (quote == null) {
            missing.add("bond_quote_snapshot");
        }
        BondConfigPO config = this.bondConfigManage.getOne(
                new LambdaQueryWrapper<BondConfigPO>()
                        .eq(BondConfigPO::getBondCode, bondCode)
                        .last("LIMIT 1"));
        String targetName = this.firstNotBlank(
                param.targetName(),
                quote == null ? null : quote.getBondName(),
                config == null ? null : config.getBondName());
        SceneAnalysisTargetDTO target = SceneTargetDataConverter.bondTarget(bondCode, targetName, quote, config);
        List<Map<String, Object>> dailyKlines = this.queryBondKlines(
                bondCode,
                KlinePeriodTypeEnum.DAILY,
                "bond_kline",
                missing);
        List<Map<String, Object>> weeklyKlines = this.queryBondKlines(
                bondCode,
                KlinePeriodTypeEnum.WEEKLY,
                "bond_weekly_kline",
                missing);
        List<Map<String, Object>> monthlyKlines = this.queryBondKlines(
                bondCode,
                KlinePeriodTypeEnum.MONTHLY,
                "bond_monthly_kline",
                missing);
        return this.message(
                taskNo,
                param,
                target,
                this.toMap(quote),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                dailyKlines,
                weeklyKlines,
                monthlyKlines,
                List.of(),
                this.convertibleBondAssetSpecificData(bondCode, quote, missing),
                missing);
    }

    private List<Map<String, Object>> queryBondKlines(
            String bondCode,
            KlinePeriodTypeEnum periodType,
            String missingKey,
            List<String> missing) {
        List<Map<String, Object>> rows = this.bondKlineManage
                .listKlines(bondCode, null, periodType, null, null, MARKET_KLINE_LIMIT)
                .stream()
                .map(this::toMap)
                .toList();
        if (rows.isEmpty()) {
            missing.add(missingKey);
        }
        return rows;
    }

    private Map<String, Object> convertibleBondAssetSpecificData(
            String bondCode,
            BondQuoteSnapshotPO quote,
            List<String> missing) {
        BondConfigPO config = this.bondConfigManage.getEnabledByBondCode(bondCode);
        ConvertibleBondSceneDataDTO sceneData = this.sceneAssetDataEnsureService.ensureBondSceneData(
                config,
                quote,
                MARKET_KLINE_LIMIT,
                MARKET_KLINE_LIMIT);
        StockQuoteSnapshotPO underlyingQuote = this.underlyingQuote(sceneData.basic());

        BigDecimal bondPrice = quote == null ? null : quote.getLatestPrice();
        BigDecimal conversionPrice = sceneData.basic() == null ? null : sceneData.basic().getConversionPrice();
        BigDecimal underlyingPrice = underlyingQuote == null ? null : underlyingQuote.getLatestPrice();
        BigDecimal realtimeConversionValue = quote == null ? null : quote.getConversionValue();
        BigDecimal realtimePremiumRate = quote == null ? null : quote.getConversionPremiumRate();
        this.collectRealtimePremiumMissingInputs(missing, bondPrice, underlyingPrice, conversionPrice);

        return SceneTargetDataConverter.convertibleBondAssetSpecificData(
                this.objectMapper(),
                quote,
                sceneData.basic(),
                sceneData.latestValuation(),
                sceneData.latestShare(),
                sceneData.valuationHistory(),
                underlyingQuote,
                bondPrice,
                conversionPrice,
                underlyingPrice,
                realtimeConversionValue,
                realtimePremiumRate,
                this.estimatedYtm(bondPrice, sceneData.basic()),
                this.maturityDays(sceneData.basic()));
    }

    private StockQuoteSnapshotPO underlyingQuote(ConvertibleBondBasicPO basic) {
        String stockCode = this.normalizeStockCode(basic == null ? null : basic.getUnderlyingStockCode());
        if (stockCode == null) {
            return null;
        }
        return this.stockQuoteSnapshotManage.getOne(new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                .eq(StockQuoteSnapshotPO::getStockCode, stockCode)
                .last("LIMIT 1"));
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }
        String trimmed = stockCode.trim();
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex > 0) {
            return trimmed.substring(0, dotIndex);
        }
        if ((trimmed.startsWith("SH") || trimmed.startsWith("SZ")) && trimmed.length() > 2) {
            return trimmed.substring(2);
        }
        return trimmed;
    }

    private void collectRealtimePremiumMissingInputs(
            List<String> missing,
            BigDecimal bondPrice,
            BigDecimal underlyingPrice,
            BigDecimal conversionPrice) {
        if (bondPrice == null) {
            missing.add("convertible_bond.realtime_premium.bond_price");
        }
        if (underlyingPrice == null) {
            missing.add("convertible_bond.realtime_premium.underlying_price");
        }
        if (conversionPrice == null || conversionPrice.signum() <= 0) {
            missing.add("convertible_bond.realtime_premium.conversion_price");
        }
    }

    private Long maturityDays(ConvertibleBondBasicPO basic) {
        if (basic == null || basic.getMaturityDate() == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), basic.getMaturityDate());
    }

    private BigDecimal estimatedYtm(Object bondPriceValue, ConvertibleBondBasicPO basic) {
        if (!(bondPriceValue instanceof BigDecimal bondPrice)
                || basic == null
                || basic.getMaturityDate() == null
                || basic.getMaturityCallPrice() == null
                || bondPrice.signum() <= 0) {
            return null;
        }
        long days = this.maturityDays(basic);
        if (days <= 0) {
            return null;
        }
        // Tushare cb_daily 不直接给 YTM；这里先用到期赎回价做不含票息的保守近似，完整现金流 YTM 后续单独计算。
        double years = days / 365.0D;
        double value = Math.pow(basic.getMaturityCallPrice().doubleValue() / bondPrice.doubleValue(), 1.0D / years) - 1.0D;
        return BigDecimal.valueOf(value * 100.0D).setScale(4, RoundingMode.HALF_UP);
    }

}
