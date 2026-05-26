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

export function deleteOcrTask(taskNo: string) {
  return requestClient.post<void>(
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

  return requestClient.post<OcrTask[]>(
    '/ai/ocr/tasks',
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      responseReturn: 'body',
    },
  );
}
