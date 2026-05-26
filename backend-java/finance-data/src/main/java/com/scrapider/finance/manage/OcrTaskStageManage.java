package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import com.scrapider.finance.mapper.OcrTaskStageMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OcrTaskStageManage extends ServiceImpl<OcrTaskStageMapper, OcrTaskStagePO> {

    public Optional<OcrTaskStagePO> findByTaskNoAndStage(String taskNo, String stage) {
        return this.lambdaQuery()
                .eq(OcrTaskStagePO::getTaskNo, taskNo)
                .eq(OcrTaskStagePO::getStage, stage)
                .oneOpt();
    }
}
