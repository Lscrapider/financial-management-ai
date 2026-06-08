import { requestClient } from '#/api/request';

export interface TargetDeleteParams {
  targetCode: string;
  targetType: 'BOND' | 'INDEX' | 'STOCK';
}

export interface TargetDeleteResult {
  targetCode: string;
  targetType: string;
  deleted: boolean;
}

export function deleteTargetConfig(data: TargetDeleteParams) {
  return requestClient.post<TargetDeleteResult>(
    '/system-config/targets/delete',
    data,
    {
      timeout: 60_000,
    },
  );
}
