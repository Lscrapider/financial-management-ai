import { requestClient } from '#/api/request';

export interface StockAlertConfig {
  id: number;
  userId?: number;
  username?: string;
  realName?: string;
  email?: string;
  emailNotification?: boolean;
  stockCode: string;
  stockName: string;
  thresholdPercent: number | string;
  enabled: boolean;
  outOfThreshold: boolean;
  latestPrice?: number | string;
  changePercent?: number | string;
  syncedAt?: string;
  lastAlertedAt?: string;
}

export interface StockAlertStockOption {
  stockCode: string;
  stockName: string;
  marketCode: string;
  exchangeCode: string;
}

export interface SaveStockAlertParams {
  enabled?: boolean;
  id?: number;
  stockCode: string;
  thresholdPercent: number;
}

export function listStockAlerts() {
  return requestClient.get<StockAlertConfig[]>('/stock-alerts');
}

export function listStockAlertStockOptions() {
  return requestClient.get<StockAlertStockOption[]>(
    '/stock-alerts/stock-options',
  );
}

export function saveStockAlert(data: SaveStockAlertParams) {
  return requestClient.post<StockAlertConfig>('/stock-alerts', data);
}

export function deleteStockAlert(id: number) {
  return requestClient.delete<void>(`/stock-alerts/${id}`);
}

export function checkStockAlerts() {
  return requestClient.post<void>('/stock-alerts/check');
}
