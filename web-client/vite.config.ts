import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

const API_TARGET = "http://localhost:8080";
const OSC_TARGET = "http://localhost:8081";

export default defineConfig({
  /* 相对资源路径：dist 可被 OSA 挂到任意子路径伺服（如 /osa/**），不绑死站点根；
     注意 file:// 双击仍不可用（ES module 被 Chrome 禁），看构建产物请走 npm run preview */
  base: "./",
  plugins: [vue()],
  server: {
    /* 开发期把 OSA API 四个前缀代理到本机 osa-server：
       服务端 CORS 未 expose 自定义 description 响应头，跨域时 JS 读不到错误文案，
       同源代理让页面以同源视角调用（部署基线同样要求与 API 同源）。 */
    proxy: {
      "/anonymous": { target: API_TARGET, changeOrigin: true },
      "/public": { target: API_TARGET, changeOrigin: true },
      "/private": { target: API_TARGET, changeOrigin: true },
      "/.well-known": { target: API_TARGET, changeOrigin: true },
      /* OSC（:8081）与 OSA 共用 /private /public 前缀，改走 /osc-api 前缀 + 去前缀重写；
         ws:true 让 event-bus 的 WebSocket 升级请求也走代理 */
      "/osc-api": {
        target: OSC_TARGET,
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/osc-api/, ""),
      },
    },
  },
});
