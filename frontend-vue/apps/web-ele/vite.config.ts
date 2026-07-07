import { defineConfig } from '@vben/vite-config';
import { VitePWA } from 'vite-plugin-pwa';
import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
  const backendProxyTarget =
    process.env.BACKEND_PROXY_TARGET ?? 'http://localhost:8081';
  const apiContextPath = normalizeContextPath(
    process.env.VITE_GLOB_API_CONTEXT_PATH ?? '/finance-api',
  );
  const apiProxyPrefix = `${apiContextPath}/api`;

  return {
    application: {
      pwa: false,
    },
    vite: {
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
        VitePWA({
          injectRegister: 'auto',
          registerType: 'autoUpdate',
          manifest: {
            name: '理财投资AI',
            short_name: '投资AI',
            description: '理财投资AI知识库',
            background_color: '#ffffff',
            display: 'standalone',
            icons: [
              { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
              { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
            ],
            orientation: 'any',
            start_url: '/',
            theme_color: '#1a1a2e',
          },
          workbox: {
            globPatterns: ['**/*.{html,js,css,ico,png,svg,woff2}'],
          },
        }),
      ],
      server: {
        proxy: {
          [apiProxyPrefix]: {
            changeOrigin: true,
            target: backendProxyTarget,
            ws: true,
          },
        },
      },
    },
  };
});

function normalizeContextPath(contextPath: string) {
  let normalized = contextPath.trim();
  if (!normalized || normalized === '/') {
    return '';
  }
  if (!normalized.startsWith('/')) {
    normalized = `/${normalized}`;
  }
  return normalized.endsWith('/') ? normalized.slice(0, -1) : normalized;
}
