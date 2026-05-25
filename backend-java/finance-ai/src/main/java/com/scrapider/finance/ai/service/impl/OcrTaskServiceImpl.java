package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.ai.service.OcrTaskService;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.OcrTaskManage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrTaskServiceImpl implements OcrTaskService {

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "png", "jpg", "jpeg", "webp");
    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;

    private final OcrTaskManage ocrTaskManage;
    private final Path storageRoot;

    public OcrTaskServiceImpl(
            OcrTaskManage ocrTaskManage,
            @Value("${finance.ocr.storage-path:../data/scans}") String storagePath) {
        this.ocrTaskManage = ocrTaskManage;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public OcrTaskVO submit(MultipartFile file) {
        this.validateFile(file);
        String originalFilename = this.originalFilename(file);
        String fileType = this.fileType(originalFilename);
        String taskNo = "ocr-" + UUID.randomUUID().toString().replace("-", "");
        String storedFilename = taskNo + "." + fileType;
        Path targetPath = this.storageRoot.resolve(storedFilename).normalize();

        if (!targetPath.startsWith(this.storageRoot)) {
            throw new IllegalArgumentException("文件路径不合法");
        }

        try {
            Files.createDirectories(this.storageRoot);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存上传文件失败", ex);
        }

        OcrTaskPO task = OcrTaskPO.createPending(
                taskNo,
                originalFilename,
                storedFilename,
                targetPath.toString(),
                fileType,
                file.getContentType(),
                file.getSize());
        return OcrTaskVO.fromPO(this.ocrTaskManage.saveTask(task));
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
}
