import { requestClient } from '#/api/request';

export interface StockAlertConfig {
  id: string;
  userId?: string;
  username?: string;
  realName?: string;
  email?: string;
  emailNotification?: boolean;
  targetType: string;
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

export interface AlertTargetOption {
  targetType: string;
  targetCode: string;
  targetName: string;
  marketCode: string;
  exchangeCode: string;
}

export interface SaveStockAlertParams {
  enabled?: boolean;
  id?: string;
  targetType: string;
  stockCode: string;
  thresholdPercent: number;
}

export function listStockAlerts(targetType?: string) {
  return requestClient.get<StockAlertConfig[]>('/stock-alerts', {
    params: targetType ? { targetType } : undefined,
  });
}

export function listAlertTargetOptions(targetType: string) {
  return requestClient.get<AlertTargetOption[]>(
    '/stock-alerts/target-options',
    { params: { targetType } },
  );
}

export function saveStockAlert(data: SaveStockAlertParams) {
  return requestClient.post<StockAlertConfig>('/stock-alerts', data);
}

export function deleteStockAlert(id: string) {
  return requestClient.delete<void>(`/stock-alerts/${id}`);
}

export function checkStockAlerts() {
  return requestClient.post<void>('/stock-alerts/check');
}
