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

describe('M3G native bridge regression', () => {
  it('keeps Java wrappers and native resources consistent', async () => {
    const vm = await JvmInstance.create(false);
    try {
      const classFiles = findCompiledClassFiles('M3GBridgeRegressionTest');
      expect(classFiles.length).toBeGreaterThan(0);
      for (const classFile of classFiles) {
        vm.loadClass(new Uint8Array(readFileSync(classFile)));
      }

      expect(vm.startMain('M3GBridgeRegressionTest')).toBe(0);
      const output = vm.run(20_000).trim();
      expect(output).toContain('FAILED: 0');
    } finally {
      vm.destroy();
    }
  });
});
