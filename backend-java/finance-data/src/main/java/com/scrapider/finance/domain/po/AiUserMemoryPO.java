package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_user_memory")
public class AiUserMemoryPO {

    public static final String TYPE_CONVERSATION_SUMMARY = "conversation_summary";

    private Long id;
    private Long userId;
    private String memoryType;
    private String title;
    private String content;
    private String metadataJson;
    private String sourceConversationId;
    private BigDecimal confidence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;

    public static AiUserMemoryPO conversationSummary(
            Long userId,
            String conversationId,
            String title,
            String content,
            String metadataJson) {
        LocalDateTime now = LocalDateTime.now();
        AiUserMemoryPO memory = new AiUserMemoryPO();
        memory.setUserId(userId);
        memory.setMemoryType(TYPE_CONVERSATION_SUMMARY);
        memory.setTitle(title);
        memory.setContent(content);
        memory.setMetadataJson(metadataJson);
        memory.setSourceConversationId(conversationId);
        memory.setConfidence(BigDecimal.ONE);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        memory.setDeleted(false);
        return memory;
    }
}
