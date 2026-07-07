import type {
  ApplicationConfig,
  VbenAdminProAppConfigRaw,
} from '@vben/types/global';

const DEFAULT_CONTEXT_PATH = '/finance';
const DEFAULT_API_CONTEXT_PATH = '/finance-api';

/**
 * 由 vite-inject-app-config 注入的全局配置
 */
export function useAppConfig(
  env: Record<string, any>,
  isProduction: boolean,
): ApplicationConfig {
  // 生产环境下，直接使用 window._VBEN_ADMIN_PRO_APP_CONF_ 全局变量
  const config = isProduction
    ? window._VBEN_ADMIN_PRO_APP_CONF_
    : (env as VbenAdminProAppConfigRaw);

  const {
    VITE_GLOB_API_CONTEXT_PATH,
    VITE_GLOB_API_URL,
    VITE_GLOB_AUTH_DINGDING_CORP_ID,
    VITE_GLOB_AUTH_DINGDING_CLIENT_ID,
    VITE_GLOB_CONTEXT_PATH,
  } = config;
  const contextPath = normalizeContextPath(
    VITE_GLOB_CONTEXT_PATH || DEFAULT_CONTEXT_PATH,
  );
  const apiContextPath = normalizeContextPath(
    VITE_GLOB_API_CONTEXT_PATH || DEFAULT_API_CONTEXT_PATH,
  );

  const applicationConfig: ApplicationConfig = {
    apiContextPath,
    apiURL: VITE_GLOB_API_URL || joinPath(apiContextPath, '/api'),
    auth: {},
    contextPath,
  };
  if (VITE_GLOB_AUTH_DINGDING_CORP_ID && VITE_GLOB_AUTH_DINGDING_CLIENT_ID) {
    applicationConfig.auth.dingding = {
      clientId: VITE_GLOB_AUTH_DINGDING_CLIENT_ID,
      corpId: VITE_GLOB_AUTH_DINGDING_CORP_ID,
    };
  }

  return applicationConfig;
}

function normalizeContextPath(contextPath: string): string {
  let normalized = contextPath.trim();
  if (!normalized || normalized === '/') {
    return '';
  }
  if (!normalized.startsWith('/')) {
    normalized = `/${normalized}`;
  }
  return normalized.endsWith('/') ? normalized.slice(0, -1) : normalized;
}

function joinPath(prefix: string, suffix: string): string {
  if (!prefix) {
    return suffix.startsWith('/') ? suffix : `/${suffix}`;
  }
  return `${prefix}${suffix.startsWith('/') ? suffix : `/${suffix}`}`;
}
