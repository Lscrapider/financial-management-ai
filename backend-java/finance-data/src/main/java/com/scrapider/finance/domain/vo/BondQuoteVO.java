package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
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
public class BondQuoteVO {

    private static final Map<Integer, String> CONFIRMED_FIELD_NAMES = Map.ofEntries(
            Map.entry(0, "市场标识"),
            Map.entry(1, "名称"),
            Map.entry(2, "代码"),
            Map.entry(3, "最新价"),
            Map.entry(4, "昨收"),
            Map.entry(5, "今开"),
            Map.entry(6, "成交量"),
            Map.entry(7, "外盘"),
            Map.entry(8, "内盘"),
            Map.entry(30, "时间"),
            Map.entry(31, "涨跌额"),
            Map.entry(32, "涨跌幅"),
            Map.entry(33, "最高价"),
            Map.entry(34, "最低价"),
            Map.entry(35, "最新价/成交量/成交额"),
            Map.entry(36, "成交量"),
            Map.entry(37, "成交额"),
            Map.entry(38, "换手率"),
            Map.entry(41, "最高价"),
            Map.entry(42, "最低价"),
            Map.entry(43, "振幅"),
            Map.entry(47, "涨停价/上限价"),
            Map.entry(48, "跌停价/下限价"),
            Map.entry(49, "量比"),
            Map.entry(51, "均价"),
            Map.entry(57, "成交额"),
            Map.entry(61, "类型"),
            Map.entry(82, "币种"),
            Map.entry(85, "扩展价格字段"),
            Map.entry(86, "扩展数量/差值字段"));

    private static final Set<Integer> DECIMAL_FIELD_INDEXES = Set.of(
            3, 4, 5, 31, 32, 33, 34, 37, 43, 47, 48, 49, 51, 57);

    private String bondCode;
    private String bondName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private BigDecimal latestPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClosePrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal turnoverRate;
    private BigDecimal amplitude;
    private BigDecimal conversionValue;
    private BigDecimal conversionPremiumRate;
    private BigDecimal averagePrice;
    private Long currentVolume;
    private String bondRating;
    private List<StockQuoteDetailVO> quoteDetails;
    private LocalDateTime syncedAt;

    public static BondQuoteVO fromPO(BondQuoteSnapshotPO po) {
        BondQuoteVO vo = new BondQuoteVO();
        vo.setBondCode(po.getBondCode());
        vo.setBondName(po.getBondName());
        vo.setSecid(po.getSecid());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        vo.setLatestPrice(po.getLatestPrice());
        vo.setOpenPrice(po.getOpenPrice());
        vo.setHighPrice(po.getHighPrice());
        vo.setLowPrice(po.getLowPrice());
        vo.setPreviousClosePrice(po.getPreviousClosePrice());
        vo.setChangeAmount(po.getChangeAmount());
        vo.setChangePercent(po.getChangePercent());
        vo.setVolume(po.getVolume());
        vo.setTurnoverAmount(po.getTurnoverAmount());
        vo.setTurnoverRate(po.getTurnoverRate());
        vo.setAmplitude(po.getAmplitude());
        vo.setConversionValue(po.getConversionValue());
        vo.setConversionPremiumRate(po.getConversionPremiumRate());
        vo.setAveragePrice(po.getAveragePrice());
        vo.setCurrentVolume(po.getCurrentVolume());
        vo.setBondRating(po.getBondRating());
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
