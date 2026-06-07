package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.vo.AiChatVO;
import com.scrapider.finance.ai.domain.vo.AiDatabaseContextVO;
import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;
import java.time.LocalDateTime;

public final class AiChatConverter {

    private AiChatConverter() {
    }

    public static AiChatVO toVO(
            String message,
            String answer,
            String model,
            AiQueryRewriteVO queryRewrite,
            AiDatabaseContextVO databaseContext) {
        return new AiChatVO(message, answer, model, queryRewrite, databaseContext, LocalDateTime.now());
    }
}
