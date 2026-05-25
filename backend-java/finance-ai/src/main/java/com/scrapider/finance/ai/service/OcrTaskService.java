package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import org.springframework.web.multipart.MultipartFile;

public interface OcrTaskService {

    OcrTaskVO submit(MultipartFile file);
}
