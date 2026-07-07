package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.exception.AiUsageLimitExceededException;
import com.scrapider.finance.ai.service.AiUsageLimitService;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.manage.AiChatMessageManage;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.manage.SceneAnalysisReportManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class AiUsageLimitServiceImpl implements AiUsageLimitService {

    private final AppUserManage appUserManage;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final SceneAnalysisReportManage sceneAnalysisReportManage;
    private final AiChatMessageManage aiChatMessageManage;

    public AiUsageLimitServiceImpl(
            AppUserManage appUserManage,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            SceneAnalysisReportManage sceneAnalysisReportManage,
            AiChatMessageManage aiChatMessageManage) {
        this.appUserManage = appUserManage;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.sceneAnalysisReportManage = sceneAnalysisReportManage;
        this.aiChatMessageManage = aiChatMessageManage;
    }

    @Override
    public void requireCanGenerateReport(Long userId) {
        LimitPolicy policy = this.limitPolicy(userId);
        if (policy.dailyReportLimit() == null) {
            return;
        }
        TimeRange today = this.today();
        long submittedReports = this.sceneAnalysisTaskManage.countSubmittedByUserBetween(
                userId,
                today.startTime(),
                today.endTime());
        long regeneratedReports = this.sceneAnalysisReportManage.countByUserAndGenerationTypeBetween(
                userId,
                SceneAnalysisReportPO.GENERATION_TYPE_REGENERATE,
                today.startTime(),
                today.endTime());
        if (submittedReports + regeneratedReports >= policy.dailyReportLimit()) {
            throw new AiUsageLimitExceededException("普通用户每天最多生成 "
                    + policy.dailyReportLimit()
                    + " 次 AI 报告，请明天再试。");
        }
    }

    @Override
    public void requireCanSendChat(Long userId) {
        LimitPolicy policy = this.limitPolicy(userId);
        if (policy.dailyChatLimit() == null) {
            return;
        }
        TimeRange today = this.today();
        long used = this.aiChatMessageManage.countUserMessagesBetween(
                userId,
                today.startTime(),
                today.endTime());
        if (used >= policy.dailyChatLimit()) {
            throw new AiUsageLimitExceededException("普通用户每天最多进行 "
                    + policy.dailyChatLimit()
                    + " 次 AI 对话，请明天再试。");
        }
    }

    private LimitPolicy limitPolicy(Long userId) {
        if (userId == null) {
            return LimitPolicy.unlimited();
        }
        AppUserPO user = this.appUserManage.getById(userId);
        if (user == null) {
            return LimitPolicy.unlimited();
        }
        return new LimitPolicy(
                this.normalizedLimit(user.getAiDailyReportLimit()),
                this.normalizedLimit(user.getAiDailyChatLimit()));
    }

    private Integer normalizedLimit(Integer limit) {
        return limit == null ? null : Math.max(0, limit);
    }

    private TimeRange today() {
        LocalDateTime startTime = LocalDate.now().atStartOfDay();
        return new TimeRange(startTime, startTime.plusDays(1));
    }

    private record TimeRange(LocalDateTime startTime, LocalDateTime endTime) {
    }

    private record LimitPolicy(Integer dailyReportLimit, Integer dailyChatLimit) {

        private static LimitPolicy unlimited() {
            return new LimitPolicy(null, null);
        }
    }
}
