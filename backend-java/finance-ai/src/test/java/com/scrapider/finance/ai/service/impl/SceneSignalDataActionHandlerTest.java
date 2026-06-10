package com.scrapider.finance.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisConfigDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisUserConfigParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SceneSignalDataActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void clampsTotalChunksToTenAndReturnsSceneMessage() {
        FakeSceneTargetDataProvider provider = new FakeSceneTargetDataProvider();
        SceneSignalDataActionHandler handler = new SceneSignalDataActionHandler(this.objectMapper, List.of(provider));
        AgentDataQueryParam param = new AgentDataQueryParam(
                "scene.signal_data",
                this.objectMapper.valueToTree(Map.of(
                        "targetType", "stock",
                        "targetCode", "002958",
                        "targetName", "青农商行",
                        "totalChunks", 15)),
                null);

        AgentDataGatewayResponseVO response = handler.handle(this.session(), param);

        assertThat(response.success()).isTrue();
        assertThat(provider.receivedTaskNo).startsWith("agent-scene-signal-");
        assertThat(provider.receivedTargetCode).isEqualTo("002958");
        assertThat(provider.receivedParam.totalChunks()).isEqualTo(10);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0)).containsEntry("totalChunks", 10);
    }

    @Test
    void returnsErrorWhenTargetCodeMissing() {
        SceneSignalDataActionHandler handler = new SceneSignalDataActionHandler(
                this.objectMapper,
                List.of(new FakeSceneTargetDataProvider()));
        AgentDataQueryParam param = new AgentDataQueryParam(
                "scene.signal_data",
                this.objectMapper.valueToTree(Map.of("targetType", "STOCK")),
                null);

        AgentDataGatewayResponseVO response = handler.handle(this.session(), param);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("TARGET_REQUIRED");
    }

    private AgentSessionDTO session() {
        return new AgentSessionDTO(
                "agent-session",
                "secret",
                1L,
                "tester",
                "conversation",
                "message",
                Set.of("scene.signal_data"),
                Instant.now().plusSeconds(60));
    }

    private static class FakeSceneTargetDataProvider implements SceneTargetDataProvider {
        private String receivedTaskNo;
        private String receivedTargetCode;
        private SceneAnalysisSubmitParam receivedParam;

        @Override
        public boolean supports(String targetType) {
            return "STOCK".equals(targetType);
        }

        @Override
        public SceneAnalysisMessageDTO buildMessage(String taskNo, String targetCode, SceneAnalysisSubmitParam param) {
            this.receivedTaskNo = taskNo;
            this.receivedTargetCode = targetCode;
            this.receivedParam = param;
            return new SceneAnalysisMessageDTO(
                    taskNo,
                    LocalDateTime.now(),
                    "quick_analysis",
                    param.totalChunks(),
                    new SceneAnalysisTargetDTO("STOCK", targetCode, param.targetName(), null, null, null),
                    new SceneAnalysisConfigDTO("system_recommended",
                            SceneAnalysisUserConfigParam.effective(null, "STOCK")),
                    Map.of("latestPrice", 3.2),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of("complete", true));
        }
    }
}
