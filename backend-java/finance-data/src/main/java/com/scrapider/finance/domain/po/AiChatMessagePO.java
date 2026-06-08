package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_chat_message")
public class AiChatMessagePO {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private Long id;
    private Long userId;
    private String conversationId;
    private String messageId;
    private String role;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;

    public static AiChatMessagePO create(
            Long userId,
            String conversationId,
            String messageId,
            String role,
            String content,
            String metadataJson) {
        AiChatMessagePO message = new AiChatMessagePO();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setMessageId(messageId);
        message.setRole(role);
        message.setContent(content);
        message.setMetadataJson(metadataJson);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
