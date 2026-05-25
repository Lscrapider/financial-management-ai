package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.StoredOcrFileDTO;
import org.springframework.web.multipart.MultipartFile;

public interface OcrFileStorageService {

    StoredOcrFileDTO saveOriginalFile(String taskNo, String fileType, MultipartFile file);
}
