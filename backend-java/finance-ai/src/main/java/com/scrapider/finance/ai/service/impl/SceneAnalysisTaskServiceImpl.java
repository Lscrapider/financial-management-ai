package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.SceneAnalysisTaskConverter;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;
import com.scrapider.finance.ai.pipeline.SceneAnalysisTaskPipeline;
import com.scrapider.finance.ai.publisher.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.security.SceneAnalysisCallbackTokenStore;
import com.scrapider.finance.ai.security.CurrentUserContext;
import com.scrapider.finance.ai.service.AiUsageLimitService;
import com.scrapider.finance.ai.service.SceneAnalysisTaskService;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import com.scrapider.finance.domain.exception.BusinessException;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisTaskServiceImpl implements SceneAnalysisTaskService {

    private final ObjectMapper objectMapper;
    private final SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final SceneAnalysisTaskPipeline sceneAnalysisTaskPipeline;
    private final SceneAnalysisCallbackTokenStore callbackTokenStore;
    private final AiUsageLimitService aiUsageLimitService;
    private final List<SceneTargetDataProvider> targetDataProviders;

    public SceneAnalysisTaskServiceImpl(
            ObjectMapper objectMapper,
            SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            SceneAnalysisTaskPipeline sceneAnalysisTaskPipeline,
            SceneAnalysisCallbackTokenStore callbackTokenStore,
            AiUsageLimitService aiUsageLimitService,
            List<SceneTargetDataProvider> targetDataProviders) {
        this.objectMapper = objectMapper;
        this.sceneAnalysisMessagePublisher = sceneAnalysisMessagePublisher;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.sceneAnalysisTaskPipeline = sceneAnalysisTaskPipeline;
        this.callbackTokenStore = callbackTokenStore;
        this.aiUsageLimitService = aiUsageLimitService;
        this.targetDataProviders = targetDataProviders;
    }

    @Override
    public SceneAnalysisSubmitVO submit(SceneAnalysisSubmitParam param) {
        if (param == null) {
            throw new BusinessException("请求体不能为空。");
        }
        String targetType = this.normalizeTargetType(param.targetType());
        String targetCode = StrUtil.trim(param.targetCode());
        if (StrUtil.isBlank(targetType) || StrUtil.isBlank(targetCode)) {
            throw new BusinessException("标的类型和标的代码不能为空。");
        }
        if (param.totalChunks() == null || param.totalChunks() <= 0) {
            throw new BusinessException("召回片段数量必须大于 0。");
        }
        Long userId = CurrentUserContext.currentUserId();
        this.aiUsageLimitService.requireCanGenerateReport(userId);
        String taskNo = this.newTaskNo();
        SceneAnalysisMessageDTO message = this.targetDataProvider(targetType)
                .buildMessage(taskNo, targetCode, param);
        this.sceneAnalysisTaskManage.saveTask(this.pendingTask(userId, message));
        String callbackToken = this.callbackTokenStore.issue(taskNo);
        try {
            this.sceneAnalysisMessagePublisher.publishCurrentSceneAnalysisMessage(message, callbackToken);
            this.sceneAnalysisTaskManage.markProcessing(taskNo);
        } catch (Exception ex) {
            this.callbackTokenStore.revoke(taskNo);
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
        return SceneAnalysisTaskConverter.submitted(taskNo, message);
    }

    @Override
    public void callback(String taskNo, String callbackToken, SceneAnalysisCallbackParam param) {
        if (StrUtil.isBlank(taskNo)) {
            throw new BusinessException("任务编号不能为空。");
        }
        if (param == null) {
            throw new BusinessException("请求体不能为空。");
        }
        SceneAnalysisCurrentScenesPayloadParam currentScenesPayload = param.currentScenesPayload();
        List<SceneRetrievalEmbeddingParam> retrievalEmbeddings = param.retrievalEmbeddings();
        if (currentScenesPayload == null && (retrievalEmbeddings == null || retrievalEmbeddings.isEmpty())) {
            throw new BusinessException("当前场景结果或召回向量不能为空。");
        }
        try {
            if (currentScenesPayload != null) {
                this.sceneAnalysisTaskManage.markCurrentScenesReady(
                        taskNo,
                        this.objectMapper.valueToTree(currentScenesPayload));
                boolean continued = this.sceneAnalysisTaskPipeline.start(taskNo, currentScenesPayload, callbackToken);
                if (!continued) {
                    this.callbackTokenStore.revoke(taskNo);
                }
                return;
            }
            this.sceneAnalysisTaskPipeline.continueWithRetrievalEmbeddings(taskNo, retrievalEmbeddings);
            this.callbackTokenStore.revoke(taskNo);
        } catch (Exception ex) {
            this.callbackTokenStore.revoke(taskNo);
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
    }

    private SceneTargetDataProvider targetDataProvider(String targetType) {
        return this.targetDataProviders.stream()
                .filter(provider -> provider.supports(targetType))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的标的类型: " + targetType));
    }

    private SceneAnalysisTaskPO pendingTask(Long userId, SceneAnalysisMessageDTO message) {
        return SceneAnalysisTaskPO.createPending(
                message.taskNo(),
                userId,
                message.target().type(),
                message.target().code(),
                message.target().name(),
                message.reportType(),
                message.config().profile(),
                this.toJsonNode(message.config().parameters()));
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return null;
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STOCK" -> "STOCK";
            case "INDEX" -> "INDEX";
            case "CONVERTIBLE_BOND", "BOND" -> "CONVERTIBLE_BOND";
            default -> normalized;
        };
    }

    private String newTaskNo() {
        return "scene-" + UUID.randomUUID().toString().replace("-", "");
    }

    private JsonNode toJsonNode(Object value) {
        return this.objectMapper.valueToTree(value);
    }

}
