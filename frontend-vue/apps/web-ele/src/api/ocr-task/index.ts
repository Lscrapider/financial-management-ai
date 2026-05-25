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
  status: 'failed' | 'finished' | 'pending' | 'running';
  submittedAt: string;
  taskNo: string;
  updatedAt: string;
}

export function submitOcrTask(file: File) {
  return requestClient.upload<OcrTask>(
    '/ai/ocr/tasks',
    { file },
    {
      responseReturn: 'body',
    },
  );
}
