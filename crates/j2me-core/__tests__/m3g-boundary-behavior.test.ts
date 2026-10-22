/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { JvmInstance } from '../index';

const __dirname = dirname(fileURLToPath(import.meta.url));

function findCompiledClassFiles(prefix: string): string[] {
  const targetDir = resolve(__dirname, '../../../target');
  const testClassDirs = new Set<string>();
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
        testClassDirs.add(directory);
      }
    }
  }

  const newestTestClassDir = [...testClassDirs].sort(
    (left, right) => statSync(right).mtimeMs - statSync(left).mtimeMs,
  )[0];
  if (!newestTestClassDir) {
    return [];
  }

  return readdirSync(newestTestClassDir)
    .filter((entry) => entry.startsWith(prefix) && entry.endsWith('.class'))
    .map((entry) => resolve(newestTestClassDir, entry))
    .sort();
}

describe('M3G boundary behavior', () => {
  it('executes the complete public M3G API regression suite', async () => {
    const vm = await JvmInstance.create(false);
    try {
      const classFiles = findCompiledClassFiles('M3GBoundaryBehaviorTest');
      expect(classFiles.length).toBeGreaterThan(0);
      for (const classFile of classFiles) {
        vm.loadClass(new Uint8Array(readFileSync(classFile)));
      }

      expect(vm.startMain('M3GBoundaryBehaviorTest')).toBe(0);
      let output = '';
      for (let attempt = 0; attempt < 100; attempt++) {
        output += vm.run(50_000);
        if (output.includes('=== Tests Complete ===')) {
          break;
        }

        const requests = vm.claimPendingAsync();
        for (const request of requests) {
          if (request.type === 'get_pixels') {
            vm.resolveAsyncPixels(
              request.id,
              new Int32Array(request.width * request.height).fill(0xff123456),
            );
          } else if (request.type === 'read_m3g_image') {
            vm.resolveAsyncM3gImage(
              request.id,
              new Uint8Array(request.width * request.height * 4),
            );
          } else {
            throw new Error(`Unexpected async request: ${request.type}`);
          }
        }
        if (requests.length > 0) {
          expect(vm.resumeAsync()).not.toBe(-1);
        }
      }

      output = output.trim();
      expect(output).toContain('PASSED:');
      expect(output).toContain('FAILED: 0');
    } finally {
      vm.destroy();
    }
  });
});
