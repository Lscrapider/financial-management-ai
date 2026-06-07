package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.vo.CategoryTagDistribution;
import com.scrapider.finance.ai.domain.vo.KnowledgeOverviewVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeStatsVO;
import com.scrapider.finance.ai.domain.vo.TagCount;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KnowledgeConverter {

    private KnowledgeConverter() {
    }

    public static KnowledgeStatsVO stats(Map<String, Object> stats) {
        return new KnowledgeStatsVO(
                number(stats, "taskCount"),
                number(stats, "chunkCount"),
                number(stats, "totalTextLength"),
                (OffsetDateTime) stats.get("latestCreatedAt"));
    }

    public static KnowledgeOverviewVO overview(
            Map<String, Object> stats,
            List<Map<String, Object>> rawRows,
            Map<String, Set<String>> validTags) {
        long taskCount = number(stats, "taskCount");
        long chunkCount = number(stats, "chunkCount");
        long totalTextLength = number(stats, "totalTextLength");
        OffsetDateTime latestCreatedAt = (OffsetDateTime) stats.get("latestCreatedAt");
        List<CategoryTagDistribution> distributions = distributions(rawRows, validTags, chunkCount);
        return new KnowledgeOverviewVO(taskCount, chunkCount, totalTextLength, latestCreatedAt, distributions);
    }

    private static List<CategoryTagDistribution> distributions(
            List<Map<String, Object>> rawRows,
            Map<String, Set<String>> validTags,
            long chunkCount) {
        Map<String, Map<String, Long>> lookup = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            String category = (String) row.get("category");
            String tag = (String) row.get("tag");
            long count = ((Number) row.get("cnt")).longValue();
            lookup.computeIfAbsent(category, key -> new LinkedHashMap<>()).put(tag, count);
        }

        List<CategoryTagDistribution> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : validTags.entrySet()) {
            String categoryKey = entry.getKey();
            Map<String, Long> existing = lookup.getOrDefault(categoryKey, Map.of());
            long categoryTotal = existing.values().stream().mapToLong(Long::longValue).sum();
            result.add(new CategoryTagDistribution(
                    categoryKey,
                    tagCounts(categoryKey, entry.getValue(), existing, categoryTotal, chunkCount)));
        }
        return result;
    }

    private static List<TagCount> tagCounts(
            String categoryKey,
            Set<String> allTags,
            Map<String, Long> existing,
            long categoryTotal,
            long chunkCount) {
        List<TagCount> tagCounts = new ArrayList<>();
        for (String tagKey : allTags) {
            long count = existing.getOrDefault(tagKey, 0L);
            double categoryPercentage = categoryTotal > 0
                    ? Math.round(count * 10000.0 / categoryTotal) / 100.0
                    : 0.0;
            double totalPercentage = chunkCount > 0
                    ? Math.round(count * 10000.0 / chunkCount) / 100.0
                    : 0.0;
            tagCounts.add(new TagCount(categoryKey, tagKey, count, categoryPercentage, totalPercentage));
        }
        return tagCounts;
    }

    private static long number(Map<String, Object> stats, String key) {
        return ((Number) stats.get(key)).longValue();
    }
}
