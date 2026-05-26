package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.OcrTaskPageParam;
import com.scrapider.finance.ai.domain.param.OcrTaskDeleteParam;
import com.scrapider.finance.ai.domain.vo.OcrTaskPageVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface OcrTaskService {

    List<OcrTaskVO> submit(List<MultipartFile> files);

    OcrTaskPageVO page(OcrTaskPageParam param);

    void delete(OcrTaskDeleteParam param);
}
