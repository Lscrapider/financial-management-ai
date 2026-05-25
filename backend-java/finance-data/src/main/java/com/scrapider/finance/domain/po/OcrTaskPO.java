package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ocr_task")
public class OcrTaskPO {

    private Long id;
    private String taskNo;
    private String originalFilename;
    private String storedFilename;
    private String filePath;
    private String fileType;
    private String contentType;
    private Long fileSize;
    private String status;
    private String currentStage;
    private Integer progress;
    private Integer pageCount;
    private Integer segmentCount;
    private String errorMessage;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;

    public static OcrTaskPO createPending(
            String taskNo,
            String originalFilename,
            String storedFilename,
            String filePath,
            String fileType,
            String contentType,
            long fileSize) {
        LocalDateTime now = LocalDateTime.now();
        OcrTaskPO task = new OcrTaskPO();
        task.setTaskNo(taskNo);
        task.setOriginalFilename(originalFilename);
        task.setStoredFilename(storedFilename);
        task.setFilePath(filePath);
        task.setFileType(fileType);
        task.setContentType(contentType);
        task.setFileSize(fileSize);
        task.setStatus("pending");
        task.setCurrentStage("等待处理");
        task.setProgress(0);
        task.setPageCount(0);
        task.setSegmentCount(0);
        task.setSubmittedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
