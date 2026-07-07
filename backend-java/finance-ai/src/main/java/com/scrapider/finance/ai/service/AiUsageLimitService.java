package com.scrapider.finance.ai.service;

public interface AiUsageLimitService {

    void requireCanGenerateReport(Long userId);

    void requireCanSendChat(Long userId);
}
