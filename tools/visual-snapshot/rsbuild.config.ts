import path from 'node:path';
import { defineConfig } from '@rsbuild/core';

const repoRoot = path.resolve(__dirname, '../..');
const aliases: Record<string, string> = {
  'j2me-web': path.join(repoRoot, 'packages/j2me-web/src'),
  '@jsjvm/j2me-simulator': path.join(repoRoot, 'packages/j2me-simulator/src'),
  '@jsjvm/j2me-sound-player': path.join(
    repoRoot,
    'packages/j2me-sound-player/src',
  ),
  '@jsjvm/j2me-core': path.join(repoRoot, 'crates/j2me-core/index.ts'),
};
aliases['@jsjvm/j2me-core/m3g-wire'] = path.join(
  repoRoot,
  'crates/j2me-core/src/gles1_webgpu/schema/m3g_webgpu_wire.json',
);

export default defineConfig({
  source: {
    entry: {
      index: './web-capture-page/index.ts',
    },
    include: [path.join(repoRoot, 'crates/j2me-core')],
  },
  html: {
    template: './web-capture-page/index.html',
  },
  resolve: {
    alias: aliases,
    extensions: ['.ts', '.tsx', '.js', '.jsx', '.json'],
  },
  output: {
    distPath: {
      root: './dist-web',
    },
    cleanDistPath: true,
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
