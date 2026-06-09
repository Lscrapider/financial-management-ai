import type { MenuRecordRaw } from '@vben/types';

type NavigationWorkspaceKey =
  | 'knowledge'
  | 'market'
  | 'research'
  | 'risk'
  | 'system'
  | 'workbench';

interface NavigationWorkspaceItem {
  label: string;
  path: string;
}

interface NavigationWorkspace {
  children: NavigationWorkspaceItem[];
  defaultPath: string;
  icon: string;
  key: NavigationWorkspaceKey;
  label: string;
  pathAliases?: Record<string, string>;
}

const NAVIGATION_WORKSPACES: NavigationWorkspace[] = [
  {
    children: [{ label: '投资工作台', path: '/investment-workbench' }],
    defaultPath: '/investment-workbench',
    icon: 'lucide:radar',
    key: 'workbench',
    label: '工作台',
  },
  {
    children: [
      { label: '股票行情', path: '/workspace' },
      { label: '指数行情', path: '/index-market' },
      { label: '可转债行情', path: '/bond-market' },
    ],
    defaultPath: '/workspace',
    icon: 'lucide:chart-candlestick',
    key: 'market',
    label: '行情',
  },
  {
    children: [
      { label: '投资观察池', path: '/watch-pool' },
      { label: '布控提醒', path: '/stock-alert' },
    ],
    defaultPath: '/watch-pool',
    icon: 'lucide:shield-alert',
    key: 'risk',
    label: '观察风控',
  },
  {
    children: [
      { label: '标的分析报告', path: '/ai-center/scene-reports' },
      { label: '报告工作台', path: '/ai-center/report-workspace' },
    ],
    defaultPath: '/ai-center/scene-reports',
    icon: 'lucide:file-text',
    key: 'research',
    label: '研究',
  },
  {
    children: [
      { label: '知识库概览', path: '/knowledge/overview' },
      { label: '知识库浏览', path: '/knowledge/base' },
      { label: '知识库OCR导入', path: '/ai-center/knowledge-processing' },
      { label: '知识库手动导入', path: '/ai-center/manual-knowledge' },
    ],
    defaultPath: '/knowledge/overview',
    icon: 'lucide:book-open',
    key: 'knowledge',
    label: '知识',
    pathAliases: {
      '/ai-center/ocr-review': '/ai-center/knowledge-processing',
    },
  },
  {
    children: [
      { label: '系统监控', path: '/analytics' },
      { label: 'Token 用量', path: '/token-usage' },
      { label: '标的配置', path: '/target-config' },
      { label: '数据同步', path: '/data-sync' },
    ],
    defaultPath: '/analytics',
    icon: 'lucide:settings',
    key: 'system',
    label: '系统',
  },
];

function getNavigationPath(path: string) {
  for (const workspace of NAVIGATION_WORKSPACES) {
    for (const [aliasPrefix, targetPath] of Object.entries(
      workspace.pathAliases ?? {},
    )) {
      if (path.startsWith(aliasPrefix)) {
        return targetPath;
      }
    }
  }
  return path;
}

function findNavigationWorkspace(
  path: string,
  workspaces: NavigationWorkspace[] = NAVIGATION_WORKSPACES,
) {
  const navigationPath = getNavigationPath(path);
  return workspaces.find((workspace) =>
    workspace.children.some((item) => item.path === navigationPath),
  );
}

function getNavigationSidebarMenus(
  workspaces: NavigationWorkspace[] = NAVIGATION_WORKSPACES,
): MenuRecordRaw[] {
  return workspaces.map((workspace) => {
    const sidebarMenu: MenuRecordRaw = {
      icon: workspace.icon,
      name: workspace.label,
      path:
        workspace.children.length > 1
          ? `/navigation-workspace/${workspace.key}`
          : workspace.defaultPath,
    };

    if (workspace.children.length > 1) {
      sidebarMenu.children = workspace.children.map((item) => ({
        name: item.label,
        path: item.path,
      }));
    }

    return sidebarMenu;
  });
}

export {
  findNavigationWorkspace,
  getNavigationPath,
  getNavigationSidebarMenus,
  NAVIGATION_WORKSPACES,
};

export type { NavigationWorkspace, NavigationWorkspaceItem };
