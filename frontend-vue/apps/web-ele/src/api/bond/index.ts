import { requestClient } from '#/api/request';
import type { StockQuoteDetail } from '#/api/stock';

export interface BondQuote {
  bondCode: string;
  bondName: string;
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
  averagePrice?: number | string;
  currentVolume?: number;
  turnoverAmount?: number | string;
  amplitude?: number | string;
  turnoverRate?: number | string;
  bondRating?: string;
  conversionPremiumRate?: number | string;
  quoteDetails?: StockQuoteDetail[];
  syncedAt?: string;
}

export interface BondIntradayTrend {
  bondCode: string;
  bondName: string;
  secid: string;
  trendMinute?: string;
  trendTime: string;
  closePrice?: number | string;
  averagePrice?: number | string;
  volume?: number;
  turnoverAmount?: number | string;
  previousClosePrice?: number | string;
  syncedAt?: string;
}

export interface BondKline {
  bondCode: string;
  bondName: string;
  secid: string;
  marketCode: string;
  exchangeCode: string;
  periodType?: string;
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

export interface BondQuoteListParams {
  limit?: number;
  marketCode?: string;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface BondKlineParams {
  endDate?: string;
  bondCode?: string;
  limit?: number;
  periodType?: 'daily' | 'monthly' | 'weekly';
  secid?: string;
  startDate?: string;
}

export interface MarketSyncStatus {
  running: boolean;
  started: boolean;
}

export interface BondConfigAddParams {
  bondCode: string;
  bondName: string;
}

export interface BondConfigAddResult {
  bondCode: string;
  bondName: string;
  secid: string;
  marketCode: string;
  exchangeCode: string;
  underlyingStockCode?: string;
  underlyingStockName?: string;
  basicSynced: boolean;
  underlyingStockSynced: boolean;
  marketDataSynced: boolean;
  dailyValuationSynced: boolean;
  shareSynced: boolean;
  initializationScheduled: boolean;
}

export function listBondQuotes(params: BondQuoteListParams) {
  return requestClient.get<BondQuote[]>('/bonds/quotes', {
    params,
    responseReturn: 'body',
  });
}

export function listBondIntradayTrends(bondCode: string) {
  return requestClient.get<BondIntradayTrend[]>('/bonds/intraday-trends', {
    params: { bondCode },
    responseReturn: 'body',
  });
}

export function listBondKlines(params: BondKlineParams) {
  return requestClient.get<BondKline[]>('/bonds/klines', {
    params,
    responseReturn: 'body',
  });
}

export function syncBondMarketData() {
  return requestClient.post<MarketSyncStatus>('/bonds/sync', undefined, {
    timeout: 60_000,
  });
}

export function syncBondKlineData(
  bondCode: string,
  periodType: BondKlineParams['periodType'] = 'daily',
  limit = 250,
) {
  return requestClient.post<MarketSyncStatus>(
    `/bonds/sync/klines/${bondCode}`,
    undefined,
    {
      params: { limit, periodType },
      timeout: 60_000,
    },
  );
}

export function getBondMarketSyncStatus() {
  return requestClient.get<MarketSyncStatus>('/bonds/sync/status', {
    timeout: 60_000,
  });
}

export function addBondConfig(data: BondConfigAddParams) {
  return requestClient.post<BondConfigAddResult>('/system-config/bonds', data, {
    timeout: 120_000,
  });
}
