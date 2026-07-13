package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.MarketTradingCalendarPO;
import com.scrapider.finance.mapper.MarketTradingCalendarMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketTradingCalendarManage
        extends ServiceImpl<MarketTradingCalendarMapper, MarketTradingCalendarPO> {

    public MarketTradingCalendarPO findByExchangeAndCalendarDate(String exchange, LocalDate calendarDate) {
        if (StrUtil.isBlank(exchange) || calendarDate == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<MarketTradingCalendarPO>()
                .eq(MarketTradingCalendarPO::getExchange, exchange)
                .eq(MarketTradingCalendarPO::getCalendarDate, calendarDate)
                .last("LIMIT 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveCalendars(List<MarketTradingCalendarPO> calendars) {
        if (CollUtil.isEmpty(calendars)) {
            return;
        }
        Map<CalendarKey, Long> existingIds = this.listExistingIds(calendars);
        calendars.forEach(calendar -> calendar.setId(existingIds.get(this.calendarKey(calendar))));
        this.saveOrUpdateBatch(calendars);
    }

    private Map<CalendarKey, Long> listExistingIds(List<MarketTradingCalendarPO> calendars) {
        Set<String> exchanges = calendars.stream()
                .map(MarketTradingCalendarPO::getExchange)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        Set<LocalDate> calendarDates = calendars.stream()
                .map(MarketTradingCalendarPO::getCalendarDate)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (exchanges.isEmpty() || calendarDates.isEmpty()) {
            return Map.of();
        }
        return this.list(new LambdaQueryWrapper<MarketTradingCalendarPO>()
                        .in(MarketTradingCalendarPO::getExchange, exchanges)
                        .in(MarketTradingCalendarPO::getCalendarDate, calendarDates))
                .stream()
                .collect(Collectors.toMap(this::calendarKey, MarketTradingCalendarPO::getId, (left, right) -> left));
    }

    private CalendarKey calendarKey(MarketTradingCalendarPO calendar) {
        return new CalendarKey(calendar.getExchange(), calendar.getCalendarDate());
    }

    private record CalendarKey(String exchange, LocalDate calendarDate) {
    }
}
