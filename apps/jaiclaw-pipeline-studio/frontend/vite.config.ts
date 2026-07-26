import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Everything the SPA fetches at runtime is same-origin under /studio/*
// or /api/pipeline-studio/*. `base: "/studio/"` makes the built HTML
// reference asset URLs relative to that mount point so index.html works
// when Spring Boot serves it from classpath:/META-INF/resources/studio/.
export default defineConfig({
  base: "/studio/",
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // dev-only — proxies the API + dashboard endpoints to a local
      // gateway so `npm run dev` can hit a real backend without CORS.
      "/api": "http://localhost:8080",
      "/actuator": "http://localhost:8080",
      "/pipelines/dashboard": "http://localhost:8080"
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./test/setup.ts"],
    include: ["test/**/*.{test,spec}.{ts,tsx}"]
  }
});
