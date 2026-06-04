package com.scrapider.finance.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.dto.KnowledgeReembedMessageDTO;
import com.scrapider.finance.ai.domain.vo.CategoryTagDistribution;
import com.scrapider.finance.ai.domain.vo.KnowledgeChunkPageVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeChunkVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeOverviewVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeStatsVO;
import com.scrapider.finance.ai.domain.vo.TagCount;
import com.scrapider.finance.ai.service.KnowledgeService;
import com.scrapider.finance.ai.service.OcrTaskMessagePublisher;
import com.scrapider.finance.domain.param.KnowledgeChunkUpdateParam;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrTaskManage;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "asset", "price", "volume", "trend", "valuation", "sentiment", "risk_strategy");

    private static final Map<String, Set<String>> VALID_TAGS = Map.of(
            "asset", Set.of("general", "stock", "index", "convertible_bond", "fund",
                    "bank_stock", "low_price_stock", "large_cap_stock", "small_cap_stock"),
            "price", Set.of("price_rise", "price_drop", "sideways", "near_recent_high",
                    "near_recent_low", "breakout", "pullback", "gap_up", "gap_down"),
            "volume", Set.of("volume_expand", "volume_shrink", "high_turnover",
                    "low_turnover", "volume_price_confirm", "volume_price_divergence",
                    "volume_spike", "volume_dry_up"),
            "trend", Set.of("uptrend", "downtrend", "range_bound", "rebound",
                    "pullback", "repair", "trend_reversal", "breakout_from_range",
                    "breakdown_from_range", "continuation", "turn_weak", "turn_strong",
                    "failed_breakout"),
            "valuation", Set.of("low_pe", "high_pe", "low_pb", "high_pb",
                    "high_dividend", "valuation_repair", "valuation_trap", "fundamental_risk"),
            "sentiment", Set.of("market_attention_rise", "short_term_emotion",
                    "panic_selling", "news_driven", "policy_driven", "sector_rotation",
                    "weak_sentiment", "herding_effect", "institutional_behavior"),
            "risk_strategy", Set.of("chase_high_risk", "false_breakout_risk",
                    "liquidity_risk", "drawdown_risk", "valuation_trap_risk",
                    "overheated_risk", "risk_control", "position_control", "wait_confirm",
                    "observe_next_day", "avoid_emotional_trade", "take_profit_plan",
                    "stop_loss_plan"));

    private final KnowledgeVectorManage knowledgeVectorManage;
    private final OcrTaskManage ocrTaskManage;
    private final OcrTaskMessagePublisher ocrTaskMessagePublisher;
    private final ObjectMapper objectMapper;

    public KnowledgeServiceImpl(
            KnowledgeVectorManage knowledgeVectorManage,
            OcrTaskManage ocrTaskManage,
            OcrTaskMessagePublisher ocrTaskMessagePublisher,
            ObjectMapper objectMapper) {
        this.knowledgeVectorManage = knowledgeVectorManage;
        this.ocrTaskManage = ocrTaskManage;
        this.ocrTaskMessagePublisher = ocrTaskMessagePublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public KnowledgeStatsVO stats() {
        Map<String, Object> stats = this.knowledgeVectorManage.stats();
        return new KnowledgeStatsVO(
                ((Number) stats.get("taskCount")).longValue(),
                ((Number) stats.get("chunkCount")).longValue(),
                ((Number) stats.get("totalTextLength")).longValue(),
                (OffsetDateTime) stats.get("latestCreatedAt"));
    }

    @Override
    public KnowledgeOverviewVO overview() {
        Map<String, Object> stats = this.knowledgeVectorManage.stats();
        long taskCount = ((Number) stats.get("taskCount")).longValue();
        long chunkCount = ((Number) stats.get("chunkCount")).longValue();
        long totalTextLength = ((Number) stats.get("totalTextLength")).longValue();
        OffsetDateTime latestCreatedAt = (OffsetDateTime) stats.get("latestCreatedAt");

        List<Map<String, Object>> rawRows = this.knowledgeVectorManage.tagDistribution();

        Map<String, Map<String, Long>> lookup = new LinkedHashMap<>();
        for (var row : rawRows) {
            String cat = (String) row.get("category");
            String tag = (String) row.get("tag");
            long cnt = ((Number) row.get("cnt")).longValue();
            lookup.computeIfAbsent(cat, k -> new LinkedHashMap<>()).put(tag, cnt);
        }

        List<CategoryTagDistribution> distributions = new ArrayList<>();
        for (var catEntry : VALID_TAGS.entrySet()) {
            String categoryKey = catEntry.getKey();
            Set<String> allTags = catEntry.getValue();
            Map<String, Long> existing = lookup.getOrDefault(categoryKey, Map.of());
            long categoryTotal = existing.values().stream().mapToLong(Long::longValue).sum();

            List<TagCount> tagCounts = new ArrayList<>();
            for (String tagKey : allTags) {
                long count = existing.getOrDefault(tagKey, 0L);
                double catPct = categoryTotal > 0
                        ? Math.round(count * 10000.0 / categoryTotal) / 100.0
                        : 0.0;
                double totalPct = chunkCount > 0
                        ? Math.round(count * 10000.0 / chunkCount) / 100.0
                        : 0.0;
                tagCounts.add(new TagCount(categoryKey, tagKey, count, catPct, totalPct));
            }
            distributions.add(new CategoryTagDistribution(categoryKey, tagCounts));
        }

        return new KnowledgeOverviewVO(
                taskCount, chunkCount, totalTextLength, latestCreatedAt, distributions);
    }

    @Override
    public KnowledgeChunkPageVO pageChunks(int pageNum, int pageSize, String filename, String sourceType,
            String category, String tag) {
        int pn = Math.max(pageNum, DEFAULT_PAGE_NUM);
        int ps = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        Set<String> taskNos = null;
        if (sourceType != null && !sourceType.isBlank()) {
            List<String> matched = this.ocrTaskManage.listTaskNosBySourceType(sourceType.trim());
            if (matched.isEmpty()) {
                return KnowledgeChunkPageVO.fromPage(
                        new Page<>(pn, ps), Map.of());
            }
            taskNos = new HashSet<>(matched);
        }
        if (filename != null && !filename.isBlank()) {
            List<String> matched = this.ocrTaskManage.listTaskNosByFilenameLike(filename.trim());
            if (matched.isEmpty()) {
                return KnowledgeChunkPageVO.fromPage(
                        new Page<>(pn, ps), Map.of());
            }
            if (taskNos == null) {
                taskNos = new HashSet<>(matched);
            } else {
                taskNos.retainAll(matched);
                if (taskNos.isEmpty()) {
                    return KnowledgeChunkPageVO.fromPage(
                            new Page<>(pn, ps), Map.of());
                }
            }
        }
        String trimmedCategory = category != null ? category.trim() : null;
        if (trimmedCategory != null && !trimmedCategory.isBlank() && !ALLOWED_CATEGORIES.contains(trimmedCategory)) {
            throw new IllegalArgumentException("未知的场景类别: " + trimmedCategory);
        }
        List<String> tags = null;
        if (tag != null && !tag.isBlank()) {
            tags = Arrays.stream(tag.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isBlank())
                    .toList();
            for (String t : tags) {
                boolean valid;
                if (trimmedCategory != null && !trimmedCategory.isBlank()) {
                    valid = VALID_TAGS.getOrDefault(trimmedCategory, Set.of()).contains(t);
                } else {
                    valid = VALID_TAGS.values().stream().anyMatch(s -> s.contains(t));
                }
                if (!valid) {
                    throw new IllegalArgumentException("未知的标签: " + t);
                }
            }
        }
        Page<KnowledgeVectorPO> page = this.knowledgeVectorManage.pageChunks(
                pn, ps, taskNos, trimmedCategory, tags);
        Map<String, String> filenameMap = this.filenameMap(page);
        return KnowledgeChunkPageVO.fromPage(page, filenameMap);
    }

    @Override
    public KnowledgeChunkVO chunkDetail(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        KnowledgeVectorPO po = this.knowledgeVectorManage.findById(id);
        if (po == null) {
            throw new IllegalArgumentException("知识条目不存在");
        }
        return KnowledgeChunkVO.fromPO(po, this.originalFilename(po.getTaskNo()));
    }

    @Override
    public KnowledgeChunkVO updateChunk(Long id, KnowledgeChunkUpdateParam param) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        if (param == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        KnowledgeVectorPO po = this.knowledgeVectorManage.findById(id);
        if (po == null) {
            throw new IllegalArgumentException("知识条目不存在");
        }
        String chunkId = null;
        if (po.getMetadata() != null && po.getMetadata().has("chunkId")) {
            chunkId = po.getMetadata().get("chunkId").asText();
        }
        // 更新文本
        if (param.getText() != null && !param.getText().isBlank()) {
            this.knowledgeVectorManage.updateText(id, param.getText());
            if (param.isReembed() && chunkId != null) {
                this.ocrTaskMessagePublisher.publishReembedMessage(
                        KnowledgeReembedMessageDTO.create(chunkId, param.getText()));
            }
        }
        // 更新场景标签
        if (param.getScenes() != null) {
            Map<String, List<String>> scenes = param.getScenes();
            for (Map.Entry<String, List<String>> entry : scenes.entrySet()) {
                String category = entry.getKey();
                if (!ALLOWED_CATEGORIES.contains(category)) {
                    throw new IllegalArgumentException("未知的场景类别: " + category);
                }
                Set<String> allowedTags = VALID_TAGS.get(category);
                for (String tag : entry.getValue()) {
                    if (!allowedTags.contains(tag)) {
                        throw new IllegalArgumentException(
                                "类别 '" + category + "' 中不包含标签: " + tag);
                    }
                }
            }
            ObjectNode metadata = (po.getMetadata() != null && !po.getMetadata().isNull())
                    ? (ObjectNode) po.getMetadata()
                    : this.objectMapper.createObjectNode();
            metadata.set("scenes", this.objectMapper.valueToTree(scenes));
            this.knowledgeVectorManage.updateMetadata(id, metadata);
        }
        KnowledgeVectorPO updated = this.knowledgeVectorManage.findById(id);
        return KnowledgeChunkVO.fromPO(updated, this.originalFilename(updated.getTaskNo()));
    }

    private Map<String, String> filenameMap(Page<KnowledgeVectorPO> page) {
        return this.ocrTaskManage.listByTaskNos(page.getRecords().stream()
                        .map(KnowledgeVectorPO::getTaskNo)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(
                        OcrTaskPO::getTaskNo,
                        OcrTaskPO::getOriginalFilename,
                        (left, right) -> left));
    }

    private String originalFilename(String taskNo) {
        return this.ocrTaskManage.listByTaskNos(List.of(taskNo)).stream()
                .findFirst()
                .map(OcrTaskPO::getOriginalFilename)
                .orElse(null);
    }
}
