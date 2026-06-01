import { requestClient } from '#/api/request';
import type { OcrReviewDetail } from '#/api/ocr-review';
import type {
  OcrTask,
  OcrTaskPage,
  OcrTaskPageParams,
} from '#/api/ocr-task';

export interface ManualKnowledgeDraft {
  chunks: string[];
  title?: string;
}

export function pageManualKnowledgeTasks(params: OcrTaskPageParams = {}) {
  const requestBody: {
    pageNum: number;
    pageSize: number;
    status?: OcrTask['status'];
  } = {
    pageNum: params.pageNum ?? 1,
    pageSize: params.pageSize ?? 20,
  };
  if (params.status) {
    requestBody.status = params.status;
  }
  return requestClient.post<OcrTaskPage>(
    '/ai/manual-knowledge/tasks/page',
    requestBody,
    {
      responseReturn: 'body',
    },
  );
}

export function createManualKnowledgeDraft(draft: ManualKnowledgeDraft) {
  return requestClient.post<OcrTask>('/ai/manual-knowledge/tasks', draft, {
    responseReturn: 'body',
  });
}

export function getManualKnowledgeTask(taskNo: string) {
  return requestClient.get<OcrReviewDetail>(
    `/ai/manual-knowledge/tasks/${taskNo}`,
    {
      responseReturn: 'body',
    },
  );
}

export function saveManualKnowledgeDraft(
  taskNo: string,
  draft: ManualKnowledgeDraft,
) {
  return requestClient.put<void>(
    `/ai/manual-knowledge/tasks/${taskNo}/draft`,
    draft,
    {
      responseReturn: 'body',
    },
  );
}

export function submitManualKnowledgeTask(
  taskNo: string,
  draft: ManualKnowledgeDraft,
) {
  return requestClient.post<void>(
    `/ai/manual-knowledge/tasks/${taskNo}/submit`,
    draft,
    {
      responseReturn: 'body',
    },
  );
}

export function deleteManualKnowledgeTask(taskNo: string) {
  return requestClient.post<void>(
    '/ai/manual-knowledge/tasks/delete',
    { taskNo },
    {
      responseReturn: 'body',
    },
  );
}
