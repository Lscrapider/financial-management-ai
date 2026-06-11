package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.AiInvestorPsychProfilePO;
import com.scrapider.finance.mapper.AiInvestorPsychProfileMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class AiInvestorPsychProfileManage
        extends ServiceImpl<AiInvestorPsychProfileMapper, AiInvestorPsychProfilePO> {

    public AiInvestorPsychProfilePO findActive(Long userId) {
        return this.lambdaQuery()
                .eq(AiInvestorPsychProfilePO::getUserId, userId)
                .eq(AiInvestorPsychProfilePO::getStatus, AiInvestorPsychProfilePO.STATUS_ACTIVE)
                .last("LIMIT 1")
                .one();
    }

    public Long nextVersion(Long userId) {
        AiInvestorPsychProfilePO latest = this.lambdaQuery()
                .eq(AiInvestorPsychProfilePO::getUserId, userId)
                .orderByDesc(AiInvestorPsychProfilePO::getProfileVersion)
                .last("LIMIT 1")
                .one();
        return latest == null || latest.getProfileVersion() == null ? 1L : latest.getProfileVersion() + 1L;
    }

    public void replaceActive(AiInvestorPsychProfilePO profile) {
        LocalDateTime now = LocalDateTime.now();
        this.lambdaUpdate()
                .eq(AiInvestorPsychProfilePO::getUserId, profile.getUserId())
                .eq(AiInvestorPsychProfilePO::getStatus, AiInvestorPsychProfilePO.STATUS_ACTIVE)
                .set(AiInvestorPsychProfilePO::getStatus, AiInvestorPsychProfilePO.STATUS_ARCHIVED)
                .set(AiInvestorPsychProfilePO::getUpdatedAt, now)
                .update();
        this.save(profile);
    }
}
