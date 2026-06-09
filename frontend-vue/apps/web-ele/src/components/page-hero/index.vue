<script lang="ts" setup>
defineProps<{
  description?: string;
  title: string;
}>();
</script>

<template>
  <section class="page-hero">
    <div class="page-hero-copy">
      <div class="page-title">{{ title }}</div>
      <div v-if="description" class="page-meta">{{ description }}</div>
    </div>
    <div v-if="$slots.actions" class="hero-actions">
      <slot name="actions"></slot>
    </div>
  </section>
</template>

<style scoped>
.page-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.page-hero-copy {
  min-width: 0;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.page-meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
}

.hero-actions :deep(.el-button span) {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

@media (max-width: 768px) {
  .page-hero {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .hero-actions {
    justify-content: flex-start;
  }
}
</style>
