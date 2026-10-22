import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      '@jsjvm/j2me-sound-player': resolve(
        __dirname,
        '../j2me-sound-player/src/index.ts',
      ),
      '@jsjvm/j2me-core/m3g-wire': resolve(
        __dirname,
        '../../crates/j2me-core/src/gles1_webgpu/schema/m3g_webgpu_wire.json',
      ),
    },
  },
  plugins: [
    {
      name: 'wgsl-loader',
      enforce: 'pre',
      async load(id) {
        if (!id.endsWith('.wgsl')) {
          return null;
        }

        const source = await readFile(id, 'utf8');
        return {
          code: `export default ${JSON.stringify(source)};`,
          map: null,
        };
      },
    },
  ],
  test: {
    globals: true,
    environment: 'node',
    include: ['src/__tests__/**/*.test.ts'],
  },
});
