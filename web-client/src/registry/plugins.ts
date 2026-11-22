import type { ClientPlugin } from "./types";
import osaPlugin from "../plugins/osa";
import oscPlugin from "../plugins/osc";
import shellIcon from "../assets/icon/oc.png";

export const SHELL_TITLE = "Omni Com";
export const SHELL_ICON = shellIcon;

/** 插件注册表：新插件在数组里加一项即接入导航栏与路由 */
export const plugins: ClientPlugin[] = [osaPlugin, oscPlugin];
