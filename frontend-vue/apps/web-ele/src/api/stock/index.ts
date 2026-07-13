import { requestClient } from '#/api/request';

export interface StockQuoteDetail {
  fieldIndex: number;
  fieldName: string;
  fieldValue: string;
}

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
  averagePrice?: number | string;
  changeAmount?: number | string;
  changePercent?: number | string;
  volume?: number;
  externalVolume?: number;
  internalVolume?: number;
  currentVolume?: number;
  turnoverAmount?: number | string;
  turnoverRate?: number | string;
  amplitude?: number | string;
  volumeRatio?: number | string;
  limitUpPrice?: number | string;
  limitDownPrice?: number | string;
  totalMarketValue?: number | string;
  floatMarketValue?: number | string;
  peTtm?: number | string;
  peDynamic?: number | string;
  peStatic?: number | string;
  pbRatio?: number | string;
  tradeStatus?: number;
  quoteDetails?: StockQuoteDetail[];
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

export interface StockKline {
  stockCode: string;
  stockName: string;
  secid: string;
  marketCode: string;
  exchangeCode: string;
  periodType: string;
  adjustType: string;
  tradeDate: string;
  openPrice?: number | string;
  closePrice?: number | string;
  highPrice?: number | string;
  lowPrice?: number | string;
  changeAmount?: number | string;
  changePercent?: number | string;
  volume?: number;
  turnoverAmount?: number | string;
  amplitude?: number | string;
  turnoverRate?: number | string;
  ma5?: number | string;
  ma10?: number | string;
  ma20?: number | string;
  syncedAt?: string;
}

export interface StockQuoteListParams {
  limit?: number;
  marketCode?: string;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface StockKlineParams {
  adjustType?: 'hfq' | 'none' | 'qfq';
  endDate?: string;
  limit?: number;
  periodType?: 'daily' | 'monthly' | 'weekly';
  secid?: string;
  startDate?: string;
  stockCode?: string;
}

export interface MarketSyncStatus {
  running: boolean;
  started: boolean;
}

export interface StockConfigAddParams {
  stockCode: string;
  stockName: string;
}

export interface StockConfigAddResult {
  stockCode: string;
  stockName: string;
  secid: string;
  marketCode: string;
  exchangeCode: string;
  quoteSynced: boolean;
  trendSynced: boolean;
  initializationScheduled: boolean;
  quote: StockQuote;
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

export function listStockKlines(params: StockKlineParams) {
  return requestClient.get<StockKline[]>('/stocks/klines', {
    params,
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

export function syncStockDailyKlineData(stockCode: string) {
  return requestClient.post<MarketSyncStatus>(
    `/stocks/sync/daily-klines/${stockCode}`,
    undefined,
    { timeout: 60_000 },
  );
}

export function addStockConfig(data: StockConfigAddParams) {
  return requestClient.post<StockConfigAddResult>(
    '/system-config/stocks',
    data,
    {
      timeout: 60_000,
    },
  );
}
