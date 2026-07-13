package com.scrapider.finance.service;

/**
 * 标的基础数据的既有查询上限和刷新时效。
 */
public final class AssetDataEnsurePolicy {

    public static final int STOCK_VALUATION_LIMIT = 250;
    public static final int STOCK_FINANCIAL_LIMIT = 10;
    public static final int STOCK_DIVIDEND_LIMIT = 10;
    public static final int STOCK_FUNDAMENTAL_FRESH_DAYS = 7;
    public static final int STOCK_DIVIDEND_FRESH_DAYS = 30;
    public static final int CONVERTIBLE_BOND_FRESH_DAYS = 7;

    private AssetDataEnsurePolicy() {
    }
}
