package com.scrapider.finance.service;

import com.scrapider.finance.domain.dto.AgentRunStartMessageDTO;

public interface AgentMessagePublisher {

    void publishRunStart(AgentRunStartMessageDTO message);
}
