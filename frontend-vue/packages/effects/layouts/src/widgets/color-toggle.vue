<script setup lang="ts">
import type { BuiltinThemeType } from '@vben/types';

import { Palette } from '@vben/icons';
import { $t } from '@vben/locales';
import {
  COLOR_PRESETS,
  preferences,
  updatePreferences,
} from '@vben/preferences';

import { VbenIconButton } from '@vben-core/shadcn-ui';

defineOptions({
  name: 'AuthenticationColorToggle',
});

function handleUpdate(colorPrimary: string, type: BuiltinThemeType) {
  updatePreferences({
    theme: {
      colorPrimary,
      builtinType: type,
    },
  });
}

function themeTypeLabel(type: BuiltinThemeType) {
  switch (type) {
    case 'default': {
      return $t('preferences.theme.builtin.default');
    }
    case 'green': {
      return $t('preferences.theme.builtin.green');
    }
    case 'pink': {
      return $t('preferences.theme.builtin.pink');
    }
    case 'sky-blue': {
      return $t('preferences.theme.builtin.skyBlue');
    }
    case 'violet': {
      return $t('preferences.theme.builtin.violet');
    }
    case 'yellow': {
      return $t('preferences.theme.builtin.yellow');
    }
    case 'zinc': {
      return $t('preferences.theme.builtin.zinc');
    }
    default: {
      return $t('preferences.theme.builtin.custom');
    }
  }
}

function colorToggleLabel(type: BuiltinThemeType) {
  return `切换为${themeTypeLabel(type)}主题色`;
}
</script>

<template>
  <div class="group relative flex items-center overflow-hidden">
    <div
      class="flex w-0 overflow-hidden transition-all duration-500 ease-out group-hover:w-60"
    >
      <template v-for="preset in COLOR_PRESETS" :key="preset.color">
        <VbenIconButton
          :tooltip="colorToggleLabel(preset.type)"
          class="flex-center shrink-0"
          @click="handleUpdate(preset.color, preset.type)"
        >
          <span class="sr-only">{{ colorToggleLabel(preset.type) }}</span>
          <div
            :style="{ backgroundColor: preset.color }"
            class="relative flex-center size-5 rounded-full hover:scale-110"
          >
            <svg
              v-if="preferences.theme.builtinType === preset.type"
              class="size-3.5 text-white"
              height="1em"
              viewBox="0 0 15 15"
              width="1em"
            >
              <path
                clip-rule="evenodd"
                d="M11.467 3.727c.289.189.37.576.181.865l-4.25 6.5a.625.625 0 0 1-.944.12l-2.75-2.5a.625.625 0 0 1 .841-.925l2.208 2.007l3.849-5.886a.625.625 0 0 1 .865-.181"
                fill="currentColor"
                fill-rule="evenodd"
              />
            </svg>
          </div>
        </VbenIconButton>
      </template>
    </div>

    <VbenIconButton :tooltip="$t('preferences.theme.builtin.title')">
      <span class="sr-only">{{ $t('preferences.theme.builtin.title') }}</span>
      <Palette class="size-4 text-primary" />
    </VbenIconButton>
  </div>
</template>
