package com.scrapider.finance.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.exception.BusinessException;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import org.junit.jupiter.api.Test;

class SceneAnalysisReportServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getReportRejectsKnowledgeMaterialTask() {
        SceneAnalysisReportServiceImpl service = new SceneAnalysisReportServiceImpl(
                new FakeTaskManage(this.materialTask()),
                null,
                null,
                null,
                null,
                null,
                this.objectMapper,
                Runnable::run);

        assertThatThrownBy(() -> service.getReport("material-test"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("场景分析报告任务不存在: material-test");
    }

    private SceneAnalysisTaskPO materialTask() {
        return SceneAnalysisTaskPO.createPending(
                "material-test",
                7L,
                "STOCK",
                "600000",
                "浦发银行",
                "knowledge_material",
                "knowledge_material",
                this.objectMapper.createObjectNode());
    }

    private static class FakeTaskManage extends SceneAnalysisTaskManage {

        private final SceneAnalysisTaskPO task;

        FakeTaskManage(SceneAnalysisTaskPO task) {
            super(new ObjectMapper());
            this.task = task;
        }

        @Override
        public SceneAnalysisTaskPO findByTaskNo(String taskNo) {
            return this.task;
        }
    }
}
