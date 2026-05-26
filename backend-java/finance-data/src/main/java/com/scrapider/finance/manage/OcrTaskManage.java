package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.OcrTaskStageEnum;
import com.scrapider.finance.domain.enums.OcrTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.mapper.OcrTaskMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class OcrTaskManage extends ServiceImpl<OcrTaskMapper, OcrTaskPO> {

    public OcrTaskPO saveTask(OcrTaskPO task) {
        this.save(task);
        return task;
    }

    public Page<OcrTaskPO> pageTasks(int pageNum, int pageSize, OcrTaskStatusEnum status) {
        return this.lambdaQuery()
                .isNull(OcrTaskPO::getDeletedAt)
                .eq(status != null, OcrTaskPO::getStatus, status == null ? null : status.getCode())
                .orderByDesc(OcrTaskPO::getSubmittedAt)
                .page(Page.of(pageNum, pageSize));
    }

    public boolean softDelete(String taskNo) {
        return this.lambdaUpdate()
                .eq(OcrTaskPO::getTaskNo, taskNo)
                .isNull(OcrTaskPO::getDeletedAt)
                .set(OcrTaskPO::getDeletedAt, LocalDateTime.now())
                .set(OcrTaskPO::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    public void markManualReviewRequired(String taskNo) {
        this.lambdaUpdate()
                .eq(OcrTaskPO::getTaskNo, taskNo)
                .set(OcrTaskPO::getStatus, OcrTaskStatusEnum.MANUAL_REVIEW_REQUIRED.getCode())
                .set(OcrTaskPO::getCurrentStage, OcrTaskStageEnum.QUALITY_VALIDATE.getCode())
                .set(OcrTaskPO::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    public void markEmbeddingIndexRunning(String taskNo, int segmentCount) {
        this.lambdaUpdate()
                .eq(OcrTaskPO::getTaskNo, taskNo)
                .set(OcrTaskPO::getStatus, OcrTaskStatusEnum.RUNNING.getCode())
                .set(OcrTaskPO::getCurrentStage, OcrTaskStageEnum.EMBEDDING_INDEX.getCode())
                .set(OcrTaskPO::getSegmentCount, segmentCount)
                .set(OcrTaskPO::getUpdatedAt, LocalDateTime.now())
                .update();
    }
}
