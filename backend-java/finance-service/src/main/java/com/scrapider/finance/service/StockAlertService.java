package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.StockAlertConfigSaveParam;
import com.scrapider.finance.domain.vo.StockAlertConfigVO;
import com.scrapider.finance.domain.vo.StockAlertStockOptionVO;
import com.scrapider.finance.security.LoginUser;
import java.util.List;

public interface StockAlertService {

    List<StockAlertConfigVO> listAlerts(LoginUser loginUser, String targetType);

    List<StockAlertStockOptionVO> listTargetOptions(String targetType);

    StockAlertConfigVO saveAlert(LoginUser loginUser, StockAlertConfigSaveParam param);

    void deleteAlert(LoginUser loginUser, Long id);

    void checkAlerts();
}
