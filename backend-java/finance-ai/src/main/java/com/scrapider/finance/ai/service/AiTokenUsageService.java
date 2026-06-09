package com.scrapider.finance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AiTokenUsageLogPageParam;
import com.scrapider.finance.ai.domain.param.AiTokenUsageQueryParam;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageLogPageVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageLogVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageTrendVO;
import com.scrapider.finance.domain.enums.AiTokenUsagePhaseEnum;
import com.scrapider.finance.domain.enums.AiTokenUsageSourceEnum;
import java.util.List;
import org.springframework.ai.chat.model.ChatResponse;

public interface AiTokenUsageService {

    AiTokenUsageLogVO recordDeepSeekResponse(
            JsonNode response,
            Long userId,
            AiTokenUsageSourceEnum source,
            String sourceRefId,
            AiTokenUsagePhaseEnum phase);

    void recordChatResponse(ChatResponse response);

    void recordAgentTokenUsage(AgentSessionDTO session, JsonNode payload);

    AiTokenUsageOverviewVO overview(AiTokenUsageQueryParam param);

    List<AiTokenUsageTrendVO> trends(AiTokenUsageQueryParam param);

    AiTokenUsageLogPageVO pageLogs(AiTokenUsageLogPageParam param);
}
