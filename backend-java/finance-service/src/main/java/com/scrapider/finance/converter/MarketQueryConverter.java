package com.scrapider.finance.converter;

import com.scrapider.finance.domain.po.BondIntradayTrendPO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexIntradayTrendPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.vo.BondIntradayTrendVO;
import com.scrapider.finance.domain.vo.BondKlineVO;
import com.scrapider.finance.domain.vo.BondQuoteVO;
import com.scrapider.finance.domain.vo.IndexIntradayTrendVO;
import com.scrapider.finance.domain.vo.IndexKlineVO;
import com.scrapider.finance.domain.vo.IndexQuoteVO;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockKlineVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import java.util.Comparator;
import java.util.List;

public final class MarketQueryConverter {

    private MarketQueryConverter() {
    }

    public static List<StockQuoteVO> toStockQuoteVOList(List<StockQuoteSnapshotPO> snapshots) {
        return snapshots.stream()
                .map(StockQuoteVO::fromPO)
                .toList();
    }

    public static List<StockIntradayTrendVO> toStockIntradayTrendVOList(List<StockIntradayTrendPO> trends) {
        return trends.stream()
                .map(StockIntradayTrendVO::fromPO)
                .toList();
    }

    public static List<StockKlineVO> toStockKlineVOList(List<StockKlinePO> klines) {
        return klines.stream()
                .map(StockKlineVO::fromPO)
                .sorted(Comparator.comparing(StockKlineVO::getTradeDate))
                .toList();
    }

    public static List<IndexQuoteVO> toIndexQuoteVOList(List<IndexQuoteSnapshotPO> snapshots) {
        return snapshots.stream()
                .map(IndexQuoteVO::fromPO)
                .toList();
    }

    public static List<IndexIntradayTrendVO> toIndexIntradayTrendVOList(List<IndexIntradayTrendPO> trends) {
        return trends.stream()
                .map(IndexIntradayTrendVO::fromPO)
                .toList();
    }

    public static List<IndexKlineVO> toIndexKlineVOList(List<IndexKlinePO> klines) {
        return klines.stream()
                .map(IndexKlineVO::fromPO)
                .sorted(Comparator.comparing(IndexKlineVO::getTradeDate))
                .toList();
    }

    public static List<BondQuoteVO> toBondQuoteVOList(List<BondQuoteSnapshotPO> snapshots) {
        return snapshots.stream()
                .map(BondQuoteVO::fromPO)
                .toList();
    }

    public static List<BondIntradayTrendVO> toBondIntradayTrendVOList(List<BondIntradayTrendPO> trends) {
        return trends.stream()
                .map(BondIntradayTrendVO::fromPO)
                .toList();
    }

    public static List<BondKlineVO> toBondKlineVOList(List<BondKlinePO> klines) {
        return klines.stream()
                .map(BondKlineVO::fromPO)
                .sorted(Comparator.comparing(BondKlineVO::getTradeDate))
                .toList();
    }
}
