package com.scrapider.finance.service;

import com.scrapider.finance.domain.dto.AgentSessionDTO;
import jakarta.servlet.http.HttpServletRequest;

public interface AgentSignatureService {

    AgentSessionDTO verify(HttpServletRequest request, String rawBody);
}
