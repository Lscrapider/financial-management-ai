import { requestClient } from '#/api/request';

export type KnowledgeMaterialSearchMode = 'natural_language' | 'target';

export type KnowledgeMaterialStatus =
  | 'current_scenes_ready'
  | 'failed'
  | 'pending'
  | 'processing_current_scenes'
  | 'retrieving_knowledge'
  | 'success';

export interface KnowledgeMaterialSearchPayload {
  configProfile?: string;
  dailyKlineLimit?: number;
  monthlyKlineLimit?: number;
  queryText?: string;
  reportType?: string;
  searchMode: KnowledgeMaterialSearchMode;
  targetCode?: string;
  targetName?: string;
  targetType?: string;
  totalChunks: number;
  weeklyKlineLimit?: number;
  userOverrides?: Record<string, unknown>;
}

export interface KnowledgeMaterialSubmitResult {
  queryText?: null | string;
  rewrittenQuery?: null | string;
  searchMode: KnowledgeMaterialSearchMode;
  status: KnowledgeMaterialStatus;
  targetCode?: null | string;
  targetName?: null | string;
  targetType?: null | string;
  taskNo: string;
}

export interface KnowledgeMaterialChunk {
  chunkId: number;
  chunkIndex?: null | number;
  crossSceneScore?: null | number;
  filename?: null | string;
  finalScore?: null | number;
  matchedTags: string[];
  scene: string;
  semanticScore?: null | number;
  tagMatchScore?: null | number;
  taskNo: string;
  text: string;
}

export interface KnowledgeMaterialTask {
  chunks: KnowledgeMaterialChunk[];
  currentScenesPayload?: null | Record<string, unknown>;
  errorMessage?: null | string;
  finishedAt?: null | string;
  knowledgeContext?: null | Record<string, KnowledgeMaterialChunk[]>;
  queryText?: null | string;
  rewrittenQuery?: null | string;
  searchMode: KnowledgeMaterialSearchMode;
  status: KnowledgeMaterialStatus;
  submittedAt?: null | string;
  targetCode?: null | string;
  targetName?: null | string;
  targetType?: null | string;
  taskNo: string;
}

export function submitKnowledgeMaterialTask(
  payload: KnowledgeMaterialSearchPayload,
) {
  return requestClient.post<KnowledgeMaterialSubmitResult>(
    '/ai/knowledge-material/tasks',
    payload,
    { responseReturn: 'body' },
  );
}

export function getKnowledgeMaterialTask(taskNo: string) {
  return requestClient.get<KnowledgeMaterialTask>(
    `/ai/knowledge-material/tasks/${taskNo}`,
    { responseReturn: 'body' },
  );
}
