package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.StoredOcrFileDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.multipart.MultipartFile;

public interface OcrFileStorageService {

    StoredOcrFileDTO saveOriginalFile(String taskNo, String fileType, MultipartFile file);

    JsonNode readJson(String bucket, String objectKey);

    byte[] readBytes(String bucket, String objectKey);

    void writeJson(String bucket, String objectKey, JsonNode content);
}
