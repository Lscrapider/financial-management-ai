package com.scrapider.finance.ai.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrapider.finance.domain.enums.AiTokenUsagePhaseEnum;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import org.junit.jupiter.api.Test;

class AiTokenUsageLogVOTest {

    @Test
    void phaseEnumProvidesChineseLabel() {
        assertThat(AiTokenUsagePhaseEnum.fromCode("tool_result_answer").getLabel())
                .isEqualTo("基于工具结果回答");
    }

    @Test
    void fromPOMapsPhaseLabel() {
        AiTokenUsageLogPO po = new AiTokenUsageLogPO();
        po.setPhase("direct_answer");

        AiTokenUsageLogVO vo = AiTokenUsageLogVO.fromPO(po);

        assertThat(vo.phase()).isEqualTo("direct_answer");
        assertThat(vo.phaseLabel()).isEqualTo("直接回答");
    }
}
