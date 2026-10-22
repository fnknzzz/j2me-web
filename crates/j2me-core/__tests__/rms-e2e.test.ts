import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { JvmInstance } from '../index';

const STEP_DEAD = 0;
const STEP_OK = 1;
const STEP_IDLE = 2;

/**
 * In-memory RMS storage for tests. Sync save/remove go through WASM FFI
 * callbacks; async load/keys go through claimPendingAsync + resolve.
 */
const createRmsStore = () => {
  const store = new Map<string, ArrayBuffer>();
  return {
    store,
    save: (key: string, data: ArrayBuffer) => {
      store.set(key, data);
    },
    remove: (key: string) => {
      store.delete(key);
    },
  };
};

/**
 * Claim pending async requests and resolve them synchronously from store.
 * Returns true if any async was resolved.
 */
const drainAsync = (
  vm: JvmInstance,
  store: Map<string, ArrayBuffer>,
): boolean => {
  const pending = vm.claimPendingAsync() as Array<{
    id: number;
    type: string;
    store_key?: string;
  }>;
  if (pending.length === 0) return false;
  for (const req of pending) {
    if (req.type === 'rms_load') {
      const data = store.get(req.store_key!) ?? null;
      vm.resolveAsyncRmsLoad(req.id, data);
    } else if (req.type === 'rms_keys') {
      const keys = Array.from(store.keys());
      vm.resolveAsyncRmsKeys(req.id, keys);
    } else if (req.type === 'decode_image') {
      vm.resolveAsyncImage(req.id, 0, 0);
    }
  }
  return true;
};

/**
 * Run frames with async support. Processes RMS async requests inline.
 */
const runFrames = (
  vm: JvmInstance,
  store: Map<string, ArrayBuffer>,
  count: number,
  frameTimeMs = 16,
) => {
  let framesRun = 0;
  for (let i = 0; i < count; i++) {
    let result = vm.step(frameTimeMs);
    if (result === STEP_DEAD) break;

    // Inner loop: step until idle, handling async along the way
    for (let j = 0; j < 5000; j++) {
      if (drainAsync(vm, store)) {
        // After resolving async, step to let thread resume
        result = vm.step(0);
        continue;
      }
      if (result !== STEP_OK) break;
      result = vm.step(0);
    }

    // Idle fast-forward
    if (result === STEP_IDLE) {
      drainAsync(vm, store);
      const delay = vm.idleDelay();
      if (delay > 0 && delay <= 500) {
        result = vm.step(delay);
        for (let j = 0; j < 1000; j++) {
          if (drainAsync(vm, store)) {
            result = vm.step(0);
            continue;
          }
          if (result !== STEP_OK) break;
          result = vm.step(0);
        }
      }
    }

    framesRun++;
    if (result === STEP_DEAD) break;
  }
  return framesRun;
};

describe('RMSTestSuite E2E', () => {
  it('should pass all RMS tests', async () => {
    const jarPath = resolve(
      __dirname,
      '../tests/fixtures/demo-jars/RMSTestSuite.jar',
    );
    const jarData = new Uint8Array(readFileSync(jarPath));

    const rms = createRmsStore();
    const vm = await JvmInstance.create(false);

    // Wire up sync RMS callbacks
    JvmInstance.setRmsSaveCallback(rms.save);
    JvmInstance.setRmsRemoveCallback(rms.remove);
    JvmInstance.setRmsLoadCallback(() => null); // unused, goes through async
    JvmInstance.setRmsKeysCallback(() => []); // unused, goes through async

    vm.loadJar(jarData);
    // Start suite — may need async rounds
    let startResult = vm.startMidlet(1);
    for (let i = 0; i < 50 && startResult === 2; i++) {
      drainAsync(vm, rms.store);
      startResult = vm.resumeAsync();
    }

    // Run enough frames for all 6 tests to complete
    runFrames(vm, rms.store, 500);

    const output = vm.flushOutput();

    // Check test results
    expect(output).toContain('=== RMS Test Suite ===');
    expect(output).toContain('=== Tests Complete ===');

    // Each test should PASS or at least not have ERROR
    expect(output).not.toContain('ERROR:');
    expect(output).not.toContain('FAIL:');
  });
});
