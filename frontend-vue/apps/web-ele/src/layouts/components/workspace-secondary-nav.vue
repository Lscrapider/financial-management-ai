<script setup lang="ts">
import type { NavigationWorkspace } from '../navigation-workspaces';

defineOptions({
  name: 'WorkspaceSecondaryNav',
});

defineProps<{
  activePath: string;
  workspace?: NavigationWorkspace;
}>();
</script>

<template>
  <nav
    v-if="workspace && workspace.children.length > 1"
    aria-label="模块二级导航"
    class="workspace-secondary-nav"
  >
    <div class="workspace-secondary-inner">
      <div class="workspace-secondary-title">{{ workspace.label }}</div>
      <div class="workspace-secondary-tabs">
        <RouterLink
          v-for="item in workspace.children"
          :key="item.path"
          :aria-current="item.path === activePath ? 'page' : undefined"
          class="workspace-secondary-tab"
          :class="{ 'is-active': item.path === activePath }"
          :to="item.path"
        >
          {{ item.label }}
        </RouterLink>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.workspace-secondary-nav {
  display: flex;
  align-items: center;
  height: 100%;
}

.workspace-secondary-inner {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 34px;
  padding: 0 8px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.workspace-secondary-title {
  flex-shrink: 0;
  padding-right: 10px;
  font-size: 13px;
  font-weight: 650;
  color: var(--el-text-color-primary);
  border-right: 1px solid var(--el-border-color-lighter);
}

.workspace-secondary-tabs {
  display: flex;
  gap: 4px;
  align-items: center;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.workspace-secondary-tabs::-webkit-scrollbar {
  display: none;
}

.workspace-secondary-tab {
  flex-shrink: 0;
  padding: 6px 9px;
  font-size: 13px;
  line-height: 1;
  color: var(--el-text-color-secondary);
  text-decoration: none;
  border: 1px solid transparent;
  border-radius: 6px;
  transition:
    color 0.16s ease,
    background-color 0.16s ease,
    border-color 0.16s ease;
}

.workspace-secondary-tab:hover {
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
}

.workspace-secondary-tab.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.workspace-secondary-tab:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
