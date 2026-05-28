package com.scrapider.finance.controller;

import com.scrapider.finance.domain.param.WatchGroupItemSaveParam;
import com.scrapider.finance.domain.param.WatchGroupSaveParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.WatchGroupItemVO;
import com.scrapider.finance.domain.vo.WatchGroupVO;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.service.WatchPoolService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watch-pool")
public class WatchPoolController {

    private final WatchPoolService watchPoolService;

    public WatchPoolController(WatchPoolService watchPoolService) {
        this.watchPoolService = watchPoolService;
    }

    @GetMapping("/groups")
    public ApiResponseVO<List<WatchGroupVO>> listGroups(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponseVO.success(this.watchPoolService.listGroups(loginUser));
    }

    @PostMapping("/groups")
    public ApiResponseVO<WatchGroupVO> saveGroup(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody WatchGroupSaveParam param) {
        return ApiResponseVO.success(this.watchPoolService.saveGroup(loginUser, param));
    }

    @DeleteMapping("/groups/{id}")
    public ApiResponseVO<Void> deleteGroup(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long id) {
        this.watchPoolService.deleteGroup(loginUser, id);
        return ApiResponseVO.success(null);
    }

    @PostMapping("/items")
    public ApiResponseVO<WatchGroupItemVO> saveItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody WatchGroupItemSaveParam param) {
        return ApiResponseVO.success(this.watchPoolService.saveItem(loginUser, param));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponseVO<Void> deleteItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long id) {
        this.watchPoolService.deleteItem(loginUser, id);
        return ApiResponseVO.success(null);
    }
}
