package com.scrapider.finance.service;

import java.util.List;

/**
 * 标的数据确保结果。
 */
public record AssetDataEnsureResult(boolean refreshAttempted, List<String> unavailableSections) {

    public AssetDataEnsureResult {
        unavailableSections = unavailableSections == null ? List.of() : List.copyOf(unavailableSections);
    }

    public boolean completed() {
        return this.unavailableSections().isEmpty();
    }
}
