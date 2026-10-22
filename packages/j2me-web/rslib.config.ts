import path from 'node:path';
import { defineConfig } from '@rslib/core';

const repoRoot = path.resolve(__dirname, '../..');

export default defineConfig({
  source: {
    entry: {
      index: './src/index.ts',
    },
    include: [
      path.join(repoRoot, 'crates/j2me-core'),
      path.join(repoRoot, 'packages/j2me-simulator'),
      path.join(repoRoot, 'packages/j2me-sound-player'),
    ],
  },
  resolve: {
    alias: {
      '@jsjvm/j2me-simulator': path.join(
        repoRoot,
        'packages/j2me-simulator/src',
      ),
      '@jsjvm/j2me-sound-player': path.join(
        repoRoot,
        'packages/j2me-sound-player/src',
      ),
      '@jsjvm/j2me-core': path.join(repoRoot, 'crates/j2me-core/index.ts'),
      '@jsjvm/j2me-core/m3g-wire': path.join(
        repoRoot,
        'crates/j2me-core/src/gles1_webgpu/schema/m3g_webgpu_wire.json',
      ),
    },
    extensions: ['.ts', '.tsx', '.js', '.jsx', '.json'],
  },
  lib: [
    {
      format: 'esm',
      bundle: true,
      dts: false,
    },
  ],
  output: {
    target: 'web',
    distPath: {
      root: './dist',
    },
    filenameHash: false,
    assetPrefix: './',
    autoExternal: false,
    minify: true,
  },
  tools: {
    rspack: (_config, { addRules }) => {
      addRules({
        test: /\.wgsl$/,
        type: 'asset/source',
      });
    },
  },
});
