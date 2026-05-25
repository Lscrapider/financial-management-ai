package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.po.OcrTaskPO;
import java.time.LocalDateTime;

public record OcrTaskVO(
        Long id,
        String taskNo,
        String originalFilename,
        String fileType,
        Long fileSize,
        String status,
        String currentStage,
        Integer progress,
        Integer pageCount,
        Integer segmentCount,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt) {

    public static OcrTaskVO fromPO(OcrTaskPO task) {
        return new OcrTaskVO(
                task.getId(),
                task.getTaskNo(),
                task.getOriginalFilename(),
                task.getFileType(),
                task.getFileSize(),
                task.getStatus(),
                task.getCurrentStage(),
                value(task.getProgress()),
                value(task.getPageCount()),
                value(task.getSegmentCount()),
                task.getSubmittedAt(),
                task.getUpdatedAt());
    }

    private static Integer value(Integer value) {
        return value == null ? 0 : value;
    }
}
