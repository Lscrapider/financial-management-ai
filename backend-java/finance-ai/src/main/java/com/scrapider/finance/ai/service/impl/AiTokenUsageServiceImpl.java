package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.AiTokenUsageConverter;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AiTokenUsageLogPageParam;
import com.scrapider.finance.ai.domain.param.AiTokenUsageQueryParam;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageLogPageVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageLogVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageTrendVO;
import com.scrapider.finance.ai.service.AiTokenUsageCostCalculator;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import com.scrapider.finance.domain.enums.AiTokenUsagePhaseEnum;
import com.scrapider.finance.domain.enums.AiTokenUsageSourceEnum;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.manage.AiTokenUsageLogManage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AiTokenUsageServiceImpl implements AiTokenUsageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiTokenUsageServiceImpl.class);
    private static final int DEFAULT_OVERVIEW_DAYS = 7;
    private static final int MAX_OVERVIEW_DAYS = 365;
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 365;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AiTokenUsageLogManage aiTokenUsageLogManage;
    private final AppUserManage appUserManage;
    private final AiTokenUsageCostCalculator aiTokenUsageCostCalculator;
    private final ObjectMapper objectMapper;

    public AiTokenUsageServiceImpl(
            AiTokenUsageLogManage aiTokenUsageLogManage,
            AppUserManage appUserManage,
            AiTokenUsageCostCalculator aiTokenUsageCostCalculator,
            ObjectMapper objectMapper) {
        this.aiTokenUsageLogManage = aiTokenUsageLogManage;
        this.appUserManage = appUserManage;
        this.aiTokenUsageCostCalculator = aiTokenUsageCostCalculator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTokenUsageLogVO recordDeepSeekResponse(
            JsonNode response,
            Long userId,
            AiTokenUsageSourceEnum source,
            String sourceRefId,
            AiTokenUsagePhaseEnum phase) {
        if (response == null || response.isNull() || response.path("usage").isMissingNode()) {
            throw new IllegalArgumentException("DeepSeek response usage must not be empty");
        }
        AiTokenUsageLogPO saved = this.aiTokenUsageLogManage.saveLog(AiTokenUsageLogPO.fromDeepSeekResponse(
                response,
                userId,
                source,
                sourceRefId,
                phase));
        return AiTokenUsageLogVO.fromPO(saved);
    }

    @Override
    public void recordChatResponse(ChatResponse response) {
        if (response == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null || usage.getTotalTokens() <= 0) {
            return;
        }
        try {
            this.aiTokenUsageLogManage.saveLog(AiTokenUsageConverter.fromChatResponse(
                    response,
                    usage,
                    this.objectMapper));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to record AI token usage", ex);
        }
    }

    @Override
    public void recordAgentTokenUsage(AgentSessionDTO session, JsonNode payload) {
        if (session == null || payload == null || payload.isNull()) {
            return;
        }
        JsonNode events = payload.path("tokenUsageEvents");
        if (!events.isArray() || events.size() == 0) {
            return;
        }
        for (JsonNode event : events) {
            this.recordAgentTokenUsageEvent(session, event);
        }
    }

    private void recordAgentTokenUsageEvent(AgentSessionDTO session, JsonNode event) {
        if (event == null || event.isNull()) {
            return;
        }
        AiTokenUsagePhaseEnum phase = AiTokenUsagePhaseEnum.fromCode(event.path("phase").asText(""));
        if (phase == null) {
            LOGGER.warn("Skip AI agent token usage with invalid phase sessionId={} phase={}",
                    session.agentSessionId(),
                    event.path("phase").asText(""));
            return;
        }
        if (event.path("totalTokens").asInt(0) <= 0) {
            return;
        }
        try {
            this.aiTokenUsageLogManage.saveLog(AiTokenUsageLogPO.fromAgentUsageEvent(
                    event,
                    session.userId(),
                    session.conversationId(),
                    phase));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to record AI agent token usage sessionId={}", session.agentSessionId(), ex);
        }
    }

    @Override
    public AiTokenUsageOverviewVO overview(AiTokenUsageQueryParam param) {
        TokenUsageQuery query = this.buildQuery(param, DEFAULT_OVERVIEW_DAYS, MAX_OVERVIEW_DAYS);
        if (query.emptyUserFilter()) {
            return AiTokenUsageOverviewVO.fromDTO(null, this.aiTokenUsageCostCalculator.calculateTotal(List.of()));
        }
        return AiTokenUsageOverviewVO.fromDTO(
                this.aiTokenUsageLogManage.summary(
                        query.startTime(),
                        query.endTime(),
                        query.source(),
                        query.phase(),
                        query.model(),
                        query.userIds()),
                this.aiTokenUsageCostCalculator.calculateTotal(this.aiTokenUsageLogManage.costSummary(
                        query.startTime(),
                        query.endTime(),
                        query.source(),
                        query.phase(),
                        query.model(),
                        query.userIds())));
    }

    @Override
    public List<AiTokenUsageTrendVO> trends(AiTokenUsageQueryParam param) {
        TokenUsageQuery query = this.buildQuery(param, DEFAULT_TREND_DAYS, MAX_TREND_DAYS);
        if (query.emptyUserFilter()) {
            return List.of();
        }
        return this.aiTokenUsageLogManage.trend(
                        query.startTime(),
                        query.endTime(),
                        query.source(),
                        query.phase(),
                        query.model(),
                        query.userIds())
                .stream()
                .map(AiTokenUsageTrendVO::fromDTO)
                .toList();
    }

    @Override
    public AiTokenUsageLogPageVO pageLogs(AiTokenUsageLogPageParam param) {
        AiTokenUsageLogPageParam query = param == null ? new AiTokenUsageLogPageParam() : param;
        int pageNum = this.normalizePageNum(query.getPageNum());
        int pageSize = this.normalizePageSize(query.getPageSize());
        TokenUsageQuery tokenUsageQuery = this.buildQuery(query, DEFAULT_OVERVIEW_DAYS, MAX_OVERVIEW_DAYS);
        if (tokenUsageQuery.emptyUserFilter()) {
            return AiTokenUsageLogPageVO.fromPage(Page.of(pageNum, pageSize));
        }
        Page<AiTokenUsageLogPO> page = this.aiTokenUsageLogManage.pageLogs(
                pageNum,
                pageSize,
                tokenUsageQuery.startTime(),
                tokenUsageQuery.endTime(),
                tokenUsageQuery.source(),
                tokenUsageQuery.phase(),
                tokenUsageQuery.model(),
                tokenUsageQuery.userIds());
        return AiTokenUsageLogPageVO.fromPage(page, this.usernameMap(page), this.aiTokenUsageCostCalculator);
    }

    private TokenUsageQuery buildQuery(AiTokenUsageQueryParam param, int defaultDays, int maxDays) {
        AiTokenUsageQueryParam query = param == null ? new AiTokenUsageQueryParam() : param;
        LocalDateTime startTime = query.getStartTime() == null
                ? LocalDateTime.now().minusDays(this.normalize(query.getDays(), defaultDays, maxDays))
                : query.getStartTime();
        LocalDateTime endTime = query.getEndTime();
        this.validateTimeRange(startTime, endTime);
        String source = this.normalizeSource(query.getSource());
        String phase = this.normalizePhase(query.getPhase());
        Set<Long> filteredUserIds = this.filteredUserIds(query.getUsername());
        return new TokenUsageQuery(
                startTime,
                endTime,
                source,
                phase,
                StrUtil.trimToNull(query.getModel()),
                filteredUserIds,
                filteredUserIds != null && filteredUserIds.isEmpty());
    }

    private int normalize(Integer value, int defaultValue, int maxValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum <= 0) {
            return DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
    }

    private String normalizeSource(String source) {
        String normalizedSource = StrUtil.trimToNull(source);
        if (normalizedSource == null) {
            return null;
        }
        if (AiTokenUsageSourceEnum.fromCode(normalizedSource) == null) {
            throw new IllegalArgumentException("unsupported token usage source: " + normalizedSource);
        }
        return normalizedSource;
    }

    private String normalizePhase(String phase) {
        String normalizedPhase = StrUtil.trimToNull(phase);
        if (normalizedPhase == null) {
            return null;
        }
        if (AiTokenUsagePhaseEnum.fromCode(normalizedPhase) == null) {
            throw new IllegalArgumentException("unsupported token usage phase: " + normalizedPhase);
        }
        return normalizedPhase;
    }

    private Set<Long> filteredUserIds(String username) {
        String normalizedUsername = StrUtil.trimToNull(username);
        if (normalizedUsername == null) {
            return null;
        }
        return this.appUserManage.listUsersByUsername(normalizedUsername).stream()
                .map(AppUserPO::getId)
                .collect(Collectors.toSet());
    }

    private Map<Long, String> usernameMap(Page<AiTokenUsageLogPO> page) {
        Set<Long> userIds = page.getRecords().stream()
                .map(AiTokenUsageLogPO::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return this.appUserManage.listUsersByIds(userIds).stream()
                .collect(Collectors.toMap(AppUserPO::getId, AppUserPO::getUsername, (left, right) -> left));
    }

    private record TokenUsageQuery(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String source,
            String phase,
            String model,
            Set<Long> userIds,
            boolean emptyUserFilter) {
    }
}
