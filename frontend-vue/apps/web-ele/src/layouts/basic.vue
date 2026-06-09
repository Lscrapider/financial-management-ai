<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';
import type { MenuRecordRaw } from '@vben/types';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import { useWatermark } from '@vben/hooks';
import {
  BasicLayout,
  LockScreen,
  Notification,
  UserDropdown,
} from '@vben/layouts';
import { preferences, usePreferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import { $t } from '#/locales';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';
import AiChatDrawer from '#/widgets/ai-chat/ai-chat-drawer.vue';

import WorkspaceSecondaryNav from './components/workspace-secondary-nav.vue';
import {
  findNavigationWorkspace,
  getNavigationPath,
  getNavigationSidebarMenus,
  NAVIGATION_WORKSPACES,
} from './navigation-workspaces';

const notifications = ref<NotificationItem[]>([]);

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();
const { isDark } = usePreferences();
const showDot = computed(() =>
  notifications.value.some((item) => !item.isRead),
);
const navigationRoutePath = computed(
  () => (route.meta.activePath as string | undefined) ?? route.path,
);
const navigationActivePath = computed(() =>
  getNavigationPath(navigationRoutePath.value),
);
const accessibleMenuPaths = computed(() =>
  collectMenuPaths(accessStore.accessMenus),
);
const visibleNavigationWorkspaces = computed(() =>
  NAVIGATION_WORKSPACES.map((workspace) => ({
    ...workspace,
    children: workspace.children.filter((item) =>
      accessibleMenuPaths.value.has(item.path),
    ),
  })).filter((workspace) => workspace.children.length > 0),
);
const currentNavigationWorkspace = computed(() =>
  findNavigationWorkspace(
    navigationRoutePath.value,
    visibleNavigationWorkspaces.value,
  ),
);
const navigationSidebarMenus = computed(() =>
  getNavigationSidebarMenus(visibleNavigationWorkspaces.value),
);
const navigationSidebarActive = computed(
  () => navigationActivePath.value,
);

const menus = computed(() => [
  {
    handler: () => {
      router.push({ name: 'Profile' });
    },
    icon: 'lucide:user',
    text: $t('page.auth.profile'),
  },
]);

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

async function handleLogout() {
  await authStore.logout(false);
}

function handleNoticeClear() {
  notifications.value = [];
}

function markRead(id: number | string) {
  const item = notifications.value.find((item) => item.id === id);
  if (item) {
    item.isRead = true;
  }
}

function remove(id: number | string) {
  notifications.value = notifications.value.filter((item) => item.id !== id);
}

function handleMakeAll() {
  notifications.value.forEach((item) => (item.isRead = true));
}

const viewAll = () => {};

const handleClick = (item: NotificationItem) => {
  // 如果通知项有链接，点击时跳转
  if (item.link) {
    navigateTo(item.link, item.query, item.state);
  }
};

function navigateTo(
  link: string,
  query?: Record<string, any>,
  state?: Record<string, any>,
) {
  if (link.startsWith('http://') || link.startsWith('https://')) {
    // 外部链接，在新标签页打开
    window.open(link, '_blank');
  } else {
    // 内部路由链接，支持 query 参数和 state
    router.push({
      path: link,
      query: query || {},
      state,
    });
  }
}

function collectMenuPaths(menus: MenuRecordRaw[]) {
  const paths = new Set<string>();
  const walk = (items: MenuRecordRaw[]) => {
    for (const item of items) {
      paths.add(item.path);
      if (item.children?.length) {
        walk(item.children);
      }
    }
  };
  walk(menus);
  return paths;
}

watch(
  () => ({
    enable: preferences.app.watermark,
    content: preferences.app.watermarkContent,
    isDark: isDark.value,
  }),
  async ({ enable, content, isDark: isDarkValue }) => {
    if (enable) {
      const watermarkColor = isDarkValue
        ? 'rgba(255, 255, 255, 0.12)'
        : 'rgba(0, 0, 0, 0.12)';

      await updateWatermark({
        advancedStyle: {
          colorStops: [
            {
              color: watermarkColor,
              offset: 0,
            },
            {
              color: watermarkColor,
              offset: 1,
            },
          ],
          type: 'linear',
        },
        content:
          content ||
          `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <BasicLayout
    :navigation-sidebar-active="navigationSidebarActive"
    :navigation-sidebar-menus="navigationSidebarMenus"
    @clear-preferences-and-logout="handleLogout"
  >
    <template #logo-text>
      <span class="sr-only">{{ preferences.app.name }}</span>
    </template>
    <template #header-left-120>
      <WorkspaceSecondaryNav
        :active-path="navigationActivePath"
        :workspace="currentNavigationWorkspace"
      />
    </template>
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :menus
        :text="userStore.userInfo?.realName"
        @logout="handleLogout"
        @clear-preferences-and-logout="handleLogout"
      />
    </template>
    <template #notification>
      <Notification
        :dot="showDot"
        :notifications="notifications"
        @clear="handleNoticeClear"
        @read="(item) => item.id && markRead(item.id)"
        @remove="(item) => item.id && remove(item.id)"
        @make-all="handleMakeAll"
        @on-click="handleClick"
        @view-all="viewAll"
      />
    </template>
    <template #extra>
      <AiChatDrawer />
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>
