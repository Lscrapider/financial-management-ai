package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.po.SceneAnalysisConfigProfilePO;
import com.scrapider.finance.mapper.SceneAnalysisConfigProfileMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisConfigProfileManage
        extends ServiceImpl<SceneAnalysisConfigProfileMapper, SceneAnalysisConfigProfilePO> {

    private final ObjectMapper objectMapper;

    public SceneAnalysisConfigProfileManage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SceneAnalysisConfigProfilePO> listAvailable(Long userId) {
        return this.list(new LambdaQueryWrapper<SceneAnalysisConfigProfilePO>()
                .eq(SceneAnalysisConfigProfilePO::getEnabled, true)
                .and(wrapper -> wrapper
                        .isNull(SceneAnalysisConfigProfilePO::getUserId)
                        .or()
                        .eq(SceneAnalysisConfigProfilePO::getUserId, userId))
                .orderByDesc(SceneAnalysisConfigProfilePO::getSystemDefault)
                .orderByAsc(SceneAnalysisConfigProfilePO::getConfigGroup)
                .orderByAsc(SceneAnalysisConfigProfilePO::getId));
    }

    public SceneAnalysisConfigProfilePO getAvailable(Long id, Long userId) {
        return this.getOne(new LambdaQueryWrapper<SceneAnalysisConfigProfilePO>()
                .eq(SceneAnalysisConfigProfilePO::getId, id)
                .eq(SceneAnalysisConfigProfilePO::getEnabled, true)
                .and(wrapper -> wrapper
                        .isNull(SceneAnalysisConfigProfilePO::getUserId)
                        .or()
                        .eq(SceneAnalysisConfigProfilePO::getUserId, userId))
                .last("LIMIT 1"));
    }

    public void createProfile(SceneAnalysisConfigProfilePO profile) {
        this.baseMapper.insertProfile(profile, this.toJson(profile.getConfigJson()));
    }

    public boolean updateEditable(SceneAnalysisConfigProfilePO profile) {
        profile.setUpdatedAt(LocalDateTime.now());
        return this.baseMapper.updateEditableProfile(profile, this.toJson(profile.getConfigJson())) > 0;
    }

    public boolean disableEditable(Long id, Long userId) {
        return this.baseMapper.disableEditableProfile(id, userId, LocalDateTime.now()) > 0;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "{}";
        }
        try {
            return this.objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize scene analysis config profile json", ex);
        }
    }
}
