package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record CategoryTagDistribution(String categoryKey, List<TagCount> tags) {
}