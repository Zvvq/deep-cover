import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Builds into src/main/resources/static for Spring Boot.
export default defineConfig({
  root: 'src/main/frontend',
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src/main/frontend/src', import.meta.url)),
    },
  },
  build: {
    outDir: '../resources/static',
    emptyOutDir: true,
    assetsDir: 'assets',
  },
});
