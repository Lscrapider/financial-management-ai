import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:book-open',
      order: 10,
      title: $t('page.knowledge.title'),
    },
    name: 'Knowledge',
    path: '/knowledge',
    children: [
      {
        name: 'KnowledgeBase',
        path: '/knowledge/base',
        component: () => import('#/views/knowledge/index.vue'),
        meta: {
          icon: 'lucide:database',
          title: $t('page.knowledge.base'),
        },
      },
      {
        name: 'KnowledgeOverview',
        path: '/knowledge/overview',
        component: () => import('#/views/knowledge/overview.vue'),
        meta: {
          icon: 'lucide:bar-chart-3',
          title: $t('page.knowledge.overview'),
        },
      },
    ],
  },
];

export default routes;
