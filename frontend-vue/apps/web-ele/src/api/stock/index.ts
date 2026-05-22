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
