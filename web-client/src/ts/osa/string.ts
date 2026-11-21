export const language = navigator.language;

const stringsEnUS = new Map();
stringsEnUS.set("title.debug-page", "OSA | Debug Settings");
stringsEnUS.set("title.login-page", "OSA | Login");
stringsEnUS.set("title.logout-page", "OSA | Logout");
stringsEnUS.set("title.register-page", "OSA | Register");
stringsEnUS.set("title.personal-page", "OSA | Personal Profile");
stringsEnUS.set("title.profile-page", "OSA | Search Profile");
stringsEnUS.set("title.update-page", "OSA | Update Profile");
stringsEnUS.set("title.delete-page", "OSA | Delete Profile");
stringsEnUS.set("name.debug-page", "Debug Settings");
stringsEnUS.set("name.login-page", "Login");
stringsEnUS.set("name.logout-page", "Logout");
stringsEnUS.set("name.register-page", "Register");
stringsEnUS.set("name.personal-page", "Personal Profile");
stringsEnUS.set("name.profile-page", "Search Profiles");
stringsEnUS.set("name.update-page", "Update Profiles");
stringsEnUS.set("name.update-avatar", "Update Avatar");
stringsEnUS.set("name.delete-page", "Delete Profile");
stringsEnUS.set("id", "ID");
stringsEnUS.set("avatar", "Avatar");
stringsEnUS.set("username", "Username");
stringsEnUS.set("password", "Password");
stringsEnUS.set("new-password", "New Password");
stringsEnUS.set("old-password", "Old Password");
stringsEnUS.set("authorities", "Authorities");
stringsEnUS.set("locked", "Locked Profile");
stringsEnUS.set("disabled", "Disabled Profile");
stringsEnUS.set("expired", "Expired Profile");
stringsEnUS.set("base-path", "Server URL");
stringsEnUS.set("search", "Search");
stringsEnUS.set("save", "Save");
stringsEnUS.set("ok", "OK");
stringsEnUS.set(
  "error.login",
  "Login failed, please check your ID or password or the server URL in the debug page.",
);
stringsEnUS.set(
  "error.login",
  "Login failed, please check your WiFi or the server URL in the debug page.",
);
stringsEnUS.set(
  "error.delete",
  "Login failed, please check your password or the server URL in the debug page.",
);
stringsEnUS.set(
  "error.update-avatar",
  "Update failed, please check your WiFi or the server URL in the debug page.",
);
stringsEnUS.set(
  "error.update-profile",
  "Update failed, please check your password which not be the same as the old password or your WiFi or the server URL in the debug page.",
);
stringsEnUS.set("error.not-number", "Input must be a number.");
stringsEnUS.set("error.no-avatar", "Please enter an avatar.");
stringsEnUS.set("error.profile-not-found", "No profile was found.");

const stringsZhCn = new Map();
stringsZhCn.set("title.debug-page", "OSA | 调试选项");
stringsZhCn.set("title.login-page", "OSA | 登录");
stringsZhCn.set("title.logout-page", "OSA | 退出登录");
stringsZhCn.set("title.register-page", "OSA | 注册");
stringsZhCn.set("title.personal-page", "OSA | 个人账号");
stringsZhCn.set("title.profile-page", "OSA | 查找账号");
stringsZhCn.set("title.update-page", "OSA | 更改个人账号信息");
stringsZhCn.set("title.delete-page", "OSA | 注销账号");
stringsZhCn.set("name.debug-page", "调试选项");
stringsZhCn.set("name.login-page", "登录");
stringsZhCn.set("name.logout-page", "退出登录");
stringsZhCn.set("name.register-page", "注册");
stringsZhCn.set("name.personal-page", "个人账号");
stringsZhCn.set("name.profile-page", "查找账号");
stringsZhCn.set("name.update-page", "更改个人账号信息");
stringsZhCn.set("name.update-avatar", "更改头像");
stringsZhCn.set("name.delete-page", "注销账号");
stringsZhCn.set("id", "账号ID");
stringsZhCn.set("avatar", "头像");
stringsZhCn.set("username", "账号名");
stringsZhCn.set("password", "密码");
stringsZhCn.set("new-password", "新的密码");
stringsZhCn.set("old-password", "原来的密码");
stringsZhCn.set("authorities", "账号权限");
stringsZhCn.set("locked", "该账号已锁定");
stringsZhCn.set("disabled", "该账号已关闭");
stringsZhCn.set("expired", "该账号已过期");
stringsZhCn.set("base-path", "服务器URL地址");
stringsZhCn.set("search", "搜索");
stringsZhCn.set("save", "保存");
stringsZhCn.set("ok", "搞定！");
stringsZhCn.set(
  "error.login",
  "登陆失败，请检查你的账号ID、密码或者服务器地址。",
);
stringsZhCn.set(
  "error.register",
  "注册失败，请检查你的网络状况或者服务器地址。",
);
stringsZhCn.set("error.delete", "注销失败，请检查你的密码或者服务器地址。");
stringsZhCn.set(
  "error.update-avatar",
  "头像更换失败，请检查你的网络状况或者服务器地址。",
);
stringsZhCn.set(
  "error.update-profile",
  "个人账号信息更改失败，请检查你输入的密码是否与原来相同、网络状况是否良好或者服务器地址。",
);
stringsZhCn.set("error.not-number", "输入值必须为一个整数。");
stringsZhCn.set("error.no-avatar", "请上传头像。");
stringsZhCn.set("error.profile-not-found", "不存在此用户。");

export function getString(key: string): string {
  var res: string | undefined;

  if (language == "zh-CN") res = stringsZhCn.get(key);
  if (res != undefined) return res;
  return stringsEnUS.get(key);
}
