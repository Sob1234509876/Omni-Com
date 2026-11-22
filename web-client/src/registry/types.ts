import type { RouteRecordRaw, Router } from "vue-router";

/** 导航栏条目：labelKey 为 i18n 键；visible/badge 在渲染期调用，闭包读 store 即保持响应性 */
export interface PluginNavItem {
  labelKey: string;
  path: string;
  visible: () => boolean;
  /** 徽标计数（如未处理请求数），≥1 时显示 */
  badge?: () => number;
}

/**
 * 客户端插件注册协议：每个插件一个独立目录，导出一份本接口的实现，
 * 在 registry/plugins.ts 的 plugins 数组里登记一项即完成接入，壳零改动。
 */
export interface ClientPlugin {
  id: string;
  /** 组标题（nav 多于一项时显示），也作插件标识 */
  name: string;
  /** 插件入口路径 */
  home: string;
  /** 插件自己的全部路由，path 建议以 /<id> 开头避免冲突 */
  routes: RouteRecordRaw[];
  /** 导航栏条目；缺省时不渲染（测试期兼容用，正式插件都应提供） */
  nav?: PluginNavItem[];
  /** 注册期回调：装路由守卫、请求层依赖注入等（此刻 pinia 已装、尚未挂载） */
  setup?: (router: Router) => void;
}
