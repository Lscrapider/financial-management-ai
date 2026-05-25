import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:brain-circuit',
      order: 20,
      title: $t('page.aiCenter.title'),
    },
    name: 'AiCenter',
    path: '/ai-center',
    children: [
      {
        name: 'AiKnowledgeProcessing',
        path: '/ai-center/knowledge-processing',
        component: () => import('#/views/ai-center/index.vue'),
        meta: {
          icon: 'lucide:file-scan',
          title: $t('page.aiCenter.knowledgeProcessing'),
        },
      },
    ],
  },
];

export default routes;
