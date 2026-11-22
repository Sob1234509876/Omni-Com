/** OSA 服务端全部端点（路径唯一出处，对接 osa-server 契约 1.1a） */
export const endpoints = {
  registerEmail: "/anonymous/register/email",
  register: "/anonymous/register",
  login: "/anonymous/login",
  publicProfile: "/public/profile",
  publicAvatar: "/public/avatar",
  userSearch: "/public/user/search",
  whoami: "/private/whoami",
  profileUpdate: "/private/profile/update",
  avatarUpload: "/private/avatar/upload",
  avatarUpdate: "/private/avatar/update",
  profileDelete: "/private/profile/delete",
  avatarDelete: "/private/avatar/delete",
} as const;
