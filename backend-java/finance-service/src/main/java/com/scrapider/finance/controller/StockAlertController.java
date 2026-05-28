package com.scrapider.finance.controller;

import com.scrapider.finance.domain.param.StockAlertConfigSaveParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.StockAlertConfigVO;
import com.scrapider.finance.domain.vo.StockAlertStockOptionVO;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.service.StockAlertService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-alerts")
public class StockAlertController {

    private final StockAlertService stockAlertService;

    public StockAlertController(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @GetMapping
    public ApiResponseVO<List<StockAlertConfigVO>> listAlerts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String targetType) {
        return ApiResponseVO.success(this.stockAlertService.listAlerts(loginUser, targetType));
    }

    @GetMapping("/target-options")
    public ApiResponseVO<List<StockAlertStockOptionVO>> listTargetOptions(
            @RequestParam String targetType) {
        return ApiResponseVO.success(this.stockAlertService.listTargetOptions(targetType));
    }

    @PostMapping
    public ApiResponseVO<StockAlertConfigVO> saveAlert(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody StockAlertConfigSaveParam param) {
        return ApiResponseVO.success(this.stockAlertService.saveAlert(loginUser, param));
    }

    @DeleteMapping("/{id}")
    public ApiResponseVO<Void> deleteAlert(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long id) {
        this.stockAlertService.deleteAlert(loginUser, id);
        return ApiResponseVO.success(null);
    }

    @PostMapping("/check")
    public ApiResponseVO<Void> checkAlerts(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null || !"admin".equals(loginUser.getRoleCode())) {
            throw new IllegalArgumentException("Only admin can trigger alert check.");
        }
        this.stockAlertService.checkAlerts();
        return ApiResponseVO.success(null);
    }
}
