package com.scrapider.finance.ai.domain.vo;

public record TagCount(String categoryKey, String tagKey, long count,
                       double categoryPercentage, double totalPercentage) {
}
