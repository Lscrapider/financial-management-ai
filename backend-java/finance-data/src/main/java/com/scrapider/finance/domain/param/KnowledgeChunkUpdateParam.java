package com.scrapider.finance.domain.param;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class KnowledgeChunkUpdateParam {

    private String text;
    private Map<String, List<String>> scenes;
    private boolean reembed;
}
