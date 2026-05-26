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
      {
        name: 'AiOcrReview',
        path: '/ai-center/ocr-review/:taskNo?',
        component: () => import('#/views/ai-center/ocr-review/index.vue'),
        meta: {
          activePath: '/ai-center/knowledge-processing',
          hideInMenu: true,
          icon: 'lucide:file-check-2',
          title: $t('page.aiCenter.ocrReview'),
        },
      },
    ],
  },
];

export default routes;
