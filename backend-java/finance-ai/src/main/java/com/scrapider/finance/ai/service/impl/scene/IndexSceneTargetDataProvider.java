package com.scrapider.finance.ai.service.impl.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.SceneTargetDataConverter;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IndexSceneTargetDataProvider extends AbstractSceneTargetDataProvider implements SceneTargetDataProvider {

    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexConfigManage indexConfigManage;
    private final IndexKlineManage indexKlineManage;

    public IndexSceneTargetDataProvider(
            ObjectMapper objectMapper,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexConfigManage indexConfigManage,
            IndexKlineManage indexKlineManage) {
        super(objectMapper);
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexConfigManage = indexConfigManage;
        this.indexKlineManage = indexKlineManage;
    }

    @Override
    public boolean supports(String targetType) {
        return "INDEX".equals(targetType);
    }

    @Override
    public SceneAnalysisMessageDTO buildMessage(String taskNo, String indexCode, SceneAnalysisSubmitParam param) {
        List<String> missing = new ArrayList<>();
        IndexQuoteSnapshotPO quote = this.indexQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .eq(IndexQuoteSnapshotPO::getIndexCode, indexCode)
                        .last("LIMIT 1"));
        if (quote == null) {
            missing.add("index_quote_snapshot");
        }
        IndexConfigPO config = this.indexConfigManage.getOne(
                new LambdaQueryWrapper<IndexConfigPO>()
                        .eq(IndexConfigPO::getIndexCode, indexCode)
                        .last("LIMIT 1"));
        String targetName = this.firstNotBlank(
                param.targetName(),
                quote == null ? null : quote.getIndexName(),
                config == null ? null : config.getIndexName());
        SceneAnalysisTargetDTO target = SceneTargetDataConverter.indexTarget(indexCode, targetName, quote, config);
        List<Map<String, Object>> dailyKlines = this.indexKlineManage
                .listKlines(indexCode, null, KlinePeriodTypeEnum.DAILY, null, null, MARKET_KLINE_LIMIT)
                .stream()
                .map(this::toMap)
                .toList();
        if (dailyKlines.isEmpty()) {
            missing.add("index_kline");
        }
        return this.message(
                taskNo,
                param,
                target,
                this.toMap(quote),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                dailyKlines,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                missing);
    }
}
