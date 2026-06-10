package com.scrapider.finance.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.ai.websocket.AiChatWebSocketSessionRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentDataGatewayServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsProgressRunningBeforeHandlerExecution() throws Exception {
        FakeSessionRegistry sessionRegistry = new FakeSessionRegistry();
        AgentDataGatewayServiceImpl service = new AgentDataGatewayServiceImpl(
                List.of(new FakeActionHandler()),
                sessionRegistry,
                this.objectMapper);
        AgentDataQueryParam param = new AgentDataQueryParam("test.action", this.objectMapper.createObjectNode(), null);

        AgentDataGatewayResponseVO response = service.query(this.session(), param);

        assertThat(response.success()).isTrue();
        assertThat(sessionRegistry.payloads).hasSize(1);

        JsonNode running = this.objectMapper.readTree(sessionRegistry.payloads.get(0));
        assertThat(running.get("type").asText()).isEqualTo("agent_progress");
        assertThat(running.get("status").asText()).isEqualTo("running");
        assertThat(running.get("content").asText()).isEqualTo("正在执行测试查询");
        assertThat(running.get("conversationId").asText()).isEqualTo("conversation");
        assertThat(running.get("messageId").asText()).isEqualTo("message");
    }

    @Test
    void keepsProgressRunningWhenHandlerFails() {
        FakeSessionRegistry sessionRegistry = new FakeSessionRegistry();
        AgentDataGatewayServiceImpl service = new AgentDataGatewayServiceImpl(
                List.of(new FailingActionHandler()),
                sessionRegistry,
                this.objectMapper);
        AgentDataQueryParam param = new AgentDataQueryParam("fail.action", this.objectMapper.createObjectNode(), null);

        assertThatThrownBy(() -> service.query(this.session(), param))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(sessionRegistry.payloads).hasSize(1);
    }

    private AgentSessionDTO session() {
        return new AgentSessionDTO(
                "agent-session",
                "secret",
                1L,
                "tester",
                "conversation",
                "message",
                Set.of("test.action"),
                Instant.now().plusSeconds(60));
    }

    private static class FakeActionHandler implements AgentDataActionHandler {
        @Override
        public String action() {
            return "test.action";
        }

        @Override
        public String runningMessage(AgentDataQueryParam param) {
            return "正在执行测试查询";
        }

        @Override
        public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
            return new AgentDataGatewayResponseVO("test.action", true, List.of(), Map.of(), null);
        }
    }

    private static class FailingActionHandler implements AgentDataActionHandler {
        @Override
        public String action() {
            return "fail.action";
        }

        @Override
        public String runningMessage(AgentDataQueryParam param) {
            return "正在执行失败查询";
        }

        @Override
        public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
            throw new IllegalStateException("boom");
        }
    }

    private static class FakeSessionRegistry extends AiChatWebSocketSessionRegistry {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public void sendToConversation(Long userId, String conversationId, String payload) throws IOException {
            this.payloads.add(payload);
        }
    }
}
