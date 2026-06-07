package com.scrapider.finance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.domain.param.AiTokenUsageDeepSeekResponseParam;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageLogVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageTrendVO;
import java.util.List;
import org.springframework.ai.chat.model.ChatResponse;

public interface AiTokenUsageService {

    AiTokenUsageLogVO recordDeepSeekResponse(JsonNode response);

    AiTokenUsageLogVO recordDeepSeekResponse(AiTokenUsageDeepSeekResponseParam param);

    void recordChatResponse(ChatResponse response);

    AiTokenUsageOverviewVO overview(Integer days);

    List<AiTokenUsageTrendVO> trends(Integer hours);
}
