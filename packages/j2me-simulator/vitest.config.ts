import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(__dirname, '../..');

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    include: ['src/__tests__/**/*.test.ts'],
    pool: 'forks',
  },
  resolve: {
    conditions: ['source', 'import', 'module', 'default'],
    alias: [
      {
        find: '@jsjvm/j2me-core/m3g-wire',
        replacement: resolve(
          repositoryRoot,
          'crates/j2me-core/src/gles1_webgpu/schema/m3g_webgpu_wire.json',
        ),
      },
      {
        find: '@jsjvm/j2me-core',
        replacement: resolve(repositoryRoot, 'crates/j2me-core/index.ts'),
      },
    ],
  },
});
