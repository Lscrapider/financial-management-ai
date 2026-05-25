package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.dto.AiTokenUsageSummaryDTO;
import com.scrapider.finance.domain.dto.AiTokenUsageTrendDTO;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import com.scrapider.finance.mapper.AiTokenUsageLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiTokenUsageLogManage extends ServiceImpl<AiTokenUsageLogMapper, AiTokenUsageLogPO> {

    public AiTokenUsageLogPO saveLog(AiTokenUsageLogPO log) {
        this.save(log);
        return log;
    }

    public AiTokenUsageSummaryDTO summarySince(LocalDateTime startTime) {
        return this.baseMapper.summarySince(startTime);
    }

    public List<AiTokenUsageTrendDTO> trendSince(LocalDateTime startTime) {
        return this.baseMapper.trendSince(startTime);
    }
}
