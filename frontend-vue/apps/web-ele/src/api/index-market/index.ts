import { requestClient } from '#/api/request';

export interface IndexQuote {
  indexCode: string;
  indexName: string;
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
  syncedAt?: string;
}

export interface IndexKline {
  indexCode: string;
  indexName: string;
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

export interface IndexIntradayTrend {
  averagePrice?: number | string;
  closePrice?: number | string;
  indexCode: string;
  indexName: string;
  previousClosePrice?: number | string;
  secid: string;
  syncedAt?: string;
  trendDate?: string;
  trendMinute?: string;
  trendTime: string;
  turnoverAmount?: number | string;
  volume?: number;
}

export interface IndexQuoteListParams {
  limit?: number;
  marketCode?: string;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface IndexKlineParams {
  endDate?: string;
  indexCode?: string;
  limit?: number;
  periodType?: 'daily' | 'monthly' | 'weekly';
  secid?: string;
  startDate?: string;
}

export interface MarketSyncStatus {
  running: boolean;
  started: boolean;
}

export function listIndexQuotes(params: IndexQuoteListParams) {
  return requestClient.get<IndexQuote[]>('/indices/quotes', {
    params,
    responseReturn: 'body',
  });
}

export function listIndexKlines(params: IndexKlineParams) {
  return requestClient.get<IndexKline[]>('/indices/klines', {
    params,
    responseReturn: 'body',
  });
}

export function listIndexIntradayTrends(indexCode: string) {
  return requestClient.get<IndexIntradayTrend[]>('/indices/intraday-trends', {
    params: { indexCode },
    responseReturn: 'body',
  });
}

export function syncIndexMarketData() {
  return requestClient.post<MarketSyncStatus>('/indices/sync', undefined, {
    timeout: 60_000,
  });
}

export function syncIndexKlineData(
  indexCode: string,
  periodType: IndexKlineParams['periodType'] = 'daily',
  limit = 250,
) {
  return requestClient.post<MarketSyncStatus>(
    `/indices/sync/klines/${indexCode}`,
    undefined,
    {
      params: { limit, periodType },
      timeout: 60_000,
    },
  );
}

export function getIndexMarketSyncStatus() {
  return requestClient.get<MarketSyncStatus>('/indices/sync/status', {
    timeout: 60_000,
  });
}
