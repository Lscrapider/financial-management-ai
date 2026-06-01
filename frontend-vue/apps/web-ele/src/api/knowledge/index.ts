import { requestClient } from '#/api/request';

export interface ScenesData {
  asset: string[];
  price: string[];
  volume: string[];
  trend: string[];
  valuation: string[];
  sentiment: string[];
  risk_strategy: string[];
  [key: string]: string[];
}

export interface KnowledgeChunk {
  avgConfidence: number | null;
  chunkIndex: number;
  createdAt: string;
  id: number;
  metadata: {
    scenes?: ScenesData;
    [key: string]: unknown;
  };
  originalFilename: null | string;
  pageNos: number[];
  paragraphNos: number[];
  taskNo: string;
  text: string;
  version: number | null;
}

export interface KnowledgeChunkPage {
  pageNum: number;
  pageSize: number;
  pages: number;
  records: KnowledgeChunk[];
  total: number;
}

export interface KnowledgeStats {
  chunkCount: number;
  latestCreatedAt: string | null;
  taskCount: number;
  totalTextLength: number;
}

export function getKnowledgeStats() {
  return requestClient.get<KnowledgeStats>('/knowledge/stats', {
    responseReturn: 'body',
  });
}

export function getKnowledgeChunks(pageNum = 1, pageSize = 20) {
  return requestClient.get<KnowledgeChunkPage>('/knowledge/chunks', {
    params: { pageNum, pageSize },
    responseReturn: 'body',
  });
}

export function getKnowledgeChunkDetail(id: number) {
  return requestClient.get<KnowledgeChunk>(`/knowledge/chunks/${id}`, {
    responseReturn: 'body',
  });
}

export interface KnowledgeChunkUpdateParam {
  reembed?: boolean;
  scenes?: ScenesData;
  text?: string;
}

export function updateKnowledgeChunk(
  id: number,
  param: KnowledgeChunkUpdateParam,
) {
  return requestClient.put<KnowledgeChunk>(
    `/knowledge/chunks/${id}`,
    param,
    {
      responseReturn: 'body',
    },
  );
}
