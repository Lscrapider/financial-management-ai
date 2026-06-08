package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.AiChatConversationPO;
import com.scrapider.finance.mapper.AiChatConversationMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class AiChatConversationManage extends ServiceImpl<AiChatConversationMapper, AiChatConversationPO> {

    public AiChatConversationPO findByConversationId(String conversationId) {
        return this.lambdaQuery()
                .eq(AiChatConversationPO::getConversationId, conversationId)
                .one();
    }

    public AiChatConversationPO findByUserIdAndConversationId(Long userId, String conversationId) {
        return this.lambdaQuery()
                .eq(AiChatConversationPO::getUserId, userId)
                .eq(AiChatConversationPO::getConversationId, conversationId)
                .one();
    }

    public AiChatConversationPO findLatestActiveByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(AiChatConversationPO::getUserId, userId)
                .eq(AiChatConversationPO::getStatus, AiChatConversationPO.STATUS_ACTIVE)
                .orderByDesc(AiChatConversationPO::getUpdatedAt)
                .last("LIMIT 1")
                .one();
    }

    public boolean markActive(Long id, Long cleanupVersion) {
        return this.lambdaUpdate()
                .eq(AiChatConversationPO::getId, id)
                .set(AiChatConversationPO::getStatus, AiChatConversationPO.STATUS_ACTIVE)
                .set(AiChatConversationPO::getCleanupVersion, cleanupVersion)
                .set(AiChatConversationPO::getCleanedAt, null)
                .set(AiChatConversationPO::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    public boolean markCleaned(Long id) {
        LocalDateTime now = LocalDateTime.now();
        return this.lambdaUpdate()
                .eq(AiChatConversationPO::getId, id)
                .set(AiChatConversationPO::getStatus, AiChatConversationPO.STATUS_CLEANED)
                .set(AiChatConversationPO::getCleanedAt, now)
                .set(AiChatConversationPO::getUpdatedAt, now)
                .update();
    }
}
