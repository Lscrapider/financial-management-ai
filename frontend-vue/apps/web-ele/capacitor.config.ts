import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.scrapider.finance.ai',
  appName: '理财投资AI',
  webDir: 'dist',
  server: {
    // 开发时热加载 Vite dev server；生产构建时注释掉这行
    // url: 'http://192.168.0.104:5777',
    cleartext: true,
  },
  android: {
    allowMixedContent: true,
  },
};

export default config;
