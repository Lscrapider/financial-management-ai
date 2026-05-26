package com.scrapider.finance.ai.domain.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scrapider.finance.domain.po.OcrTaskPO;
import java.util.List;

public record OcrTaskPageVO(
        List<OcrTaskVO> records,
        Long total,
        Long pageNum,
        Long pageSize,
        Long pages) {

    public static OcrTaskPageVO fromPage(Page<OcrTaskPO> page) {
        return new OcrTaskPageVO(
                page.getRecords().stream().map(OcrTaskVO::fromPO).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages());
    }
}
