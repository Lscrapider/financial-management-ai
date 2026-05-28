import { requestClient } from '#/api/request';

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
  turnoverAmount?: number | string;
  amplitude?: number | string;
  turnoverRate?: number | string;
  conversionPremiumRate?: number | string;
  bondRating?: string;
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

export interface BondDailyKline {
  bondCode: string;
  bondName: string;
  secid: string;
  marketCode: string;
  exchangeCode: string;
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
  syncedAt?: string;
}

export interface BondQuoteListParams {
  limit?: number;
  marketCode?: string;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface BondDailyKlineParams {
  endDate?: string;
  bondCode?: string;
  limit?: number;
  secid?: string;
  startDate?: string;
}

export interface MarketSyncStatus {
  running: boolean;
  started: boolean;
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

export function listBondDailyKlines(params: BondDailyKlineParams) {
  return requestClient.get<BondDailyKline[]>('/bonds/daily-klines', {
    params,
    responseReturn: 'body',
  });
}

export function syncBondMarketData() {
  return requestClient.post<MarketSyncStatus>('/bonds/sync', undefined, {
    timeout: 60_000,
  });
}

export function getBondMarketSyncStatus() {
  return requestClient.get<MarketSyncStatus>('/bonds/sync/status', {
    timeout: 60_000,
  });
}

export function syncBondTrendData(bondCode: string) {
  return requestClient.post<MarketSyncStatus>(
    `/bonds/sync/trends/${bondCode}`,
    undefined,
    { timeout: 60_000 },
  );
}
