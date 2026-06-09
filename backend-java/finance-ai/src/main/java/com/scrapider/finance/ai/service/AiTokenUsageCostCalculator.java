package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.config.AiTokenUsagePricingProperties;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageCostVO;
import com.scrapider.finance.domain.dto.AiTokenUsageCostSummaryDTO;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiTokenUsageCostCalculator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final int COST_SCALE = 8;

    private final AiTokenUsagePricingProperties pricingProperties;

    public AiTokenUsageCostCalculator(AiTokenUsagePricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    public AiTokenUsageCostVO calculate(AiTokenUsageLogPO log) {
        if (log == null) {
            return null;
        }
        int cacheHitTokens = this.cacheHitTokens(log);
        int cacheMissTokens = this.cacheMissTokens(log, cacheHitTokens);
        int outputTokens = nonNegative(log.getCompletionTokens());
        return this.calculate(log.getModel(), cacheHitTokens, cacheMissTokens, outputTokens);
    }

    public AiTokenUsageCostVO calculateTotal(List<AiTokenUsageCostSummaryDTO> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return new AiTokenUsageCostVO(
                    zeroCost(),
                    zeroCost(),
                    zeroCost(),
                    zeroCost(),
                    this.pricingProperties.getCurrency());
        }
        BigDecimal cacheHitInputCost = zeroCost();
        BigDecimal cacheMissInputCost = zeroCost();
        BigDecimal outputCost = zeroCost();
        for (AiTokenUsageCostSummaryDTO summary : summaries) {
            AiTokenUsageCostVO cost = this.calculate(summary);
            if (cost == null) {
                continue;
            }
            cacheHitInputCost = cacheHitInputCost.add(cost.cacheHitInputCost());
            cacheMissInputCost = cacheMissInputCost.add(cost.cacheMissInputCost());
            outputCost = outputCost.add(cost.outputCost());
        }
        BigDecimal totalCost = cacheHitInputCost
                .add(cacheMissInputCost)
                .add(outputCost)
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
        return new AiTokenUsageCostVO(
                cacheHitInputCost.setScale(COST_SCALE, RoundingMode.HALF_UP),
                cacheMissInputCost.setScale(COST_SCALE, RoundingMode.HALF_UP),
                outputCost.setScale(COST_SCALE, RoundingMode.HALF_UP),
                totalCost,
                this.pricingProperties.getCurrency());
    }

    private AiTokenUsageCostVO calculate(AiTokenUsageCostSummaryDTO summary) {
        if (summary == null) {
            return null;
        }
        long cacheHitTokens = this.cacheHitTokens(summary);
        long cacheMissTokens = this.cacheMissTokens(summary, cacheHitTokens);
        long outputTokens = nonNegative(summary.getCompletionTokens());
        return this.calculate(summary.getModel(), cacheHitTokens, cacheMissTokens, outputTokens);
    }

    private AiTokenUsageCostVO calculate(String model, long cacheHitTokens, long cacheMissTokens, long outputTokens) {
        AiTokenUsagePricingProperties.ModelPrice price = this.pricingProperties.priceOf(model);
        if (price == null) {
            return null;
        }
        BigDecimal cacheHitInputCost = cost(cacheHitTokens, price.getCacheHitInputYuanPerMillion());
        BigDecimal cacheMissInputCost = cost(cacheMissTokens, price.getCacheMissInputYuanPerMillion());
        BigDecimal outputCost = cost(outputTokens, price.getOutputYuanPerMillion());
        BigDecimal totalCost = cacheHitInputCost
                .add(cacheMissInputCost)
                .add(outputCost)
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
        return new AiTokenUsageCostVO(
                cacheHitInputCost,
                cacheMissInputCost,
                outputCost,
                totalCost,
                this.pricingProperties.getCurrency());
    }

    private int cacheHitTokens(AiTokenUsageLogPO log) {
        int promptCacheHitTokens = nonNegative(log.getPromptCacheHitTokens());
        if (promptCacheHitTokens > 0) {
            return promptCacheHitTokens;
        }
        return nonNegative(log.getCachedTokens());
    }

    private int cacheMissTokens(AiTokenUsageLogPO log, int cacheHitTokens) {
        int promptCacheMissTokens = nonNegative(log.getPromptCacheMissTokens());
        if (promptCacheMissTokens > 0) {
            return promptCacheMissTokens;
        }
        return Math.max(nonNegative(log.getPromptTokens()) - cacheHitTokens, 0);
    }

    private long cacheHitTokens(AiTokenUsageCostSummaryDTO summary) {
        long promptCacheHitTokens = nonNegative(summary.getPromptCacheHitTokens());
        if (promptCacheHitTokens > 0) {
            return promptCacheHitTokens;
        }
        return nonNegative(summary.getCachedTokens());
    }

    private long cacheMissTokens(AiTokenUsageCostSummaryDTO summary, long cacheHitTokens) {
        long promptCacheMissTokens = nonNegative(summary.getPromptCacheMissTokens());
        if (promptCacheMissTokens > 0) {
            return promptCacheMissTokens;
        }
        return Math.max(nonNegative(summary.getPromptTokens()) - cacheHitTokens, 0L);
    }

    private static BigDecimal cost(long tokens, BigDecimal yuanPerMillionTokens) {
        if (tokens <= 0 || yuanPerMillionTokens == null) {
            return zeroCost();
        }
        return BigDecimal.valueOf(tokens)
                .multiply(yuanPerMillionTokens)
                .divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private static int nonNegative(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    private static long nonNegative(Long value) {
        if (value == null || value < 0L) {
            return 0L;
        }
        return value;
    }

    private static BigDecimal zeroCost() {
        return BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }
}
