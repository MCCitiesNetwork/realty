import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Where `npm run dev` forwards /v1. Overridable because a local API does not always
// sit on 8080 -- the compose stack already claims it.
//
// 127.0.0.1, not localhost: Node resolves localhost to ::1 first, while the API binds
// 0.0.0.0 (IPv4 only), so a "localhost" target fails with "socket hang up" against a
// server that is plainly running.
const apiTarget = process.env.REALTY_API_PROXY ?? "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react()],
  optimizeDeps: {
    // Left out of dependency pre-bundling: the renderer loads its WASM mesher by a
    // URL relative to its own module, and the pre-bundled copy resolves that to a
    // path the dev server answers with index.html -- so in development the preview
    // failed to initialise with "expected magic word 00 61 73 6d". The production
    // build emits the .wasm as an asset and was never affected.
    exclude: ["schematic-renderer"],
  },
  server: {
    // Development is same-origin: the SPA calls a relative /v1 and this forwards it,
    // so there is no CORS in development and no config.json either.
    proxy: {
      "/v1": { target: apiTarget, changeOrigin: true },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test-setup.ts"],
  },
});
