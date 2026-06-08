<script lang="ts" setup>
import type { AiChatResponse } from '#/api/ai-chat';

import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue';

import {
  CornerDownLeft,
  Eraser,
  Expand,
  MessageSquareCode,
  Shrink,
  UserRoundPen,
} from '@vben/icons';

import { ElButton, ElEmpty, ElInput, ElScrollbar, ElTag } from 'element-plus';

import { closeAiChatConnection, sendAiChatMessage } from '#/api/ai-chat';

interface ChatMessage {
  content: string;
  createdAt: string;
  id: number;
  model?: string;
  role: 'assistant' | 'user';
}

const open = ref(false);
const input = ref('');
const fullscreen = ref(false);
const loading = ref(false);
const messages = ref<ChatMessage[]>([]);
const scrollbarRef = ref<InstanceType<typeof ElScrollbar>>();
const triggerSize = 60;
const triggerPosition = reactive({
  left: 24,
  top: window.innerHeight - triggerSize - 24,
});
const dragState = reactive({
  moved: false,
  offsetX: 0,
  offsetY: 0,
  startX: 0,
  startY: 0,
});

const triggerStyle = computed(() => ({
  left: `${triggerPosition.left}px`,
  top: `${triggerPosition.top}px`,
}));

const panelStyle = computed(() => {
  if (fullscreen.value) {
    return {
      height: 'calc(100vh - 32px)',
      left: '16px',
      top: '16px',
      width: 'calc(100vw - 32px)',
    };
  }

  const panelWidth = Math.min(420, window.innerWidth - 32);
  const panelHeight = Math.min(560, window.innerHeight - 32);
  const preferRight =
    triggerPosition.left + triggerSize + 16 + panelWidth <=
    window.innerWidth - 16;
  const left = preferRight
    ? triggerPosition.left + triggerSize + 16
    : Math.max(16, triggerPosition.left - panelWidth - 16);
  const top = Math.min(
    Math.max(16, triggerPosition.top - 18),
    Math.max(16, window.innerHeight - panelHeight - 16),
  );

  return {
    height: `${panelHeight}px`,
    left: `${left}px`,
    top: `${top}px`,
    width: `${panelWidth}px`,
  };
});

let messageId = 0;

function toggleChat() {
  if (open.value) {
    closeChat();
    return;
  }

  open.value = true;
  scrollToBottom();
}

function closeChat() {
  open.value = false;
  closeAiChatConnection();
}

function clearMessages() {
  messages.value = [];
}

function toggleFullscreen() {
  fullscreen.value = !fullscreen.value;
  scrollToBottom();
}

async function submitMessage() {
  const content = input.value.trim();
  if (!content || loading.value) {
    return;
  }

  input.value = '';
  messages.value.push(createMessage('user', content));
  loading.value = true;
  scrollToBottom();

  try {
    const response = await sendAiChatMessage({ message: content });
    messages.value.push(createAssistantMessage(response));
  } catch {
    messages.value.push(
      createMessage('assistant', 'AI 服务暂时不可用，请稍后再试。'),
    );
  } finally {
    loading.value = false;
    scrollToBottom();
  }
}

function handleInputKeydown(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent)) {
    return;
  }
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return;
  }
  event.preventDefault();
  submitMessage();
}

function createMessage(
  role: ChatMessage['role'],
  content: string,
): ChatMessage {
  messageId += 1;
  return {
    content,
    createdAt: formatTime(new Date()),
    id: messageId,
    role,
  };
}

function createAssistantMessage(response: AiChatResponse): ChatMessage {
  messageId += 1;
  return {
    content: response.answer || '没有返回内容。',
    createdAt: formatTime(
      response.answeredAt ? new Date(response.answeredAt) : new Date(),
    ),
    id: messageId,
    model: response.model,
    role: 'assistant',
  };
}

function formatTime(date: Date) {
  if (Number.isNaN(date.getTime())) {
    return '--:--';
  }
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

async function scrollToBottom() {
  await nextTick();
  const wrap = scrollbarRef.value?.wrapRef;
  if (wrap) {
    scrollbarRef.value?.setScrollTop(wrap.scrollHeight);
  }
}

function startDrag(event: PointerEvent) {
  dragState.moved = false;
  dragState.startX = event.clientX;
  dragState.startY = event.clientY;
  dragState.offsetX = event.clientX - triggerPosition.left;
  dragState.offsetY = event.clientY - triggerPosition.top;
  window.addEventListener('pointermove', moveTrigger);
  window.addEventListener('pointerup', stopDrag);
}

function moveTrigger(event: PointerEvent) {
  const deltaX = Math.abs(event.clientX - dragState.startX);
  const deltaY = Math.abs(event.clientY - dragState.startY);
  dragState.moved = dragState.moved || deltaX > 4 || deltaY > 4;

  triggerPosition.left = clamp(
    event.clientX - dragState.offsetX,
    12,
    window.innerWidth - triggerSize - 12,
  );
  triggerPosition.top = clamp(
    event.clientY - dragState.offsetY,
    12,
    window.innerHeight - triggerSize - 12,
  );
}

function stopDrag() {
  window.removeEventListener('pointermove', moveTrigger);
  window.removeEventListener('pointerup', stopDrag);
  if (!dragState.moved) {
    toggleChat();
  }
}

function renderMessage(content: string) {
  const lines = escapeHtml(content).split('\n');
  const html: string[] = [];
  let listOpen = false;

  lines.forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed) {
      if (listOpen) {
        html.push('</ul>');
        listOpen = false;
      }
      return;
    }

    const bullet = trimmed.match(/^[-*]\s+(.+)$/);
    const ordered = trimmed.match(/^\d+[.)]\s+(.+)$/);
    if (bullet || ordered) {
      if (!listOpen) {
        html.push('<ul>');
        listOpen = true;
      }
      html.push(
        `<li>${formatInline((bullet?.[1] ?? ordered?.[1]) || '')}</li>`,
      );
      return;
    }

    if (listOpen) {
      html.push('</ul>');
      listOpen = false;
    }

    if (trimmed.startsWith('### ')) {
      html.push(`<h4>${formatInline(trimmed.slice(4))}</h4>`);
      return;
    }
    if (trimmed.startsWith('## ')) {
      html.push(`<h3>${formatInline(trimmed.slice(3))}</h3>`);
      return;
    }
    if (trimmed.startsWith('# ')) {
      html.push(`<h2>${formatInline(trimmed.slice(2))}</h2>`);
      return;
    }
    html.push(`<p>${formatInline(trimmed)}</p>`);
  });

  if (listOpen) {
    html.push('</ul>');
  }
  return html.join('');
}

function formatInline(content: string) {
  return content.replaceAll(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
}

function escapeHtml(content: string) {
  return content
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

onBeforeUnmount(() => {
  closeAiChatConnection();
  window.removeEventListener('pointermove', moveTrigger);
  window.removeEventListener('pointerup', stopDrag);
});
</script>

<template>
  <Teleport to="body">
    <button
      class="ai-chat-trigger"
      :style="triggerStyle"
      type="button"
      @pointerdown.prevent="startDrag"
    >
      <MessageSquareCode :size="22" />
    </button>

    <section v-if="open" class="ai-chat-window" :style="panelStyle">
      <div class="window-header">
        <div class="window-title-group">
          <div class="window-title">AI Chat</div>
          <div class="window-subtitle">理财分析助手</div>
        </div>
        <div class="window-actions">
          <ElButton
            :disabled="messages.length === 0 || loading"
            :icon="Eraser"
            circle
            plain
            size="small"
            @click="clearMessages"
          />
          <ElButton
            :icon="fullscreen ? Shrink : Expand"
            circle
            plain
            size="small"
            @click="toggleFullscreen"
          />
          <ElButton circle plain size="small" @click="closeChat">
            ×
          </ElButton>
        </div>
      </div>

      <ElScrollbar ref="scrollbarRef" class="message-scrollbar window-body">
        <div v-if="messages.length > 0" class="message-list">
          <div
            v-for="message in messages"
            :key="message.id"
            class="message-row"
            :class="[message.role]"
          >
            <div class="message-avatar">
              <UserRoundPen v-if="message.role === 'user'" :size="16" />
              <MessageSquareCode v-else :size="16" />
            </div>
            <div class="message-main">
              <div class="message-meta">
                <span>{{ message.role === 'user' ? '我' : 'AI' }}</span>
                <ElTag v-if="message.model" effect="plain" size="small">
                  {{ message.model }}
                </ElTag>
                <time>{{ message.createdAt }}</time>
              </div>
              <div
                class="message-bubble"
                :class="{ rendered: message.role === 'assistant' }"
              >
                <div
                  v-if="message.role === 'assistant'"
                  class="rendered-content"
                  v-html="renderMessage(message.content)"
                ></div>
                <template v-else>
                  {{ message.content }}
                </template>
              </div>
            </div>
          </div>
          <div v-if="loading" class="message-row assistant">
            <div class="message-avatar">
              <MessageSquareCode :size="16" />
            </div>
            <div class="message-main">
              <div class="message-bubble pending">正在分析...</div>
            </div>
          </div>
        </div>
        <ElEmpty v-else description="输入问题开始对话" />
      </ElScrollbar>

      <div class="input-area">
        <ElInput
          v-model="input"
          :autosize="{ minRows: 3, maxRows: 6 }"
          :disabled="loading"
          maxlength="1000"
          placeholder="输入你的问题，Shift + Enter 换行"
          resize="none"
          show-word-limit
          type="textarea"
          @keydown="handleInputKeydown"
        />
        <div class="input-actions">
          <span>回答仅供研究参考</span>
          <ElButton
            :disabled="!input.trim()"
            :icon="CornerDownLeft"
            :loading="loading"
            type="primary"
            @click="submitMessage"
          >
            发送
          </ElButton>
        </div>
      </div>
    </section>
  </Teleport>
</template>

<style scoped>
.ai-chat-trigger {
  position: fixed;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  color: #fff;
  touch-action: none;
  cursor: pointer;
  background: var(--el-color-primary);
  border: 0;
  border-radius: 50%;
  box-shadow: 0 10px 24px rgb(0 0 0 / 18%);
}

.ai-chat-trigger:hover {
  background: var(--el-color-primary-light-3);
}

.ai-chat-window {
  position: fixed;
  z-index: 2999;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  box-shadow: 0 18px 48px rgb(0 0 0 / 24%);
}

.window-header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.window-title-group {
  min-width: 0;
}

.window-title {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.25;
  color: var(--el-text-color-primary);
}

.window-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.window-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.message-scrollbar {
  flex: 1;
  min-height: 0;
}

.window-body {
  padding: 14px 14px 0;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 2px 4px 16px;
}

.message-row {
  display: flex;
  gap: 10px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  display: flex;
  flex: 0 0 30px;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-top: 22px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 50%;
}

.message-row.user .message-avatar {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.message-main {
  max-width: calc(100% - 48px);
}

.message-row.user .message-main {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-bubble {
  max-width: 100%;
  padding: 10px 12px;
  line-height: 1.65;
  color: var(--el-text-color-primary);
  overflow-wrap: break-word;
  white-space: pre-wrap;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.message-bubble.rendered {
  white-space: normal;
}

.rendered-content :deep(h2),
.rendered-content :deep(h3),
.rendered-content :deep(h4) {
  margin: 12px 0 8px;
  font-weight: 700;
  line-height: 1.35;
}

.rendered-content :deep(h2:first-child),
.rendered-content :deep(h3:first-child),
.rendered-content :deep(h4:first-child),
.rendered-content :deep(p:first-child),
.rendered-content :deep(ul:first-child) {
  margin-top: 0;
}

.rendered-content :deep(p) {
  margin: 8px 0;
}

.rendered-content :deep(ul) {
  padding-left: 20px;
  margin: 8px 0;
}

.rendered-content :deep(li) {
  margin: 4px 0;
}

.rendered-content :deep(strong) {
  font-weight: 700;
}

.rendered-content :deep(p:last-child),
.rendered-content :deep(ul:last-child) {
  margin-bottom: 0;
}

.message-row.user .message-bubble {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.message-bubble.pending {
  color: var(--el-text-color-secondary);
}

.input-area {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 640px) {
  .ai-chat-window {
    border-radius: 8px;
  }
}
</style>
