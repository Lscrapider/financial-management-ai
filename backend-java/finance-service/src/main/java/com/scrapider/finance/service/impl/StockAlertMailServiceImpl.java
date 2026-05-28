package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.service.StockAlertMailService;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class StockAlertMailServiceImpl implements StockAlertMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    public StockAlertMailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendAlert(AppUserPO user, StockAlertConfigPO config, BigDecimal latestPrice, BigDecimal changePercent, String syncedAt) {
        if (StrUtil.isBlank(this.from)) {
            throw new IllegalStateException("spring.mail.username must not be blank.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.from);
        message.setTo(user.getEmail());
        message.setSubject(this.subject(config, changePercent));
        message.setText(this.content(config, latestPrice, changePercent, syncedAt));
        this.mailSender.send(message);
    }

    private String subject(StockAlertConfigPO config, BigDecimal changePercent) {
        return String.format(
                "%s涨跌幅提醒：%s(%s) %s%%",
                this.typeLabel(config.getTargetType()),
                config.getStockName(),
                config.getStockCode(),
                this.format(changePercent));
    }

    private String content(StockAlertConfigPO config, BigDecimal latestPrice, BigDecimal changePercent, String syncedAt) {
        return String.format(
                """
                您关注的%s已超过涨跌幅提醒阈值。

                目标：%s(%s)
                当前价格：%s
                当前涨跌幅：%s%%
                配置阈值：%s%%
                """,
                this.typeLabel(config.getTargetType()),
                config.getStockName(),
                config.getStockCode(),
                this.format(latestPrice),
                this.format(changePercent),
                this.format(config.getThresholdPercent()));
    }

    private String typeLabel(String targetType) {
        return switch (targetType) {
            case "STOCK" -> "股票";
            case "INDEX" -> "指数";
            case "BOND" -> "可转债";
            default -> "目标";
        };
    }

    private String format(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
