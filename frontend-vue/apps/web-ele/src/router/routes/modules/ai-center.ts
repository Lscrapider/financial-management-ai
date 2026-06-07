import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:sparkles',
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
          authority: ['admin'],
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
          authority: ['admin'],
          hideInMenu: true,
          icon: 'lucide:file-check-2',
          title: $t('page.aiCenter.ocrReview'),
        },
      },
      {
        name: 'AiManualKnowledge',
        path: '/ai-center/manual-knowledge',
        component: () => import('#/views/ai-center/manual-knowledge/index.vue'),
        meta: {
          authority: ['admin'],
          icon: 'lucide:file-plus-2',
          title: $t('page.aiCenter.manualKnowledge'),
        },
      },
      {
        name: 'AiSceneReports',
        path: '/ai-center/scene-reports',
        component: () => import('#/views/ai-center/scene-reports/index.vue'),
        meta: {
          icon: 'lucide:file-text',
          title: $t('page.aiCenter.sceneReports'),
        },
      },
      {
        name: 'AiReportWorkspace',
        path: '/ai-center/report-workspace',
        component: () => import('#/views/ai-center/report-workspace/index.vue'),
        meta: {
          icon: 'lucide:layout-panel-top',
          title: $t('page.aiCenter.reportWorkspace'),
        },
      },
    ],
  },
];

export default routes;
