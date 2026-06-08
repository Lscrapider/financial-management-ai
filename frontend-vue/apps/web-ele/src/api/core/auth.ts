import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    roleCode?: string;
    username?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
  }

  export interface RefreshTokenResponse {
    code: number;
    data: string;
    message: string;
  }

  /** 注册接口参数 */
  export interface RegisterParams {
    confirmPassword?: string;
    password?: string;
    username?: string;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/login', data, {
    withCredentials: true,
  });
}

/**
 * 注册
 */
export async function registerApi(data: AuthApi.RegisterParams) {
  return requestClient.post<unknown>('/auth/register', data);
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  const response = await baseRequestClient.post<{
    data: AuthApi.RefreshTokenResponse;
  }>('/auth/refresh', undefined, {
    withCredentials: true,
  });
  return response.data;
}

/**
 * 退出登录
 */
export async function logoutApi(accessToken?: null | string) {
  return baseRequestClient.post('/auth/logout', undefined, {
    headers: accessToken
      ? {
          Authorization: `Bearer ${accessToken}`,
        }
      : undefined,
    withCredentials: true,
  });
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}
