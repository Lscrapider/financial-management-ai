package com.scrapider.finance.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesTargetParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSceneModuleParam;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SceneAnalysisTaskPipelineTest {

    @Test
    void startMarksTaskFailedWhenNoSceneCanRetrieveKnowledge() {
        FakeTaskManage taskManage = new FakeTaskManage();
        SceneAnalysisTaskPipeline service = new SceneAnalysisTaskPipeline(
                new ObjectMapper(),
                null,
                taskManage,
                null,
                null);

        service.start("scene-test", this.lowScorePayload());

        assertThat(taskManage.retrievingTaskNo).isEqualTo("scene-test");
        assertThat(taskManage.failedTaskNo).isEqualTo("scene-test");
        assertThat(taskManage.errorMessage).contains("场景信号不足");
    }

    private SceneAnalysisCurrentScenesPayloadParam lowScorePayload() {
        SceneAnalysisSceneModuleParam low = new SceneAnalysisSceneModuleParam(
                0.2,
                "low",
                "neutral",
                Map.of("quiet", 0.2),
                List.of(),
                "quiet scene",
                Map.of());
        return new SceneAnalysisCurrentScenesPayloadParam(
                new SceneAnalysisCurrentScenesTargetParam("CONVERTIBLE_BOND", "113001", "测试转债"),
                "quick_analysis",
                6,
                new ObjectMapper().createObjectNode(),
                new SceneAnalysisCurrentScenesParam(null, low, low, low, low, low, low));
    }

    private static class FakeTaskManage extends SceneAnalysisTaskManage {
        private String retrievingTaskNo;
        private String failedTaskNo;
        private String errorMessage;

        FakeTaskManage() {
            super(new ObjectMapper());
        }

        @Override
        public void markRetrievingKnowledge(String taskNo) {
            this.retrievingTaskNo = taskNo;
        }

        @Override
        public void markFailed(String taskNo, String errorMessage) {
            this.failedTaskNo = taskNo;
            this.errorMessage = errorMessage;
        }
    }

}
