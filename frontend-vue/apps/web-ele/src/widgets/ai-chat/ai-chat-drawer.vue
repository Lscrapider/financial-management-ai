<script lang="ts" setup>
import type { AiChatResponse } from '#/api/ai-chat';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue';

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

interface ChatLayoutState {
  enlarged?: boolean;
  fullscreen?: boolean;
  panel?: Partial<PanelRect>;
  trigger?: Partial<Position>;
}

interface PanelRect {
  height: number;
  left: number;
  top: number;
  width: number;
}

interface Position {
  left: number;
  top: number;
}

type InteractionType = 'panel' | 'resize' | 'trigger';

const STORAGE_KEY = 'financial-management-ai-chat-layout';
const safeGap = 16;
const triggerWidth = 132;
const triggerHeight = 40;
const mobileBreakpoint = 640;
const defaultPanelWidth = 440;
const defaultPanelHeight = 620;
const enlargedPanelWidth = 720;
const enlargedPanelHeight = 720;
const minPanelWidth = 360;
const minPanelHeight = 420;
const quickPrompts = ['解释今日异动', '列复盘清单', '查相关知识'];

const open = ref(false);
const input = ref('');
const fullscreen = ref(false);
const enlarged = ref(false);
const loading = ref(false);
const messages = ref<ChatMessage[]>([]);
const scrollbarRef = ref<InstanceType<typeof ElScrollbar>>();
const viewport = reactive({
  height: window.innerHeight,
  width: window.innerWidth,
});
const triggerPosition = reactive<Position>(createDefaultTriggerPosition());
const panelRect = reactive<PanelRect>(createDefaultPanelRect());
const interactionState = reactive({
  offsetX: 0,
  offsetY: 0,
  panelStartHeight: 0,
  panelStartLeft: 0,
  panelStartTop: 0,
  panelStartWidth: 0,
  moved: false,
  startX: 0,
  startY: 0,
  type: undefined as InteractionType | undefined,
});

const isMobile = computed(() => viewport.width <= mobileBreakpoint);

const triggerStyle = computed(() => ({
  left: `${triggerPosition.left}px`,
  top: `${triggerPosition.top}px`,
}));

const panelStyle = computed(() => {
  if (fullscreen.value || isMobile.value) {
    const gap = isMobile.value ? 8 : 16;
    return {
      height: `calc(100vh - ${gap * 2}px)`,
      left: `${gap}px`,
      top: `${gap}px`,
      width: `calc(100vw - ${gap * 2}px)`,
    };
  }

  return {
    height: `${panelRect.height}px`,
    left: `${panelRect.left}px`,
    top: `${panelRect.top}px`,
    width: `${panelRect.width}px`,
  };
});

let messageId = 0;

function toggleChat() {
  if (open.value) {
    closeChat();
    return;
  }

  open.value = true;
  clampPanelRect();
  saveLayoutState();
  scrollToBottom();
}

function closeChat() {
  open.value = false;
  closeAiChatConnection();
}

function clearMessages() {
  messages.value = [];
}

function applyQuickPrompt(prompt: string) {
  if (loading.value) {
    return;
  }
  input.value = prompt;
}

function toggleEnlarged() {
  if (fullscreen.value || isMobile.value) {
    return;
  }

  const previousRight = viewport.width - panelRect.left - panelRect.width;
  enlarged.value = !enlarged.value;
  panelRect.width = Math.min(
    enlarged.value ? enlargedPanelWidth : defaultPanelWidth,
    viewport.width - safeGap * 2,
  );
  panelRect.height = Math.min(
    enlarged.value ? enlargedPanelHeight : defaultPanelHeight,
    viewport.height - safeGap * 2,
  );
  panelRect.left = viewport.width - previousRight - panelRect.width;
  clampPanelRect();
  saveLayoutState();
  scrollToBottom();
}

function toggleFullscreen() {
  fullscreen.value = !fullscreen.value;
  saveLayoutState();
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

function startTriggerDrag(event: PointerEvent) {
  startInteraction(event, 'trigger');
  interactionState.offsetX = event.clientX - triggerPosition.left;
  interactionState.offsetY = event.clientY - triggerPosition.top;
}

function startPanelDrag(event: PointerEvent) {
  if (fullscreen.value || isMobile.value) {
    return;
  }

  const target = event.target as HTMLElement;
  if (target.closest('.window-actions')) {
    return;
  }

  startInteraction(event, 'panel');
  interactionState.offsetX = event.clientX - panelRect.left;
  interactionState.offsetY = event.clientY - panelRect.top;
}

function startPanelResize(event: PointerEvent) {
  if (fullscreen.value || isMobile.value) {
    return;
  }

  startInteraction(event, 'resize');
  interactionState.panelStartLeft = panelRect.left;
  interactionState.panelStartTop = panelRect.top;
  interactionState.panelStartWidth = panelRect.width;
  interactionState.panelStartHeight = panelRect.height;
}

function startInteraction(event: PointerEvent, type: InteractionType) {
  interactionState.type = type;
  interactionState.moved = false;
  interactionState.startX = event.clientX;
  interactionState.startY = event.clientY;
  window.addEventListener('pointermove', moveInteraction);
  window.addEventListener('pointerup', stopInteraction);
}

function moveInteraction(event: PointerEvent) {
  const deltaX = event.clientX - interactionState.startX;
  const deltaY = event.clientY - interactionState.startY;
  interactionState.moved =
    interactionState.moved || Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4;

  if (interactionState.type === 'trigger') {
    triggerPosition.left = event.clientX - interactionState.offsetX;
    triggerPosition.top = event.clientY - interactionState.offsetY;
    clampTriggerPosition();
    return;
  }

  if (interactionState.type === 'panel') {
    panelRect.left = event.clientX - interactionState.offsetX;
    panelRect.top = event.clientY - interactionState.offsetY;
    clampPanelRect();
    return;
  }

  if (interactionState.type === 'resize') {
    panelRect.width = clamp(
      interactionState.panelStartWidth + deltaX,
      minPanelWidth,
      viewport.width - interactionState.panelStartLeft - safeGap,
    );
    panelRect.height = clamp(
      interactionState.panelStartHeight + deltaY,
      minPanelHeight,
      viewport.height - interactionState.panelStartTop - safeGap,
    );
    clampPanelRect();
  }
}

function stopInteraction() {
  window.removeEventListener('pointermove', moveInteraction);
  window.removeEventListener('pointerup', stopInteraction);

  if (interactionState.type === 'trigger') {
    if (!interactionState.moved) {
      toggleChat();
    } else {
      snapTriggerToEdge();
      saveLayoutState();
    }
  }

  if (interactionState.type === 'panel' || interactionState.type === 'resize') {
    saveLayoutState();
  }

  interactionState.type = undefined;
}

function createDefaultTriggerPosition(): Position {
  return {
    left: window.innerWidth - triggerWidth - 24,
    top: window.innerHeight - triggerHeight - 88,
  };
}

function createDefaultPanelRect(): PanelRect {
  const width = Math.min(defaultPanelWidth, window.innerWidth - safeGap * 2);
  const height = Math.min(defaultPanelHeight, window.innerHeight - safeGap * 2);
  return {
    height,
    left: window.innerWidth - width - 24,
    top: Math.max(safeGap, window.innerHeight - height - 24),
    width,
  };
}

function clampTriggerPosition() {
  const currentWidth = getTriggerWidth();
  triggerPosition.left = clamp(
    triggerPosition.left,
    safeGap,
    viewport.width - currentWidth - safeGap,
  );
  triggerPosition.top = clamp(
    triggerPosition.top,
    safeGap,
    viewport.height - triggerHeight - safeGap,
  );
}

function snapTriggerToEdge() {
  const currentWidth = getTriggerWidth();
  const distanceToLeft = triggerPosition.left;
  const distanceToRight = viewport.width - triggerPosition.left - currentWidth;
  triggerPosition.left =
    distanceToLeft <= distanceToRight
      ? safeGap
      : viewport.width - currentWidth - safeGap;
  clampTriggerPosition();
}

function getTriggerWidth() {
  return viewport.width <= mobileBreakpoint ? 48 : triggerWidth;
}

function clampPanelRect() {
  panelRect.width = clamp(
    panelRect.width,
    Math.min(minPanelWidth, viewport.width - safeGap * 2),
    viewport.width - safeGap * 2,
  );
  panelRect.height = clamp(
    panelRect.height,
    Math.min(minPanelHeight, viewport.height - safeGap * 2),
    viewport.height - safeGap * 2,
  );
  panelRect.left = clamp(
    panelRect.left,
    safeGap,
    viewport.width - panelRect.width - safeGap,
  );
  panelRect.top = clamp(
    panelRect.top,
    safeGap,
    viewport.height - panelRect.height - safeGap,
  );
}

function loadLayoutState() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return;
    }
    const state = JSON.parse(raw) as ChatLayoutState;
    Object.assign(triggerPosition, pickPosition(state.trigger));
    Object.assign(panelRect, pickPanelRect(state.panel));
    enlarged.value = Boolean(state.enlarged);
    fullscreen.value = Boolean(state.fullscreen);
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
  } finally {
    clampTriggerPosition();
    clampPanelRect();
  }
}

function saveLayoutState() {
  const state: ChatLayoutState = {
    enlarged: enlarged.value,
    fullscreen: fullscreen.value,
    panel: { ...panelRect },
    trigger: { ...triggerPosition },
  };
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function pickPosition(position?: Partial<Position>): Position {
  return {
    left: Number.isFinite(position?.left)
      ? Number(position?.left)
      : triggerPosition.left,
    top: Number.isFinite(position?.top)
      ? Number(position?.top)
      : triggerPosition.top,
  };
}

function pickPanelRect(rect?: Partial<PanelRect>): PanelRect {
  return {
    height: Number.isFinite(rect?.height)
      ? Number(rect?.height)
      : panelRect.height,
    left: Number.isFinite(rect?.left) ? Number(rect?.left) : panelRect.left,
    top: Number.isFinite(rect?.top) ? Number(rect?.top) : panelRect.top,
    width: Number.isFinite(rect?.width) ? Number(rect?.width) : panelRect.width,
  };
}

function handleViewportResize() {
  viewport.width = window.innerWidth;
  viewport.height = window.innerHeight;
  clampTriggerPosition();
  clampPanelRect();
  saveLayoutState();
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

onMounted(() => {
  loadLayoutState();
  window.addEventListener('resize', handleViewportResize);
});

onBeforeUnmount(() => {
  closeAiChatConnection();
  window.removeEventListener('pointermove', moveInteraction);
  window.removeEventListener('pointerup', stopInteraction);
  window.removeEventListener('resize', handleViewportResize);
});
</script>

<template>
  <Teleport to="body">
    <button
      class="ai-chat-trigger"
      aria-label="打开 AI 研究助手"
      :style="triggerStyle"
      type="button"
      @pointerdown.prevent="startTriggerDrag"
    >
      <MessageSquareCode :size="22" />
      <span>AI 研究助手</span>
    </button>

    <section
      v-if="open"
      class="ai-chat-window"
      :class="{ fullscreen }"
      :style="panelStyle"
    >
      <div class="window-header" @pointerdown.prevent="startPanelDrag">
        <div class="window-title-group">
          <div class="window-title">AI 研究助手</div>
          <div class="window-subtitle">
            基于当前页面、会话历史和知识库辅助分析
          </div>
        </div>
        <div class="window-actions" @pointerdown.stop>
          <ElButton
            aria-label="清空对话"
            :disabled="messages.length === 0 || loading"
            :icon="Eraser"
            plain
            size="small"
            @click="clearMessages"
          >
            清空
          </ElButton>
          <ElButton
            v-if="!isMobile"
            :aria-label="enlarged ? '恢复默认尺寸' : '放大对话窗口'"
            :disabled="fullscreen"
            plain
            size="small"
            @click="toggleEnlarged"
          >
            {{ enlarged ? '恢复' : '放大' }}
          </ElButton>
          <ElButton
            :aria-label="fullscreen ? '退出专注模式' : '进入专注模式'"
            :icon="fullscreen ? Shrink : Expand"
            plain
            size="small"
            @click="toggleFullscreen"
          >
            {{ fullscreen ? '退出' : '专注' }}
          </ElButton>
          <ElButton
            aria-label="收起 AI 研究助手"
            plain
            size="small"
            @click="closeChat"
          >
            收起
          </ElButton>
        </div>
      </div>

      <div class="context-bar">
        <span class="context-chip active">当前页</span>
        <span class="context-chip">自选池</span>
        <span class="context-chip">近 20 条会话</span>
        <span class="context-chip">知识库材料</span>
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
              <div class="message-bubble pending">
                <span class="pending-text">正在分析</span>
                <span aria-hidden="true" class="pending-dots">
                  <span></span>
                  <span></span>
                  <span></span>
                </span>
              </div>
            </div>
          </div>
        </div>
        <ElEmpty v-else description="输入问题开始研究对话" />
      </ElScrollbar>

      <div class="input-area">
        <div class="quick-prompts">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            class="quick-prompt"
            :disabled="loading"
            type="button"
            @click="applyQuickPrompt(prompt)"
          >
            {{ prompt }}
          </button>
        </div>
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
          <span>回答仅供研究参考，不构成投资建议</span>
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

      <button
        v-if="!fullscreen && !isMobile"
        aria-label="拖动调整 AI 研究助手窗口大小"
        class="resize-handle"
        type="button"
        @pointerdown.prevent="startPanelResize"
      ></button>
    </section>
  </Teleport>
</template>

<style scoped>
.ai-chat-trigger {
  position: fixed;
  z-index: 3000;
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  width: 132px;
  height: 40px;
  padding: 0 12px;
  color: #fff;
  touch-action: none;
  cursor: grab;
  background: var(--el-color-primary);
  border: 1px solid var(--el-color-primary);
  border-radius: 8px;
  box-shadow: 0 4px 8px rgb(0 0 0 / 18%);
  transition:
    background-color 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.ai-chat-trigger:active {
  cursor: grabbing;
}

.ai-chat-trigger span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.ai-chat-trigger:hover {
  background: var(--el-color-primary-light-3);
  border-color: var(--el-color-primary-light-3);
}

.ai-chat-window {
  position: fixed;
  z-index: 3001;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  box-shadow: 0 6px 8px rgb(0 0 0 / 22%);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.ai-chat-window.fullscreen {
  border-radius: 8px;
}

.window-header {
  display: flex;
  flex: 0 0 auto;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  cursor: move;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.window-title-group {
  min-width: 0;
}

.window-title {
  font-size: 16px;
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
  flex: 0 0 auto;
  gap: 6px;
  align-items: center;
  cursor: default;
}

.context-bar {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 14px 0;
}

.context-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  font-size: 12px;
  line-height: 1.2;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.context-chip.active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.message-scrollbar {
  flex: 1;
  min-height: 0;
}

.window-body {
  padding: 12px 14px 0;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 2px 2px 16px;
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
  width: 28px;
  height: 28px;
  margin-top: 21px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
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
  font-size: 13px;
  line-height: 1.7;
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
  position: relative;
  display: inline-flex;
  gap: 8px;
  align-items: center;
  min-width: 116px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
}

.message-bubble.pending::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  content: '';
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--el-color-primary) 45%,
    transparent 90%
  );
  opacity: 0.78;
  animation: pending-scan 1.4s cubic-bezier(0.22, 1, 0.36, 1) infinite;
}

.pending-text {
  flex: 0 0 auto;
}

.pending-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.pending-dots span {
  width: 4px;
  height: 4px;
  background: var(--el-color-primary);
  border-radius: 50%;
  opacity: 0.35;
  animation: pending-dot 1.2s ease-in-out infinite;
}

.pending-dots span:nth-child(2) {
  animation-delay: 160ms;
}

.pending-dots span:nth-child(3) {
  animation-delay: 320ms;
}

.input-area {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px 14px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-prompt {
  min-height: 28px;
  padding: 0 9px;
  font-size: 12px;
  line-height: 1;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  transition:
    color 180ms ease,
    background-color 180ms ease,
    border-color 180ms ease;
}

.quick-prompt:hover:not(:disabled) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-7);
}

.quick-prompt:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.input-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 22px;
  height: 22px;
  padding: 0;
  cursor: nwse-resize;
  background:
    linear-gradient(
      135deg,
      transparent 52%,
      var(--el-color-primary) 53%,
      var(--el-color-primary) 58%,
      transparent 59%
    ),
    linear-gradient(
      135deg,
      transparent 66%,
      var(--el-color-primary) 67%,
      var(--el-color-primary) 72%,
      transparent 73%
    );
  border: 0;
  border-bottom-right-radius: 8px;
  opacity: 0.85;
}

.resize-handle:hover {
  opacity: 1;
}

@media (max-width: 640px) {
  .ai-chat-trigger {
    width: 48px;
    height: 48px;
    padding: 0;
    border-radius: 8px;
  }

  .ai-chat-trigger span {
    display: none;
  }

  .ai-chat-window {
    border-radius: 8px;
  }

  .window-header {
    align-items: flex-start;
    padding: 10px 12px;
  }

  .window-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .window-title {
    font-size: 15px;
  }

  .window-subtitle {
    max-width: 220px;
  }

  .context-bar {
    padding: 8px 12px 0;
  }

  .window-body {
    padding: 10px 12px 0;
  }

  .input-area {
    padding: 10px 12px 12px;
  }

  .message-row.user {
    flex-direction: row;
  }

  .message-row.user .message-main {
    align-items: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-chat-trigger,
  .ai-chat-window,
  .quick-prompt {
    transition: none;
  }

  .message-bubble.pending::after,
  .pending-dots span {
    animation: none;
  }
}

@keyframes pending-dot {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@keyframes pending-scan {
  0% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(100%);
  }
}
</style>
