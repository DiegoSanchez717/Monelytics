import { defineConfig } from 'vitest/config';

// Keep the suite deterministic on developer laptops and small CI runners.
export default defineConfig({
  test: {
    pool: 'threads',
    maxWorkers: 1,
    fileParallelism: false,
    testTimeout: 15000,
    hookTimeout: 15000,
  },
});
