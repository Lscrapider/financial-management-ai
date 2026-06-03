import { requestClient } from '#/api/request';

export type SceneReportStatus =
  | 'failed'
  | 'generating_report'
  | 'pending'
  | 'processing_current_scenes'
  | 'retrieving_knowledge'
  | 'success';

export interface SceneAnalysisReportTarget {
  latestCreatedAt?: null | string;
  latestGeneratedAt?: null | string;
  latestGenerationType?: null | string;
  latestModel?: null | string;
  latestReportId?: null | number;
  latestReportPreview?: null | string;
  latestReportType?: null | string;
  latestStatus: SceneReportStatus;
  latestTaskNo: string;
  latestVersionNo?: null | number;
  reportCount: number;
  targetCode: string;
  targetName?: null | string;
  targetType: string;
}

export interface SceneAnalysisReportTargetPage {
  pageNum: number;
  pageSize: number;
  pages: number;
  records: SceneAnalysisReportTarget[];
  total: number;
}

export interface SceneAnalysisReportTargetPageParams {
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface SceneAnalysisReportHistory {
  createdAt?: null | string;
  errorMessage?: null | string;
  generatedAt?: null | string;
  generationType: string;
  model?: null | string;
  reportId: number;
  reportType: string;
  status: SceneReportStatus;
  targetCode: string;
  targetName?: null | string;
  targetType: string;
  taskNo: string;
  versionNo: number;
}

export interface SceneAnalysisReportDetail extends SceneAnalysisReportHistory {
  reportContent?: null | Record<string, unknown>;
  reportText?: null | string;
  taskId: number;
}

export interface SceneAnalysisTaskReport {
  errorMessage?: null | string;
  generatedAt?: null | string;
  generationType?: null | string;
  model?: null | string;
  reportContent?: null | Record<string, unknown>;
  reportId?: null | number;
  reportText?: null | string;
  status: SceneReportStatus;
  taskNo: string;
  versionNo?: null | number;
}

export function listSceneReportTargets(params: SceneAnalysisReportTargetPageParams = {}) {
  return requestClient.get<SceneAnalysisReportTargetPage>(
    '/ai/scene-analysis/tasks/reports/targets',
    {
      params: {
        keyword: params.keyword || undefined,
        pageNum: params.pageNum ?? 1,
        pageSize: params.pageSize ?? 20,
      },
      responseReturn: 'body',
    },
  );
}

export function listSceneReportHistory(targetType: string, targetCode: string) {
  return requestClient.get<SceneAnalysisReportHistory[]>(
    '/ai/scene-analysis/tasks/reports',
    {
      params: { targetCode, targetType },
      responseReturn: 'body',
    },
  );
}

export function getSceneReportDetail(reportId: number) {
  return requestClient.get<SceneAnalysisReportDetail>(
    `/ai/scene-analysis/tasks/reports/${reportId}`,
    { responseReturn: 'body' },
  );
}

export function regenerateSceneReport(taskNo: string) {
  return requestClient.post<void>(
    `/ai/scene-analysis/tasks/${taskNo}/report/regenerate`,
    undefined,
    { responseReturn: 'body' },
  );
}

export function getSceneAnalysisTaskReport(taskNo: string) {
  return requestClient.get<SceneAnalysisTaskReport>(
    `/ai/scene-analysis/tasks/${taskNo}/report`,
    { responseReturn: 'body' },
  );
}
