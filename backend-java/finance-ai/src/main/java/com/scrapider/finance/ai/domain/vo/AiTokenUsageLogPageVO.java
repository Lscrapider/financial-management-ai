package com.scrapider.finance.ai.domain.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import java.util.List;
import java.util.Map;

public record AiTokenUsageLogPageVO(
        List<AiTokenUsageLogVO> records,
        Long total,
        Long pageNum,
        Long pageSize,
        Long pages) {

    public static AiTokenUsageLogPageVO fromPage(Page<AiTokenUsageLogPO> page) {
        return fromPage(page, Map.of());
    }

    public static AiTokenUsageLogPageVO fromPage(Page<AiTokenUsageLogPO> page, Map<Long, String> usernameMap) {
        return new AiTokenUsageLogPageVO(
                page.getRecords().stream()
                        .map(po -> AiTokenUsageLogVO.fromPO(po, usernameMap.get(po.getUserId())))
                        .toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages());
    }
}
