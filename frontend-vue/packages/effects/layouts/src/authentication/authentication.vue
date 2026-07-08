<script setup lang="ts">
import type { ToolbarType } from './types';

import { computed } from 'vue';

import { preferences, usePreferences } from '@vben/preferences';

import { Copyright } from '../basic/copyright';
import AuthenticationFormView from './form.vue';
import SloganIcon from './icons/slogan.vue';
import Toolbar from './toolbar.vue';

interface Props {
  appName?: string;
  logo?: string;
  logoDark?: string;
  pageTitle?: string;
  pageDescription?: string;
  sloganImage?: string;
  toolbar?: boolean;
  copyright?: boolean;
  toolbarList?: ToolbarType[];
  clickLogo?: () => void;
}

const props = withDefaults(defineProps<Props>(), {
  appName: '',
  copyright: true,
  logo: '',
  logoDark: '',
  pageDescription: '',
  pageTitle: '',
  sloganImage: '',
  toolbar: true,
  toolbarList: () => ['color', 'language', 'layout', 'theme'],
  clickLogo: () => {},
});

const { authPanelCenter, authPanelLeft, authPanelRight, isDark } =
  usePreferences();

/**
 * @zh_CN 根据主题选择合适的 logo 图标
 */
const logoSrc = computed(() => {
  // 如果是暗色主题且提供了 logoDark，则使用暗色主题的 logo
  if (isDark.value && props.logoDark) {
    return props.logoDark;
  }
  // 否则使用默认的 logo
  return props.logo;
});
</script>

<template>
  <div
    :class="[isDark ? 'dark' : '']"
    class="flex min-h-full flex-1 overflow-x-hidden select-none"
  >
    <template v-if="toolbar">
      <slot name="toolbar">
        <Toolbar :toolbar-list="toolbarList" />
      </slot>
    </template>
    <!-- 左侧认证面板 -->
    <AuthenticationFormView
      v-if="authPanelLeft"
      class="min-h-full w-2/5 flex-1"
      data-side="left"
    >
      <template v-if="copyright" #copyright>
        <slot name="copyright">
          <Copyright
            v-if="preferences.copyright.enable"
            v-bind="preferences.copyright"
          />
        </slot>
      </template>
    </AuthenticationFormView>

    <slot name="logo">
      <!-- 头部 Logo 和应用名称 -->
      <div
        v-if="logoSrc || appName"
        class="absolute top-0 left-0 z-10 flex flex-1"
        @click="clickLogo"
      >
        <div
          class="mt-4 ml-4 flex flex-1 items-center text-foreground sm:top-6 sm:left-6 lg:text-foreground"
        >
          <img
            v-if="logoSrc"
            :key="logoSrc"
            :alt="appName"
            :src="logoSrc"
            class="mr-2"
            width="42"
          />
          <p v-if="appName" class="m-0 text-xl font-medium">
            {{ appName }}
          </p>
        </div>
      </div>
    </slot>

    <!-- 系统介绍 -->
    <div v-if="!authPanelCenter" class="relative hidden w-0 flex-1 lg:block">
      <div
        class="auth-hero absolute inset-0 size-full overflow-hidden bg-background-deep dark:bg-[#070709]"
      >
        <div class="login-background absolute top-0 left-0 size-full"></div>
        <div aria-hidden="true" class="market-signal-stage">
          <div class="signal-grid"></div>
          <div class="signal-orbit signal-orbit-outer"></div>
          <div class="signal-orbit signal-orbit-inner"></div>
          <div class="signal-scan"></div>
          <div class="signal-line signal-line-up"></div>
          <div class="signal-line signal-line-down"></div>
          <div class="signal-node signal-node-primary"></div>
          <div class="signal-node signal-node-success"></div>
          <div class="signal-node signal-node-warning"></div>
          <div class="signal-node signal-node-danger"></div>
          <div class="signal-card signal-card-left">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div class="signal-card signal-card-right">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div class="signal-ticker signal-ticker-top">
            <span></span>
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div class="signal-ticker signal-ticker-bottom">
            <span></span>
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
        <div
          :key="authPanelLeft ? 'left' : authPanelRight ? 'right' : 'center'"
          class="relative z-[1] mr-20 flex-col-center h-full"
          :class="{
            'enter-x': authPanelLeft,
            '-enter-x': authPanelRight,
          }"
        >
          <template v-if="sloganImage">
            <img
              :alt="appName"
              :src="sloganImage"
              class="auth-slogan h-80 w-1/2 max-w-[560px] animate-float xl:h-96 xl:w-3/5"
            />
          </template>
          <SloganIcon
            v-else
            :alt="appName"
            class="auth-slogan h-80 w-1/2 max-w-[560px] animate-float xl:h-96 xl:w-3/5"
          />
          <div class="text-1xl mt-6 font-sans text-foreground lg:text-2xl">
            {{ pageTitle }}
          </div>
          <div class="mt-2 dark:text-muted-foreground">
            {{ pageDescription }}
          </div>
        </div>
      </div>
    </div>

    <!-- 中心认证面板 -->
    <div v-if="authPanelCenter" class="relative flex-center w-full">
      <div class="login-background absolute top-0 left-0 size-full"></div>
      <AuthenticationFormView
        class="w-full rounded-3xl pb-20 shadow-float shadow-primary/5 md:w-2/3 md:bg-background lg:w-1/2 xl:w-[36%]"
        data-side="bottom"
      >
        <template v-if="copyright" #copyright>
          <slot name="copyright">
            <Copyright
              v-if="preferences.copyright.enable"
              v-bind="preferences.copyright"
            />
          </slot>
        </template>
      </AuthenticationFormView>
    </div>

    <!-- 右侧认证面板 -->
    <AuthenticationFormView
      v-if="authPanelRight"
      class="min-h-full w-2/5 flex-1"
      data-side="right"
    >
      <template v-if="copyright" #copyright>
        <slot name="copyright">
          <Copyright
            v-if="preferences.copyright.enable"
            v-bind="preferences.copyright"
          />
        </slot>
      </template>
    </AuthenticationFormView>
  </div>
</template>

<style scoped>
.auth-hero {
  isolation: isolate;
}

.login-background {
  background: linear-gradient(
    154deg,
    #07070915 30%,
    hsl(var(--primary) / 30%) 48%,
    #07070915 64%
  );
  filter: blur(100px);
}

.market-signal-stage {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.signal-grid {
  position: absolute;
  inset: -16% -8%;
  background-image:
    linear-gradient(rgb(255 255 255 / 5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 5%) 1px, transparent 1px);
  background-size: 56px 56px;
  opacity: 0.52;
  mask-image: radial-gradient(
    ellipse at 48% 49%,
    rgb(0 0 0 / 90%) 0%,
    rgb(0 0 0 / 42%) 40%,
    transparent 72%
  );
  transform: perspective(820px) rotateX(58deg) rotateZ(-14deg)
    translate3d(-4%, 4%, 0);
  transform-origin: center;
}

.market-signal-stage::before {
  position: absolute;
  inset: 12% 10% 10% 2%;
  content: '';
  background:
    linear-gradient(
      90deg,
      transparent 0 38%,
      rgb(87 209 136 / 20%) 38% 39%,
      transparent 39%
    ),
    linear-gradient(
      120deg,
      transparent 0 45%,
      rgb(239 189 72 / 16%) 45% 46%,
      transparent 46%
    ),
    linear-gradient(
      154deg,
      transparent 0 52%,
      hsl(var(--primary) / 30%) 52% 53%,
      transparent 53%
    );
  opacity: 0.7;
  filter: blur(0.2px);
  mask-image: radial-gradient(
    ellipse at 48% 48%,
    rgb(0 0 0 / 84%) 0%,
    transparent 70%
  );
}

.market-signal-stage::after {
  position: absolute;
  inset: 18% 13% 16% 8%;
  content: '';
  border: 1px solid rgb(255 255 255 / 7%);
  border-radius: 18px;
  box-shadow: inset 0 0 0 1px hsl(var(--primary) / 9%);
  transform: perspective(860px) rotateX(58deg) rotateZ(-14deg)
    translate3d(-3%, 5%, 0);
}

.signal-orbit {
  position: absolute;
  top: 51%;
  left: 45%;
  border: 1px solid hsl(var(--primary) / 22%);
  border-radius: 999px;
  transform: translate(-50%, -50%) rotate(-12deg);
}

.signal-orbit::before,
.signal-orbit::after {
  position: absolute;
  width: 6px;
  height: 6px;
  content: '';
  background: #57d188;
  border-radius: 2px;
  box-shadow: 0 0 16px rgb(87 209 136 / 55%);
}

.signal-orbit::before {
  top: 14%;
  right: 18%;
}

.signal-orbit::after {
  bottom: 18%;
  left: 16%;
  background: #efbd48;
  box-shadow: 0 0 16px rgb(239 189 72 / 50%);
}

.signal-orbit-outer {
  width: min(48vw, 640px);
  height: min(24vw, 320px);
  animation: signal-drift 12s ease-in-out infinite;
}

.signal-orbit-inner {
  width: min(32vw, 430px);
  height: min(16vw, 220px);
  border-color: rgb(87 209 136 / 18%);
  animation: signal-drift 9s ease-in-out infinite reverse;
}

.signal-scan {
  position: absolute;
  top: 22%;
  left: 45%;
  width: min(40vw, 540px);
  height: min(40vw, 540px);
  background: conic-gradient(
    from 210deg,
    transparent 0 62%,
    hsl(var(--primary) / 22%) 70%,
    rgb(87 209 136 / 18%) 76%,
    transparent 84% 100%
  );
  border-radius: 50%;
  opacity: 0.72;
  filter: blur(0.4px);
  mask-image: radial-gradient(
    circle,
    transparent 0 28%,
    rgb(0 0 0 / 86%) 29% 54%,
    transparent 72%
  );
  transform: translateX(-50%) rotate(-18deg);
  animation: signal-scan 8s linear infinite;
}

.signal-line {
  position: absolute;
  left: 17%;
  width: min(38vw, 540px);
  height: 72px;
  border: 1px solid transparent;
  border-radius: 16px;
  opacity: 0.78;
}

.signal-line::before {
  position: absolute;
  inset: 0;
  content: '';
  background: currentcolor;
  clip-path: polygon(
    0 70%,
    14% 52%,
    25% 60%,
    39% 25%,
    52% 42%,
    65% 18%,
    82% 34%,
    100% 12%,
    100% 18%,
    82% 40%,
    66% 24%,
    53% 49%,
    40% 31%,
    26% 67%,
    14% 58%,
    0 76%
  );
}

.signal-line-up {
  top: 34%;
  color: rgb(87 209 136 / 58%);
  transform: rotate(-11deg);
  animation: signal-line-breathe 5.8s ease-in-out infinite;
}

.signal-line-down {
  top: 58%;
  color: rgb(220 68 70 / 46%);
  transform: rotate(-11deg) scaleX(0.82);
  transform-origin: left center;
  animation: signal-line-breathe 6.6s ease-in-out infinite reverse;
}

.signal-node {
  position: absolute;
  width: 10px;
  height: 10px;
  border: 1px solid rgb(255 255 255 / 58%);
  border-radius: 3px;
  transform: rotate(45deg);
  animation: signal-node-pulse 3.6s ease-in-out infinite;
}

.signal-node::after {
  position: absolute;
  inset: -12px;
  content: '';
  border: 1px solid currentcolor;
  border-radius: 6px;
  opacity: 0;
  animation: signal-node-ring 3.6s ease-out infinite;
}

.signal-node-primary {
  top: 41%;
  left: 49%;
  color: hsl(var(--primary) / 68%);
  background: hsl(var(--primary) / 82%);
}

.signal-node-success {
  top: 32%;
  left: 33%;
  color: rgb(87 209 136 / 70%);
  background: #57d188;
  animation-delay: 0.5s;
}

.signal-node-warning {
  top: 61%;
  left: 57%;
  color: rgb(239 189 72 / 68%);
  background: #efbd48;
  animation-delay: 1.1s;
}

.signal-node-danger {
  top: 54%;
  left: 25%;
  color: rgb(220 68 70 / 64%);
  background: #dc4446;
  animation-delay: 1.7s;
}

.signal-card {
  position: absolute;
  display: grid;
  gap: 9px;
  width: 148px;
  padding: 14px;
  background: rgb(20 22 26 / 56%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 10px;
  box-shadow: 0 8px 20px rgb(0 0 0 / 18%);
  backdrop-filter: blur(10px);
}

.signal-card span,
.signal-ticker span {
  display: block;
  height: 5px;
  background: rgb(242 242 242 / 46%);
  border-radius: 999px;
}

.signal-card span:nth-child(1) {
  width: 72%;
}

.signal-card span:nth-child(2) {
  width: 100%;
  background: hsl(var(--primary) / 48%);
}

.signal-card span:nth-child(3) {
  width: 46%;
  background: rgb(87 209 136 / 50%);
}

.signal-card-left {
  top: 35%;
  left: 12%;
  transform: perspective(600px) rotateY(20deg) rotateZ(-2deg);
  animation: signal-card-float 7s ease-in-out infinite;
}

.signal-card-right {
  top: 27%;
  right: 18%;
  transform: perspective(600px) rotateY(-24deg) rotateZ(3deg);
  animation: signal-card-float 8s ease-in-out infinite reverse;
}

.signal-ticker {
  position: absolute;
  display: flex;
  gap: 18px;
  width: min(38vw, 520px);
  padding: 10px 12px;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 7%);
  border-radius: 8px;
  opacity: 0.56;
  transform: rotate(-11deg);
}

.signal-ticker span {
  flex: 0 0 86px;
  height: 4px;
  background: rgb(242 242 242 / 26%);
}

.signal-ticker span:nth-child(2) {
  background: rgb(87 209 136 / 48%);
}

.signal-ticker span:nth-child(3) {
  background: rgb(239 189 72 / 46%);
}

.signal-ticker-top {
  top: 20%;
  left: 20%;
  animation: signal-ticker 14s linear infinite;
}

.signal-ticker-bottom {
  bottom: 20%;
  left: 9%;
  animation: signal-ticker 18s linear infinite reverse;
}

.auth-slogan {
  position: relative;
  z-index: 1;
  filter: drop-shadow(0 22px 36px rgb(0 0 0 / 38%));
}

@keyframes signal-drift {
  0%,
  100% {
    transform: translate(-50%, -50%) rotate(-12deg) scale(1);
  }

  50% {
    transform: translate(-48%, -52%) rotate(-9deg) scale(1.03);
  }
}

@keyframes signal-scan {
  to {
    transform: translateX(-50%) rotate(342deg);
  }
}

@keyframes signal-line-breathe {
  0%,
  100% {
    opacity: 0.46;
  }

  50% {
    opacity: 0.9;
  }
}

@keyframes signal-node-pulse {
  0%,
  100% {
    filter: brightness(0.9);
  }

  50% {
    filter: brightness(1.28);
  }
}

@keyframes signal-node-ring {
  0% {
    opacity: 0.46;
    transform: scale(0.4);
  }

  100% {
    opacity: 0;
    transform: scale(1.28);
  }
}

@keyframes signal-card-float {
  0%,
  100% {
    translate: 0 0;
  }

  50% {
    translate: 0 -10px;
  }
}

@keyframes signal-ticker {
  0% {
    translate: -3% 0;
  }

  100% {
    translate: 8% 0;
  }
}

.dark {
  .login-background {
    background: linear-gradient(
      154deg,
      #07070915 30%,
      hsl(var(--primary) / 20%) 48%,
      #07070915 64%
    );
    filter: blur(100px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .signal-orbit,
  .signal-scan,
  .signal-line-up,
  .signal-line-down,
  .signal-node,
  .signal-node::after,
  .signal-card,
  .signal-ticker {
    animation: none;
  }
}
</style>
