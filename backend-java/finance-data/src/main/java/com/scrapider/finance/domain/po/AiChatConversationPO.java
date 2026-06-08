package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_chat_conversation")
public class AiChatConversationPO {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CLEANED = "cleaned";

    private Long id;
    private Long userId;
    private String conversationId;
    private String title;
    private String status;
    private Long cleanupVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cleanedAt;

    public static AiChatConversationPO create(Long userId, String conversationId) {
        LocalDateTime now = LocalDateTime.now();
        AiChatConversationPO conversation = new AiChatConversationPO();
        conversation.setUserId(userId);
        conversation.setConversationId(conversationId);
        conversation.setStatus(STATUS_ACTIVE);
        conversation.setCleanupVersion(0L);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return conversation;
    }
}
