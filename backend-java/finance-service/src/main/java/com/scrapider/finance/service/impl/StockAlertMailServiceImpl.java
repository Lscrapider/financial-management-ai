package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
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
    public void sendAlert(AppUserPO user, StockAlertConfigPO config, StockQuoteSnapshotPO quote) {
        if (StrUtil.isBlank(this.from)) {
            throw new IllegalStateException("spring.mail.username must not be blank.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.from);
        message.setTo(user.getEmail());
        message.setSubject(this.subject(config, quote));
        message.setText(this.content(config, quote));
        this.mailSender.send(message);
    }

    private String subject(StockAlertConfigPO config, StockQuoteSnapshotPO quote) {
        return String.format(
                "股票涨跌幅提醒：%s(%s) %s%%",
                config.getStockName(),
                config.getStockCode(),
                this.format(quote.getChangePercent()));
    }

    private String content(StockAlertConfigPO config, StockQuoteSnapshotPO quote) {
        return String.format(
                """
                您关注的股票已超过涨跌幅提醒阈值。

                股票：%s(%s)
                当前价格：%s
                当前涨跌幅：%s%%
                配置阈值：%s%%
                行情同步时间：%s
                """,
                config.getStockName(),
                config.getStockCode(),
                this.format(quote.getLatestPrice()),
                this.format(quote.getChangePercent()),
                this.format(config.getThresholdPercent()),
                quote.getSyncedAt());
    }

    private String format(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
