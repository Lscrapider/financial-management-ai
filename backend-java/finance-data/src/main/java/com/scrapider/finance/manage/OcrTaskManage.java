package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.mapper.OcrTaskMapper;
import org.springframework.stereotype.Service;

@Service
public class OcrTaskManage extends ServiceImpl<OcrTaskMapper, OcrTaskPO> {

    public OcrTaskPO saveTask(OcrTaskPO task) {
        this.save(task);
        return task;
    }
}
