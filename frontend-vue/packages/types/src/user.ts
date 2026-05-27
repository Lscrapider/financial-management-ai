import type { BasicUserInfo } from '@vben-core/typings';

/** 用户信息 */
interface UserInfo extends BasicUserInfo {
  /**
   * 用户描述
   */
  desc: string;
  /**
   * 首页地址
   */
  homePath: string;
  /**
   * accessToken
   */
  token: string;
  /**
   * 个人简介
   */
  introduction?: string;
  /**
   * 邮箱
   */
  email?: string;
  /**
   * 手机号
   */
  phone?: string;
  /**
   * 邮件通知开关
   */
  emailNotification?: boolean;
}

export type { UserInfo };
