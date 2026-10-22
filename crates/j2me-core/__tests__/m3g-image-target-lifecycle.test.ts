/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { JvmInstance } from '../index';

const __dirname = dirname(fileURLToPath(import.meta.url));

const findCompiledClassFiles = (prefix: string): string[] => {
  const targetDir = resolve(__dirname, '../../../target');
  const classFiles: string[] = [];
  const directories = [targetDir];

  while (directories.length > 0) {
    const directory = directories.pop()!;
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const fullPath = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        directories.push(fullPath);
      } else if (
        directory.endsWith('test_classes') &&
        entry.name.startsWith(prefix) &&
        entry.name.endsWith('.class')
      ) {
        classFiles.push(fullPath);
      }
    }
  }

  return classFiles
    .sort((left, right) => statSync(right).mtimeMs - statSync(left).mtimeMs)
    .slice(0, 1);
};

describe('M3G Image2D target lifecycle', () => {
  it('reads RGBA8 render-target pixels back without committing the Image2D', async () => {
    const vm = await JvmInstance.create(false);
    try {
      const classFiles = findCompiledClassFiles('M3GImageTargetLifecycleTest');
      expect(classFiles.length).toBeGreaterThan(0);
      for (const classFile of classFiles) {
        vm.loadClass(new Uint8Array(readFileSync(classFile)));
      }

      expect(vm.startMain('M3GImageTargetLifecycleTest')).toBe(0);
      let output = '';
      let imageReadCount = 0;
      for (let attempt = 0; attempt < 20; attempt++) {
        output += vm.run(20_000);
        const requests = vm.claimPendingAsync();
        if (requests.length === 0) {
          if (output.includes('PASS: Image2D target lifecycle')) {
            break;
          }
          continue;
        }

        for (const request of requests) {
          expect(request.type).toBe('read_m3g_image');
          if (request.type === 'read_m3g_image') {
            expect(request.width).toBe(2);
            expect(request.height).toBe(1);
            const pixels =
              imageReadCount++ === 0
                ? new Uint8Array([
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
                  ])
                : new Uint8Array([
                    0x89, 0xab, 0xcd, 0xef, 0x01, 0x23, 0x45, 0x67,
                  ]);
            vm.resolveAsyncM3gImage(request.id, pixels);
          }
        }
        expect(vm.resumeAsync()).not.toBe(-1);
      }

      expect(imageReadCount).toBe(2);
      expect(output).toContain('PASS: Image2D target lifecycle');
    } finally {
      vm.destroy();
    }
  });
});
