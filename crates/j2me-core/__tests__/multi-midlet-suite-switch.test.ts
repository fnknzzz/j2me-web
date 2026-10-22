import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { JvmInstance } from '../index';

const STEP_OK = 1;
const STEP_IDLE = 2;

const KEY_EVENT = 1;
const COMMAND_EVENT = 3;
const KEY_PRESSED = 1;
const KEY_NUM5 = 53;
const KEY_NUM8 = 56;
const KEY_NUM9 = 57;
const CMD_DRAW_STRING = 4;
const CMD_DRAW_RGBA = 16;
const CMD_LCDUI_DRAW_SHAPE_STREAM = 18;
const CMD_DRAW_IMAGE_STREAM = 19;
const CMD_MENU_UPDATE_COMMANDS = 40;

const flushCommandText = (vm: JvmInstance): string => {
  const result = vm.flushCommandBuffer();
  if (!result) return '';
  return new TextDecoder().decode(result.data.subarray(0, result.len));
};

const readPaddedStringEnd = (view: DataView, offset: number): number => {
  const len = view.getUint32(offset, true);
  return offset + 4 + ((len + 3) & ~3);
};

const readPaddedString = (
  view: DataView,
  offset: number,
): { value: string; next: number } => {
  const len = view.getUint32(offset, true);
  const start = offset + 4;
  const bytes = new Uint8Array(view.buffer, view.byteOffset + start, len);
  return {
    value: new TextDecoder().decode(bytes),
    next: start + ((len + 3) & ~3),
  };
};

const skipGraphicsState = (offset: number): number => offset + 48;

const parseSelectorCommandBuffer = (result: {
  data: Uint8Array;
  len: number;
}): {
  drawStrings: string[];
  softKeyUpdates: Array<{ left: string; right: string }>;
  drawImageCount: number;
  drawRgbCount: number;
} => {
  const view = new DataView(
    result.data.buffer,
    result.data.byteOffset,
    result.len,
  );
  const drawStrings: string[] = [];
  const softKeyUpdates: Array<{ left: string; right: string }> = [];
  let offset = 0;
  let drawImageCount = 0;
  let drawRgbCount = 0;
  while (offset < result.len) {
    const cmd = view.getUint8(offset);
    offset += 1;
    switch (cmd) {
      case CMD_DRAW_STRING: {
        const text = readPaddedString(view, skipGraphicsState(offset));
        drawStrings.push(text.value);
        offset = text.next + 12;
        break;
      }
      case 12:
        offset = skipGraphicsState(offset) + 36;
        break;
      case 13:
        offset = skipGraphicsState(offset) + 28;
        break;
      case 14:
        offset += 4;
        break;
      case 15:
        offset += 12;
        break;
      case CMD_DRAW_RGBA: {
        drawRgbCount += 1;
        const next = skipGraphicsState(offset);
        const pixelCount = view.getUint32(next + 16, true);
        offset = next + 20 + pixelCount * 4;
        break;
      }
      case 17:
        offset += 4;
        break;
      case CMD_LCDUI_DRAW_SHAPE_STREAM: {
        const next = skipGraphicsState(offset);
        const packetCount = view.getUint32(next, true);
        offset = next + 4;
        for (let packet = 0; packet < packetCount; packet++) {
          const stride = view.getUint32(offset + 4, true);
          const vertexCount = view.getUint32(offset + 8, true);
          offset += 12 + stride * vertexCount * 4;
        }
        break;
      }
      case CMD_DRAW_IMAGE_STREAM: {
        const next = skipGraphicsState(offset);
        const count = view.getUint32(next + 4, true);
        drawImageCount += count;
        offset = next + 8 + count * 12;
        break;
      }
      case 20:
        offset = readPaddedStringEnd(view, offset);
        break;
      case 21:
        offset += 4;
        break;
      case 30:
      case 31:
        offset += 4;
        break;
      case 32:
        offset += 8;
        break;
      case CMD_MENU_UPDATE_COMMANDS: {
        offset += 4;
        const left = readPaddedString(view, offset);
        const right = readPaddedString(view, left.next);
        softKeyUpdates.push({ left: left.value, right: right.value });
        offset = right.next;
        break;
      }
      case 50:
      case 51:
        offset = readPaddedStringEnd(view, offset);
        break;
      case 60:
      case 73:
        offset += 4;
        offset = readPaddedStringEnd(view, offset);
        break;
      case 61:
      case 62:
      case 63:
      case 64:
      case 65:
        offset += 4;
        break;
      case 66:
      case 67:
      case 68:
      case 70:
        offset += 8;
        break;
      case 69:
        offset += 12;
        break;
      case 71:
        offset = readPaddedStringEnd(view, offset);
        break;
      case 72:
        offset += 3;
        break;
      default:
        throw new Error(`Unknown command ${cmd} at offset ${offset - 1}`);
    }
  }
  return { drawStrings, softKeyUpdates, drawImageCount, drawRgbCount };
};

const countImageCommandsInBuffer = (result: {
  data: Uint8Array;
  len: number;
}): { drawImageCount: number; drawRgbCount: number } => {
  const { drawImageCount, drawRgbCount } = parseSelectorCommandBuffer(result);
  return { drawImageCount, drawRgbCount };
};

const runUntilCommandTextWithImages = (
  vm: JvmInstance,
  needle: string,
  maxSteps = 400,
): { text: string; drawImageCount: number; drawRgbCount: number } => {
  let text = '';
  let drawImageCount = 0;
  let drawRgbCount = 0;
  const flush = () => {
    const result = vm.flushCommandBuffer();
    if (!result) return;
    text += new TextDecoder().decode(result.data.subarray(0, result.len));
    const counts = countImageCommandsInBuffer(result);
    drawImageCount += counts.drawImageCount;
    drawRgbCount += counts.drawRgbCount;
  };
  flush();
  for (let i = 0; i < maxSteps && !text.includes(needle); i++) {
    let result = vm.step(16);
    for (let j = 0; j < 200 && result === STEP_OK; j++) {
      drainAsync(vm);
      flush();
      result = vm.step(0);
    }
    if (result === STEP_IDLE) {
      drainAsync(vm);
      const delay = vm.idleDelay();
      if (delay > 0 && delay <= 500) vm.step(delay);
    }
    flush();
  }
  return { text, drawImageCount, drawRgbCount };
};

const loadSuiteSwitchJar = (vm: JvmInstance): void => {
  const jarPath = resolve(__dirname, '../tests/jars/SuiteSwitch.jar');
  expect(vm.loadJar(new Uint8Array(readFileSync(jarPath)))).toBeGreaterThan(0);
};

const loadDemosJar = (vm: JvmInstance): void => {
  const jarPath = resolve(
    __dirname,
    '../../../packages/j2me-simulator/src/__tests__/official-demo/Demos/bin/Demos.jar',
  );
  expect(vm.loadJar(new Uint8Array(readFileSync(jarPath)))).toBeGreaterThan(0);
};

const drainAsync = (vm: JvmInstance): boolean => {
  const pending = vm.claimPendingAsync();
  for (const req of pending) {
    if (req.type === 'decode_image') {
      vm.resolveAsyncImage(req.id, 1, 1);
    } else if (req.type === 'get_pixels') {
      vm.resolveAsyncPixels(
        req.id,
        new Int32Array(Math.max(0, req.width * req.height)),
      );
    } else if (req.type === 'rms_load') {
      vm.resolveAsyncRmsLoad(req.id, null);
    } else if (req.type === 'rms_keys') {
      vm.resolveAsyncRmsKeys(req.id, []);
    }
  }
  return pending.length > 0;
};

const runUntilOutput = (
  vm: JvmInstance,
  needle: string,
  maxSteps = 400,
  options: { drainAsync?: boolean } = {},
): string => {
  const shouldDrainAsync = options.drainAsync ?? true;
  let output = vm.flushOutput();
  for (let i = 0; i < maxSteps && !output.includes(needle); i++) {
    let result = vm.step(16);
    for (let j = 0; j < 200 && result === STEP_OK; j++) {
      if (shouldDrainAsync) drainAsync(vm);
      result = vm.step(0);
    }
    if (result === STEP_IDLE) {
      if (shouldDrainAsync) drainAsync(vm);
      const delay = vm.idleDelay();
      if (delay > 0 && delay <= 500) vm.step(delay);
    }
    output += vm.flushOutput();
  }
  return output;
};

const runFrames = (
  vm: JvmInstance,
  count: number,
  options: { drainAsync?: boolean } = {},
): string => {
  const shouldDrainAsync = options.drainAsync ?? true;
  let output = '';
  for (let i = 0; i < count; i++) {
    let result = vm.step(16);
    for (let j = 0; j < 200 && result === STEP_OK; j++) {
      if (shouldDrainAsync) drainAsync(vm);
      result = vm.step(0);
    }
    if (result === STEP_IDLE) {
      if (shouldDrainAsync) drainAsync(vm);
      const delay = vm.idleDelay();
      if (delay > 0 && delay <= 500) vm.step(delay);
    }
    output += vm.flushOutput();
  }
  return output;
};

const runUntilCommandText = (
  vm: JvmInstance,
  needle: string,
  maxSteps = 400,
): string => {
  let text = flushCommandText(vm);
  for (let i = 0; i < maxSteps && !text.includes(needle); i++) {
    let result = vm.step(16);
    for (let j = 0; j < 200 && result === STEP_OK; j++) {
      drainAsync(vm);
      text += flushCommandText(vm);
      result = vm.step(0);
    }
    if (result === STEP_IDLE) {
      drainAsync(vm);
      const delay = vm.idleDelay();
      if (delay > 0 && delay <= 500) vm.step(delay);
    }
    text += flushCommandText(vm);
  }
  return text;
};

const completeAsync = (vm: JvmInstance, maxRounds = 100): void => {
  for (let i = 0; i < maxRounds; i++) {
    const hadPending = drainAsync(vm);
    const result = vm.resumeAsync();
    if (!hadPending && result !== 2) return;
    if (result === -1) return;
  }
};

const startSelector = (vm: JvmInstance): void => {
  const result = vm.startMidlet(0);
  expect([0, 2]).toContain(result);
  if (result === 2) completeAsync(vm);
};

describe('multi-MIDlet suite switching', () => {
  it('exposes selector softkeys as commands instead of drawing footer labels', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      const result = vm.flushCommandBuffer();
      expect(result).not.toBeNull();
      const parsed = parseSelectorCommandBuffer(result!);

      expect(parsed.softKeyUpdates).toContainEqual({
        left: '进入',
        right: '退出',
      });
      expect(parsed.drawStrings).toContain('选择应用');
      expect(parsed.drawStrings).not.toContain('进入');
      expect(parsed.drawStrings).not.toContain('退出');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('starts the selector then launches a selected MIDlet with clean static state', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);

      const selectorOutput = runFrames(vm, 20);
      expect(selectorOutput).not.toContain('FirstMIDlet start');
      expect(selectorOutput).not.toContain('SecondMIDlet start');

      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM8,
        0,
        vm.foregroundDisplayId(),
      );
      runFrames(vm, 10);
      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM5,
        0,
        vm.foregroundDisplayId(),
      );
      const appOutput = runUntilOutput(vm, 'SUITE_SWITCH_STATIC_CLEAN');

      expect(appOutput).toContain('SecondMIDlet start value=0 initCount=1');
      expect(appOutput).toContain('SUITE_SWITCH_STATIC_CLEAN');
      expect(appOutput).not.toContain('SUITE_SWITCH_STATIC_DIRTY');
      expect(appOutput).not.toContain('FirstMIDlet start');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('launches the selected MIDlet when the selector enter command fires', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM8,
        0,
        vm.foregroundDisplayId(),
      );
      runFrames(vm, 10);
      vm.pushNativeEvent(COMMAND_EVENT, 0, 0, 0, vm.foregroundDisplayId());
      const appOutput = runUntilOutput(vm, 'SUITE_SWITCH_STATIC_CLEAN');

      expect(appOutput).toContain('SecondMIDlet start value=0 initCount=1');
      expect(appOutput).toContain('SUITE_SWITCH_STATIC_CLEAN');
      expect(appOutput).not.toContain('FirstMIDlet start');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('launches the first MIDlet when the selector enter command fires on the default selection', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      vm.pushNativeEvent(COMMAND_EVENT, 0, 0, 0, vm.foregroundDisplayId());
      const output = runUntilOutput(vm, 'FirstMIDlet start');
      const commandText = flushCommandText(vm);

      expect(output).toContain('FirstMIDlet start value=41');
      expect(commandText).toContain('FirstMIDlet screen');
      expect(output).not.toContain('SecondMIDlet start');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('restores selector commands after destroying the first Demos MIDlet', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadDemosJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      vm.pushNativeEvent(COMMAND_EVENT, 0, 0, 0, vm.foregroundDisplayId());
      runFrames(vm, 60);
      vm.flushCommandBuffer();

      expect(vm.destroyMidlet(true)).toBe(0);
      const result = vm.flushCommandBuffer();
      expect(result).not.toBeNull();
      const parsed = parseSelectorCommandBuffer(result!);
      const lastSoftKeyUpdate =
        parsed.softKeyUpdates[parsed.softKeyUpdates.length - 1];

      expect(lastSoftKeyUpdate).toEqual({ left: '进入', right: '退出' });
      expect(parsed.drawStrings).toContain('选择应用');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('returns to the selector when the first MIDlet handles NUM5', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      vm.pushNativeEvent(COMMAND_EVENT, 0, 0, 0, vm.foregroundDisplayId());
      expect(runUntilOutput(vm, 'FirstMIDlet start')).toContain(
        'FirstMIDlet start value=41',
      );
      vm.flushOutput();
      vm.flushCommandBuffer();

      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM5,
        0,
        vm.foregroundDisplayId(),
      );
      const commandText = runUntilCommandText(vm, '选择应用');

      expect(commandText).toContain('选择应用');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('restores selector icon surfaces when NUM5 exits a cold-restored first MIDlet save state', async () => {
    const vm = await JvmInstance.create(false);
    let snapshot: Uint8Array | null = null;
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      vm.pushNativeEvent(COMMAND_EVENT, 0, 0, 0, vm.foregroundDisplayId());
      expect(runUntilOutput(vm, 'FirstMIDlet start')).toContain(
        'FirstMIDlet start value=41',
      );

      const selectorIconTargets = vm
        .collectImageSurfaceSnapshotTargets()
        .filter((target) => target.width === 32 && target.height === 32);
      expect(selectorIconTargets).toHaveLength(3);

      vm.clearImageSurfaceSnapshots();
      for (const target of selectorIconTargets) {
        vm.addImageSurfaceSnapshot(
          target,
          new Int32Array(target.width * target.height).fill(0xff00ff00),
        );
      }

      snapshot = vm.saveState();
      expect(snapshot).not.toBeNull();
    } finally {
      vm.destroy();
    }

    const restored = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(restored);
      restored.loadState(snapshot!);
      restored.flushOutput();
      restored.flushCommandBuffer();

      restored.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM5,
        0,
        restored.foregroundDisplayId(),
      );
      const selectorRender = runUntilCommandTextWithImages(
        restored,
        '选择应用',
      );

      expect(selectorRender.text).toContain('选择应用');
      expect(selectorRender.drawRgbCount).toBeGreaterThanOrEqual(3);
      expect(selectorRender.drawImageCount).toBeGreaterThan(0);
    } finally {
      restored.destroy();
    }
  }, 30_000);

  it('direct launch starts the requested MIDlet without showing the selector', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      expect(vm.startMidlet(2)).toBe(0);
      const output = runUntilOutput(vm, 'SUITE_SWITCH_STATIC_CLEAN');

      expect(output).toContain('SecondMIDlet start value=0 initCount=1');
      expect(output).toContain('SUITE_SWITCH_STATIC_CLEAN');
      expect(output).not.toContain('FirstMIDlet start');
      expect(output).not.toContain('选择应用');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('direct launch does not render the selector after the MIDlet exits', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      expect(vm.startMidlet(1)).toBe(0);
      expect(runUntilOutput(vm, 'FirstMIDlet start')).toContain(
        'FirstMIDlet start value=41',
      );
      vm.flushOutput();
      vm.flushCommandBuffer();

      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM5,
        0,
        vm.foregroundDisplayId(),
      );
      const commandText = runUntilCommandText(vm, '选择应用', 30);

      expect(commandText).not.toContain('选择应用');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('switches from selector into a MIDlet whose constructor waits for async init', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);
      const selectorSurfaceIds = new Set(
        vm
          .collectImageSurfaceSnapshotTargets()
          .map((target) => target.surface_id),
      );
      expect(selectorSurfaceIds.size).toBeGreaterThan(0);

      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM8,
        0,
        vm.foregroundDisplayId(),
      );
      runFrames(vm, 10);
      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM8,
        0,
        vm.foregroundDisplayId(),
      );
      runFrames(vm, 10);
      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM5,
        0,
        vm.foregroundDisplayId(),
      );

      const beforeResolve = runUntilOutput(
        vm,
        'AsyncSwitchMIDlet constructor start',
        80,
        { drainAsync: false },
      );
      expect(beforeResolve).toContain('AsyncSwitchMIDlet constructor start');
      expect(beforeResolve).not.toContain('AsyncSwitchMIDlet startApp');
      const pending = vm.claimPendingAsync();
      expect(pending.some((req) => req.type === 'decode_image')).toBe(true);
      const decodeImageRequest = pending.find(
        (req) => req.type === 'decode_image',
      );
      expect(decodeImageRequest?.surface_id).toBeDefined();
      expect(selectorSurfaceIds.has(decodeImageRequest!.surface_id)).toBe(
        false,
      );

      for (const req of pending) {
        if (req.type === 'decode_image') vm.resolveAsyncImage(req.id, 1, 1);
      }
      completeAsync(vm);
      const output = runUntilOutput(vm, 'SUITE_SWITCH_ASYNC_START_OK');

      expect(output).toContain('AsyncSwitchMIDlet constructor image w=1');
      expect(output).toContain('AsyncSwitchMIDlet startApp w=1');
      expect(output).toContain('SUITE_SWITCH_ASYNC_START_OK');
      expect(output).not.toContain('NullPointerException');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('drops queued selector events before the launched MIDlet receives input', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM8,
        0,
        vm.foregroundDisplayId(),
      );
      runFrames(vm, 10);
      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM5,
        0,
        vm.foregroundDisplayId(),
      );
      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM9,
        0,
        vm.foregroundDisplayId(),
      );

      const output = runUntilOutput(vm, 'SUITE_SWITCH_STATIC_CLEAN');
      const afterLaunchOutput = output.slice(
        output.indexOf('SecondMIDlet start'),
      );
      expect(afterLaunchOutput).toContain('SUITE_SWITCH_STATIC_CLEAN');
      expect(afterLaunchOutput).not.toContain('AsyncSwitchMIDlet');
      expect(afterLaunchOutput).not.toContain('SUITE_SWITCH_ASYNC_START_OK');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('stops threads from the previous MIDlet when returning to the selector', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      expect(vm.startMidlet(2)).toBe(0);
      const startedOutput = runUntilOutput(vm, 'SECOND_WORKER_TICK');
      expect(startedOutput).toContain('SECOND_WORKER_TICK');
      vm.flushOutput();

      expect(vm.destroyMidlet(true)).toBe(0);
      const selectorOutput = runUntilOutput(vm, '选择应用');
      expect(selectorOutput).toContain('SecondMIDlet destroy');

      vm.flushOutput();
      const postReturnOutput = runFrames(vm, 30);
      expect(postReturnOutput).not.toContain('SECOND_WORKER_TICK');
      expect(postReturnOutput).not.toContain('SecondMIDlet start');
    } finally {
      vm.destroy();
    }
  }, 30_000);

  it('returns to the selector after an async destroyApp completes', async () => {
    const vm = await JvmInstance.create(false);
    try {
      loadSuiteSwitchJar(vm);
      startSelector(vm);
      runFrames(vm, 20);

      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM8,
        0,
        vm.foregroundDisplayId(),
      );
      runFrames(vm, 10);
      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM8,
        0,
        vm.foregroundDisplayId(),
      );
      runFrames(vm, 10);
      vm.pushNativeEvent(
        KEY_EVENT,
        KEY_PRESSED,
        KEY_NUM5,
        0,
        vm.foregroundDisplayId(),
      );

      const constructorOutput = runUntilOutput(
        vm,
        'AsyncSwitchMIDlet constructor start',
        80,
        { drainAsync: false },
      );
      expect(constructorOutput).toContain(
        'AsyncSwitchMIDlet constructor start',
      );
      const startPending = vm.claimPendingAsync();
      expect(startPending.some((req) => req.type === 'decode_image')).toBe(
        true,
      );
      for (const req of startPending) {
        if (req.type === 'decode_image') vm.resolveAsyncImage(req.id, 1, 1);
      }
      completeAsync(vm);
      const started = runUntilOutput(vm, 'SUITE_SWITCH_ASYNC_START_OK');
      expect(started).toContain('SUITE_SWITCH_ASYNC_START_OK');
      vm.flushOutput();
      vm.flushCommandBuffer();

      expect(vm.destroyMidlet(true)).toBe(2);
      const beforeResolve = vm.flushOutput();
      expect(beforeResolve).toContain('AsyncSwitchMIDlet destroy start');
      expect(beforeResolve).not.toContain('SUITE_SWITCH_ASYNC_DESTROY_OK');

      const pending = vm.claimPendingAsync();
      expect(pending.some((req) => req.type === 'decode_image')).toBe(true);
      for (const req of pending) {
        if (req.type === 'decode_image') vm.resolveAsyncImage(req.id, 1, 1);
      }

      const resumeResult = vm.resumeAsync();
      expect(resumeResult).toBe(0);
      const commandText = runUntilCommandText(vm, '选择应用');
      const destroyOutput = vm.flushOutput();

      expect(destroyOutput).toContain('AsyncSwitchMIDlet destroy image w=1');
      expect(destroyOutput).toContain('SUITE_SWITCH_ASYNC_DESTROY_OK');
      expect(commandText).toContain('选择应用');
    } finally {
      vm.destroy();
    }
  }, 30_000);
});
