import { defineConfig } from '@vben/vite-config';
import { VitePWA } from 'vite-plugin-pwa';
import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
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
          '/api/stocks': {
            changeOrigin: true,
            target: 'http://localhost:8081',
            ws: true,
          },
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            target: 'http://localhost:8081/api',
            ws: true,
          },
        },
      },
    },
  };
});
