package com.scrapider.finance.ai.exception;

import com.scrapider.finance.domain.exception.BusinessException;

public class AiUsageLimitExceededException extends BusinessException {

    public AiUsageLimitExceededException(String message) {
        super(message);
    }
}
