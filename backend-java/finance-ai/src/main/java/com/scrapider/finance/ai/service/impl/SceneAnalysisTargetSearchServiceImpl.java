package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisTargetOptionVO;
import com.scrapider.finance.ai.service.SceneAnalysisTargetSearchService;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.StockConfigManage;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisTargetSearchServiceImpl implements SceneAnalysisTargetSearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final StockConfigManage stockConfigManage;
    private final IndexConfigManage indexConfigManage;
    private final BondConfigManage bondConfigManage;

    public SceneAnalysisTargetSearchServiceImpl(
            StockConfigManage stockConfigManage,
            IndexConfigManage indexConfigManage,
            BondConfigManage bondConfigManage) {
        this.stockConfigManage = stockConfigManage;
        this.indexConfigManage = indexConfigManage;
        this.bondConfigManage = bondConfigManage;
    }

    @Override
    public List<SceneAnalysisTargetOptionVO> search(String targetType, String keyword, Integer limit) {
        String normalizedTargetType = this.normalizeTargetType(targetType);
        String normalizedKeyword = StrUtil.trim(keyword);
        int limited = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return switch (normalizedTargetType) {
            case "STOCK" -> this.stockConfigManage.searchEnabledStocks(normalizedKeyword, limited).stream()
                    .map(this::stockOption)
                    .toList();
            case "INDEX" -> this.indexConfigManage.searchEnabledIndices(normalizedKeyword, limited).stream()
                    .map(this::indexOption)
                    .toList();
            case "CONVERTIBLE_BOND" -> this.bondConfigManage.searchEnabledBonds(normalizedKeyword, limited).stream()
                    .map(this::bondOption)
                    .toList();
            default -> throw new IllegalArgumentException("unsupported targetType: " + targetType);
        };
    }

    private SceneAnalysisTargetOptionVO stockOption(StockConfigPO po) {
        return new SceneAnalysisTargetOptionVO(
                "STOCK",
                po.getStockCode(),
                po.getStockName(),
                po.getSecid(),
                po.getMarketCode(),
                po.getExchangeCode());
    }

    private SceneAnalysisTargetOptionVO indexOption(IndexConfigPO po) {
        return new SceneAnalysisTargetOptionVO(
                "INDEX",
                po.getIndexCode(),
                po.getIndexName(),
                po.getSecid(),
                po.getMarketCode(),
                po.getExchangeCode());
    }

    private SceneAnalysisTargetOptionVO bondOption(BondConfigPO po) {
        return new SceneAnalysisTargetOptionVO(
                "CONVERTIBLE_BOND",
                po.getBondCode(),
                po.getBondName(),
                po.getSecid(),
                po.getMarketCode(),
                po.getExchangeCode());
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return "STOCK";
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STOCK" -> "STOCK";
            case "INDEX" -> "INDEX";
            case "CONVERTIBLE_BOND", "BOND" -> "CONVERTIBLE_BOND";
            default -> normalized;
        };
    }
}
