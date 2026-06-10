import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

export interface AiChatRequest {
  message: string;
}

export interface AiChatResponse {
  answer: string;
  answeredAt: string;
  message: string;
  model?: string;
}

export interface AiChatProgress {
  content?: string;
  status?: string;
}

export interface AiChatDelta {
  answer: string;
  content: string;
}

interface AiChatWebSocketMessage {
  answeredAt?: string;
  content?: string;
  conversationId: string;
  messageId: string;
  model?: string;
  status?: string;
  type: 'agent_progress' | 'agent_status' | 'answer_delta' | 'final_answer';
}

interface PendingMessage {
  answer: string;
  message: string;
  onDelta?: (delta: AiChatDelta) => void;
  onProgress?: (progress: AiChatProgress) => void;
  reject: (reason?: unknown) => void;
  resolve: (response: AiChatResponse) => void;
}

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

let messageSequence = 0;
let socket: null | WebSocket = null;
let connectingPromise: null | Promise<WebSocket> = null;
let pendingMessage: null | PendingMessage = null;

export async function sendAiChatMessage(
  data: AiChatRequest,
  onProgress?: (progress: AiChatProgress) => void,
  onDelta?: (delta: AiChatDelta) => void,
) {
  const ws = await connectAiChatSocket();
  return new Promise<AiChatResponse>((resolve, reject) => {
    if (pendingMessage) {
      reject(new Error('上一条消息仍在处理中'));
      return;
    }

    const messageId = createMessageId();
    pendingMessage = {
      answer: '',
      message: data.message,
      onDelta,
      onProgress,
      reject,
      resolve,
    };
    ws.send(
      JSON.stringify({
        content: data.message,
        messageId,
        type: 'user_message',
      }),
    );
  });
}

export function closeAiChatConnection() {
  pendingMessage?.reject(new Error('AI Chat 连接已关闭'));
  pendingMessage = null;
  connectingPromise = null;
  if (socket) {
    socket.close();
    socket = null;
  }
}

function connectAiChatSocket() {
  if (socket?.readyState === WebSocket.OPEN) {
    return Promise.resolve(socket);
  }
  if (connectingPromise) {
    return connectingPromise;
  }

  const accessStore = useAccessStore();
  const accessToken = accessStore.accessToken;
  if (!accessToken) {
    return Promise.reject(new Error('请先登录后再使用 AI Chat。'));
  }

  connectingPromise = new Promise<WebSocket>((resolve, reject) => {
    let opened = false;
    const ws = new WebSocket(buildAiChatSocketUrl(accessToken));
    socket = ws;

    ws.addEventListener('open', () => {
      opened = true;
      connectingPromise = null;
      resolve(ws);
    });

    ws.addEventListener('message', (event) => {
      handleAiChatSocketMessage(event.data);
    });

    ws.addEventListener('error', () => {
      if (ws.readyState !== WebSocket.OPEN) {
        reject(new Error('AI Chat 连接失败'));
      }
    });

    ws.addEventListener('close', () => {
      if (socket === ws) {
        socket = null;
      }
      connectingPromise = null;
      if (!opened) {
        reject(new Error('AI Chat 连接已关闭'));
      }
      pendingMessage?.reject(new Error('AI Chat 连接已断开'));
      pendingMessage = null;
    });
  });

  return connectingPromise;
}

function handleAiChatSocketMessage(data: unknown) {
  if (!pendingMessage) {
    return;
  }

  const response = JSON.parse(String(data)) as AiChatWebSocketMessage;
  if (response.type === 'agent_progress') {
    pendingMessage.onProgress?.({
      content: response.content,
      status: response.status,
    });
    return;
  }

  if (response.type === 'answer_delta') {
    const content = response.content || '';
    if (content) {
      pendingMessage.answer += content;
      pendingMessage.onDelta?.({
        answer: pendingMessage.answer,
        content,
      });
    }
    return;
  }

  if (response.type !== 'final_answer') {
    return;
  }

  const pending = pendingMessage;
  pendingMessage = null;
  pending.resolve({
    answer: response.content || pending.answer || '没有返回内容。',
    answeredAt: response.answeredAt || new Date().toISOString(),
    message: pending.message,
    model: response.model || 'AI 研究助手',
  });
}

function buildAiChatSocketUrl(token: string) {
  const endpoint = joinUrl(apiURL, '/ws/ai-chat');
  const url = new URL(endpoint, window.location.origin);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.searchParams.set('accessToken', token);
  return url.toString();
}

function joinUrl(baseUrl: string, path: string) {
  return `${baseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
}

function createMessageId() {
  messageSequence += 1;
  return `msg-${Date.now()}-${messageSequence}`;
}
