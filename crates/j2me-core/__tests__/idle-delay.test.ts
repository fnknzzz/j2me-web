/// <reference types="node" />
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { JvmInstance } from '../index';

const __dirname = dirname(fileURLToPath(import.meta.url));

const findCompiledClassFiles = (prefix: string): string[] => {
  const targetDir = resolve(__dirname, '../../../target');
  const classFiles: string[] = [];
  const dirs = [targetDir];

  while (dirs.length > 0) {
    const dir = dirs.pop()!;
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const fullPath = resolve(dir, entry.name);
      if (entry.isDirectory()) {
        dirs.push(fullPath);
        continue;
      }
      if (
        dir.endsWith('test_classes') &&
        entry.name.startsWith(prefix) &&
        entry.name.endsWith('.class')
      ) {
        classFiles.push(fullPath);
      }
    }
  }

  return classFiles.sort();
};

const loadTestClasses = (vm: JvmInstance, prefix: string) => {
  const classFiles = findCompiledClassFiles(prefix);
  if (classFiles.length === 0) {
    throw new Error(`No compiled test classes found for prefix "${prefix}"`);
  }
  for (const file of classFiles) {
    vm.loadClass(new Uint8Array(readFileSync(file)));
  }
};

describe('idle_delay correctness', () => {
  const STEP_IDLE = 2;
  const STEP_DEAD = 0;

  it('should return ~1000ms idle_delay when thread sleeps for 1000ms', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'IdleDelayTest');
      expect(vm.startMain('IdleDelayTest')).toBe(0);

      // Step once to let the thread enter Thread.sleep(1000)
      let result = vm.step(0);
      let maxIter = 100;
      while (result === 1 && maxIter-- > 0) {
        result = vm.step(0);
      }

      // Should now be IDLE (thread sleeping)
      expect(result).toBe(STEP_IDLE);

      // idle_delay should be close to 1000ms (not 0, not u32::MAX)
      const delay = vm.idleDelay();
      expect(delay).toBeGreaterThan(900);
      expect(delay).toBeLessThanOrEqual(1000);

      // Now fast-forward time to wake the thread
      result = vm.step(delay);
      maxIter = 500;
      while (result === 1 && maxIter-- > 0) {
        result = vm.step(0);
      }
      expect(result).toBe(STEP_DEAD);

      const output = vm.getOutput().trim();
      expect(output).toContain('OK sleep done');
    } finally {
      vm.destroy();
    }
  });

  it('should return u32::MAX idle_delay when threads are in indefinite wait', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'IdleDelayWaitTest');
      expect(vm.startMain('IdleDelayWaitTest')).toBe(0);

      // Step until IDLE (all threads in Object.wait() with no timeout)
      let result = vm.step(0);
      let maxIter = 500;
      while (result === 1 && maxIter-- > 0) {
        result = vm.step(0);
      }

      // Should be IDLE (threads alive but all in indefinite wait)
      expect(result).toBe(STEP_IDLE);

      // idle_delay should be i32::MAX (2147483647) — no timed sleepers
      const delay = vm.idleDelay();
      expect(delay).toBe(2147483647);
    } finally {
      vm.destroy();
    }
  });

  it('should not busy-poll: idle_delay must never be 0 when IDLE with alive threads', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'IdleDelayTest');
      expect(vm.startMain('IdleDelayTest')).toBe(0);

      // Run through the program, checking that idle_delay is never 0 when IDLE
      let result = vm.step(16);
      const MAX_FRAMES = 200;

      for (let frame = 0; frame < MAX_FRAMES && result !== STEP_DEAD; frame++) {
        if (result === STEP_IDLE) {
          const delay = vm.idleDelay();
          // When IDLE, delay must be > 0 (either a real sleep time, or u32::MAX)
          expect(delay).toBeGreaterThan(0);
          result = vm.step(Math.min(delay, 16));
        } else {
          result = vm.step(0);
        }
      }

      const output = vm.getOutput().trim();
      expect(output).toContain('OK sleep done');
    } finally {
      vm.destroy();
    }
  });
});
