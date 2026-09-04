import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // Development is same-origin: the SPA calls a relative /v1 and this forwards it,
    // so there is no CORS in development and no config.json either.
    proxy: {
      "/v1": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test-setup.ts"],
  },
});
