import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react()],
  base: process.env.DESIGNER_BASE_URL ?? '/',
  resolve: { alias: { '@': path.resolve(dir, './src') } },
  server: { port: 5173 },
});
