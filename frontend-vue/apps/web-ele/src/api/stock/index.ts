import { requestClient } from '#/api/request';

export interface StockQuote {
  stockCode: string;
  stockName: string;
  secid: string;
  marketCode: string;
  exchangeCode: string;
  latestPrice?: number | string;
  openPrice?: number | string;
  highPrice?: number | string;
  lowPrice?: number | string;
  previousClosePrice?: number | string;
  changeAmount?: number | string;
  changePercent?: number | string;
  volume?: number;
  turnoverAmount?: number | string;
  turnoverRate?: number | string;
  amplitude?: number | string;
  totalMarketValue?: number | string;
  floatMarketValue?: number | string;
  syncedAt?: string;
}

export interface StockIntradayTrend {
  stockCode: string;
  stockName: string;
  secid: string;
  syncBatchNo: string;
  trendMinute?: string;
  trendTime: string;
  closePrice?: number | string;
  averagePrice?: number | string;
  volume?: number;
  turnoverAmount?: number | string;
  previousClosePrice?: number | string;
  syncedAt?: string;
}

export interface StockQuoteListParams {
  limit?: number;
  marketCode?: string;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface MarketSyncStatus {
  running: boolean;
  started: boolean;
}

export function listStockQuotes(params: StockQuoteListParams) {
  return requestClient.get<StockQuote[]>('/stocks/quotes', {
    params,
    responseReturn: 'body',
  });
}

export function listStockIntradayTrends(stockCode: string) {
  return requestClient.get<StockIntradayTrend[]>('/stocks/intraday-trends', {
    params: { stockCode },
    responseReturn: 'body',
  });
}

export function syncStockMarketData() {
  return requestClient.post<MarketSyncStatus>('/stocks/sync', undefined, {
    timeout: 60_000,
  });
}

export function getStockMarketSyncStatus() {
  return requestClient.get<MarketSyncStatus>('/stocks/sync/status', {
    timeout: 60_000,
  });
}

export function syncStockTrendData(stockCode: string) {
  return requestClient.post<MarketSyncStatus>(
    `/stocks/sync/trends/${stockCode}`,
    undefined,
    { timeout: 60_000 },
  );
}
