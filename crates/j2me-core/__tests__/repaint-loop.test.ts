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

describe('RepaintLoop stuck test', () => {
  it('should complete 10 frames with step(1) per-ms mode', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'RepaintLoopTest');
      expect(vm.startMain('RepaintLoopTest')).toBe(0);
      const output = vm.run(5000).trim();
      expect(output).toContain('OK frames=10');
    } finally {
      vm.destroy();
    }
  });

  it('should complete 10 frames with rAF-style frame loop (16ms steps)', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'RepaintLoopTest');
      expect(vm.startMain('RepaintLoopTest')).toBe(0);

      const FRAME_BUDGET_STEPS = 500;
      const MAX_FRAMES = 200;

      for (let frame = 0; frame < MAX_FRAMES; frame++) {
        let result = vm.step(16);
        if (result === 0) break;
        for (let i = 0; i < FRAME_BUDGET_STEPS && result === 1; i++) {
          result = vm.step(0);
        }
        if (result === 0) break;
      }

      const output = vm.getOutput().trim();
      expect(output).toContain('OK frames=10');
    } finally {
      vm.destroy();
    }
  });

  it('should complete with small frame steps + JS-side idle fast-forward', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'RepaintLoopTest');
      expect(vm.startMain('RepaintLoopTest')).toBe(0);

      const STEP_IDLE = 2;
      const STEP_DEAD = 0;
      const MAX_FRAMES = 100;
      const FRAME_TIME = 16;
      let framesUsed = 0;

      for (let frame = 0; frame < MAX_FRAMES; frame++) {
        framesUsed = frame + 1;
        let result = vm.step(FRAME_TIME);
        if (result === STEP_DEAD) break;
        for (let i = 0; i < 500 && result === 1; i++) {
          result = vm.step(0);
        }
        if (result === STEP_DEAD) break;
        if (result === STEP_IDLE) {
          const delay = vm.idleDelay();
          if (delay > 0 && delay <= FRAME_TIME * 2) {
            result = vm.step(delay);
            for (let i = 0; i < 500 && result === 1; i++) {
              result = vm.step(0);
            }
          }
        }
        if (result === STEP_DEAD) break;
      }

      const output = vm.getOutput().trim();
      expect(output).toContain('OK frames=10');
      expect(framesUsed).toBeLessThan(100);
    } finally {
      vm.destroy();
    }
  });
});

describe('KeyFire stuck test', () => {
  it('should complete 10 frames with step(1) + key events', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'KeyFireStuckTest');
      expect(vm.startMain('KeyFireStuckTest')).toBe(0);
      const output = vm.run(10000).trim();
      expect(output).toContain('OK frames=10');
    } finally {
      vm.destroy();
    }
  });

  it('should complete 10 frames with rAF-style loop + key events', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'KeyFireStuckTest');
      expect(vm.startMain('KeyFireStuckTest')).toBe(0);

      const STEP_IDLE = 2;
      const STEP_DEAD = 0;
      const MAX_FRAMES = 800;
      const FRAME_TIME = 16;

      for (let frame = 0; frame < MAX_FRAMES; frame++) {
        let result = vm.step(FRAME_TIME);
        if (result === STEP_DEAD) break;
        for (let i = 0; i < 500 && result === 1; i++) {
          result = vm.step(0);
        }
        if (result === STEP_DEAD) break;
        if (result === STEP_IDLE) {
          const delay = vm.idleDelay();
          if (delay > 0 && delay <= FRAME_TIME * 2) {
            result = vm.step(delay);
            for (let i = 0; i < 500 && result === 1; i++) {
              result = vm.step(0);
            }
          }
        }
        if (result === STEP_DEAD) break;
      }

      const output = vm.getOutput().trim();
      expect(output).toContain('OK frames=10');
    } finally {
      vm.destroy();
    }
  });

  it('should complete with rAF loop + pushNativeEvent key injection', async () => {
    // This test uses pushNativeEvent (the real JS→Rust path) to inject key
    // events between step() calls, mimicking what the browser actually does.
    // Uses RepaintLoopTest (simpler) since pushNativeEvent goes directly to
    // the Rust native_event_queue, not through a Java thread.
    const vm = await JvmInstance.create(false);
    try {
      loadTestClasses(vm, 'RepaintLoopTest');
      expect(vm.startMain('RepaintLoopTest')).toBe(0);

      const STEP_IDLE = 2;
      const STEP_DEAD = 0;
      const MAX_FRAMES = 300;
      const FRAME_TIME = 16;

      for (let frame = 0; frame < MAX_FRAMES; frame++) {
        // Inject key events every 3 frames (simulating rapid fire pressing)
        if (frame >= 5 && frame % 3 === 0) {
          // KEY_EVENT=1, PRESSED=1, keyCode=53 ('5'=fire), y=0, displayId=1
          vm.pushNativeEvent(1, 1, 53, 0, 1);
          // Also inject RELEASED shortly after
          vm.pushNativeEvent(1, 2, 53, 0, 1);
        }

        let result = vm.step(FRAME_TIME);
        if (result === STEP_DEAD) break;
        for (let i = 0; i < 500 && result === 1; i++) {
          result = vm.step(0);
        }
        if (result === STEP_DEAD) break;
        if (result === STEP_IDLE) {
          const delay = vm.idleDelay();
          if (delay > 0 && delay <= FRAME_TIME * 2) {
            result = vm.step(delay);
            for (let i = 0; i < 500 && result === 1; i++) {
              result = vm.step(0);
            }
          }
        }
        if (result === STEP_DEAD) break;
      }

      const output = vm.getOutput().trim();
      expect(output).toContain('OK frames=10');
    } finally {
      vm.destroy();
    }
  });
});
