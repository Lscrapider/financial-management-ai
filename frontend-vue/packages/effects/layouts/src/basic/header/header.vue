<script lang="ts" setup>
import { computed, useSlots } from 'vue';

import { useRefresh } from '@vben/hooks';
import { RotateCw } from '@vben/icons';
import { preferences, usePreferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { VbenFullScreen, VbenIconButton } from '@vben-core/shadcn-ui';

import {
  GlobalSearch,
  LanguageToggle,
  PreferencesButton,
  ThemeToggle,
  TimezoneButton,
} from '../../widgets';

interface Props {
  /**
   * Logo 主题
   */
  theme?: string;
}

defineOptions({
  name: 'LayoutHeader',
});

withDefaults(defineProps<Props>(), {
  theme: 'light',
});

const emit = defineEmits<{ clearPreferencesAndLogout: [] }>();

const REFERENCE_VALUE = 100;

const accessStore = useAccessStore();
const { globalSearchShortcutKey, preferencesButtonPosition } = usePreferences();
const slots = useSlots();
const { refresh } = useRefresh();

/**
 * 插槽列表类型
 */
type SlotItem = { index: number; name: string };

const rightSlots = computed(() => {
  const list: Array<SlotItem> = [];
  // 全局搜索
  if (preferences.widget.globalSearch) {
    list.push({
      index: REFERENCE_VALUE,
      name: 'global-search',
    });
  }
  // 偏好设置快捷功能
  if (preferencesButtonPosition.value.header) {
    list.push({
      index: REFERENCE_VALUE + 10,
      name: 'preferences',
    });
    // 将偏好设置中的子功能分组到同一个按钮位置控制逻辑下
    if (preferences.widget.themeToggle) {
      list.push({
        index: REFERENCE_VALUE + 20,
        name: 'theme-toggle',
      });
    }
    if (preferences.widget.languageToggle) {
      list.push({
        index: REFERENCE_VALUE + 30,
        name: 'language-toggle',
      });
    }
    if (preferences.widget.timezone) {
      list.push({
        index: REFERENCE_VALUE + 40,
        name: 'timezone',
      });
    }
  }
  // 全屏
  if (preferences.widget.fullscreen) {
    list.push({
      index: REFERENCE_VALUE + 50,
      name: 'fullscreen',
    });
  }
  // 消息通知
  if (preferences.widget.notification) {
    list.push({
      index: REFERENCE_VALUE + 60,
      name: 'notification',
    });
  }

  Object.keys(slots).forEach((key) => {
    // 适配插槽名称，例如第一个插槽名：header-right-1
    if (key.startsWith('header-right')) {
      // 取第三个占位的数字，若是第三个占位不是数字，则自动分配排序索引
      const slotIndex = Number(key.split('-')[2]);
      const index = Number.isNaN(slotIndex) ? nextIndex(list) : slotIndex;
      list.push({ index, name: key });
    }
  });
  // 最后追加用户下拉框，若是索引值超过1000时则固定在1000（适配用户按钮不在最后的场景）
  const userDropdownIndex = Math.min(1000, nextIndex(list));
  list.push({ index: userDropdownIndex, name: 'user-dropdown' });
  // 按照索引排序，保证插槽顺序
  return list.toSorted((a, b) => a.index - b.index);
});

const utilitySlots = computed(() =>
  rightSlots.value.filter((item) => item.name !== 'user-dropdown'),
);

const userDropdownSlot = computed(() =>
  rightSlots.value.find((item) => item.name === 'user-dropdown'),
);

const leftSlots = computed(() => {
  const list: Array<SlotItem> = [];
  // 刷新
  if (preferences.widget.refresh) {
    list.push({
      index: 0,
      name: 'refresh',
    });
  }

  Object.keys(slots).forEach((key) => {
    // 适配插槽名称，例如第一个插槽名：header-left-1
    if (key.startsWith('header-left')) {
      // 取第三个占位的数字，若是第三个占位不是数字，则自动分配排序索引
      const slotIndex = Number(key.split('-')[2]);
      const index = Number.isNaN(slotIndex) ? nextIndex(list) : slotIndex;
      list.push({ index, name: key });
    }
  });
  // 按照索引排序，保证插槽顺序
  return list.toSorted((a, b) => a.index - b.index);
});

/**
 * 获取列表下一个索引值(用于排序)
 * @param list 列表
 */
function nextIndex(list: Array<SlotItem>) {
  const index =
    list.length > 0 ? Math.max(...list.map((item) => item.index)) : 0;
  return index + 1;
}

function clearPreferencesAndLogout() {
  emit('clearPreferencesAndLogout');
}
</script>

<template>
  <template
    v-for="slot in leftSlots.filter((item) => item.index < REFERENCE_VALUE)"
    :key="slot.name"
  >
    <slot :name="slot.name">
      <template v-if="slot.name === 'refresh'">
        <VbenIconButton class="my-0 mr-1 rounded-md" @click="refresh">
          <RotateCw class="size-4" />
        </VbenIconButton>
      </template>
    </slot>
  </template>
  <div class="flex-center hidden lg:block">
    <slot name="breadcrumb"></slot>
  </div>
  <template
    v-for="slot in leftSlots.filter((item) => item.index > REFERENCE_VALUE)"
    :key="slot.name"
  >
    <slot :name="slot.name"></slot>
  </template>
  <div
    :class="`menu-align-${preferences.header.menuAlign}`"
    class="flex h-full min-w-0 flex-1 items-center"
  >
    <slot name="menu"></slot>
  </div>
  <div class="flex h-full min-w-0 shrink-0 items-center">
    <div class="header-actions-tray">
      <div
        v-if="utilitySlots.length > 0"
        aria-label="顶部快捷工具"
        class="header-actions-popover"
        role="group"
      >
        <template v-for="slot in utilitySlots" :key="slot.name">
          <slot :name="slot.name">
            <template v-if="slot.name === 'global-search'">
              <GlobalSearch
                :enable-shortcut-key="globalSearchShortcutKey"
                :menus="accessStore.accessMenus"
                class="header-tray-search"
              />
            </template>

            <template v-else-if="slot.name === 'preferences'">
              <PreferencesButton
                class="header-tray-action"
                @clear-preferences-and-logout="clearPreferencesAndLogout"
              />
            </template>
            <template v-else-if="slot.name === 'theme-toggle'">
              <ThemeToggle class="header-tray-action" />
            </template>
            <template v-else-if="slot.name === 'language-toggle'">
              <LanguageToggle class="header-tray-action" />
            </template>
            <template v-else-if="slot.name === 'fullscreen'">
              <VbenFullScreen class="header-tray-action" />
            </template>
            <template v-else-if="slot.name === 'timezone'">
              <TimezoneButton class="header-tray-action" />
            </template>
          </slot>
        </template>
      </div>
      <slot v-if="userDropdownSlot" :name="userDropdownSlot.name"></slot>
    </div>
  </div>
</template>
<style lang="scss" scoped>
.menu-align-start {
  --menu-align: start;
}

.menu-align-center {
  --menu-align: center;
}

.menu-align-end {
  --menu-align: end;
}

.header-actions-tray {
  position: relative;
  display: flex;
  align-items: center;
  height: 100%;
}

.header-actions-popover {
  position: absolute;
  top: 50%;
  right: calc(100% + 6px);
  display: flex;
  visibility: hidden;
  align-items: center;
  max-width: calc(100vw - 260px);
  height: 38px;
  padding: 4px 6px;
  color: hsl(var(--popover-foreground));
  pointer-events: none;
  background: hsl(var(--popover) / 96%);
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  box-shadow:
    0 10px 28px hsl(var(--background) / 45%),
    inset 0 1px 0 hsl(var(--foreground) / 6%);
  opacity: 0;
  transform: translateY(-50%) translateX(8px) scale(0.98);
  transform-origin: right center;
  transition:
    opacity 140ms ease,
    visibility 140ms ease,
    transform 140ms ease;
}

.header-actions-tray:hover .header-actions-popover,
.header-actions-tray:focus-within .header-actions-popover {
  visibility: visible;
  pointer-events: auto;
  opacity: 1;
  transform: translateY(-50%) translateX(0) scale(1);
}

.header-actions-popover::after {
  position: absolute;
  top: 0;
  right: -8px;
  width: 8px;
  height: 100%;
  content: '';
}

.header-actions-popover :deep(.header-tray-action) {
  margin: 0 2px;
}

.header-actions-popover :deep(.header-tray-search) {
  margin: 0 6px 0 0;
}
</style>
