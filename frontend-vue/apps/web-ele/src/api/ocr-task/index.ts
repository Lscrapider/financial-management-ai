import { requestClient } from '#/api/request';

export interface OcrTask {
  currentStage: string;
  fileSize: number;
  fileType: string;
  id: number;
  originalFilename: string;
  pageCount: number;
  progress: number;
  segmentCount: number;
  sourceType: 'manual_text' | 'ocr' | string;
  status:
    | 'failed'
    | 'finished'
    | 'manual_review_required'
    | 'ready'
    | 'running';
  submittedAt: string;
  taskNo: string;
  updatedAt: string;
}

export type OcrTaskStatus = OcrTask['status'];

export interface OcrTaskPage {
  pageNum: number;
  pageSize: number;
  pages: number;
  records: OcrTask[];
  total: number;
}

export interface OcrTaskPageParams {
  pageNum?: number;
  pageSize?: number;
  status?: OcrTaskStatus;
}

export interface OcrChunkTagChunk {
  chunkId: string;
  chunkIndex: number;
  currentStage: string;
  deleted: boolean;
  errorMessage?: null | string;
  needLlm: boolean;
  pageNos: number[];
  paragraphNos: number[];
  scenes?: Record<string, string[]>;
  status: string;
  text: string;
}

export interface OcrChunkTagDetail {
  chunks: OcrChunkTagChunk[];
  deletedChunkCount: number;
  failedChunkCount: number;
  finishedChunkCount: number;
  llmChunkCount: number;
  pendingChunkCount: number;
  ruleOnlyChunkCount: number;
  taskNo: string;
  totalChunkCount: number;
}

export interface OcrStageItem {
  attemptCount: number;
  errorMessage?: null | string;
  finishedAt?: null | string;
  inputRef?: Record<string, unknown>;
  maxAttempts: number;
  metrics?: Record<string, unknown>;
  outputRef?: Record<string, unknown>;
  stage: string;
  startedAt?: null | string;
  status: string;
  updatedAt?: null | string;
}

export interface OcrStageDetail {
  stages: OcrStageItem[];
  taskNo: string;
}

export function pageOcrTasks(params: OcrTaskPageParams = {}) {
  const requestBody: {
    pageNum: number;
    pageSize: number;
    status?: OcrTaskStatus;
  } = {
    pageNum: params.pageNum ?? 1,
    pageSize: params.pageSize ?? 20,
  };
  if (params.status) {
    requestBody.status = params.status;
  }
  return requestClient.post<OcrTaskPage>('/ai/ocr/tasks/page', requestBody, {
    responseReturn: 'body',
  });
}

export function getOcrStageDetail(taskNo: string) {
  return requestClient.get<OcrStageDetail>(`/ai/ocr/tasks/${taskNo}/stages`, {
    responseReturn: 'body',
  });
}

export function getOcrChunkTagDetail(taskNo: string) {
  return requestClient.get<OcrChunkTagDetail>(
    `/ai/ocr/tasks/${taskNo}/chunk-tags`,
    {
      responseReturn: 'body',
    },
  );
}

export function deleteOcrTask(taskNo: string) {
  return requestClient.post<unknown>(
    '/ai/ocr/tasks/delete',
    { taskNo },
    {
      responseReturn: 'body',
    },
  );
}

export function submitOcrTask(files: File[]) {
  const formData = new FormData();
  files.forEach((file) => {
    formData.append('file', file);
  });

  return requestClient.post<OcrTask[]>('/ai/ocr/tasks', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    responseReturn: 'body',
  });
}
