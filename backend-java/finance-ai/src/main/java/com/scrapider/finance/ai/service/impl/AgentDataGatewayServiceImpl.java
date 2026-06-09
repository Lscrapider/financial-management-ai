package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.ai.service.AgentDataGatewayService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentDataGatewayServiceImpl implements AgentDataGatewayService {

    private final Map<String, AgentDataActionHandler> actionHandlers;

    public AgentDataGatewayServiceImpl(List<AgentDataActionHandler> handlers) {
        Map<String, AgentDataActionHandler> handlerMap = new LinkedHashMap<>();
        for (AgentDataActionHandler handler : handlers) {
            String action = handler.action();
            if (StrUtil.isBlank(action)) {
                throw new IllegalStateException("Agent data action handler action 不能为空: "
                        + handler.getClass().getName());
            }
            AgentDataActionHandler exists = handlerMap.putIfAbsent(action, handler);
            if (exists != null) {
                throw new IllegalStateException("Agent data action handler 重复注册: " + action);
            }
        }
        this.actionHandlers = Map.copyOf(handlerMap);
    }

    @Override
    public AgentDataGatewayResponseVO query(AgentSessionDTO session, AgentDataQueryParam param) {
        if (param == null || StrUtil.isBlank(param.action())) {
            return this.error(null, "ACTION_REQUIRED", "数据查询 action 不能为空");
        }
        AgentDataActionHandler handler = this.actionHandlers.get(param.action());
        if (handler == null) {
            return this.error(param.action(), "UNSUPPORTED_ACTION", "暂不支持数据查询 action: " + param.action());
        }
        return handler.handle(session, param);
    }

    private AgentDataGatewayResponseVO error(String action, String code, String message) {
        return new AgentDataGatewayResponseVO(
                action,
                false,
                List.of(),
                Map.of("queriedAt", OffsetDateTime.now().toString()),
                new AgentDataGatewayResponseVO.Error(code, message));
    }
}
