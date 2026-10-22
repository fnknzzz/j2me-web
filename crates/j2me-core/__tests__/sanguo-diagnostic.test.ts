/**
 * Optional local diagnostic test. It is deliberately excluded from the
 * standalone repository because its game JAR is not redistributable.
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
      vm.resolveAsyncRmsLoad(req.id, null);
    } else if (req.type === 'rms_keys') {
      vm.resolveAsyncRmsKeys(req.id, []);
    }
  }
  return pending.length;
};

const completeAsyncInit = (vm: JvmInstance, maxRounds = 200): number => {
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
): { flushes: number; framesRun: number; output: string } => {
  let flushes = 0;
  let framesRun = 0;
  let output = '';
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
      const delay = vm.idleDelay();
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
    output += vm.flushOutput();
    framesRun++;
    if (result === STEP_DEAD) break;
  }
  return { flushes, framesRun, output };
};

const localGameJar = process.env.JSJVM_LOCAL_GAME_JAR;

describe.skipIf(!localGameJar)('local game diagnostic', () => {
  const jarPath = localGameJar ? resolve(localGameJar) : '';

  it('should start and render without errors', async () => {
    const jarData = new Uint8Array(readFileSync(jarPath));
    const vm = await JvmInstance.create(false);

    try {
      vm.loadJar(jarData);

      completeAsyncInit(vm);

      const startResult = vm.startMidlet(1);

      let output = vm.flushOutput();

      if (startResult === 2) {
        completeAsyncInit(vm);
      }

      // Run some steps to get past initialization
      for (let i = 0; i < 100; i++) {
        const s = vm.step(16);
        drainAsync(vm);
        output += vm.flushOutput();
        if (s === STEP_DEAD) break;
      }

      // Now run frames properly
      output += runFrames(vm, 200).output;

      output += vm.flushOutput();

      expect(output).not.toContain('NullPointerException');
      expect(output).not.toContain('ArrayIndexOutOfBoundsException');
      expect(output).not.toContain('Unhandled exception');
    } finally {
      vm.destroy();
    }
  }, 60_000);
});
