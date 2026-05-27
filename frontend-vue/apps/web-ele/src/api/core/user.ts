import type { UserInfo } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/user/info');
}

/**
 * 更新用户基本资料
 */
export async function updateProfileApi(data: {
  realName?: string;
  introduction?: string;
  email?: string | null;
  phone?: string | null;
}) {
  return requestClient.put('/user/info', data);
}

/**
 * 修改密码
 */
export async function changePasswordApi(data: {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}) {
  return requestClient.put('/user/password', data);
}

/**
 * 更新通知设置
 */
export async function updateNotificationApi(data: {
  emailNotification: boolean;
}) {
  return requestClient.put('/user/notification', data);
}
