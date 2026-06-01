package com.scrapider.finance.ai.domain.param;

import java.util.List;

public record ManualKnowledgeDraftParam(
        String title,
        List<String> chunks) {
}
