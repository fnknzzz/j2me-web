import path from 'node:path';
import { defineConfig } from '@rsbuild/core';

const repositoryRoot = path.resolve(__dirname, '../..');
const aliases: Record<string, string> = {
  'j2me-web': path.join(repositoryRoot, 'packages/j2me-web/src'),
  '@jsjvm/j2me-simulator': path.join(
    repositoryRoot,
    'packages/j2me-simulator/src',
  ),
  '@jsjvm/j2me-sound-player': path.join(
    repositoryRoot,
    'packages/j2me-sound-player/src',
  ),
  '@jsjvm/j2me-core': path.join(repositoryRoot, 'crates/j2me-core/index.ts'),
  '@/j2me-phone': path.join(__dirname, 'src/components/j2me-phone.ts'),
};
aliases['@jsjvm/j2me-core/m3g-wire'] = path.resolve(
  __dirname,
  '../../crates/j2me-core/src/gles1_webgpu/schema/m3g_webgpu_wire.json',
);

export default defineConfig({
  html: {
    template: './public/index.html',
  },
  source: {
    entry: {
      index: './src/index.ts',
    },
    include: [path.resolve(__dirname, '../../crates/j2me-core')],
  },
  resolve: {
    alias: aliases,
    extensions: ['.ts', '.tsx', '.js', '.jsx', '.json'],
  },
  output: {
    assetPrefix: './',
  },
  dev: {
    lazyCompilation: false,
    client: {
      overlay: false,
    },
  },
  tools: {
    rspack: (config, { addRules }) => {
      config.experiments = {
        ...config.experiments,
        asyncWebAssembly: true,
        topLevelAwait: true,
      };

      addRules({
        test: /\.jar$/,
        type: 'asset/inline',
        generator: {
          dataUrl: (content: Buffer) => {
            const base64 = content.toString('base64');
            return `data:application/octet-stream;base64,${base64}`;
          },
        },
      });

      addRules({
        test: /\.wgsl$/,
        type: 'asset/source',
      });

      addRules({
        test: /j2me-core\.wasi-browser\.js$/,
        type: 'javascript/esm',
      });

      addRules({
        test: /j2me-core[\\/](?:dist[\\/]index\.js|index\.ts)$/,
        sideEffects: true,
      });

      if (config.resolve) {
        config.resolve.extensions = ['.ts', '.tsx', '.js', '.jsx', '.json'];
      }
    },
  },
});
