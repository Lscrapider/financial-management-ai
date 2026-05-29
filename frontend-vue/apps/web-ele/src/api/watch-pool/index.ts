import { requestClient } from '#/api/request';
import type { StockQuoteDetail } from '#/api/stock';

export type WatchTargetType = 'BOND' | 'FUND' | 'INDEX' | 'SECTOR' | 'STOCK';

export interface WatchItem {
  buyPrice?: number | string;
  changePercent?: number | string;
  id: string;
  latestPrice?: number | string;
  averagePrice?: number | string;
  position?: number | string;
  remark: null | string;
  secid: null | string;
  syncedAt?: string;
  targetCode: string;
  targetName: string;
  targetType: WatchTargetType;
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
  quoteDetails?: StockQuoteDetail[];
}

export interface WatchGroup {
  id: string;
  items: WatchItem[];
  name: string;
}

interface WatchGroupResponse {
  groupName: string;
  id: string;
  items: WatchItem[];
}

export interface WatchGroupSaveParams {
  groupName: string;
  id?: string;
}

export interface WatchItemSaveParams {
  buyPrice?: number;
  groupId: string;
  id?: string;
  position?: number;
  remark?: string;
  secid?: string;
  targetCode: string;
  targetName: string;
  targetType: WatchTargetType;
}

export async function listWatchGroups() {
  const groups =
    await requestClient.get<WatchGroupResponse[]>('/watch-pool/groups');
  return groups.map(normalizeGroup);
}

export async function saveWatchGroup(params: WatchGroupSaveParams) {
  const group = await requestClient.post<WatchGroupResponse>(
    '/watch-pool/groups',
    params,
  );
  return normalizeGroup(group);
}

export function deleteWatchGroup(id: string) {
  return requestClient.delete<void>(`/watch-pool/groups/${id}`);
}

export function saveWatchItem(params: WatchItemSaveParams) {
  return requestClient.post<WatchItem>('/watch-pool/items', params);
}

export function deleteWatchItem(id: string) {
  return requestClient.delete<void>(`/watch-pool/items/${id}`);
}

function normalizeGroup(group: WatchGroupResponse): WatchGroup {
  return {
    id: group.id,
    items: group.items ?? [],
    name: group.groupName,
  };
}
