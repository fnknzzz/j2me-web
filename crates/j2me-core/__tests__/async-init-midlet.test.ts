/**
 * Unit test for the async-init MIDlet pattern.
 *
 * Reproduces the 傲气雄鹰 bug: MIDlet's <init> triggers async image decoding.
 * If startApp runs before <init> completes, fields are null → NPE.
 *
 * Uses AsyncInitMidlet.class — a precompiled test MIDlet whose constructor
 * calls Image.createImage(byte[], 0, len) with an inline 1×1 PNG. That call
 * issues a decode_image async request and blocks <init> until the host
 * resolves it.  This lets us test the async-init flow without any external JAR.
 *
 * The correct flow with startMidlet:
 *   1. startMidlet(1) → internally triggers createMidletContext + startMidlet
 *   2. If ASYNC_PENDING (return 2): claimPendingAsync → resolve → resumeAsync
 *   3. Repeat until resumeAsync returns 0
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { JvmInstance } from '../index';

const STEP_DEAD = 0;
const STEP_OK = 1;
const STEP_IDLE = 2;
const CMD_FLUSH = 14;

const drainAsync = (vm: JvmInstance): number => {
  const pending = vm.claimPendingAsync() as Array<{ id: number; type: string }>;
  if (pending.length === 0) return 0;
  for (const req of pending) {
    if (req.type === 'decode_image') {
      vm.resolveAsyncImage(req.id, 1, 1);
    } else if (req.type === 'rms_load') {
      (vm as any).resolveAsyncRmsLoad(req.id, null);
    } else if (req.type === 'rms_keys') {
      (vm as any).resolveAsyncRmsKeys(req.id, []);
    }
  }
  return pending.length;
};

const resolveClaimedAsync = (
  vm: JvmInstance,
  claimed: Array<{ id: number; type: string }>,
): void => {
  for (const req of claimed) {
    if (req.type === 'decode_image') {
      vm.resolveAsyncImage(req.id, 1, 1);
    } else if (req.type === 'rms_load') {
      (vm as any).resolveAsyncRmsLoad(req.id, null);
    } else if (req.type === 'rms_keys') {
      (vm as any).resolveAsyncRmsKeys(req.id, []);
    }
  }
};

const _completeAsyncInit = (vm: JvmInstance, maxRounds = 200): number => {
  let rounds = 0;
  for (let i = 0; i < maxRounds; i++) {
    drainAsync(vm);
    const resumeResult = vm.resumeAsync();
    rounds++;
    if (resumeResult === 0) break;
    if (resumeResult === -1) break;
  }
  return rounds;
};

const countFlushes = (vm: JvmInstance): number => {
  const result = vm.flushCommandBuffer();
  if (!result) return 0;
  let count = 0;
  const { data, len } = result;
  for (let i = 0; i < len; i++) {
    if (data[i] === CMD_FLUSH) count++;
  }
  return count;
};

const runFrames = (
  vm: JvmInstance,
  count: number,
  frameTimeMs = 16,
): { flushes: number; framesRun: number } => {
  let flushes = 0;
  let framesRun = 0;
  for (let i = 0; i < count; i++) {
    let result = vm.step(frameTimeMs);
    if (result === STEP_DEAD) break;

    for (let j = 0; j < 5000; j++) {
      if (drainAsync(vm) > 0) {
        result = vm.step(0);
        continue;
      }
      if (result !== STEP_OK) break;
      result = vm.step(0);
    }

    if (result === STEP_DEAD) break;

    if (result === STEP_IDLE) {
      const delay = (vm as any).idleDelay?.() ?? 0;
      if (delay > 0 && delay <= 500) {
        result = vm.step(delay);
        for (let j = 0; j < 1000; j++) {
          if (drainAsync(vm) > 0) {
            result = vm.step(0);
            continue;
          }
          if (result !== STEP_OK) break;
          result = vm.step(0);
        }
      }
    }

    flushes += countFlushes(vm);
    framesRun++;
    if (result === STEP_DEAD) break;
  }
  return { flushes, framesRun };
};

/**
 * Load AsyncInitMidlet.class — a MIDlet whose <init> calls
 * Image.createImage(byte[], 0, len) with an inline 1×1 PNG, triggering
 * a decode_image async request that blocks until the host resolves it.
 */
const loadAsyncInitMidlet = (vm: JvmInstance) => {
  const classPath = resolve(__dirname, '../tests/java/AsyncInitMidlet.class');
  const loaded = vm.loadClass(new Uint8Array(readFileSync(classPath)));
  if (!loaded) throw new Error('Failed to load AsyncInitMidlet.class');
  // Provide a manifest so startMidlet can find the entry class
  const manifest = 'MIDlet-1: AsyncInitMidlet,,AsyncInitMidlet\n';
  vm.loadResource(
    'META-INF/MANIFEST.MF',
    new Uint8Array(Buffer.from(manifest)),
  );
};

describe('Async init MIDlet (傲气雄鹰 pattern)', () => {
  it('should complete <init> async before startApp (correct flow)', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadAsyncInitMidlet(vm);

      // startMidlet triggers create_midlet_context (<init>) + start_midlet (startApp)
      // <init> calls Image.createImage → decode_image async → ASYNC_PENDING
      let startResult = vm.startMidlet(1);

      // Resolve async requests until suite is fully started
      for (let i = 0; i < 200 && startResult === 2; i++) {
        drainAsync(vm);
        startResult = vm.resumeAsync();
      }
      expect(startResult).not.toBe(-1);
      let output = '';

      for (let i = 0; i < 50; i++) {
        const s = vm.step(16);
        drainAsync(vm);
        if (s === STEP_DEAD) break;
      }

      output += vm.flushOutput();
      expect(output).not.toContain('NullPointerException');
      // Constructor and startApp both printed successfully
      expect(output).toContain('AsyncInitMidlet: constructor start');
      expect(output).toContain('AsyncInitMidlet: startApp ok');

      // Step 4: verify run_queue is clean
      const _result = runFrames(vm, 50);
      const finalThreads = vm.dumpThreads();
      const runQMatch = finalThreads.match(/runQ=\[([^\]]*)\]/);
      if (runQMatch) {
        const entries = runQMatch[1]
          .split(',')
          .filter((s) => s.trim().length > 0);
        expect(entries.length).toBeLessThan(20);
      }
      vm.flushOutput();
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('should have a pending decode_image request right after startMidlet', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadAsyncInitMidlet(vm);

      // startMidlet triggers <init> which issues decode_image async
      const startResult = vm.startMidlet(1);
      expect(startResult).toBe(2); // ASYNC_PENDING

      // <init> called Image.createImage → decode_image must be pending
      const pending = vm.claimPendingAsync() as Array<{
        id: number;
        type: string;
      }>;
      const hasDecodeImage = pending.some((p) => p.type === 'decode_image');
      expect(hasDecodeImage).toBe(true);

      // Resolve the claimed batch so <init> can continue, then start
      resolveClaimedAsync(vm, pending);
      vm.resumeAsync();

      // Drain any remaining rounds
      for (let i = 0; i < 50; i++) {
        const p = vm.claimPendingAsync() as Array<{ id: number; type: string }>;
        if (p.length === 0) {
          const r = vm.resumeAsync();
          if (r === 0 || r === -1) break;
        } else {
          resolveClaimedAsync(vm, p);
          vm.resumeAsync();
        }
      }

      const output = vm.flushOutput();
      expect(output).not.toContain('NullPointerException');
      expect(output).toContain('AsyncInitMidlet: startApp ok');
    } finally {
      vm.destroy();
    }
  }, 30_000);
});
