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
import com.scrapider.finance.ai.publisher.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.security.CurrentUserContext;
import com.scrapider.finance.ai.service.SceneAnalysisTaskService;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
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
    private final List<SceneTargetDataProvider> targetDataProviders;

    public SceneAnalysisTaskServiceImpl(
            ObjectMapper objectMapper,
            SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            SceneAnalysisTaskPipeline sceneAnalysisTaskPipeline,
            List<SceneTargetDataProvider> targetDataProviders) {
        this.objectMapper = objectMapper;
        this.sceneAnalysisMessagePublisher = sceneAnalysisMessagePublisher;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.sceneAnalysisTaskPipeline = sceneAnalysisTaskPipeline;
        this.targetDataProviders = targetDataProviders;
    }

    @Override
    public SceneAnalysisSubmitVO submit(SceneAnalysisSubmitParam param) {
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String targetType = this.normalizeTargetType(param.targetType());
        String targetCode = StrUtil.trim(param.targetCode());
        if (StrUtil.isBlank(targetType) || StrUtil.isBlank(targetCode)) {
            throw new IllegalArgumentException("targetType and targetCode are required");
        }
        if (param.totalChunks() == null || param.totalChunks() <= 0) {
            throw new IllegalArgumentException("totalChunks is required and must be greater than 0");
        }
        Long userId = CurrentUserContext.currentUserId();
        String taskNo = this.newTaskNo();
        SceneAnalysisMessageDTO message = this.targetDataProvider(targetType)
                .buildMessage(taskNo, targetCode, param);
        this.sceneAnalysisTaskManage.saveTask(this.pendingTask(userId, message));
        try {
            this.sceneAnalysisMessagePublisher.publishCurrentSceneAnalysisMessage(message);
            this.sceneAnalysisTaskManage.markProcessing(taskNo);
        } catch (Exception ex) {
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
        return SceneAnalysisTaskConverter.submitted(taskNo, message);
    }

    @Override
    public void callback(String taskNo, SceneAnalysisCallbackParam param) {
        if (StrUtil.isBlank(taskNo)) {
            throw new IllegalArgumentException("taskNo is required");
        }
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        SceneAnalysisCurrentScenesPayloadParam currentScenesPayload = param.currentScenesPayload();
        List<SceneRetrievalEmbeddingParam> retrievalEmbeddings = param.retrievalEmbeddings();
        if (currentScenesPayload == null && (retrievalEmbeddings == null || retrievalEmbeddings.isEmpty())) {
            throw new IllegalArgumentException("currentScenesPayload or retrievalEmbeddings is required");
        }
        try {
            if (currentScenesPayload != null) {
                this.sceneAnalysisTaskManage.markCurrentScenesReady(
                        taskNo,
                        this.objectMapper.valueToTree(currentScenesPayload));
                this.sceneAnalysisTaskPipeline.start(taskNo, currentScenesPayload);
                return;
            }
            this.sceneAnalysisTaskPipeline.continueWithRetrievalEmbeddings(taskNo, retrievalEmbeddings);
        } catch (Exception ex) {
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
    }

    private SceneTargetDataProvider targetDataProvider(String targetType) {
        return this.targetDataProviders.stream()
                .filter(provider -> provider.supports(targetType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported targetType: " + targetType));
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
