import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      authority: ['admin'],
      icon: 'lucide:book-open',
      order: 10,
      title: $t('page.knowledge.title'),
    },
    name: 'Knowledge',
    path: '/knowledge',
    children: [
      {
        name: 'KnowledgeOverview',
        path: '/knowledge/overview',
        component: () => import('#/views/knowledge/overview.vue'),
        meta: {
          icon: 'lucide:bar-chart-3',
          title: $t('page.knowledge.overview'),
        },
      },
      {
        name: 'KnowledgeMaterials',
        path: '/knowledge/materials',
        component: () => import('#/views/knowledge/materials.vue'),
        meta: {
          icon: 'lucide:search-check',
          title: $t('page.knowledge.materials'),
        },
      },
      {
        name: 'KnowledgeBase',
        path: '/knowledge/base',
        component: () => import('#/views/knowledge/index.vue'),
        meta: {
          icon: 'lucide:database',
          title: $t('page.knowledge.base'),
        },
      },
    ],
  },
];

export default routes;
