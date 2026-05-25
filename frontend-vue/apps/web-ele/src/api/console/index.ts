import { requestClient } from '#/api/request';

export interface AiConsoleOverview {
  tokenUsage: {
    completionTokens: number;
    promptTokens: number;
    totalTokens: number;
  };
  user: {
    totalUserCount: number;
  };
  visit: {
    periodVisitCount: number;
    totalVisitCount: number;
    uniqueUserCount: number;
  };
}

export interface AiTokenUsageTrend {
  completionTokens: number;
  promptTokens: number;
  timeBucket: string;
  totalTokens: number;
}

export function getAiConsoleOverview(days = 7) {
  return requestClient.get<AiConsoleOverview>('/ai/console/overview', {
    params: { days },
    responseReturn: 'body',
  });
}

export function listAiTokenUsageTrends(days = 7) {
  return requestClient.get<AiTokenUsageTrend[]>('/ai/token-usage/trends', {
    params: { days },
    responseReturn: 'body',
  });
}
