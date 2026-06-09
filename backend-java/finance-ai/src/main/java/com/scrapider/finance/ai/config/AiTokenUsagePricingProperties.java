package com.scrapider.finance.ai.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "finance.ai-token-usage.pricing")
public class AiTokenUsagePricingProperties {

    private String currency = "CNY";
    private Map<String, ModelPrice> models = defaultModels();

    public ModelPrice priceOf(String model) {
        if (model == null) {
            return null;
        }
        return this.models.get(model.trim().toLowerCase(Locale.ROOT));
    }

    @Data
    public static class ModelPrice {

        private BigDecimal cacheHitInputYuanPerMillion = BigDecimal.ZERO;
        private BigDecimal cacheMissInputYuanPerMillion = BigDecimal.ZERO;
        private BigDecimal outputYuanPerMillion = BigDecimal.ZERO;
    }

    private static Map<String, ModelPrice> defaultModels() {
        Map<String, ModelPrice> defaults = new LinkedHashMap<>();
        defaults.put("deepseek-v4-flash", modelPrice("0.02", "1", "2"));
        defaults.put("deepseek-v4-pro", modelPrice("0.025", "3", "6"));
        return defaults;
    }

    private static ModelPrice modelPrice(String cacheHitInput, String cacheMissInput, String output) {
        ModelPrice modelPrice = new ModelPrice();
        modelPrice.setCacheHitInputYuanPerMillion(new BigDecimal(cacheHitInput));
        modelPrice.setCacheMissInputYuanPerMillion(new BigDecimal(cacheMissInput));
        modelPrice.setOutputYuanPerMillion(new BigDecimal(output));
        return modelPrice;
    }
}
