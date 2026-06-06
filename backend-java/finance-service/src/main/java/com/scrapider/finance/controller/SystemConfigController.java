package com.scrapider.finance.controller;

import com.scrapider.finance.domain.param.BondConfigAddParam;
import com.scrapider.finance.domain.param.StockConfigAddParam;
import com.scrapider.finance.domain.param.TargetDeleteParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.BondConfigAddResultVO;
import com.scrapider.finance.domain.vo.StockConfigAddResultVO;
import com.scrapider.finance.domain.vo.TargetDeleteResultVO;
import com.scrapider.finance.service.SystemConfigBondService;
import com.scrapider.finance.service.SystemConfigStockService;
import com.scrapider.finance.service.SystemConfigTargetDeleteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {

    private final SystemConfigStockService systemConfigStockService;
    private final SystemConfigBondService systemConfigBondService;
    private final SystemConfigTargetDeleteService systemConfigTargetDeleteService;

    public SystemConfigController(
            SystemConfigStockService systemConfigStockService,
            SystemConfigBondService systemConfigBondService,
            SystemConfigTargetDeleteService systemConfigTargetDeleteService) {
        this.systemConfigStockService = systemConfigStockService;
        this.systemConfigBondService = systemConfigBondService;
        this.systemConfigTargetDeleteService = systemConfigTargetDeleteService;
    }

    @PostMapping("/stocks")
    public ApiResponseVO<StockConfigAddResultVO> addStock(@RequestBody StockConfigAddParam param) {
        return ApiResponseVO.success(this.systemConfigStockService.addStock(param));
    }

    @PostMapping("/bonds")
    public ApiResponseVO<BondConfigAddResultVO> addBond(@RequestBody BondConfigAddParam param) {
        return ApiResponseVO.success(this.systemConfigBondService.addBond(param));
    }

    @PostMapping("/targets/delete")
    public ApiResponseVO<TargetDeleteResultVO> deleteTarget(@RequestBody TargetDeleteParam param) {
        return ApiResponseVO.success(this.systemConfigTargetDeleteService.deleteTarget(param));
    }
}
