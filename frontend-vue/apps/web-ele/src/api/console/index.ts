import { requestClient } from '#/api/request';

export interface AiConsoleOverview {
  tokenUsage: {
    cachedTokens: number;
    completionTokens: number;
    latestOccurredAt?: string;
    promptTokens: number;
    reasoningTokens: number;
    requestCount: number;
    totalTokens: number;
  };
  user: {
    totalUserCount: number;
  };
  visit: {
    latestOccurredAt?: string;
    periodVisitCount: number;
    totalVisitCount: number;
    uniqueUserCount: number;
  };
}

export interface AppVisitTrend {
  timeBucket: string;
  uniqueUserCount: number;
  visitCount: number;
}

export interface AiTokenUsageTrend {
  completionTokens: number;
  promptTokens: number;
  requestCount: number;
  timeBucket: string;
  totalTokens: number;
}

export function getAiConsoleOverview(days = 7) {
  return requestClient.get<AiConsoleOverview>('/ai/console/overview', {
    params: { days },
    responseReturn: 'body',
  });
}

export function listAiTokenUsageTrends(days = 20) {
  return requestClient.get<AiTokenUsageTrend[]>('/ai/token-usage/trends', {
    params: { days },
    responseReturn: 'body',
  });
}

export function listAppVisitTrends(hours = 24) {
  return requestClient.get<AppVisitTrend[]>('/ai/console/visit-trends', {
    params: { hours },
    responseReturn: 'body',
  });
}
