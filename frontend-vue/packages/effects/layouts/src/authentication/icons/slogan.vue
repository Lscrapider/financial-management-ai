<script setup lang="ts">
const appBaseUrl = (import.meta.env.BASE_URL || '/').replace(/\/?$/, '/');
const heroUrl = `${appBaseUrl}astraquant-ai-login-hero.png`;
</script>

<template>
  <svg
    v-bind="$attrs"
    role="img"
    viewBox="0 0 1200 1200"
    xmlns="http://www.w3.org/2000/svg"
  >
    <image
      height="1200"
      :href="heroUrl"
      preserveAspectRatio="xMidYMid meet"
      width="1200"
    />
  </svg>
</template>
