export type WorkbenchComponentType = 'detail' | 'quote' | 'report' | 'trend';

export type WorkbenchLayout = '2x1' | '2x2' | '2x3' | '3x2' | '3x3';

export type WorkbenchTargetType = 'CONVERTIBLE_BOND' | 'INDEX' | 'STOCK';

export interface WorkbenchCellConfig {
  componentType?: WorkbenchComponentType;
  reportId?: number;
  targetCode?: string;
  targetName?: string;
  targetType?: WorkbenchTargetType;
}

export interface WorkbenchItem extends WorkbenchCellConfig {
  h: number;
  i: string;
  minH?: number;
  minW?: number;
  w: number;
  x: number;
  y: number;
}
