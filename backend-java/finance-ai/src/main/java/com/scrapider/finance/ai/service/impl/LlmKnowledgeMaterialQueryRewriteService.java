package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.ai.chat.AiQueryRewriteService;
import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;
import com.scrapider.finance.ai.service.KnowledgeMaterialQueryRewriteService;
import org.springframework.stereotype.Service;

@Service
public class LlmKnowledgeMaterialQueryRewriteService implements KnowledgeMaterialQueryRewriteService {

    private final AiQueryRewriteService aiQueryRewriteService;

    public LlmKnowledgeMaterialQueryRewriteService(AiQueryRewriteService aiQueryRewriteService) {
        this.aiQueryRewriteService = aiQueryRewriteService;
    }

    @Override
    public String rewrite(String queryText) {
        AiQueryRewriteVO rewrite = this.aiQueryRewriteService.rewrite(queryText);
        if (rewrite == null || StrUtil.isBlank(rewrite.rewrittenQuestion())) {
            return queryText;
        }
        return rewrite.rewrittenQuestion().trim();
    }
}
