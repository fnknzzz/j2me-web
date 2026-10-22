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

describe('M3G LCDUI Image2D', () => {
  it('creates a texture image from LCDUI pixels', async () => {
    const vm = await JvmInstance.create(false);
    try {
      const classFiles = findCompiledClassFiles('M3GLcduiImage2DTest');
      expect(classFiles.length).toBeGreaterThan(0);
      for (const classFile of classFiles) {
        vm.loadClass(new Uint8Array(readFileSync(classFile)));
      }

      expect(vm.startMain('M3GLcduiImage2DTest')).toBe(0);
      let output = '';
      for (let attempt = 0; attempt < 20; attempt++) {
        output += vm.run(20_000);
        const requests = vm.claimPendingAsync();
        if (requests.length === 0) {
          break;
        }
        for (const request of requests) {
          if (request.type === 'get_pixels') {
            expect(request.width).toBe(2);
            expect(request.height).toBe(1);
            vm.resolveAsyncPixels(
              request.id,
              new Int32Array([0x40112233, 0xff445566]),
            );
          } else {
            throw new Error(`Unexpected async request: ${request.type}`);
          }
        }
        expect(vm.resumeAsync()).not.toBe(-1);
      }

      expect(output).toContain('PASS: LCDUI Image2D conversion');
    } finally {
      vm.destroy();
    }
  });
});
