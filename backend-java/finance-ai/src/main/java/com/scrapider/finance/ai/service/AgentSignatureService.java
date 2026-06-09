package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import jakarta.servlet.http.HttpServletRequest;

public interface AgentSignatureService {

    AgentSessionDTO verify(HttpServletRequest request, String rawBody);
}
