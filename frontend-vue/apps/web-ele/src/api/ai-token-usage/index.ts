import { requestClient } from '#/api/request';

export interface AiTokenUsageOverview {
  cachedTokens: number;
  completionTokens: number;
  latestOccurredAt?: null | string;
  promptTokens: number;
  reasoningTokens: number;
  requestCount: number;
  totalTokens: number;
}

export interface AiTokenUsageTrend {
  completionTokens: number;
  promptTokens: number;
  requestCount: number;
  timeBucket: string;
  totalTokens: number;
}

export interface AiTokenUsageLog {
  cachedTokens: number;
  completionTokens: number;
  createdAt?: null | string;
  finishReason?: null | string;
  id: number;
  model?: null | string;
  objectType?: null | string;
  occurredAt?: null | string;
  phase?: null | string;
  promptCacheHitTokens: number;
  promptCacheMissTokens: number;
  promptTokens: number;
  provider?: null | string;
  reasoningTokens: number;
  responseId?: null | string;
  source?: null | string;
  totalTokens: number;
  userId?: null | number;
  username?: null | string;
}

export interface AiTokenUsageLogPage {
  pageNum: number;
  pageSize: number;
  pages: number;
  records: AiTokenUsageLog[];
  total: number;
}

export interface AiTokenUsageLogPageParams {
  endTime?: string;
  model?: string;
  pageNum?: number;
  pageSize?: number;
  phase?: string;
  responseId?: string;
  source?: string;
  startTime?: string;
  username?: string;
}

export function getAiTokenUsageOverview(days = 30) {
  return requestClient.get<AiTokenUsageOverview>('/ai/token-usage/overview', {
    params: { days },
    responseReturn: 'body',
  });
}

export function listAiTokenUsageTrends(days = 30) {
  return requestClient.get<AiTokenUsageTrend[]>('/ai/token-usage/trends', {
    params: { days },
    responseReturn: 'body',
  });
}

export function listAiTokenUsageLogs(params: AiTokenUsageLogPageParams) {
  return requestClient.get<AiTokenUsageLogPage>('/ai/token-usage/logs', {
    params,
    responseReturn: 'body',
  });
}
