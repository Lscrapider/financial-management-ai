import { requestClient } from '#/api/request';

export interface AiChatRequest {
  message: string;
}

export interface AiChatResponse {
  answer: string;
  answeredAt: string;
  message: string;
  model: string;
}

export function sendAiChatMessage(data: AiChatRequest) {
  return requestClient.post<AiChatResponse>('/ai/chat', data, {
    responseReturn: 'body',
    timeout: 120_000,
  });
}
