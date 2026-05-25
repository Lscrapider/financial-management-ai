package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.ai.domain.dto.OcrNormalizeMessageDTO;
import com.scrapider.finance.ai.domain.dto.StoredOcrFileDTO;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import com.scrapider.finance.ai.service.OcrTaskService;
import com.scrapider.finance.ai.service.OcrTaskMessagePublisher;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.OcrTaskManage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrTaskServiceImpl implements OcrTaskService {

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "png", "jpg", "jpeg", "webp");
    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;
    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int MAX_LIST_LIMIT = 200;

    private final OcrTaskManage ocrTaskManage;
    private final OcrFileStorageService ocrFileStorageService;
    private final OcrTaskMessagePublisher ocrTaskMessagePublisher;

    public OcrTaskServiceImpl(
            OcrTaskManage ocrTaskManage,
            OcrFileStorageService ocrFileStorageService,
            OcrTaskMessagePublisher ocrTaskMessagePublisher) {
        this.ocrTaskManage = ocrTaskManage;
        this.ocrFileStorageService = ocrFileStorageService;
        this.ocrTaskMessagePublisher = ocrTaskMessagePublisher;
    }

    @Override
    public List<OcrTaskVO> submit(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        files.forEach(this::validateFile);
        return files.stream().map(this::submitOne).toList();
    }

    @Override
    public List<OcrTaskVO> listRecent(int limit) {
        int normalizedLimit = this.normalizeLimit(limit);
        return this.ocrTaskManage.listRecentTasks(normalizedLimit).stream()
                .map(OcrTaskVO::fromPO)
                .toList();
    }

    private OcrTaskVO submitOne(MultipartFile file) {
        String originalFilename = this.originalFilename(file);
        String fileType = this.fileType(originalFilename);
        String taskNo = "ocr-" + UUID.randomUUID().toString().replace("-", "");
        StoredOcrFileDTO storedFile = this.ocrFileStorageService.saveOriginalFile(taskNo, fileType, file);

        OcrTaskPO task = OcrTaskPO.createReady(
                taskNo,
                originalFilename,
                storedFile.storedFilename(),
                storedFile.storageUri(),
                fileType,
                file.getContentType(),
                file.getSize());
        OcrTaskPO savedTask = this.ocrTaskManage.saveTask(task);
        this.ocrTaskMessagePublisher.publishNormalizeMessage(OcrNormalizeMessageDTO.create(
                taskNo,
                storedFile.bucket(),
                storedFile.objectKey(),
                originalFilename,
                file.getContentType(),
                file.getSize()));
        return OcrTaskVO.fromPO(savedTask);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("上传文件不能超过50MB");
        }
        String fileType = this.fileType(this.originalFilename(file));
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new IllegalArgumentException("仅支持 PDF、PNG、JPG、JPEG、WEBP 文件");
        }
    }

    private String originalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("上传文件名不能为空");
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String fileType(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }
}
