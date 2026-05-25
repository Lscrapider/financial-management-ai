package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.dto.AppVisitSummaryDTO;
import com.scrapider.finance.domain.dto.AppVisitTrendDTO;
import com.scrapider.finance.domain.po.AppVisitLogPO;
import com.scrapider.finance.mapper.AppVisitLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AppVisitLogManage extends ServiceImpl<AppVisitLogMapper, AppVisitLogPO> {

    public void saveLog(AppVisitLogPO log) {
        this.save(log);
    }

    public AppVisitSummaryDTO summarySince(LocalDateTime startTime) {
        return this.baseMapper.summarySince(startTime);
    }

    public List<AppVisitTrendDTO> trendSince(LocalDateTime startTime) {
        return this.baseMapper.trendSince(startTime);
    }
}
