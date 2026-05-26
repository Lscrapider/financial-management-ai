import { requestClient } from '#/api/request';

export interface OcrReviewSegmentRef {
  pageNo: number;
  segmentNo: number;
}

export interface OcrReviewWarning {
  confidence?: number;
  type: string;
}

export interface OcrReviewParagraph {
  avgConfidence: number;
  paragraphNo: number;
  sourcePages: number[];
  sourceSegments: OcrReviewSegmentRef[];
  text: string;
  warnings: OcrReviewWarning[];
}

export interface OcrReviewDraftContent {
  createdAt?: string;
  metrics?: {
    avgConfidence?: number;
    emptySegmentCount?: number;
    lowConfidenceParagraphCount?: number;
    paragraphCount?: number;
    warningCount?: number;
  };
  paragraphCount: number;
  paragraphs: OcrReviewParagraph[];
  sourceRef?: {
    bucket: string;
    objectKey: string;
    storageType: string;
  };
  taskNo: string;
}

export interface OcrReviewPage {
  imageRef: {
    bucket: string;
    objectKey: string;
    storageType: string;
  };
  imageUrl: string;
  pageNo: number;
}

export interface OcrReviewDetail {
  draftContent: OcrReviewDraftContent;
  overallConfidence: number;
  pages: OcrReviewPage[];
  paragraphCount: number;
  status: 'approved' | 'pending' | 'rejected' | 'saved';
  taskNo: string;
  warningCount: number;
}

export function getOcrReview(taskNo: string) {
  return requestClient.get<OcrReviewDetail>(`/ai/ocr/reviews/${taskNo}`, {
    responseReturn: 'body',
  });
}

export function saveOcrReviewDraft(
  taskNo: string,
  draftContent: OcrReviewDraftContent,
) {
  return requestClient.put<void>(
    `/ai/ocr/reviews/${taskNo}/draft`,
    { draftContent },
    {
      responseReturn: 'body',
    },
  );
}

export function submitOcrReview(
  taskNo: string,
  draftContent: OcrReviewDraftContent,
) {
  return requestClient.post<void>(
    `/ai/ocr/reviews/${taskNo}/submit`,
    { draftContent },
    {
      responseReturn: 'body',
    },
  );
}
