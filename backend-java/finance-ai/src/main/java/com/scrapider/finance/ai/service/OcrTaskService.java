package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface OcrTaskService {

    List<OcrTaskVO> submit(List<MultipartFile> files);

    List<OcrTaskVO> listRecent(int limit);
}
