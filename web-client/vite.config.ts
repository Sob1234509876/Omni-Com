import { defineConfig } from "vite";

// The dev server serves the page and forwards the three server routes to the local Java server: the
// WebSocket, the plugin asset manifest/files the page fetches on start, and the world snapshot the view
// polls. All are same-origin for the browser, so no CORS or Origin special case is needed in development.
export default defineConfig({
  server: {
    proxy: {
      "/ws": {
        target: "ws://127.0.0.1:25567",
        ws: true,
        changeOrigin: true,
      },
      "/plugin-assets": {
        target: "http://127.0.0.1:25567",
        changeOrigin: true,
      },
      "/world": {
        target: "http://127.0.0.1:25567",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
  },
});
