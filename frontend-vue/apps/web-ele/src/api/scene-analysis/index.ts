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
  pageNum?: number;
  pageSize?: number;
  targetCode?: string;
  targetName?: string;
  targetType?: string;
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

export interface SceneAnalysisTaskSubmitPayload {
  configProfile?: string;
  dailyKlineLimit?: number;
  monthlyKlineLimit?: number;
  reportType?: string;
  targetCode: string;
  targetName?: string;
  targetType: string;
  totalChunks: number;
  weeklyKlineLimit?: number;
  userOverrides?: Record<string, unknown>;
}

export interface SceneAnalysisSubmitResult {
  configProfile: string;
  status: SceneReportStatus;
  targetCode: string;
  targetType: string;
  taskNo: string;
}

export interface SceneAnalysisConfigProfile {
  configGroup: string;
  configJson: Record<string, unknown>;
  configProfile: string;
  createdAt?: null | string;
  enabled: boolean;
  id: number;
  name: string;
  reportType: string;
  systemDefault: boolean;
  targetType?: null | string;
  updatedAt?: null | string;
}

export interface SceneAnalysisConfigProfilePayload {
  configGroup?: string;
  configJson: Record<string, unknown>;
  name: string;
  reportType?: string;
  targetType?: string;
}

export interface SceneAnalysisConfigField {
  defaultValue: number;
  description: string;
  key: string;
  label: string;
  max: number;
  min: number;
  path: string[];
  recommended: string;
  step: number;
  unit?: null | string;
}

export interface SceneAnalysisConfigGroup {
  fields: SceneAnalysisConfigField[];
  label: string;
  name: string;
}

export interface SceneAnalysisReportType {
  code: string;
  label: string;
}

export interface SceneAnalysisTargetOption {
  exchangeCode?: null | string;
  marketCode?: null | string;
  secid?: null | string;
  targetCode: string;
  targetName?: null | string;
  targetType: string;
}

export function listSceneReportTargets(params: SceneAnalysisReportTargetPageParams = {}) {
  return requestClient.get<SceneAnalysisReportTargetPage>(
    '/ai/scene-analysis/tasks/reports/targets',
    {
      params: {
        pageNum: params.pageNum ?? 1,
        pageSize: params.pageSize ?? 20,
        targetCode: params.targetCode || undefined,
        targetName: params.targetName || undefined,
        targetType: params.targetType || undefined,
      },
      responseReturn: 'body',
    },
  );
}

export function submitSceneAnalysisTask(payload: SceneAnalysisTaskSubmitPayload) {
  return requestClient.post<SceneAnalysisSubmitResult>(
    '/ai/scene-analysis/tasks',
    payload,
    { responseReturn: 'body' },
  );
}

export function listSceneAnalysisConfigProfiles() {
  return requestClient.get<SceneAnalysisConfigProfile[]>(
    '/ai/scene-analysis/config-profiles',
    { responseReturn: 'body' },
  );
}

export function getSceneAnalysisConfigParameterSchema() {
  return requestClient.get<SceneAnalysisConfigGroup[]>(
    '/ai/scene-analysis/config-profiles/parameter-schema',
    { responseReturn: 'body' },
  );
}

export function getSceneAnalysisReportTypes() {
  return requestClient.get<SceneAnalysisReportType[]>(
    '/ai/scene-analysis/config-profiles/report-types',
    { responseReturn: 'body' },
  );
}

export function createSceneAnalysisConfigProfile(payload: SceneAnalysisConfigProfilePayload) {
  return requestClient.post<SceneAnalysisConfigProfile>(
    '/ai/scene-analysis/config-profiles',
    payload,
    { responseReturn: 'body' },
  );
}

export function updateSceneAnalysisConfigProfile(
  id: number,
  payload: SceneAnalysisConfigProfilePayload,
) {
  return requestClient.put<SceneAnalysisConfigProfile>(
    `/ai/scene-analysis/config-profiles/${id}`,
    payload,
    { responseReturn: 'body' },
  );
}

export function deleteSceneAnalysisConfigProfile(id: number) {
  return requestClient.delete<void>(`/ai/scene-analysis/config-profiles/${id}`);
}

export function searchSceneAnalysisTargets(params: {
  keyword?: string;
  limit?: number;
  targetType: string;
}) {
  return requestClient.get<SceneAnalysisTargetOption[]>(
    '/ai/scene-analysis/targets/search',
    {
      params,
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
