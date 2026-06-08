package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.AiChatMessagePO;
import com.scrapider.finance.mapper.AiChatMessageMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiChatMessageManage extends ServiceImpl<AiChatMessageMapper, AiChatMessagePO> {

    public List<AiChatMessagePO> listRecent(Long userId, String conversationId, String excludeMessageId, int limit) {
        return this.lambdaQuery()
                .eq(AiChatMessagePO::getUserId, userId)
                .eq(AiChatMessagePO::getConversationId, conversationId)
                .ne(excludeMessageId != null && !excludeMessageId.isBlank(),
                        AiChatMessagePO::getMessageId,
                        excludeMessageId)
                .orderByDesc(AiChatMessagePO::getCreatedAt)
                .last("LIMIT " + limit)
                .list()
                .stream()
                .sorted(java.util.Comparator.comparing(AiChatMessagePO::getCreatedAt))
                .toList();
    }

    public boolean deleteByUserIdAndConversationId(Long userId, String conversationId) {
        return this.lambdaUpdate()
                .eq(AiChatMessagePO::getUserId, userId)
                .eq(AiChatMessagePO::getConversationId, conversationId)
                .remove();
    }
}
