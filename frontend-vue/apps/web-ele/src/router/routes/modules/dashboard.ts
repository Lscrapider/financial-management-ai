import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:layout-dashboard',
      order: -1,
      title: $t('page.dashboard.title'),
    },
    name: 'Dashboard',
    path: '/dashboard',
    children: [
      {
        name: 'Workspace',
        path: '/workspace',
        component: () => import('#/views/dashboard/workspace/index.vue'),
        meta: {
          icon: 'carbon:workspace',
          title: $t('page.dashboard.workspace'),
        },
      },
      {
        name: 'IndexMarket',
        path: '/index-market',
        component: () => import('#/views/dashboard/index-market/index.vue'),
        meta: {
          icon: 'lucide:chart-candlestick',
          title: $t('page.dashboard.indexMarket'),
        },
      },
      {
        name: 'BondMarket',
        path: '/bond-market',
        component: () => import('#/views/dashboard/bond-market/index.vue'),
        meta: {
          icon: 'lucide:landmark',
          title: $t('page.dashboard.bondMarket'),
        },
      },
      {
        name: 'WatchPool',
        path: '/watch-pool',
        component: () => import('#/views/dashboard/watch-pool/index.vue'),
        meta: {
          icon: 'lucide:spool',
          title: $t('page.dashboard.watchPool'),
        },
      },
      {
        name: 'StockAlert',
        path: '/stock-alert',
        component: () => import('#/views/dashboard/stock-alert/index.vue'),
        meta: {
          icon: 'lucide:bell-ring',
          title: $t('page.dashboard.stockAlert'),
        },
      },
    ],
  },
  {
    meta: {
      icon: 'lucide:settings',
      order: 100,
      title: $t('page.systemConfig.title'),
    },
    name: 'SystemConfig',
    path: '/system-config',
    children: [
      {
        name: 'Analytics',
        path: '/analytics',
        component: () => import('#/views/dashboard/analytics/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:area-chart',
          title: $t('page.dashboard.analytics'),
        },
      },
      {
        name: 'DataSync',
        path: '/data-sync',
        component: () => import('#/views/system-config/data-sync/index.vue'),
        meta: {
          icon: 'lucide:refresh-cw',
          title: $t('page.systemConfig.dataSync'),
        },
      },
    ],
  },
];

export default routes;
