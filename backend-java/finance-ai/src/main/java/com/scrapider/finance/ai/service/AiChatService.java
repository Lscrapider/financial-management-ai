package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.AiChatParam;
import com.scrapider.finance.ai.domain.vo.AiChatVO;

public interface AiChatService {

    AiChatVO chat(AiChatParam param);
}
