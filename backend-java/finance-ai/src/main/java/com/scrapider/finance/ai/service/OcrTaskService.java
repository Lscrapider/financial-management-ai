package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.OcrTaskPageParam;
import com.scrapider.finance.ai.domain.param.OcrTaskDeleteParam;
import com.scrapider.finance.ai.domain.vo.OcrChunkTagDetailVO;
import com.scrapider.finance.ai.domain.vo.OcrStageDetailVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskPageVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface OcrTaskService {

    List<OcrTaskVO> submit(List<MultipartFile> files);

    OcrTaskPageVO page(OcrTaskPageParam param);

    OcrStageDetailVO stageDetail(String taskNo);

    OcrChunkTagDetailVO chunkTagDetail(String taskNo);

    void delete(OcrTaskDeleteParam param);
}
