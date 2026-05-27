package com.scrapider.finance.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.param.StockAlertConfigSaveParam;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.vo.StockAlertConfigVO;
import com.scrapider.finance.domain.vo.StockAlertStockOptionVO;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.manage.StockAlertConfigManage;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.service.StockAlertMailService;
import com.scrapider.finance.service.StockAlertService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StockAlertServiceImpl implements StockAlertService {

    private static final String ADMIN_ROLE_CODE = "admin";

    private final StockAlertConfigManage stockAlertConfigManage;
    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final AppUserManage appUserManage;
    private final StockAlertMailService stockAlertMailService;

    public StockAlertServiceImpl(
            StockAlertConfigManage stockAlertConfigManage,
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            AppUserManage appUserManage,
            StockAlertMailService stockAlertMailService) {
        this.stockAlertConfigManage = stockAlertConfigManage;
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.appUserManage = appUserManage;
        this.stockAlertMailService = stockAlertMailService;
    }

    @Override
    public List<StockAlertConfigVO> listAlerts(LoginUser loginUser) {
        Long currentUserId = this.currentUserId(loginUser);
        List<StockAlertConfigPO> configs = this.isAdmin(loginUser)
                ? this.stockAlertConfigManage.listAll()
                : this.stockAlertConfigManage.listByUserId(currentUserId);
        return this.toVOList(configs);
    }

    @Override
    public List<StockAlertStockOptionVO> listStockOptions() {
        return this.stockConfigManage.listEnabledStocks().stream()
                .map(StockAlertStockOptionVO::fromPO)
                .toList();
    }

    @Override
    public StockAlertConfigVO saveAlert(LoginUser loginUser, StockAlertConfigSaveParam param) {
        this.validateSaveParam(param);
        Long userId = this.currentUserId(loginUser);
        StockConfigPO stock = this.stockConfigManage.getEnabledByStockCode(param.getStockCode().trim());
        if (stock == null) {
            throw new IllegalArgumentException("Stock config not found or disabled.");
        }

        StockAlertConfigPO config = this.resolveSaveConfig(userId, stock, param);
        this.stockAlertConfigManage.saveOrUpdate(config);
        StockAlertConfigPO saved = this.stockAlertConfigManage.getByUserIdAndStockCode(userId, stock.getStockCode());
        return this.toVOList(List.of(saved)).get(0);
    }

    @Override
    public void deleteAlert(LoginUser loginUser, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null.");
        }
        StockAlertConfigPO config = this.stockAlertConfigManage.getByIdAndUserId(id, this.currentUserId(loginUser));
        if (config == null) {
            throw new IllegalArgumentException("Stock alert config not found.");
        }
        this.stockAlertConfigManage.removeById(config.getId());
    }

    @Override
    public void checkAlerts() {
        List<StockAlertConfigPO> configs = this.stockAlertConfigManage.listEnabled();
        if (CollUtil.isEmpty(configs)) {
            return;
        }
        Map<Long, AppUserPO> userMap = this.loadUsers(configs);
        Map<String, StockQuoteSnapshotPO> quoteMap = this.loadQuotes(configs);
        configs.forEach(config -> this.checkOneAlert(config, userMap.get(config.getUserId()), quoteMap.get(config.getStockCode())));
    }

    private StockAlertConfigPO resolveSaveConfig(Long userId, StockConfigPO stock, StockAlertConfigSaveParam param) {
        StockAlertConfigPO existing = param.getId() == null
                ? this.stockAlertConfigManage.getByUserIdAndStockCode(userId, stock.getStockCode())
                : this.stockAlertConfigManage.getByIdAndUserId(param.getId(), userId);
        StockAlertConfigPO config = StockAlertConfigPO.fromSaveParam(userId, stock, param);
        if (param.getId() != null && existing == null) {
            throw new IllegalArgumentException("Stock alert config not found.");
        }
        StockAlertConfigPO sameStockConfig =
                this.stockAlertConfigManage.getByUserIdAndStockCode(userId, stock.getStockCode());
        if (sameStockConfig != null && (existing == null || !sameStockConfig.getId().equals(existing.getId()))) {
            throw new IllegalArgumentException("Stock alert config already exists.");
        }
        if (existing != null) {
            config.setId(existing.getId());
            boolean thresholdChanged = existing.getThresholdPercent() == null
                    || existing.getThresholdPercent().compareTo(config.getThresholdPercent()) != 0;
            config.setAlertActive(!thresholdChanged && existing.getAlertActive());
            config.setLastAlertChangePercent(existing.getLastAlertChangePercent());
            config.setLastAlertedAt(existing.getLastAlertedAt());
        }
        return config;
    }

    private List<StockAlertConfigVO> toVOList(List<StockAlertConfigPO> configs) {
        if (CollUtil.isEmpty(configs)) {
            return List.of();
        }
        Map<Long, AppUserPO> userMap = this.loadUsers(configs);
        Map<String, StockQuoteSnapshotPO> quoteMap = this.loadQuotes(configs);
        return configs.stream()
                .map(config -> {
                    StockAlertConfigVO vo = StockAlertConfigVO.fromPO(config, quoteMap.get(config.getStockCode()));
                    AppUserPO user = userMap.get(config.getUserId());
                    if (user != null) {
                        vo.fillUser(user.getUsername(), user.getRealName(), user.getEmail(), user.getEmailNotification());
                    }
                    return vo;
                })
                .toList();
    }

    private Map<Long, AppUserPO> loadUsers(List<StockAlertConfigPO> configs) {
        List<Long> userIds = configs.stream()
                .map(StockAlertConfigPO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return this.appUserManage.listUsersByIds(userIds).stream()
                .collect(Collectors.toMap(AppUserPO::getId, Function.identity()));
    }

    private Map<String, StockQuoteSnapshotPO> loadQuotes(List<StockAlertConfigPO> configs) {
        List<String> stockCodes = configs.stream()
                .map(StockAlertConfigPO::getStockCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        return this.stockQuoteSnapshotManage.listByStockCodes(stockCodes).stream()
                .collect(Collectors.toMap(StockQuoteSnapshotPO::getStockCode, Function.identity()));
    }

    private void checkOneAlert(StockAlertConfigPO config, AppUserPO user, StockQuoteSnapshotPO quote) {
        if (quote == null || quote.getChangePercent() == null || user == null) {
            return;
        }
        boolean outOfThreshold = this.isOutOfThreshold(quote.getChangePercent(), config.getThresholdPercent());
        if (!outOfThreshold) {
            this.resetAlertActiveIfNeeded(config);
            return;
        }
        if (Boolean.TRUE.equals(config.getAlertActive()) || !this.canSendEmail(user)) {
            return;
        }
        try {
            this.stockAlertMailService.sendAlert(user, config, quote);
            config.setAlertActive(true);
            config.setLastAlertChangePercent(quote.getChangePercent());
            config.setLastAlertedAt(LocalDateTime.now());
            this.stockAlertConfigManage.updateById(config);
        } catch (Exception ex) {
            log.warn(
                    "Failed to send stock alert email, userId: {}, stockCode: {}",
                    config.getUserId(),
                    config.getStockCode(),
                    ex);
        }
    }

    private void resetAlertActiveIfNeeded(StockAlertConfigPO config) {
        if (!Boolean.TRUE.equals(config.getAlertActive())) {
            return;
        }
        config.setAlertActive(false);
        this.stockAlertConfigManage.updateById(config);
    }

    private boolean canSendEmail(AppUserPO user) {
        return Boolean.TRUE.equals(user.getEnabled())
                && Boolean.TRUE.equals(user.getEmailNotification())
                && StrUtil.isNotBlank(user.getEmail());
    }

    private boolean isOutOfThreshold(BigDecimal changePercent, BigDecimal thresholdPercent) {
        if (changePercent == null || thresholdPercent == null) {
            return false;
        }
        return changePercent.compareTo(thresholdPercent) > 0
                || changePercent.compareTo(thresholdPercent.negate()) < 0;
    }

    private void validateSaveParam(StockAlertConfigSaveParam param) {
        if (param == null || StrUtil.isBlank(param.getStockCode())) {
            throw new IllegalArgumentException("stockCode must not be blank.");
        }
        if (param.getThresholdPercent() == null || param.getThresholdPercent().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("thresholdPercent must be greater than 0.");
        }
    }

    private Long currentUserId(LoginUser loginUser) {
        if (loginUser == null || loginUser.getUser() == null || loginUser.getUser().getId() == null) {
            throw new IllegalArgumentException("Login user is required.");
        }
        return loginUser.getUser().getId();
    }

    private boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && ADMIN_ROLE_CODE.equals(loginUser.getRoleCode());
    }
}
