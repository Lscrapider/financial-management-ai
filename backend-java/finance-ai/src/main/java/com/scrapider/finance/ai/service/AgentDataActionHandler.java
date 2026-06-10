package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;

public interface AgentDataActionHandler {

    String action();

    default String runningMessage(AgentDataQueryParam param) {
        return "正在查询相关数据";
    }

    AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param);
}
