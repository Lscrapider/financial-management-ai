package com.scrapider.finance.domain.util;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class StockMarketJsonParser {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private StockMarketJsonParser() {
    }

    public static String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        return value.asText(defaultValue);
    }

    public static BigDecimal price(JsonNode node, String fieldName) {
        BigDecimal value = decimal(node, fieldName);
        if (value == null) {
            return null;
        }
        return value.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentLike(JsonNode node, String fieldName) {
        BigDecimal value = decimal(node, fieldName);
        if (value == null) {
            return null;
        }
        return value.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || "-".equals(value.asText())) {
            return null;
        }
        return decimal(value.asText());
    }

    public static BigDecimal decimal(String value) {
        if (StrUtil.isBlank(value) || "-".equals(value.trim())) {
            return null;
        }
        return NumberUtil.toBigDecimal(value.trim());
    }

    public static Long longValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || "-".equals(value.asText())) {
            return null;
        }
        return value.asLong();
    }

    public static Long longValue(String value) {
        if (StrUtil.isBlank(value) || "-".equals(value.trim())) {
            return null;
        }
        return NumberUtil.toBigDecimal(value.trim()).longValue();
    }

    public static Integer intValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || "-".equals(value.asText())) {
            return null;
        }
        return value.asInt();
    }

    public static Integer intValue(String value) {
        if (StrUtil.isBlank(value) || "-".equals(value.trim())) {
            return null;
        }
        return NumberUtil.toBigDecimal(value.trim()).intValue();
    }
}
