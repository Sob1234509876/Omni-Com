import { defineConfig } from "vite";

// The dev server serves the page and forwards /ws to the local Java server. The browser talks to a
// same-origin WebSocket, so no CORS or Origin special case is needed during development.
export default defineConfig({
  server: {
    proxy: {
      "/ws": {
        target: "ws://127.0.0.1:25567",
        ws: true,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
  },
});
