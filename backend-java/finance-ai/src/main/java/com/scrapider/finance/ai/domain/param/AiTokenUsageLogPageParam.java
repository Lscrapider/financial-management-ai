package com.scrapider.finance.ai.domain.param;

public class AiTokenUsageLogPageParam extends AiTokenUsageQueryParam {

    private Integer pageNum = 1;
    private Integer pageSize = 20;

    public Integer getPageNum() {
        return this.pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return this.pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

}
