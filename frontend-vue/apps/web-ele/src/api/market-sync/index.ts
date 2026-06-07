import { requestClient } from '#/api/request';

export interface MarketSyncJob {
  dataScope: string;
  durationMs?: number;
  errorMessage?: string;
  finishedAt?: string;
  jobNo: string;
  startedAt?: string;
  status: 'failed' | 'running' | 'success';
  syncMode: string;
  targetCode?: string;
  targetType: 'bond' | 'index' | 'stock';
  triggerType: 'manual' | 'scheduled';
}

export function listLatestFullMarketSyncJobs() {
  return requestClient.get<MarketSyncJob[]>('/market-sync/jobs/latest-full', {
    timeout: 60_000,
  });
}
