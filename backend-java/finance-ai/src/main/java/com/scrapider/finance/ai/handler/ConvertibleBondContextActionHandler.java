package com.scrapider.finance.ai.handler;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.converter.AiAgentDomainToolDataConverter;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.ConvertibleBondShareManage;
import com.scrapider.finance.service.AssetDataEnsurePolicy;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConvertibleBondContextActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "convertible_bond.context";

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int TARGET_CANDIDATE_QUERY_LIMIT = 6;
    private static final int TARGET_CANDIDATE_RESULT_LIMIT = 5;
    private static final String SECTION_BASIC = "basic";
    private static final String SECTION_VALUATION_HISTORY = "valuationHistory";
    private static final String SECTION_SHARE_CHANGES = "shareChanges";

    private final BondConfigManage bondConfigManage;
    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;
    private final ConvertibleBondShareManage convertibleBondShareManage;
    private final AssetDataEnsureService assetDataEnsureService;

    public ConvertibleBondContextActionHandler(
            BondConfigManage bondConfigManage,
            ConvertibleBondBasicManage convertibleBondBasicManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage,
            ConvertibleBondShareManage convertibleBondShareManage,
            AssetDataEnsureService assetDataEnsureService) {
        this.bondConfigManage = bondConfigManage;
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
        this.convertibleBondShareManage = convertibleBondShareManage;
        this.assetDataEnsureService = assetDataEnsureService;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "正在查询可转债条款和估值数据";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        JsonNode params = param.params();
        String targetCode = this.textParam(params, "targetCode");
        String targetName = this.textParam(params, "targetName");
        Set<String> sections = this.normalizeSections(params);
        int limit = this.normalizeLimit(params);
        ResolvedBondTarget target = this.resolveTarget(targetCode, targetName);

        ConvertibleBondContextData data = this.loadContextData(target.targetCode(), sections, limit);
        Map<String, Object> dataRefresh = null;
        if (this.requiresRefresh(target.targetCode(), sections, data)) {
            BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(target.targetCode());
            if (bond != null) {
                try {
                    AssetDataEnsureResult result = this.assetDataEnsureService.ensureConvertibleBondData(bond);
                    dataRefresh = AgentDataRefreshMetadata.completed(List.copyOf(sections), result);
                    data = this.loadContextData(target.targetCode(), sections, limit);
                } catch (Exception ex) {
                    log.warn("Agent 可转债上下文兜底刷新失败，bondCode: {}", target.targetCode(), ex);
                    dataRefresh = AgentDataRefreshMetadata.failed(List.copyOf(sections));
                }
            }
        }
        ConvertibleBondBasicPO basic = data.basic();
        List<ConvertibleBondDailyValuationPO> valuationHistory = data.valuationHistory();
        List<ConvertibleBondSharePO> shareChanges = data.shareChanges();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("target", AiAgentDomainToolDataConverter.bondTarget(target.targetCode(), target.targetName()));
        row.put("basic", sections.contains(SECTION_BASIC)
                ? AiAgentDomainToolDataConverter.convertibleBondBasic(basic)
                : Map.of());
        row.put("valuationHistory", sections.contains(SECTION_VALUATION_HISTORY)
                ? AiAgentDomainToolDataConverter.convertibleBondValuationHistory(valuationHistory)
                : List.of());
        row.put("shareChanges", sections.contains(SECTION_SHARE_CHANGES)
                ? AiAgentDomainToolDataConverter.convertibleBondShareChanges(shareChanges)
                : List.of());
        row.put("dataCompleteness", this.dataCompleteness(target, sections, basic, valuationHistory, shareChanges));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queriedAt", OffsetDateTime.now().toString());
        metadata.put("targetType", "bond");
        metadata.put("targetCode", StrUtil.nullToEmpty(target.targetCode()));
        metadata.put("targetName", StrUtil.nullToEmpty(target.targetName()));
        metadata.put("sections", List.copyOf(sections));
        metadata.put("limit", limit);
        if (dataRefresh != null) {
            metadata.put("dataRefresh", dataRefresh);
        }
        return new AgentDataGatewayResponseVO(param.action(), true, List.of(row), metadata, null);
    }

    private ConvertibleBondContextData loadContextData(String targetCode, Set<String> sections, int limit) {
        if (StrUtil.isBlank(targetCode)) {
            return ConvertibleBondContextData.empty();
        }
        ConvertibleBondBasicPO basic = sections.contains(SECTION_BASIC)
                ? this.convertibleBondBasicManage.latestByBondCode(targetCode)
                : null;
        List<ConvertibleBondDailyValuationPO> valuationHistory = sections.contains(SECTION_VALUATION_HISTORY)
                ? this.convertibleBondDailyValuationManage.listByBondCode(targetCode, limit)
                : List.of();
        List<ConvertibleBondSharePO> shareChanges = sections.contains(SECTION_SHARE_CHANGES)
                ? this.convertibleBondShareManage.listByBondCode(targetCode, limit)
                : List.of();
        return new ConvertibleBondContextData(basic, valuationHistory, shareChanges);
    }

    private boolean requiresRefresh(String targetCode, Set<String> sections, ConvertibleBondContextData data) {
        if (StrUtil.isBlank(targetCode)) {
            return false;
        }
        return (sections.contains(SECTION_BASIC)
                        && (data.basic() == null || this.isBondDataStale(data.basic().getSyncedAt())))
                || (sections.contains(SECTION_VALUATION_HISTORY) && data.valuationHistory().isEmpty())
                || (sections.contains(SECTION_SHARE_CHANGES)
                        && (data.shareChanges().isEmpty()
                                || this.isBondDataStale(data.shareChanges().get(0).getSyncedAt())));
    }

    private boolean isBondDataStale(LocalDateTime syncedAt) {
        return syncedAt == null || syncedAt.isBefore(
                LocalDateTime.now().minusDays(AssetDataEnsurePolicy.CONVERTIBLE_BOND_FRESH_DAYS));
    }

    private ResolvedBondTarget resolveTarget(String targetCode, String targetName) {
        if (StrUtil.isNotBlank(targetCode)) {
            BondConfigPO config = this.bondConfigManage.getEnabledByBondCode(targetCode);
            if (config != null) {
                return new ResolvedBondTarget(
                        config.getBondCode(),
                        StrUtil.blankToDefault(config.getBondName(), targetName),
                        true,
                        true,
                        null,
                        List.of());
            }
            ConvertibleBondBasicPO basic = this.convertibleBondBasicManage.latestByBondCode(targetCode);
            if (basic != null) {
                return new ResolvedBondTarget(
                        basic.getBondCode(),
                        StrUtil.blankToDefault(basic.getBondName(), targetName),
                        true,
                        true,
                        null,
                        List.of());
            }
            return new ResolvedBondTarget(targetCode, targetName, true, false, "bond_target", List.of());
        }
        if (StrUtil.isBlank(targetName)) {
            return new ResolvedBondTarget(null, null, false, false, "bond_target", List.of());
        }
        List<BondTargetCandidate> candidates = this.findBondCandidatesByName(targetName);
        List<BondTargetCandidate> exactCandidates = candidates.stream()
                .filter(candidate -> targetName.equals(candidate.targetName()))
                .toList();
        List<BondTargetCandidate> effectiveCandidates = exactCandidates.isEmpty() ? candidates : exactCandidates;
        if (effectiveCandidates.size() == 1) {
            BondTargetCandidate candidate = effectiveCandidates.get(0);
            return new ResolvedBondTarget(
                    candidate.targetCode(),
                    StrUtil.blankToDefault(candidate.targetName(), targetName),
                    false,
                    true,
                    null,
                    List.of());
        }
        if (effectiveCandidates.size() > 1) {
            return new ResolvedBondTarget(
                    null,
                    targetName,
                    false,
                    false,
                    "ambiguous_bond_target",
                    effectiveCandidates.stream().limit(TARGET_CANDIDATE_RESULT_LIMIT).toList());
        }
        return new ResolvedBondTarget(null, targetName, false, false, "bond_target", List.of());
    }

    private List<BondTargetCandidate> findBondCandidatesByName(String targetName) {
        return this.bondConfigManage.searchEnabledBonds(targetName, TARGET_CANDIDATE_QUERY_LIMIT).stream()
                .map(config -> new BondTargetCandidate(config.getBondCode(), config.getBondName()))
                .toList();
    }

    private Map<String, Object> dataCompleteness(
            ResolvedBondTarget target,
            Set<String> sections,
            ConvertibleBondBasicPO basic,
            List<ConvertibleBondDailyValuationPO> valuationHistory,
            List<ConvertibleBondSharePO> shareChanges) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("targetCodeProvided", target.targetCodeProvided());
        row.put("targetResolved", target.targetResolved());
        row.put("missingReason", target.missingReason());
        if (!target.targetCandidates().isEmpty()) {
            row.put("targetCandidates", target.targetCandidates().stream()
                    .map(BondTargetCandidate::toMap)
                    .toList());
        }
        row.put("requestedSections", List.copyOf(sections));
        row.put("basicRequested", sections.contains(SECTION_BASIC));
        row.put("basicAvailable", basic != null);
        row.put("valuationHistoryRequested", sections.contains(SECTION_VALUATION_HISTORY));
        row.put("valuationHistoryCount", valuationHistory.size());
        row.put("shareChangesRequested", sections.contains(SECTION_SHARE_CHANGES));
        row.put("shareChangeCount", shareChanges.size());
        return row;
    }

    private Set<String> normalizeSections(JsonNode params) {
        Set<String> sections = new LinkedHashSet<>();
        if (params == null || params.isNull() || !params.has("sections") || params.get("sections").isNull()) {
            sections.add(SECTION_BASIC);
            sections.add(SECTION_VALUATION_HISTORY);
            sections.add(SECTION_SHARE_CHANGES);
            return sections;
        }
        JsonNode sectionNode = params.get("sections");
        if (sectionNode.isArray()) {
            sectionNode.forEach(item -> this.addSection(sections, item.asText()));
        } else {
            for (String item : sectionNode.asText("").split(",")) {
                this.addSection(sections, item);
            }
        }
        if (sections.isEmpty()) {
            sections.add(SECTION_BASIC);
            sections.add(SECTION_VALUATION_HISTORY);
            sections.add(SECTION_SHARE_CHANGES);
        }
        return sections;
    }

    private void addSection(Set<String> sections, String value) {
        String section = StrUtil.trimToNull(value);
        if (section == null) {
            return;
        }
        if (SECTION_BASIC.equals(section)) {
            sections.add(SECTION_BASIC);
        }
        if (SECTION_VALUATION_HISTORY.equals(section) || "valuation_history".equals(section)) {
            sections.add(SECTION_VALUATION_HISTORY);
        }
        if (SECTION_SHARE_CHANGES.equals(section) || "share_change".equals(section)) {
            sections.add(SECTION_SHARE_CHANGES);
        }
    }

    private int normalizeLimit(JsonNode params) {
        if (params == null || params.isNull() || !params.has("limit")) {
            return DEFAULT_LIMIT;
        }
        int limit = params.get("limit").asInt(DEFAULT_LIMIT);
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String textParam(JsonNode params, String fieldName) {
        if (params == null || params.isNull()) {
            return null;
        }
        JsonNode value = params.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return StrUtil.trimToNull(value.asText());
    }

    private record ResolvedBondTarget(
            String targetCode,
            String targetName,
            boolean targetCodeProvided,
            boolean targetResolved,
            String missingReason,
            List<BondTargetCandidate> targetCandidates) {
    }

    private record ConvertibleBondContextData(
            ConvertibleBondBasicPO basic,
            List<ConvertibleBondDailyValuationPO> valuationHistory,
            List<ConvertibleBondSharePO> shareChanges) {

        private static ConvertibleBondContextData empty() {
            return new ConvertibleBondContextData(null, List.of(), List.of());
        }
    }

    private record BondTargetCandidate(String targetCode, String targetName) {

        private Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("targetCode", this.targetCode);
            row.put("targetName", this.targetName);
            return row;
        }
    }
}
