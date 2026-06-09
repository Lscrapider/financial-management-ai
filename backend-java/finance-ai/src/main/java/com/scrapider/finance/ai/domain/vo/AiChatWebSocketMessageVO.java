package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiChatWebSocketMessageVO(
        String type,
        String conversationId,
        String messageId,
        String status,
        String content,
        String answeredAt) {

    public static AiChatWebSocketMessageVO finalAnswer(String conversationId, String messageId, String answeredAt) {
        return finalAnswer(
                conversationId,
                messageId,
                "第一版 WebSocket 已连通，后续将接入 Agent。",
                answeredAt);
    }

    public static AiChatWebSocketMessageVO finalAnswer(
            String conversationId,
            String messageId,
            String content,
            String answeredAt) {
        return new AiChatWebSocketMessageVO(
                "final_answer",
                conversationId,
                messageId,
                null,
                content,
                answeredAt);
    }
}
