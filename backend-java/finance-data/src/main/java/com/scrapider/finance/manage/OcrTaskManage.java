package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.mapper.OcrTaskMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OcrTaskManage extends ServiceImpl<OcrTaskMapper, OcrTaskPO> {

    public OcrTaskPO saveTask(OcrTaskPO task) {
        this.save(task);
        return task;
    }

    public List<OcrTaskPO> listRecentTasks(int limit) {
        return this.lambdaQuery()
                .orderByDesc(OcrTaskPO::getSubmittedAt)
                .page(Page.of(1, limit))
                .getRecords();
    }
}
