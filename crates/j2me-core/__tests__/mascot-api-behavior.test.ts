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

  const grouped = new Map<string, string[]>();
  for (const f of classFiles) {
    grouped.set(dirname(f), [...(grouped.get(dirname(f)) ?? []), f]);
  }

  let newest = '';
  let newestTime = 0;
  for (const [dir, files] of grouped) {
    const mainFile = files.find((f) => f.endsWith(`${prefix}.class`));
    if (mainFile) {
      const mtime = statSync(mainFile).mtimeMs;
      if (mtime > newestTime) {
        newestTime = mtime;
        newest = dir;
      }
    }
  }

  return grouped.get(newest) ?? [];
};

describe('Mascot Capsule API behavior', () => {
  it('executes state, validation, and unbound entry-point checks', async () => {
    const vm = await JvmInstance.create(false);
    try {
      const classFiles = findCompiledClassFiles('MascotApiBehaviorTest');
      expect(classFiles.length).toBeGreaterThan(0);
      for (const classFile of classFiles) {
        vm.loadClass(new Uint8Array(readFileSync(classFile)));
      }

      expect(vm.startMain('MascotApiBehaviorTest')).toBe(0);
      const output = vm.run(20_000).trim();
      expect(output).toContain('=== Mascot API Behavior Test ===');
      expect(output).toContain('FAILED: 0');
      expect(output).toContain(
        'Graphics3D unbound render entry points are no-op',
      );
    } finally {
      vm.destroy();
    }
  });
});
