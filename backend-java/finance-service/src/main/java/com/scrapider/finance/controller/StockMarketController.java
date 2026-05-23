package com.scrapider.finance.controller;

import com.scrapider.finance.domain.param.StockIntradayTrendParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.vo.ErrorResponseVO;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.service.StockMarketQueryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class StockMarketController {

    private final StockMarketQueryService stockMarketQueryService;

    public StockMarketController(StockMarketQueryService stockMarketQueryService) {
        this.stockMarketQueryService = stockMarketQueryService;
    }

    @GetMapping("/quotes")
    public ResponseEntity<List<StockQuoteVO>> listQuotes(@ModelAttribute StockQuoteListParam param) {
        return ResponseEntity.ok(this.stockMarketQueryService.listQuotes(param));
    }

    @GetMapping("/intraday-trends")
    public ResponseEntity<List<StockIntradayTrendVO>> listIntradayTrends(
            @ModelAttribute StockIntradayTrendParam param) {
        return ResponseEntity.ok(this.stockMarketQueryService.listIntradayTrends(param));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseVO> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseVO(ex.getMessage()));
    }
}
