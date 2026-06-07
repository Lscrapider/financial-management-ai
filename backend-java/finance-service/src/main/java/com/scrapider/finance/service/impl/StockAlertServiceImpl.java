package com.scrapider.finance.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.converter.StockAlertConverter;
import com.scrapider.finance.domain.param.StockAlertConfigSaveParam;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.vo.StockAlertConfigVO;
import com.scrapider.finance.domain.vo.StockAlertStockOptionVO;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
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
    private static final String TYPE_STOCK = "STOCK";
    private static final String TYPE_INDEX = "INDEX";
    private static final String TYPE_BOND = "BOND";

    private final StockAlertConfigManage stockAlertConfigManage;
    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final IndexConfigManage indexConfigManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final BondConfigManage bondConfigManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final AppUserManage appUserManage;
    private final StockAlertMailService stockAlertMailService;

    public StockAlertServiceImpl(
            StockAlertConfigManage stockAlertConfigManage,
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            IndexConfigManage indexConfigManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            BondConfigManage bondConfigManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            AppUserManage appUserManage,
            StockAlertMailService stockAlertMailService) {
        this.stockAlertConfigManage = stockAlertConfigManage;
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.indexConfigManage = indexConfigManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.bondConfigManage = bondConfigManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.appUserManage = appUserManage;
        this.stockAlertMailService = stockAlertMailService;
    }

    private record QuoteSnapshot(BigDecimal latestPrice, BigDecimal changePercent, LocalDateTime syncedAt) {}

    @Override
    public List<StockAlertConfigVO> listAlerts(LoginUser loginUser, String targetType) {
        Long currentUserId = this.currentUserId(loginUser);
        List<StockAlertConfigPO> configs = this.isAdmin(loginUser)
                ? this.stockAlertConfigManage.listAll(targetType)
                : this.stockAlertConfigManage.listByUserId(currentUserId, targetType);
        return this.toVOList(configs);
    }

    @Override
    public List<StockAlertStockOptionVO> listTargetOptions(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return List.of();
        }
        return switch (targetType.toUpperCase()) {
            case TYPE_STOCK -> this.stockConfigManage.listEnabledStocks().stream()
                    .map(StockAlertStockOptionVO::fromStockPO)
                    .toList();
            case TYPE_INDEX -> this.indexConfigManage.listEnabledIndices().stream()
                    .map(StockAlertStockOptionVO::fromIndexPO)
                    .toList();
            case TYPE_BOND -> this.bondConfigManage.listEnabledBonds().stream()
                    .map(StockAlertStockOptionVO::fromBondPO)
                    .toList();
            default -> List.of();
        };
    }

    @Override
    public StockAlertConfigVO saveAlert(LoginUser loginUser, StockAlertConfigSaveParam param) {
        this.validateSaveParam(param);
        Long userId = this.currentUserId(loginUser);
        String targetType = param.getTargetType().trim().toUpperCase();
        String targetCode = param.getStockCode().trim();
        String targetName = this.resolveTargetName(targetType, targetCode);

        StockAlertConfigPO config = this.resolveSaveConfig(loginUser, userId, targetType, targetCode, targetName, param);
        this.stockAlertConfigManage.saveOrUpdate(config);
        StockAlertConfigPO saved = this.stockAlertConfigManage.getById(config.getId());
        return this.toVOList(List.of(saved)).get(0);
    }

    @Override
    public void deleteAlert(LoginUser loginUser, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null.");
        }
        StockAlertConfigPO config = this.stockAlertConfigManage.getById(id);
        if (config == null) {
            throw new IllegalArgumentException("Alert config not found.");
        }
        this.validateEditable(loginUser, this.currentUserId(loginUser), config);
        this.stockAlertConfigManage.removeById(config.getId());
    }

    @Override
    public void checkAlerts() {
        List<StockAlertConfigPO> configs = this.stockAlertConfigManage.listEnabled();
        if (CollUtil.isEmpty(configs)) {
            return;
        }
        Map<Long, AppUserPO> userMap = this.loadUsers(configs);
        Map<String, StockQuoteSnapshotPO> stockQuoteMap = this.loadStockQuotes(configs);
        Map<String, IndexQuoteSnapshotPO> indexQuoteMap = this.loadIndexQuotes(configs);
        Map<String, BondQuoteSnapshotPO> bondQuoteMap = this.loadBondQuotes(configs);
        configs.forEach(config -> {
            AppUserPO user = userMap.get(config.getUserId());
            QuoteSnapshot snapshot = this.getSnapshot(config, stockQuoteMap, indexQuoteMap, bondQuoteMap);
            this.checkOneAlert(config, user, snapshot);
        });
    }

    private String resolveTargetName(String targetType, String targetCode) {
        return switch (targetType) {
            case TYPE_STOCK -> {
                StockConfigPO stock = this.stockConfigManage.getEnabledByStockCode(targetCode);
                if (stock == null) {
                    throw new IllegalArgumentException("Stock config not found or disabled: " + targetCode);
                }
                yield stock.getStockName();
            }
            case TYPE_INDEX -> {
                List<IndexConfigPO> indices = this.indexConfigManage.listEnabledIndices();
                IndexConfigPO index = indices.stream()
                        .filter(i -> i.getIndexCode().equals(targetCode))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Index config not found or disabled: " + targetCode));
                yield index.getIndexName();
            }
            case TYPE_BOND -> {
                List<BondConfigPO> bonds = this.bondConfigManage.listEnabledBonds();
                BondConfigPO bond = bonds.stream()
                        .filter(b -> b.getBondCode().equals(targetCode))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Bond config not found or disabled: " + targetCode));
                yield bond.getBondName();
            }
            default -> throw new IllegalArgumentException("Unknown targetType: " + targetType);
        };
    }

    private QuoteSnapshot getSnapshot(
            StockAlertConfigPO config,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap,
            Map<String, IndexQuoteSnapshotPO> indexQuoteMap,
            Map<String, BondQuoteSnapshotPO> bondQuoteMap) {
        return switch (config.getTargetType()) {
            case TYPE_STOCK -> {
                StockQuoteSnapshotPO q = stockQuoteMap.get(config.getStockCode());
                yield q != null ? new QuoteSnapshot(q.getLatestPrice(), q.getChangePercent(), q.getSyncedAt()) : null;
            }
            case TYPE_INDEX -> {
                IndexQuoteSnapshotPO q = indexQuoteMap.get(config.getStockCode());
                yield q != null ? new QuoteSnapshot(q.getLatestPrice(), q.getChangePercent(), q.getSyncedAt()) : null;
            }
            case TYPE_BOND -> {
                BondQuoteSnapshotPO q = bondQuoteMap.get(config.getStockCode());
                yield q != null ? new QuoteSnapshot(q.getLatestPrice(), q.getChangePercent(), q.getSyncedAt()) : null;
            }
            default -> null;
        };
    }

    private StockAlertConfigPO resolveSaveConfig(
            LoginUser loginUser,
            Long currentUserId,
            String targetType,
            String targetCode,
            String targetName,
            StockAlertConfigSaveParam param) {
        StockAlertConfigPO existing = param.getId() == null
                ? this.stockAlertConfigManage.getByUserIdAndTarget(currentUserId, targetType, targetCode)
                : this.resolveEditableConfig(loginUser, currentUserId, param.getId());
        if (param.getId() != null && existing == null) {
            throw new IllegalArgumentException("Alert config not found.");
        }
        Long targetUserId = existing == null ? currentUserId : existing.getUserId();
        StockAlertConfigPO config = StockAlertConfigPO.fromSaveParam(targetUserId, targetCode, targetName, param);
        StockAlertConfigPO sameConfig =
                this.stockAlertConfigManage.getByUserIdAndTarget(targetUserId, targetType, targetCode);
        if (sameConfig != null && (existing == null || !sameConfig.getId().equals(existing.getId()))) {
            throw new IllegalArgumentException("Alert config already exists for this target.");
        }
        if (existing != null) {
            config.setId(existing.getId());
            boolean thresholdChanged = existing.getThresholdPercent() == null
                    || existing.getThresholdPercent().compareTo(config.getThresholdPercent()) != 0;
            config.setAlertActive(!thresholdChanged && Boolean.TRUE.equals(existing.getAlertActive()));
            config.setLastAlertChangePercent(existing.getLastAlertChangePercent());
            config.setLastAlertedAt(existing.getLastAlertedAt());
        }
        return config;
    }

    private StockAlertConfigPO resolveEditableConfig(LoginUser loginUser, Long currentUserId, Long id) {
        StockAlertConfigPO config = this.stockAlertConfigManage.getById(id);
        if (config == null) {
            return null;
        }
        this.validateEditable(loginUser, currentUserId, config);
        return config;
    }

    private void validateEditable(LoginUser loginUser, Long currentUserId, StockAlertConfigPO config) {
        if (this.isAdmin(loginUser) || Objects.equals(config.getUserId(), currentUserId)) {
            return;
        }
        throw new IllegalArgumentException("No permission to edit this alert config.");
    }

    private List<StockAlertConfigVO> toVOList(List<StockAlertConfigPO> configs) {
        if (CollUtil.isEmpty(configs)) {
            return List.of();
        }
        Map<Long, AppUserPO> userMap = this.loadUsers(configs);
        Map<String, StockQuoteSnapshotPO> stockQuoteMap = this.loadStockQuotes(configs);
        Map<String, IndexQuoteSnapshotPO> indexQuoteMap = this.loadIndexQuotes(configs);
        Map<String, BondQuoteSnapshotPO> bondQuoteMap = this.loadBondQuotes(configs);
        return StockAlertConverter.toVOList(configs, userMap, stockQuoteMap, indexQuoteMap, bondQuoteMap);
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

    private Map<String, StockQuoteSnapshotPO> loadStockQuotes(List<StockAlertConfigPO> configs) {
        List<String> codes = configs.stream()
                .filter(c -> TYPE_STOCK.equals(c.getTargetType()))
                .map(StockAlertConfigPO::getStockCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        return this.stockQuoteSnapshotManage.listByStockCodes(codes).stream()
                .collect(Collectors.toMap(StockQuoteSnapshotPO::getStockCode, Function.identity()));
    }

    private Map<String, IndexQuoteSnapshotPO> loadIndexQuotes(List<StockAlertConfigPO> configs) {
        List<String> codes = configs.stream()
                .filter(c -> TYPE_INDEX.equals(c.getTargetType()))
                .map(StockAlertConfigPO::getStockCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        return this.indexQuoteSnapshotManage.listByIndexCodes(codes).stream()
                .collect(Collectors.toMap(IndexQuoteSnapshotPO::getIndexCode, Function.identity()));
    }

    private Map<String, BondQuoteSnapshotPO> loadBondQuotes(List<StockAlertConfigPO> configs) {
        List<String> codes = configs.stream()
                .filter(c -> TYPE_BOND.equals(c.getTargetType()))
                .map(StockAlertConfigPO::getStockCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        return this.bondQuoteSnapshotManage.listByBondCodes(codes).stream()
                .collect(Collectors.toMap(BondQuoteSnapshotPO::getBondCode, Function.identity()));
    }

    private void checkOneAlert(StockAlertConfigPO config, AppUserPO user, QuoteSnapshot snapshot) {
        if (snapshot == null || snapshot.changePercent() == null || user == null) {
            return;
        }
        boolean outOfThreshold = this.isOutOfThreshold(snapshot.changePercent(), config.getThresholdPercent());
        if (!outOfThreshold) {
            this.resetAlertActiveIfNeeded(config);
            return;
        }
        if (Boolean.TRUE.equals(config.getAlertActive()) || !this.canSendEmail(user)) {
            return;
        }
        try {
            this.stockAlertMailService.sendAlert(
                    user, config,
                    snapshot.latestPrice(),
                    snapshot.changePercent(),
                    snapshot.syncedAt() != null ? snapshot.syncedAt().toString() : null);
            config.setAlertActive(true);
            config.setLastAlertChangePercent(snapshot.changePercent());
            config.setLastAlertedAt(LocalDateTime.now());
            this.stockAlertConfigManage.updateById(config);
        } catch (Exception ex) {
            log.warn(
                    "Failed to send alert email, userId: {}, targetCode: {}",
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
        if (param == null || StrUtil.isBlank(param.getTargetType())) {
            throw new IllegalArgumentException("targetType must not be blank.");
        }
        String type = param.getTargetType().trim().toUpperCase();
        if (!List.of(TYPE_STOCK, TYPE_INDEX, TYPE_BOND).contains(type)) {
            throw new IllegalArgumentException("targetType must be one of STOCK, INDEX, BOND.");
        }
        if (StrUtil.isBlank(param.getStockCode())) {
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
