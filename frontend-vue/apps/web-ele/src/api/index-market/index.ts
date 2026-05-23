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

export interface IndexDailyKline {
  indexCode: string;
  indexName: string;
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
  turnoverRate?: number | string;
  syncedAt?: string;
}

export interface IndexQuoteListParams {
  limit?: number;
  marketCode?: string;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface IndexDailyKlineParams {
  endDate?: string;
  indexCode?: string;
  limit?: number;
  secid?: string;
  startDate?: string;
}

export function listIndexQuotes(params: IndexQuoteListParams) {
  return requestClient.get<IndexQuote[]>('/indices/quotes', {
    params,
    responseReturn: 'body',
  });
}

export function listIndexDailyKlines(params: IndexDailyKlineParams) {
  return requestClient.get<IndexDailyKline[]>('/indices/daily-klines', {
    params,
    responseReturn: 'body',
  });
}
