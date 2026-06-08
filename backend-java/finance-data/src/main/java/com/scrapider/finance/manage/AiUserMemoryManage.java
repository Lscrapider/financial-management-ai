package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.AiUserMemoryPO;
import com.scrapider.finance.mapper.AiUserMemoryMapper;
import org.springframework.stereotype.Service;

@Service
public class AiUserMemoryManage extends ServiceImpl<AiUserMemoryMapper, AiUserMemoryPO> {

    public boolean existsConversationSummary(Long userId, String conversationId) {
        return this.lambdaQuery()
                .eq(AiUserMemoryPO::getUserId, userId)
                .eq(AiUserMemoryPO::getMemoryType, AiUserMemoryPO.TYPE_CONVERSATION_SUMMARY)
                .eq(AiUserMemoryPO::getSourceConversationId, conversationId)
                .eq(AiUserMemoryPO::getDeleted, false)
                .exists();
    }
}
