import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  test: {
    hookTimeout: 30000,
    testTimeout: 30000
  },
  resolve: {
    alias: {
      '@tatrman/grammar': path.resolve(__dirname, '../grammar/src')
    }
  }
});