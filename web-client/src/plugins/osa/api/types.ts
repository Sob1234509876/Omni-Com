/**
 * 服务端脱敏后的用户形状（osa-api OsaApiUser 的 JSON）。
 * 三个字段是脱敏假值，界面不得当真值使用：password 恒为单空格、
 * authorities 恒为 []、email 为 m***@domain 遮盖（旧用户可能为 null）。
 */
export interface OsaUserView {
  id: number;
  username: string;
  password: string;
  email: string | null;
  authorities: { authority: string }[];
  accountNonExpired: boolean;
  accountNonLocked: boolean;
  credentialsNonExpired: boolean;
  enabled: boolean;
}
