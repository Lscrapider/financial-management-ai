import type { WorkbenchTargetType } from './types';

import type { BondIntradayTrend, BondKline, BondQuote } from '#/api/bond';
import type {
  IndexIntradayTrend,
  IndexKline,
  IndexQuote,
} from '#/api/index-market';
import type { StockIntradayTrend, StockKline, StockQuote } from '#/api/stock';

import {
  listBondIntradayTrends,
  listBondKlines,
  listBondQuotes,
} from '#/api/bond';
import {
  listIndexIntradayTrends,
  listIndexKlines,
  listIndexQuotes,
} from '#/api/index-market';
import {
  listStockIntradayTrends,
  listStockKlines,
  listStockQuotes,
} from '#/api/stock';

export type WorkbenchTrendPeriod = 'daily' | 'intraday' | 'monthly' | 'weekly';

export type WorkbenchKlineAdjustType = 'hfq' | 'none' | 'qfq';

export interface MarketDetailRow {
  label: string;
  value?: number | string;
}

export interface MarketKlinePoint {
  closePrice?: number | string;
  highPrice?: number | string;
  lowPrice?: number | string;
  ma10?: number | string;
  ma20?: number | string;
  ma5?: number | string;
  openPrice?: number | string;
  tradeDate: string;
  volume?: number;
}

export interface MarketQuote {
  amplitude?: number | string;
  averagePrice?: number | string;
  changePercent?: number | string;
  code: string;
  currentVolume?: number;
  detailRows: MarketDetailRow[];
  exchangeCode?: string;
  highPrice?: number | string;
  latestPrice?: number | string;
  lowPrice?: number | string;
  name: string;
  openPrice?: number | string;
  previousClosePrice?: number | string;
  quoteFields: MarketDetailRow[];
  secid?: string;
  targetType: WorkbenchTargetType;
  turnoverAmount?: number | string;
  turnoverRate?: number | string;
  volume?: number;
}

export interface MarketTrendPoint {
  averagePrice?: number | string;
  closePrice?: number | string;
  trendMinute?: string;
  trendTime: string;
}

export async function findMarketQuote(
  targetType: WorkbenchTargetType,
  targetCode: string,
) {
  const quotes = await listMarketQuotes(targetType);
  return quotes.find((item) => item.code === targetCode);
}

export async function listMarketQuotes(targetType: WorkbenchTargetType) {
  if (targetType === 'INDEX') {
    const quotes = await listIndexQuotes({
      limit: 100,
      marketCode: 'INDEX',
      sortField: 'indexCode',
      sortOrder: 'asc',
    });
    return quotes.map((item) => indexQuoteToMarketQuote(item));
  }
  if (targetType === 'CONVERTIBLE_BOND') {
    const quotes = await listBondQuotes({
      limit: 500,
      sortField: 'changePercent',
      sortOrder: 'desc',
    });
    return quotes.map((item) => bondQuoteToMarketQuote(item));
  }
  const quotes = await listStockQuotes({
    limit: 500,
    sortField: 'changePercent',
    sortOrder: 'desc',
  });
  return quotes.map((item) => stockQuoteToMarketQuote(item));
}

export async function listMarketKlines(
  targetType: WorkbenchTargetType,
  targetCode: string,
  periodType: Exclude<WorkbenchTrendPeriod, 'intraday'>,
  secid?: string,
  adjustType: WorkbenchKlineAdjustType = 'hfq',
) {
  if (targetType === 'INDEX') {
    const klines = await listIndexKlines({
      indexCode: targetCode,
      limit: 250,
      periodType,
      secid,
    });
    return klines.map((item) => indexKlineToMarketKline(item));
  }
  if (targetType === 'CONVERTIBLE_BOND') {
    const klines = await listBondKlines({
      bondCode: targetCode,
      limit: 250,
      periodType,
      secid,
    });
    return klines.map((item) => bondKlineToMarketKline(item));
  }
  const klines = await listStockKlines({
    adjustType,
    limit: 250,
    periodType,
    stockCode: targetCode,
  });
  return klines.map((item) => stockKlineToMarketKline(item));
}

export async function listMarketTrends(
  targetType: WorkbenchTargetType,
  targetCode: string,
) {
  if (targetType === 'CONVERTIBLE_BOND') {
    const trends = await listBondIntradayTrends(targetCode);
    return trends.map((item) => bondTrendToMarketTrend(item));
  }
  if (targetType === 'INDEX') {
    const trends = await listIndexIntradayTrends(targetCode);
    return trends.map((item) => indexTrendToMarketTrend(item));
  }
  if (targetType === 'STOCK') {
    const trends = await listStockIntradayTrends(targetCode);
    return trends.map((item) => stockTrendToMarketTrend(item));
  }
  return [];
}

function baseQuoteFields(item: {
  amplitude?: number | string;
  averagePrice?: number | string;
  changePercent?: number | string;
  highPrice?: number | string;
  latestPrice?: number | string;
  lowPrice?: number | string;
  openPrice?: number | string;
  previousClosePrice?: number | string;
  turnoverAmount?: number | string;
  turnoverRate?: number | string;
  volume?: number;
}) {
  return [
    { label: '最新价', value: item.latestPrice },
    { label: '涨跌幅', value: item.changePercent },
    { label: '今开', value: item.openPrice },
    { label: '昨收', value: item.previousClosePrice },
    { label: '最高', value: item.highPrice },
    { label: '最低', value: item.lowPrice },
    { label: '均价', value: item.averagePrice },
    { label: '振幅', value: item.amplitude },
    { label: '换手率', value: item.turnoverRate },
    { label: '成交量', value: item.volume },
    { label: '成交额', value: item.turnoverAmount },
  ];
}

function bondKlineToMarketKline(item: BondKline): MarketKlinePoint {
  return {
    closePrice: item.closePrice,
    highPrice: item.highPrice,
    lowPrice: item.lowPrice,
    ma10: item.ma10,
    ma20: item.ma20,
    ma5: item.ma5,
    openPrice: item.openPrice,
    tradeDate: item.tradeDate,
    volume: item.volume,
  };
}

function bondQuoteToMarketQuote(item: BondQuote): MarketQuote {
  const quoteFields = [
    ...baseQuoteFields(item),
    { label: '评级', value: item.bondRating },
    { label: '现手', value: item.currentVolume },
  ];
  return {
    ...item,
    code: item.bondCode,
    detailRows:
      item.quoteDetails?.map((detail) => ({
        label: detail.fieldName,
        value: detail.fieldValue,
      })) ?? quoteFields,
    name: item.bondName,
    quoteFields,
    targetType: 'CONVERTIBLE_BOND',
  };
}

function bondTrendToMarketTrend(item: BondIntradayTrend): MarketTrendPoint {
  return {
    averagePrice: item.averagePrice,
    closePrice: item.closePrice,
    trendMinute: item.trendMinute,
    trendTime: item.trendTime,
  };
}

function indexKlineToMarketKline(item: IndexKline): MarketKlinePoint {
  return {
    closePrice: item.closePrice,
    highPrice: item.highPrice,
    lowPrice: item.lowPrice,
    ma10: item.ma10,
    ma20: item.ma20,
    ma5: item.ma5,
    openPrice: item.openPrice,
    tradeDate: item.tradeDate,
    volume: item.volume,
  };
}

function indexQuoteToMarketQuote(item: IndexQuote): MarketQuote {
  const quoteFields = baseQuoteFields(item);
  return {
    ...item,
    code: item.indexCode,
    detailRows: quoteFields,
    name: item.indexName,
    quoteFields,
    targetType: 'INDEX',
  };
}

function indexTrendToMarketTrend(item: IndexIntradayTrend): MarketTrendPoint {
  return {
    averagePrice: item.averagePrice,
    closePrice: item.closePrice,
    trendMinute: item.trendMinute,
    trendTime: item.trendTime,
  };
}

function stockKlineToMarketKline(item: StockKline): MarketKlinePoint {
  return {
    closePrice: item.closePrice,
    highPrice: item.highPrice,
    lowPrice: item.lowPrice,
    ma10: item.ma10,
    ma20: item.ma20,
    ma5: item.ma5,
    openPrice: item.openPrice,
    tradeDate: item.tradeDate,
    volume: item.volume,
  };
}

function stockQuoteToMarketQuote(item: StockQuote): MarketQuote {
  const quoteFields = [
    ...baseQuoteFields(item),
    { label: '现手', value: item.currentVolume },
    { label: '量比', value: item.volumeRatio },
    { label: '涨停价', value: item.limitUpPrice },
    { label: '跌停价', value: item.limitDownPrice },
    { label: '总市值', value: item.totalMarketValue },
    { label: '流通市值', value: item.floatMarketValue },
    { label: 'TTM市盈率', value: item.peTtm },
    { label: '动态市盈率', value: item.peDynamic },
    { label: '静态市盈率', value: item.peStatic },
    { label: '市净率', value: item.pbRatio },
  ];
  return {
    ...item,
    code: item.stockCode,
    detailRows:
      item.quoteDetails?.map((detail) => ({
        label: detail.fieldName,
        value: detail.fieldValue,
      })) ?? quoteFields,
    name: item.stockName,
    quoteFields,
    targetType: 'STOCK',
  };
}

function stockTrendToMarketTrend(item: StockIntradayTrend): MarketTrendPoint {
  return {
    averagePrice: item.averagePrice,
    closePrice: item.closePrice,
    trendMinute: item.trendMinute,
    trendTime: item.trendTime,
  };
}
