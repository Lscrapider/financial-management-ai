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

export function listOcrTasks(limit = 50) {
  return requestClient.get<OcrTask[]>('/ai/ocr/tasks', {
    params: { limit },
    responseReturn: 'body',
  });
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
