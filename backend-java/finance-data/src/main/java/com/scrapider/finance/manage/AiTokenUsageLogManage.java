package com.scrapider.finance.manage;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.dto.AiTokenUsageCostSummaryDTO;
import com.scrapider.finance.domain.dto.AiTokenUsageSummaryDTO;
import com.scrapider.finance.domain.dto.AiTokenUsageTrendDTO;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import com.scrapider.finance.mapper.AiTokenUsageLogMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiTokenUsageLogManage extends ServiceImpl<AiTokenUsageLogMapper, AiTokenUsageLogPO> {

    public AiTokenUsageLogPO saveLog(AiTokenUsageLogPO log) {
        this.save(log);
        return log;
    }

    public AiTokenUsageSummaryDTO summary(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String source,
            String phase,
            String model,
            Collection<Long> userIds) {
        return this.baseMapper.summary(startTime, endTime, source, phase, model, userIds);
    }

    public List<AiTokenUsageCostSummaryDTO> costSummary(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String source,
            String phase,
            String model,
            Collection<Long> userIds) {
        return this.baseMapper.costSummary(startTime, endTime, source, phase, model, userIds);
    }

    public List<AiTokenUsageTrendDTO> trend(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String source,
            String phase,
            String model,
            Collection<Long> userIds) {
        return this.baseMapper.trend(startTime, endTime, source, phase, model, userIds);
    }

    public Page<AiTokenUsageLogPO> pageLogs(
            int pageNum,
            int pageSize,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String source,
            String phase,
            String model,
            Collection<Long> userIds) {
        return this.lambdaQuery()
                .select(
                        AiTokenUsageLogPO::getId,
                        AiTokenUsageLogPO::getProvider,
                        AiTokenUsageLogPO::getResponseId,
                        AiTokenUsageLogPO::getObjectType,
                        AiTokenUsageLogPO::getModel,
                        AiTokenUsageLogPO::getFinishReason,
                        AiTokenUsageLogPO::getPromptTokens,
                        AiTokenUsageLogPO::getCompletionTokens,
                        AiTokenUsageLogPO::getTotalTokens,
                        AiTokenUsageLogPO::getCachedTokens,
                        AiTokenUsageLogPO::getReasoningTokens,
                        AiTokenUsageLogPO::getPromptCacheHitTokens,
                        AiTokenUsageLogPO::getPromptCacheMissTokens,
                        AiTokenUsageLogPO::getUserId,
                        AiTokenUsageLogPO::getSource,
                        AiTokenUsageLogPO::getPhase,
                        AiTokenUsageLogPO::getOccurredAt,
                        AiTokenUsageLogPO::getCreatedAt)
                .ge(startTime != null, AiTokenUsageLogPO::getOccurredAt, startTime)
                .le(endTime != null, AiTokenUsageLogPO::getOccurredAt, endTime)
                .eq(StrUtil.isNotBlank(source), AiTokenUsageLogPO::getSource, source)
                .eq(StrUtil.isNotBlank(phase), AiTokenUsageLogPO::getPhase, phase)
                .eq(StrUtil.isNotBlank(model), AiTokenUsageLogPO::getModel, model)
                .in(userIds != null && !userIds.isEmpty(), AiTokenUsageLogPO::getUserId, userIds)
                .orderByDesc(AiTokenUsageLogPO::getOccurredAt)
                .orderByDesc(AiTokenUsageLogPO::getId)
                .page(Page.of(pageNum, pageSize));
    }

}
