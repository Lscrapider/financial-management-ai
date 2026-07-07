package com.scrapider.finance.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.dto.SceneChunkAllocationDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeChunkDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeRetrievalTaskDTO;
import com.scrapider.finance.ai.domain.dto.SceneRetrievalEmbeddingMessageDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import com.scrapider.finance.ai.publisher.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.service.SceneAnalysisReportService;
import com.scrapider.finance.ai.service.SceneKnowledgeRetrievalService;
import com.scrapider.finance.domain.exception.BusinessException;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SceneAnalysisTaskPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(SceneAnalysisTaskPipeline.class);
    private static final String NO_RETRIEVAL_SCENE_ERROR = "当前标的场景信号不足，未生成有效知识库召回任务";

    private final ObjectMapper objectMapper;
    private final SceneKnowledgeRetrievalService sceneKnowledgeRetrievalService;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher;
    private final SceneAnalysisReportService sceneAnalysisReportService;

    public SceneAnalysisTaskPipeline(
            ObjectMapper objectMapper,
            SceneKnowledgeRetrievalService sceneKnowledgeRetrievalService,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher,
            SceneAnalysisReportService sceneAnalysisReportService) {
        this.objectMapper = objectMapper;
        this.sceneKnowledgeRetrievalService = sceneKnowledgeRetrievalService;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.sceneAnalysisMessagePublisher = sceneAnalysisMessagePublisher;
        this.sceneAnalysisReportService = sceneAnalysisReportService;
    }

    public boolean start(
            String taskNo,
            SceneAnalysisCurrentScenesPayloadParam currentScenesPayload,
            String callbackToken) {
        this.sceneAnalysisTaskManage.markRetrievingKnowledge(taskNo);
        // 保持报告流程原有 6.2 / 6.3 语义，只把知识库召回算法下沉到公共服务。
        List<SceneChunkAllocationDTO> allocations =
                this.sceneKnowledgeRetrievalService.allocateChunks(currentScenesPayload);
        List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks =
                this.sceneKnowledgeRetrievalService.buildRetrievalTasks(allocations, currentScenesPayload);
        if (retrievalTasks.isEmpty()) {
            this.sceneAnalysisTaskManage.markFailed(taskNo, NO_RETRIEVAL_SCENE_ERROR);
            LOGGER.info("scene report retrieval skipped task_no={} reason={}", taskNo, NO_RETRIEVAL_SCENE_ERROR);
            return false;
        }
        this.sceneAnalysisMessagePublisher.publishRetrievalEmbeddingMessage(
                SceneRetrievalEmbeddingMessageDTO.create(taskNo, retrievalTasks),
                callbackToken);
        LOGGER.info(
                "scene report retrieval embedding message published task_no={} allocations={} retrieval_tasks={}",
                taskNo,
                allocations,
                retrievalTasks.size());
        return true;
    }

    public void continueWithRetrievalEmbeddings(
            String taskNo,
            List<SceneRetrievalEmbeddingParam> retrievalEmbeddings) {
        if (retrievalEmbeddings == null || retrievalEmbeddings.isEmpty()) {
            throw new BusinessException("召回向量不能为空。");
        }
        SceneAnalysisCurrentScenesPayloadParam currentScenesPayload = this.currentScenesPayload(taskNo);
        List<SceneChunkAllocationDTO> allocations =
                this.sceneKnowledgeRetrievalService.allocateChunks(currentScenesPayload);
        Map<String, List<SceneKnowledgeChunkDTO>> knowledgeContext =
                this.sceneKnowledgeRetrievalService.retrieveKnowledge(retrievalEmbeddings);
        ObjectNode reportPayload = this.objectMapper.createObjectNode();
        reportPayload.set("chunkAllocation", this.objectMapper.valueToTree(allocations));
        reportPayload.set("retrievalTasks",
                this.objectMapper.valueToTree(this.sceneKnowledgeRetrievalService.retrievalTasks(retrievalEmbeddings)));
        reportPayload.set("knowledgeContext", this.objectMapper.valueToTree(knowledgeContext));
        this.sceneAnalysisTaskManage.saveKnowledgeContextPayload(taskNo, reportPayload);
        this.sceneAnalysisReportService.generateAfterKnowledgeRetrieved(taskNo);
        LOGGER.info(
                "scene report knowledge context calculated task_no={} allocations={} retrieval_embeddings={} scenes={}",
                taskNo,
                allocations,
                retrievalEmbeddings.size(),
                knowledgeContext.keySet());
    }

    private SceneAnalysisCurrentScenesPayloadParam currentScenesPayload(String taskNo) {
        SceneAnalysisTaskPO task = this.sceneAnalysisTaskManage.findByTaskNo(taskNo);
        if (task == null || task.getCurrentScenesPayload() == null || task.getCurrentScenesPayload().isNull()) {
            throw new BusinessException("当前场景结果尚未就绪。");
        }
        try {
            return this.objectMapper.treeToValue(
                    task.getCurrentScenesPayload(),
                    SceneAnalysisCurrentScenesPayloadParam.class);
        } catch (Exception ex) {
            throw new IllegalStateException("当前场景结果解析失败。", ex);
        }
    }
}
