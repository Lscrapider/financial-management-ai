package com.scrapider.finance.service;

import com.scrapider.finance.domain.dto.AgentSessionDTO;
import com.scrapider.finance.domain.param.AgentDataQueryParam;
import com.scrapider.finance.domain.vo.AgentDataGatewayResponseVO;

public interface AgentDataGatewayService {

    AgentDataGatewayResponseVO query(AgentSessionDTO session, AgentDataQueryParam param);
}
