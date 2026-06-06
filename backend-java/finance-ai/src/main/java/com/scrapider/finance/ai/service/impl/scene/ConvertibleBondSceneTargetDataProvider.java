package com.scrapider.finance.ai.service.impl.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConvertibleBondSceneTargetDataProvider extends AbstractSceneTargetDataProvider implements SceneTargetDataProvider {

    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondConfigManage bondConfigManage;
    private final BondKlineManage bondKlineManage;

    public ConvertibleBondSceneTargetDataProvider(
            ObjectMapper objectMapper,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondConfigManage bondConfigManage,
            BondKlineManage bondKlineManage) {
        super(objectMapper);
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondConfigManage = bondConfigManage;
        this.bondKlineManage = bondKlineManage;
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
        SceneAnalysisTargetDTO target = new SceneAnalysisTargetDTO(
                "CONVERTIBLE_BOND",
                bondCode,
                targetName,
                this.firstNotBlank(quote == null ? null : quote.getSecid(), config == null ? null : config.getSecid()),
                this.firstNotBlank(
                        quote == null ? null : quote.getMarketCode(),
                        config == null ? null : config.getMarketCode()),
                this.firstNotBlank(
                        quote == null ? null : quote.getExchangeCode(),
                        config == null ? null : config.getExchangeCode()));
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
                this.convertibleBondAssetSpecificData(quote),
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

    private Map<String, Object> convertibleBondAssetSpecificData(BondQuoteSnapshotPO quote) {
        if (quote == null) {
            return Map.of("convertibleBond", Map.of());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bondPrice", quote.getLatestPrice());
        data.put("turnoverRate", quote.getTurnoverRate());
        data.put("bondRating", quote.getBondRating());
        // 可转债专属字段契约：当前数据源未全部接入，补齐后 Python 会按这些 key 直接读取。
        data.put("premiumRate", null);
        data.put("premiumRateHistory", List.of());
        data.put("conversionValue", null);
        data.put("conversionValueHistory", List.of());
        data.put("pureBondValue", null);
        data.put("ytm", null);
        data.put("remainingSize", null);
        data.put("maturityDays", null);
        data.put("redeemStatus", null);
        data.put("redeemTriggerProgress", null);
        data.put("putbackStatus", null);
        data.put("underlyingStockCode", null);
        data.put("underlyingStockName", null);
        data.put("underlyingQuote", Map.of());
        data.put("underlyingDailyKlines", List.of());
        data.put("underlyingChangePct", null);
        data.put("underlyingTrendScore", null);
        data.put("stockBondLinkage", null);
        return Map.of("convertibleBond", this.compactMap(data));
    }
}
