package com.scrapider.finance.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scrapider.finance.ai.domain.param.OcrTaskDeleteParam;
import com.scrapider.finance.ai.domain.param.OcrTaskPageParam;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskPageVO;
import com.scrapider.finance.ai.domain.dto.OcrNormalizeMessageDTO;
import com.scrapider.finance.ai.domain.dto.StoredOcrFileDTO;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import com.scrapider.finance.ai.service.OcrTaskService;
import com.scrapider.finance.ai.service.OcrTaskMessagePublisher;
import com.scrapider.finance.domain.enums.OcrTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
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
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final OcrTaskManage ocrTaskManage;
    private final KnowledgeVectorManage knowledgeVectorManage;
    private final OcrFileStorageService ocrFileStorageService;
    private final OcrTaskMessagePublisher ocrTaskMessagePublisher;

    public OcrTaskServiceImpl(
            OcrTaskManage ocrTaskManage,
            KnowledgeVectorManage knowledgeVectorManage,
            OcrFileStorageService ocrFileStorageService,
            OcrTaskMessagePublisher ocrTaskMessagePublisher) {
        this.ocrTaskManage = ocrTaskManage;
        this.knowledgeVectorManage = knowledgeVectorManage;
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
    public OcrTaskPageVO page(OcrTaskPageParam param) {
        OcrTaskPageParam query = param == null ? new OcrTaskPageParam() : param;
        int pageNum = this.normalizePageNum(query.getPageNum());
        int pageSize = this.normalizePageSize(query.getPageSize());
        OcrTaskStatusEnum taskStatus = OcrTaskStatusEnum.fromCode(query.getStatus());
        Page<OcrTaskPO> page = this.ocrTaskManage.pageTasks(pageNum, pageSize, taskStatus);
        return OcrTaskPageVO.fromPage(page);
    }

    @Override
    public void delete(OcrTaskDeleteParam param) {
        if (param == null || param.taskNo() == null || param.taskNo().isBlank()) {
            throw new IllegalArgumentException("任务编号不能为空");
        }
        boolean deleted = this.ocrTaskManage.softDelete(param.taskNo().trim());
        if (!deleted) {
            throw new IllegalArgumentException("OCR 任务不存在或已删除");
        }
        this.knowledgeVectorManage.deleteByTaskNo(param.taskNo().trim());
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

    private int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum <= 0) {
            return DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
