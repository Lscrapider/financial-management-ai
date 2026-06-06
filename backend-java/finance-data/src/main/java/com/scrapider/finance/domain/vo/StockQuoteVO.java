package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.Data;

@Data
public class StockQuoteVO {

    private static final Map<Integer, String> CONFIRMED_FIELD_NAMES = Map.ofEntries(
            Map.entry(0, "市场标识"),
            Map.entry(1, "股票名称"),
            Map.entry(2, "股票代码"),
            Map.entry(3, "最新价"),
            Map.entry(4, "昨收"),
            Map.entry(5, "今开"),
            Map.entry(6, "成交量"),
            Map.entry(7, "外盘"),
            Map.entry(8, "内盘"),
            Map.entry(9, "买一价"),
            Map.entry(10, "买一量"),
            Map.entry(11, "买二价"),
            Map.entry(12, "买二量"),
            Map.entry(13, "买三价"),
            Map.entry(14, "买三量"),
            Map.entry(15, "买四价"),
            Map.entry(16, "买四量"),
            Map.entry(17, "买五价"),
            Map.entry(18, "买五量"),
            Map.entry(19, "卖一价"),
            Map.entry(20, "卖一量"),
            Map.entry(21, "卖二价"),
            Map.entry(22, "卖二量"),
            Map.entry(23, "卖三价"),
            Map.entry(24, "卖三量"),
            Map.entry(25, "卖四价"),
            Map.entry(26, "卖四量"),
            Map.entry(27, "卖五价"),
            Map.entry(28, "卖五量"),
            Map.entry(30, "行情时间"),
            Map.entry(31, "涨跌额"),
            Map.entry(32, "涨跌幅"),
            Map.entry(33, "最高价"),
            Map.entry(34, "最低价"),
            Map.entry(35, "价格/成交量/成交额"),
            Map.entry(36, "成交量"),
            Map.entry(37, "成交额"),
            Map.entry(38, "换手率"),
            Map.entry(39, "市盈率(TTM)"),
            Map.entry(43, "振幅"),
            Map.entry(44, "流通市值"),
            Map.entry(45, "总市值"),
            Map.entry(46, "市净率"),
            Map.entry(47, "涨停价"),
            Map.entry(48, "跌停价"),
            Map.entry(49, "量比"),
            Map.entry(50, "现手"),
            Map.entry(51, "均价"),
            Map.entry(52, "动态市盈率"),
            Map.entry(53, "静态市盈率"),
            Map.entry(57, "成交额"),
            Map.entry(59, "交易状态"),
            Map.entry(61, "类型"),
            Map.entry(82, "币种"));

    private static final Set<Integer> DECIMAL_FIELD_INDEXES = Set.of(
            3, 4, 5, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27,
            31, 32, 33, 34, 37, 38, 39, 43, 44, 45, 46, 47, 48, 49, 51, 52, 53, 57);

    private String stockCode;
    private String stockName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private BigDecimal latestPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClosePrice;
    private BigDecimal averagePrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private Long externalVolume;
    private Long internalVolume;
    private Long currentVolume;
    private BigDecimal turnoverAmount;
    private BigDecimal turnoverRate;
    private BigDecimal amplitude;
    private BigDecimal volumeRatio;
    private BigDecimal limitUpPrice;
    private BigDecimal limitDownPrice;
    private BigDecimal totalMarketValue;
    private BigDecimal floatMarketValue;
    private BigDecimal peTtm;
    private BigDecimal peDynamic;
    private BigDecimal peStatic;
    private BigDecimal pbRatio;
    private Integer tradeStatus;
    private List<StockQuoteDetailVO> quoteDetails;
    private LocalDateTime syncedAt;

    public static StockQuoteVO fromPO(StockQuoteSnapshotPO po) {
        StockQuoteVO vo = new StockQuoteVO();
        vo.setStockCode(po.getStockCode());
        vo.setStockName(po.getStockName());
        vo.setSecid(po.getSecid());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        vo.setLatestPrice(po.getLatestPrice());
        vo.setOpenPrice(po.getOpenPrice());
        vo.setHighPrice(po.getHighPrice());
        vo.setLowPrice(po.getLowPrice());
        vo.setPreviousClosePrice(po.getPreviousClosePrice());
        vo.setAveragePrice(po.getAveragePrice());
        vo.setChangeAmount(po.getChangeAmount());
        vo.setChangePercent(po.getChangePercent());
        vo.setVolume(po.getVolume());
        vo.setExternalVolume(po.getExternalVolume());
        vo.setInternalVolume(po.getInternalVolume());
        vo.setCurrentVolume(po.getCurrentVolume());
        vo.setTurnoverAmount(po.getTurnoverAmount());
        vo.setTurnoverRate(po.getTurnoverRate());
        vo.setAmplitude(po.getAmplitude());
        vo.setVolumeRatio(po.getVolumeRatio());
        vo.setLimitUpPrice(po.getLimitUpPrice());
        vo.setLimitDownPrice(po.getLimitDownPrice());
        vo.setTotalMarketValue(po.getTotalMarketValue());
        vo.setFloatMarketValue(po.getFloatMarketValue());
        vo.setPeTtm(po.getPeTtm());
        vo.setPeDynamic(po.getPeDynamic());
        vo.setPeStatic(po.getPeStatic());
        vo.setPbRatio(po.getPbRatio());
        vo.setTradeStatus(po.getTradeStatus());
        vo.setQuoteDetails(parseDetails(po.getRawResponse()));
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }

    private static List<StockQuoteDetailVO> parseDetails(String rawResponse) {
        String[] fields = StockQuoteSnapshotPO.extractTencentFields(rawResponse);
        return IntStream.range(0, fields.length)
                .filter(CONFIRMED_FIELD_NAMES::containsKey)
                .mapToObj(index -> StockQuoteDetailVO.of(index, fieldName(index), detailValue(index, fields[index])))
                .toList();
    }

    private static String fieldName(int index) {
        return CONFIRMED_FIELD_NAMES.getOrDefault(index, "字段" + index);
    }

    private static String detailValue(int index, String value) {
        if (!DECIMAL_FIELD_INDEXES.contains(index)) {
            return value;
        }
        BigDecimal decimal = StockMarketJsonParser.decimal(value);
        return decimal == null ? value : decimal.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }
}
