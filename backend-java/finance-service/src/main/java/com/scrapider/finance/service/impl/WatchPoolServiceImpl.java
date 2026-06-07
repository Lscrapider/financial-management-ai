package com.scrapider.finance.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.converter.WatchPoolConverter;
import com.scrapider.finance.domain.enums.WatchTargetTypeEnum;
import java.math.BigDecimal;
import com.scrapider.finance.domain.param.WatchGroupItemSaveParam;
import com.scrapider.finance.domain.param.WatchGroupSaveParam;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.WatchGroupItemPO;
import com.scrapider.finance.domain.po.WatchGroupPO;
import com.scrapider.finance.domain.vo.WatchGroupItemVO;
import com.scrapider.finance.domain.vo.WatchGroupVO;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.manage.WatchGroupItemManage;
import com.scrapider.finance.manage.WatchGroupManage;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.service.WatchPoolService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchPoolServiceImpl implements WatchPoolService {

    private static final String DEFAULT_GROUP_NAME = "默认观察";

    private final WatchGroupManage watchGroupManage;
    private final WatchGroupItemManage watchGroupItemManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;

    public WatchPoolServiceImpl(
            WatchGroupManage watchGroupManage,
            WatchGroupItemManage watchGroupItemManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage) {
        this.watchGroupManage = watchGroupManage;
        this.watchGroupItemManage = watchGroupItemManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
    }

    @Override
    @Transactional
    public List<WatchGroupVO> listGroups(LoginUser loginUser) {
        Long userId = this.currentUserId(loginUser);
        List<WatchGroupPO> groups = this.watchGroupManage.listByUserId(userId);
        if (CollUtil.isEmpty(groups)) {
            WatchGroupPO group = WatchGroupPO.create(userId, DEFAULT_GROUP_NAME, 0);
            this.watchGroupManage.save(group);
            groups = List.of(group);
        }
        return this.toGroupVOList(groups);
    }

    @Override
    @Transactional
    public WatchGroupVO saveGroup(LoginUser loginUser, WatchGroupSaveParam param) {
        Long userId = this.currentUserId(loginUser);
        String groupName = this.normalizeGroupName(param);
        WatchGroupPO sameNameGroup = this.watchGroupManage.findByUserIdAndName(userId, groupName);
        if (param.getId() == null) {
            if (sameNameGroup != null) {
                throw new IllegalArgumentException("分组名称已存在");
            }
            WatchGroupPO group = WatchGroupPO.create(userId, groupName, this.watchGroupManage.nextSortOrder(userId));
            this.watchGroupManage.save(group);
            return this.toGroupVOList(List.of(group)).get(0);
        }

        WatchGroupPO existing = this.requireGroup(param.getId(), userId);
        if (sameNameGroup != null && !Objects.equals(sameNameGroup.getId(), existing.getId())) {
            throw new IllegalArgumentException("分组名称已存在");
        }
        this.watchGroupManage.rename(existing.getId(), userId, groupName);
        WatchGroupPO updated = this.watchGroupManage.findByIdAndUserId(existing.getId(), userId);
        return this.toGroupVOList(List.of(updated)).get(0);
    }

    @Override
    @Transactional
    public void deleteGroup(LoginUser loginUser, Long id) {
        Long userId = this.currentUserId(loginUser);
        WatchGroupPO group = this.requireGroup(id, userId);
        this.watchGroupItemManage.lambdaUpdate()
                .eq(WatchGroupItemPO::getGroupId, group.getId())
                .eq(WatchGroupItemPO::getUserId, userId)
                .remove();
        this.watchGroupManage.removeById(group.getId());
    }

    @Override
    @Transactional
    public WatchGroupItemVO saveItem(LoginUser loginUser, WatchGroupItemSaveParam param) {
        Long userId = this.currentUserId(loginUser);
        if (param == null) {
            throw new IllegalArgumentException("标的参数不能为空");
        }
        WatchGroupPO group = this.requireGroup(param.getGroupId(), userId);
        WatchTargetTypeEnum targetType = WatchTargetTypeEnum.of(param.getTargetType());
        String targetCode = this.requiredTrim(param.getTargetCode(), "targetCode must not be blank.");
        String targetName = this.requiredTrim(param.getTargetName(), "targetName must not be blank.");
        String secid = StrUtil.blankToDefault(StrUtil.trim(param.getSecid()), null);
        String remark = StrUtil.blankToDefault(StrUtil.trim(param.getRemark()), null);
        BigDecimal buyPrice = param.getBuyPrice();
        BigDecimal position = param.getPosition();

        WatchGroupItemPO duplicate = this.watchGroupItemManage.findDuplicate(group.getId(), targetType.name(), targetCode);
        WatchGroupItemPO item;
        if (param.getId() == null) {
            if (duplicate != null) {
                throw new IllegalArgumentException("当前分组已存在该标的");
            }
            item = WatchGroupItemPO.create(
                    group.getId(),
                    userId,
                    targetType.name(),
                    targetCode,
                    targetName,
                    secid,
                    remark,
                    buyPrice,
                    position,
                    this.watchGroupItemManage.nextSortOrder(group.getId()));
            this.watchGroupItemManage.save(item);
        } else {
            item = this.requireItem(param.getId(), userId);
            if (!Objects.equals(item.getGroupId(), group.getId())) {
                throw new IllegalArgumentException("标的不属于当前分组");
            }
            if (duplicate != null && !Objects.equals(duplicate.getId(), item.getId())) {
                throw new IllegalArgumentException("当前分组已存在该标的");
            }
            item.setTargetType(targetType.name());
            item.setTargetCode(targetCode);
            item.setTargetName(targetName);
            item.setSecid(secid);
            item.setRemark(remark);
            item.setBuyPrice(buyPrice);
            item.setPosition(position);
            this.watchGroupItemManage.updateItem(item);
        }

        WatchGroupItemPO saved = this.watchGroupItemManage.getById(item.getId());
        return this.toItemVOList(List.of(saved)).get(0);
    }

    @Override
    @Transactional
    public void deleteItem(LoginUser loginUser, Long id) {
        Long userId = this.currentUserId(loginUser);
        WatchGroupItemPO item = this.requireItem(id, userId);
        this.watchGroupItemManage.removeById(item.getId());
    }

    private List<WatchGroupVO> toGroupVOList(List<WatchGroupPO> groups) {
        List<Long> groupIds = groups.stream().map(WatchGroupPO::getId).toList();
        List<WatchGroupItemPO> allItems = this.watchGroupItemManage.listByGroupIds(groupIds);
        return WatchPoolConverter.toGroupVOList(
                groups,
                allItems,
                this.stockQuoteMap(allItems),
                this.indexQuoteMap(allItems),
                this.bondQuoteMap(allItems));
    }

    private List<WatchGroupItemVO> toItemVOList(List<WatchGroupItemPO> items) {
        if (CollUtil.isEmpty(items)) {
            return List.of();
        }
        return WatchPoolConverter.toItemVOList(
                items,
                this.stockQuoteMap(items),
                this.indexQuoteMap(items),
                this.bondQuoteMap(items));
    }

    private Map<String, StockQuoteSnapshotPO> stockQuoteMap(List<WatchGroupItemPO> items) {
        List<String> stockCodes = items.stream()
                .filter(item -> WatchTargetTypeEnum.STOCK.name().equals(item.getTargetType()))
                .map(WatchGroupItemPO::getTargetCode)
                .distinct()
                .toList();
        return this.stockQuoteSnapshotManage.listByStockCodes(stockCodes).stream()
                .collect(Collectors.toMap(StockQuoteSnapshotPO::getStockCode, Function.identity()));
    }

    private Map<String, IndexQuoteSnapshotPO> indexQuoteMap(List<WatchGroupItemPO> items) {
        List<String> indexCodes = items.stream()
                .filter(item -> WatchTargetTypeEnum.INDEX.name().equals(item.getTargetType()))
                .map(WatchGroupItemPO::getTargetCode)
                .distinct()
                .toList();
        return this.indexQuoteSnapshotManage.listByIndexCodes(indexCodes).stream()
                .collect(Collectors.toMap(IndexQuoteSnapshotPO::getIndexCode, Function.identity()));
    }

    private Map<String, BondQuoteSnapshotPO> bondQuoteMap(List<WatchGroupItemPO> items) {
        List<String> bondCodes = items.stream()
                .filter(item -> WatchTargetTypeEnum.BOND.name().equals(item.getTargetType()))
                .map(WatchGroupItemPO::getTargetCode)
                .distinct()
                .toList();
        if (bondCodes.isEmpty()) {
            return Map.of();
        }
        return this.bondQuoteSnapshotManage.listByBondCodes(bondCodes).stream()
                .collect(Collectors.toMap(BondQuoteSnapshotPO::getBondCode, Function.identity()));
    }

    private WatchGroupPO requireGroup(Long groupId, Long userId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId must not be null.");
        }
        WatchGroupPO group = this.watchGroupManage.findByIdAndUserId(groupId, userId);
        if (group == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        return group;
    }

    private WatchGroupItemPO requireItem(Long itemId, Long userId) {
        if (itemId == null) {
            throw new IllegalArgumentException("id must not be null.");
        }
        WatchGroupItemPO item = this.watchGroupItemManage.findByIdAndUserId(itemId, userId);
        if (item == null) {
            throw new IllegalArgumentException("标的不存在");
        }
        return item;
    }

    private String normalizeGroupName(WatchGroupSaveParam param) {
        if (param == null) {
            throw new IllegalArgumentException("分组参数不能为空");
        }
        String groupName = this.requiredTrim(param.getGroupName(), "groupName must not be blank.");
        if (groupName.length() > 64) {
            throw new IllegalArgumentException("分组名称不能超过 64 个字符");
        }
        return groupName;
    }

    private String requiredTrim(String value, String message) {
        String trimmed = StrUtil.trim(value);
        if (StrUtil.isBlank(trimmed)) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private Long currentUserId(LoginUser loginUser) {
        if (loginUser == null || loginUser.getUser() == null || loginUser.getUser().getId() == null) {
            throw new IllegalArgumentException("Login user is required.");
        }
        return loginUser.getUser().getId();
    }
}
