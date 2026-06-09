package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.converter.SceneAnalysisConfigConverter;
import com.scrapider.finance.ai.converter.SceneAnalysisTargetConverter;
import com.scrapider.finance.ai.domain.param.SceneAnalysisConfigProfileParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigFieldVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigGroupVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigProfileVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTypeVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisTargetOptionVO;
import com.scrapider.finance.ai.service.SceneAnalysisMetadataService;
import com.scrapider.finance.domain.enums.SceneAnalysisReportTypeEnum;
import com.scrapider.finance.domain.po.SceneAnalysisConfigProfilePO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.SceneAnalysisConfigProfileManage;
import com.scrapider.finance.manage.StockConfigManage;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisMetadataServiceImpl implements SceneAnalysisMetadataService {

    private static final int DEFAULT_TOTAL_CHUNKS = 10;
    private static final int DEFAULT_TARGET_SEARCH_LIMIT = 20;
    private static final int MAX_TARGET_SEARCH_LIMIT = 50;
    private static final List<SceneAnalysisConfigGroupVO> PARAMETER_SCHEMA = List.of(
            SceneAnalysisConfigConverter.group("asset", "标的", List.of(
                    SceneAnalysisConfigConverter.field("lowPriceThreshold", "低价阈值", List.of("asset_config", "low_price_threshold"),
                            5.0, 1.0, 20.0, 0.5, "元", "3 到 10",
                            "判断低价标的的价格边界，主要影响资产特征相关标签。"))),
            SceneAnalysisConfigConverter.group("price", "价格", List.of(
                    SceneAnalysisConfigConverter.field("priceRiseCenter", "上涨中心", List.of("price_config", "price_rise_center"),
                            2.0, 0.5, 8.0, 0.1, "%", "1.5 到 4",
                            "涨幅达到该中心值附近时，更容易触发上涨相关场景。"),
                    SceneAnalysisConfigConverter.field("priceRiseScale", "上涨敏感度", List.of("price_config", "price_rise_scale"),
                            1.2, 0.2, 4.0, 0.1, null, "0.8 到 1.8",
                            "上涨信号的缩放参数，数值越低越敏感。"),
                    SceneAnalysisConfigConverter.field("priceDropCenter", "下跌中心", List.of("price_config", "price_drop_center"),
                            2.0, 0.5, 8.0, 0.1, "%", "1.5 到 4",
                            "跌幅达到该中心值附近时，更容易触发下跌相关场景。"),
                    SceneAnalysisConfigConverter.field("priceDropScale", "下跌敏感度", List.of("price_config", "price_drop_scale"),
                            1.2, 0.2, 4.0, 0.1, null, "0.8 到 1.8",
                            "下跌信号的缩放参数，数值越低越敏感。"),
                    SceneAnalysisConfigConverter.field("priceMoveCenter", "波动中心", List.of("price_config", "price_move_center"),
                            2.0, 0.5, 8.0, 0.1, "%", "1.5 到 4",
                            "绝对涨跌幅达到该中心值附近时，更容易触发价格波动相关场景。"),
                    SceneAnalysisConfigConverter.field("priceMoveScale", "波动敏感度", List.of("price_config", "price_move_scale"),
                            1.2, 0.2, 4.0, 0.1, null, "0.8 到 1.8",
                            "价格波动信号的缩放参数，数值越低越敏感。"),
                    SceneAnalysisConfigConverter.field("pullbackThreshold", "回撤阈值", List.of("price_config", "pullback_threshold"),
                            0.08, 0.01, 0.3, 0.01, null, "0.05 到 0.12",
                            "用于识别从高点回撤的幅度阈值。"),
                    SceneAnalysisConfigConverter.field("gapThreshold", "跳空阈值", List.of("price_config", "gap_threshold"),
                            0.03, 0.005, 0.12, 0.005, null, "0.02 到 0.05",
                            "用于识别跳空缺口，数值越小越敏感。"))),
            SceneAnalysisConfigConverter.group("volume", "量能", List.of(
                    SceneAnalysisConfigConverter.field("volumeExpandCenter", "放量中心", List.of("volume_config", "volume_expand_center"),
                            1.0, 0.2, 4.0, 0.1, null, "0.8 到 1.8",
                            "量能相对历史或行业分布的放大中心。"),
                    SceneAnalysisConfigConverter.field("volumeExpandScale", "放量敏感度", List.of("volume_config", "volume_expand_scale"),
                            0.8, 0.1, 3.0, 0.1, null, "0.5 到 1.2",
                            "放量信号的缩放参数，数值越低越敏感。"),
                    SceneAnalysisConfigConverter.field("volumeSpikeCenter", "脉冲放量中心", List.of("volume_config", "volume_spike_center"),
                            1.8, 0.5, 6.0, 0.1, null, "1.5 到 3",
                            "极端放量识别中心，越低越容易识别为脉冲放量。"),
                    SceneAnalysisConfigConverter.field("volumeSpikeScale", "脉冲放量敏感度", List.of("volume_config", "volume_spike_scale"),
                            0.7, 0.1, 3.0, 0.1, null, "0.5 到 1.0",
                            "脉冲放量信号的缩放参数，数值越低越敏感。"))),
            SceneAnalysisConfigConverter.group("sentiment", "情绪", List.of(
                    SceneAnalysisConfigConverter.field("attentionRiseCenter", "关注升温中心",
                            List.of("sentiment_config", "attention_rise_center"),
                            1.5, 0.2, 4.0, 0.1, null, "1 到 2",
                            "市场关注度上升的中心值，影响情绪升温相关场景。"),
                    SceneAnalysisConfigConverter.field("attentionRiseScale", "关注升温敏感度",
                            List.of("sentiment_config", "attention_rise_scale"),
                            0.4, 0.1, 1.5, 0.1, null, "0.3 到 0.8",
                            "市场关注度升温信号的缩放参数，数值越低越敏感。"),
                    SceneAnalysisConfigConverter.field("lowAttentionScale", "低关注敏感度",
                            List.of("sentiment_config", "low_attention_scale"),
                            0.5, 0.1, 1.5, 0.1, null, "0.3 到 0.8",
                            "低关注识别的缩放参数，越低越敏感。"),
                    SceneAnalysisConfigConverter.field("emotionPriceRise", "情绪价格权重",
                            List.of("sentiment_config", "market_proxy_emotion_weights", "price_rise"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "情绪升温代理指标中，价格上涨信号的权重。"),
                    SceneAnalysisConfigConverter.field("emotionVolumeExpand", "情绪放量权重",
                            List.of("sentiment_config", "market_proxy_emotion_weights", "volume_expand"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "情绪升温代理指标中，放量信号的权重。"),
                    SceneAnalysisConfigConverter.field("emotionHighTurnover", "情绪高换手权重",
                            List.of("sentiment_config", "market_proxy_emotion_weights", "high_turnover"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "情绪升温代理指标中，高换手信号的权重。"),
                    SceneAnalysisConfigConverter.field("emotionAttentionRise", "情绪关注权重",
                            List.of("sentiment_config", "market_proxy_emotion_weights", "market_attention_rise"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "情绪升温代理指标中，交易关注度上升信号的权重。"),
                    SceneAnalysisConfigConverter.field("panicPriceDrop", "恐慌下跌权重",
                            List.of("sentiment_config", "market_proxy_panic_weights", "price_drop"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "恐慌抛售代理指标中，价格下跌信号的权重。"),
                    SceneAnalysisConfigConverter.field("panicVolumeExpand", "恐慌放量权重",
                            List.of("sentiment_config", "market_proxy_panic_weights", "volume_expand"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "恐慌抛售代理指标中，放量信号的权重。"),
                    SceneAnalysisConfigConverter.field("panicBreakRecentLow", "恐慌破位权重",
                            List.of("sentiment_config", "market_proxy_panic_weights", "break_recent_low"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "恐慌抛售代理指标中，跌破近期低位信号的权重。"),
                    SceneAnalysisConfigConverter.field("panicCloseWeak", "恐慌收弱权重",
                            List.of("sentiment_config", "market_proxy_panic_weights", "close_weak"),
                            0.15, 0.0, 1.0, 0.05, null, "0.10 到 0.25",
                            "恐慌抛售代理指标中，收盘偏弱信号的权重。"),
                    SceneAnalysisConfigConverter.field("weakPriceDrop", "弱势下跌权重",
                            List.of("sentiment_config", "market_proxy_weak_weights", "price_drop"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "弱势情绪代理指标中，价格下跌信号的权重。"),
                    SceneAnalysisConfigConverter.field("weakVolumeShrink", "弱势缩量权重",
                            List.of("sentiment_config", "market_proxy_weak_weights", "volume_shrink"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "弱势情绪代理指标中，缩量信号的权重。"),
                    SceneAnalysisConfigConverter.field("weakLowTurnover", "弱势低换手权重",
                            List.of("sentiment_config", "market_proxy_weak_weights", "low_turnover"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "弱势情绪代理指标中，低换手信号的权重。"),
                    SceneAnalysisConfigConverter.field("weakCloseWeak", "弱势收弱权重",
                            List.of("sentiment_config", "market_proxy_weak_weights", "close_weak"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "弱势情绪代理指标中，收盘偏弱信号的权重。"),
                    SceneAnalysisConfigConverter.field("weakLowAttention", "弱势低关注权重",
                            List.of("sentiment_config", "market_proxy_weak_weights", "low_attention"),
                            0.15, 0.0, 1.0, 0.05, null, "0.10 到 0.25",
                            "弱势情绪代理指标中，低关注信号的权重。"),
                    SceneAnalysisConfigConverter.field("herdingHighTurnover", "拥挤高换手权重",
                            List.of("sentiment_config", "market_proxy_herding_weights", "high_turnover"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "羊群效应代理指标中，高换手信号的权重。"),
                    SceneAnalysisConfigConverter.field("herdingVolumeExpand", "拥挤放量权重",
                            List.of("sentiment_config", "market_proxy_herding_weights", "volume_expand"),
                            0.30, 0.0, 1.0, 0.05, null, "0.20 到 0.40",
                            "羊群效应代理指标中，放量信号的权重。"),
                    SceneAnalysisConfigConverter.field("herdingAttentionRise", "拥挤关注权重",
                            List.of("sentiment_config", "market_proxy_herding_weights", "market_attention_rise"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "羊群效应代理指标中，交易关注度上升信号的权重。"))),
            SceneAnalysisConfigConverter.group("risk", "风险", List.of(
                    SceneAnalysisConfigConverter.field("supportDistanceThreshold", "支撑距离阈值",
                            List.of("risk_strategy_config", "support_distance_threshold"),
                            0.08, 0.01, 0.3, 0.01, null, "0.05 到 0.12",
                            "距离支撑位过远时，仓位和止损策略会更谨慎。"),
                    SceneAnalysisConfigConverter.field("chaseHighPriceRise", "追高价格权重",
                            List.of("risk_strategy_config", "chase_high_risk_weights", "price_rise"),
                            0.25, 0.0, 1.0, 0.05, null, "0.2 到 0.35",
                            "追高风险中，价格上涨信号的权重。"),
                    SceneAnalysisConfigConverter.field("chaseHighNearRecentHigh", "追高高位权重",
                            List.of("risk_strategy_config", "chase_high_risk_weights", "near_recent_high"),
                            0.25, 0.0, 1.0, 0.05, null, "0.2 到 0.35",
                            "追高风险中，接近近期高位信号的权重。"),
                    SceneAnalysisConfigConverter.field("chaseHighVolumeExpand", "追高放量权重",
                            List.of("risk_strategy_config", "chase_high_risk_weights", "volume_expand"),
                            0.20, 0.0, 1.0, 0.05, null, "0.1 到 0.3",
                            "追高风险中，放量信号的权重。"),
                    SceneAnalysisConfigConverter.field("chaseHighTurnover", "追高换手权重",
                            List.of("risk_strategy_config", "chase_high_risk_weights", "high_turnover"),
                            0.15, 0.0, 1.0, 0.05, null, "0.1 到 0.25",
                            "追高风险中，高换手信号的权重。"),
                    SceneAnalysisConfigConverter.field("chaseHighEmotion", "追高情绪权重",
                            List.of("risk_strategy_config", "chase_high_risk_weights", "short_term_emotion"),
                            0.15, 0.0, 1.0, 0.05, null, "0.1 到 0.25",
                            "追高风险中，短线情绪升温信号的权重。"),
                    SceneAnalysisConfigConverter.field("falseBreakout", "假突破权重",
                            List.of("risk_strategy_config", "false_breakout_risk_weights", "breakout"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "假突破风险中，突破信号自身的权重。"),
                    SceneAnalysisConfigConverter.field("falseBreakoutCloseWeak", "假突破收弱权重",
                            List.of("risk_strategy_config", "false_breakout_risk_weights", "close_weak"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "假突破风险中，收盘偏弱信号的权重。"),
                    SceneAnalysisConfigConverter.field("falseBreakoutUpperShadow", "假突破上影权重",
                            List.of("risk_strategy_config", "false_breakout_risk_weights", "upper_shadow"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "假突破风险中，上影线信号的权重。"),
                    SceneAnalysisConfigConverter.field("falseBreakoutVolumeExpand", "假突破放量权重",
                            List.of("risk_strategy_config", "false_breakout_risk_weights", "volume_expand"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "假突破风险中，放量信号的权重。"),
                    SceneAnalysisConfigConverter.field("liquidityLowTurnover", "流动性低换手权重",
                            List.of("risk_strategy_config", "liquidity_risk_weights", "low_turnover"),
                            0.40, 0.0, 1.0, 0.05, null, "0.30 到 0.50",
                            "流动性风险中，低换手信号的权重。"),
                    SceneAnalysisConfigConverter.field("liquidityLowVolume", "流动性低成交权重",
                            List.of("risk_strategy_config", "liquidity_risk_weights", "low_volume"),
                            0.30, 0.0, 1.0, 0.05, null, "0.20 到 0.40",
                            "流动性风险中，低成交量信号的权重。"),
                    SceneAnalysisConfigConverter.field("drawdownNearRecentHigh", "回撤高位权重",
                            List.of("risk_strategy_config", "drawdown_risk_weights", "near_recent_high"),
                            0.30, 0.0, 1.0, 0.05, null, "0.20 到 0.40",
                            "回撤风险中，接近近期高位信号的权重。"),
                    SceneAnalysisConfigConverter.field("drawdownVolatility", "回撤波动权重",
                            List.of("risk_strategy_config", "drawdown_risk_weights", "volatility"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "回撤风险中，波动率信号的权重。"),
                    SceneAnalysisConfigConverter.field("drawdownPriceRise", "回撤上涨权重",
                            List.of("risk_strategy_config", "drawdown_risk_weights", "price_rise"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "回撤风险中，上涨强度信号的权重。"),
                    SceneAnalysisConfigConverter.field("drawdownSupportDistance", "回撤支撑距离权重",
                            List.of("risk_strategy_config", "drawdown_risk_weights", "support_distance"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "回撤风险中，距离支撑位信号的权重。"),
                    SceneAnalysisConfigConverter.field("overheatedPriceRise", "过热上涨权重",
                            List.of("risk_strategy_config", "overheated_risk_weights", "price_rise"),
                            0.30, 0.0, 1.0, 0.05, null, "0.20 到 0.40",
                            "过热风险中，上涨强度信号的权重。"),
                    SceneAnalysisConfigConverter.field("overheatedVolumeExpand", "过热放量权重",
                            List.of("risk_strategy_config", "overheated_risk_weights", "volume_expand"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "过热风险中，放量信号的权重。"),
                    SceneAnalysisConfigConverter.field("overheatedHighTurnover", "过热换手权重",
                            List.of("risk_strategy_config", "overheated_risk_weights", "high_turnover"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "过热风险中，高换手信号的权重。"),
                    SceneAnalysisConfigConverter.field("overheatedEmotion", "过热情绪权重",
                            List.of("risk_strategy_config", "overheated_risk_weights", "short_term_emotion"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "过热风险中，短线情绪升温信号的权重。"),
                    SceneAnalysisConfigConverter.field("positionRiskControl", "仓位风控权重",
                            List.of("risk_strategy_config", "position_control_weights", "risk_control"),
                            0.50, 0.0, 1.0, 0.05, null, "0.40 到 0.60",
                            "仓位控制中，综合风险控制信号的权重。"),
                    SceneAnalysisConfigConverter.field("positionVolatility", "仓位波动权重",
                            List.of("risk_strategy_config", "position_control_weights", "volatility"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "仓位控制中，波动率信号的权重。"),
                    SceneAnalysisConfigConverter.field("positionUncertainty", "仓位不确定性权重",
                            List.of("risk_strategy_config", "position_control_weights", "uncertainty"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "仓位控制中，不确定性信号的权重。"),
                    SceneAnalysisConfigConverter.field("takeProfitPriceRise", "止盈上涨权重",
                            List.of("risk_strategy_config", "take_profit_plan_weights", "price_rise"),
                            0.30, 0.0, 1.0, 0.05, null, "0.20 到 0.40",
                            "止盈计划中，上涨强度信号的权重。"),
                    SceneAnalysisConfigConverter.field("takeProfitNearRecentHigh", "止盈高位权重",
                            List.of("risk_strategy_config", "take_profit_plan_weights", "near_recent_high"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "止盈计划中，接近近期高位信号的权重。"),
                    SceneAnalysisConfigConverter.field("takeProfitOverheated", "止盈过热权重",
                            List.of("risk_strategy_config", "take_profit_plan_weights", "overheated_risk"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "止盈计划中，过热风险信号的权重。"),
                    SceneAnalysisConfigConverter.field("takeProfitDrawdown", "止盈回撤权重",
                            List.of("risk_strategy_config", "take_profit_plan_weights", "drawdown_risk"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "止盈计划中，回撤风险信号的权重。"),
                    SceneAnalysisConfigConverter.field("stopLossBreakLow", "止损破位权重",
                            List.of("risk_strategy_config", "stop_loss_plan_weights", "break_recent_low"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "止损计划中，跌破近期低位信号的权重。"),
                    SceneAnalysisConfigConverter.field("stopLossDowntrend", "止损下行权重",
                            List.of("risk_strategy_config", "stop_loss_plan_weights", "downtrend"),
                            0.25, 0.0, 1.0, 0.05, null, "0.15 到 0.35",
                            "止损计划中，下行趋势信号的权重。"),
                    SceneAnalysisConfigConverter.field("stopLossPanic", "止损恐慌权重",
                            List.of("risk_strategy_config", "stop_loss_plan_weights", "panic_selling"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "止损计划中，恐慌抛售信号的权重。"),
                    SceneAnalysisConfigConverter.field("stopLossDrawdown", "止损回撤权重",
                            List.of("risk_strategy_config", "stop_loss_plan_weights", "drawdown_risk"),
                            0.20, 0.0, 1.0, 0.05, null, "0.10 到 0.30",
                            "止损计划中，回撤风险信号的权重。"),
                    SceneAnalysisConfigConverter.field("uncertaintySentimentConflict", "情绪冲突权重",
                            List.of("risk_strategy_config", "uncertainty_weights", "sentiment_conflict"),
                            1.00, 0.0, 1.0, 0.05, null, "1.0",
                            "不确定性计算中，情绪冲突信号的权重。"))));

    private final SceneAnalysisConfigProfileManage sceneAnalysisConfigProfileManage;
    private final StockConfigManage stockConfigManage;
    private final IndexConfigManage indexConfigManage;
    private final BondConfigManage bondConfigManage;

    public SceneAnalysisMetadataServiceImpl(
            SceneAnalysisConfigProfileManage sceneAnalysisConfigProfileManage,
            StockConfigManage stockConfigManage,
            IndexConfigManage indexConfigManage,
            BondConfigManage bondConfigManage) {
        this.sceneAnalysisConfigProfileManage = sceneAnalysisConfigProfileManage;
        this.stockConfigManage = stockConfigManage;
        this.indexConfigManage = indexConfigManage;
        this.bondConfigManage = bondConfigManage;
    }

    @Override
    public List<SceneAnalysisConfigGroupVO> parameterSchema() {
        return PARAMETER_SCHEMA;
    }

    @Override
    public List<SceneAnalysisReportTypeVO> reportTypes() {
        return Arrays.stream(SceneAnalysisReportTypeEnum.values())
                .map(SceneAnalysisReportTypeVO::fromEnum)
                .toList();
    }

    @Override
    public List<SceneAnalysisConfigProfileVO> listProfiles() {
        Long userId = this.currentUserId();
        return SceneAnalysisConfigConverter.toProfileVOList(
                this.sceneAnalysisConfigProfileManage.listAvailable(userId));
    }

    @Override
    public SceneAnalysisConfigProfileVO create(SceneAnalysisConfigProfileParam param) {
        Long userId = this.currentUserId();
        NormalizedProfile normalized = this.normalized(param, this.generatedConfigProfile(userId));
        SceneAnalysisConfigProfilePO profile = SceneAnalysisConfigProfilePO.createCustom(
                userId,
                normalized.name(),
                normalized.configGroup(),
                normalized.configProfile(),
                normalized.targetType(),
                normalized.reportType(),
                normalized.configJson());
        this.sceneAnalysisConfigProfileManage.createProfile(profile);
        return SceneAnalysisConfigProfileVO.fromPO(profile);
    }

    @Override
    public SceneAnalysisConfigProfileVO update(Long id, SceneAnalysisConfigProfileParam param) {
        if (id == null) {
            throw new IllegalArgumentException("config profile id is required");
        }
        Long userId = this.currentUserId();
        SceneAnalysisConfigProfilePO existing = this.sceneAnalysisConfigProfileManage.getAvailable(id, userId);
        if (existing == null || Boolean.TRUE.equals(existing.getSystemDefault())) {
            throw new IllegalArgumentException("config profile is not editable");
        }
        NormalizedProfile normalized = this.normalized(param, existing.getConfigProfile());
        existing.setName(normalized.name());
        existing.setConfigGroup(normalized.configGroup());
        existing.setConfigProfile(normalized.configProfile());
        existing.setTargetType(normalized.targetType());
        existing.setReportType(normalized.reportType());
        existing.setConfigJson(normalized.configJson());
        if (!this.sceneAnalysisConfigProfileManage.updateEditable(existing)) {
            throw new IllegalArgumentException("config profile is not editable");
        }
        return SceneAnalysisConfigProfileVO.fromPO(existing);
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("config profile id is required");
        }
        Long userId = this.currentUserId();
        SceneAnalysisConfigProfilePO existing = this.sceneAnalysisConfigProfileManage.getById(id);
        if (existing == null) {
            return;
        }
        if (Boolean.TRUE.equals(existing.getSystemDefault())
                || existing.getUserId() == null
                || !Objects.equals(existing.getUserId(), userId)) {
            throw new IllegalArgumentException("config profile is not editable");
        }
        if (!Boolean.TRUE.equals(existing.getEnabled())) {
            return;
        }
        if (!this.sceneAnalysisConfigProfileManage.disableEditable(id, userId)) {
            SceneAnalysisConfigProfilePO latest = this.sceneAnalysisConfigProfileManage.getById(id);
            if (latest != null && !Boolean.TRUE.equals(latest.getEnabled())) {
                return;
            }
            throw new IllegalArgumentException("config profile is not editable");
        }
    }

    @Override
    public List<SceneAnalysisTargetOptionVO> search(String targetType, String keyword, Integer limit) {
        String normalizedTargetType = this.normalizeSearchTargetType(targetType);
        String normalizedKeyword = StrUtil.trim(keyword);
        int limited = limit == null || limit <= 0
                ? DEFAULT_TARGET_SEARCH_LIMIT
                : Math.min(limit, MAX_TARGET_SEARCH_LIMIT);
        return switch (normalizedTargetType) {
            case "STOCK" -> this.stockConfigManage.searchEnabledStocks(normalizedKeyword, limited).stream()
                    .map(SceneAnalysisTargetConverter::stockOption)
                    .toList();
            case "INDEX" -> this.indexConfigManage.searchEnabledIndices(normalizedKeyword, limited).stream()
                    .map(SceneAnalysisTargetConverter::indexOption)
                    .toList();
            case "CONVERTIBLE_BOND" -> this.bondConfigManage.searchEnabledBonds(normalizedKeyword, limited).stream()
                    .map(SceneAnalysisTargetConverter::bondOption)
                    .toList();
            default -> throw new IllegalArgumentException("unsupported targetType: " + targetType);
        };
    }

    private NormalizedProfile normalized(SceneAnalysisConfigProfileParam param, String configProfile) {
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String name = StrUtil.trim(param.name());
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("config profile name is required");
        }
        JsonNode configJson = this.configJson(param.configJson());
        String reportType = this.normalizeReportType(this.firstNotBlank(
                param.reportType(),
                configJson.path("reportType").asText(null),
                SceneAnalysisReportTypeEnum.QUICK_ANALYSIS.getCode()));
        String targetType = this.normalizeTargetType(this.firstNotBlank(
                param.targetType(),
                configJson.path("targetType").asText(null)));
        String configGroup = StrUtil.blankToDefault(StrUtil.trim(param.configGroup()), "自定义");

        ObjectNode normalizedJson = SceneAnalysisConfigConverter.normalizedConfigJson(
                configJson,
                reportType,
                this.totalChunks(configJson),
                configProfile,
                targetType);
        if (!normalizedJson.path("userOverrides").isObject()) {
            throw new IllegalArgumentException("userOverrides must be an object");
        }
        return new NormalizedProfile(
                name,
                configGroup,
                configProfile,
                targetType,
                reportType,
                normalizedJson);
    }

    private String normalizeReportType(String reportType) {
        if (StrUtil.isBlank(reportType)) {
            return SceneAnalysisReportTypeEnum.QUICK_ANALYSIS.getCode();
        }
        return SceneAnalysisReportTypeEnum.of(reportType.trim()).getCode();
    }

    private JsonNode configJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("configJson must be an object");
        }
        return node;
    }

    private int totalChunks(JsonNode configJson) {
        int totalChunks = configJson.path("totalChunks").asInt(DEFAULT_TOTAL_CHUNKS);
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("totalChunks must be greater than 0");
        }
        return totalChunks;
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return null;
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STOCK" -> "STOCK";
            case "INDEX" -> "INDEX";
            case "CONVERTIBLE_BOND", "BOND" -> "CONVERTIBLE_BOND";
            default -> throw new IllegalArgumentException("unsupported targetType: " + targetType);
        };
    }

    private String normalizeSearchTargetType(String targetType) {
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

    private String generatedConfigProfile(Long userId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "user_" + userId + "_" + suffix;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("login required");
        }
        Object principal = authentication.getPrincipal();
        try {
            Method getUser = principal.getClass().getMethod("getUser");
            Object user = getUser.invoke(principal);
            Method getId = user.getClass().getMethod("getId");
            Object id = getId.invoke(user);
            if (id instanceof Long userId) {
                return userId;
            }
            if (id instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("login user id is unavailable", ex);
        }
        throw new IllegalArgumentException("login user id is unavailable");
    }

    private record NormalizedProfile(
            String name,
            String configGroup,
            String configProfile,
            String targetType,
            String reportType,
            ObjectNode configJson) {
    }

}
