import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { JvmInstance } from '../index';

const STEP_DEAD = 0;
const STEP_OK = 1;
const STEP_IDLE = 2;

const OLD_CMD_LCDUI_RENDER = 71;
const CMD_DRAW_STRING = 4;
const CMD_DRAW_REGION = 12;
const CMD_COPY_AREA = 13;
const CMD_FLUSH = 14;
const CMD_CREATE_SURFACE = 15;
const CMD_DRAW_RGBA = 16;
const CMD_DESTROY_SURFACE = 17;
const CMD_LCDUI_DRAW_SHAPE_STREAM = 18;
const CMD_DRAW_IMAGE_STREAM = 19;
const CMD_PRINTLN = 20;
const CMD_PRINT_CHAR = 21;
const CMD_DISPLAY_GET_WIDTH = 30;
const CMD_DISPLAY_GET_HEIGHT = 31;
const CMD_DISPLAY_SIZE_CHANGED = 32;
const CMD_MENU_UPDATE_COMMANDS = 40;
const CMD_RMS_LOAD = 50;
const CMD_RMS_KEYS = 51;
const CMD_MEDIA_CREATE_PLAYER = 60;
const CMD_MEDIA_CLOSE = 61;
const CMD_MEDIA_REALIZE = 62;
const CMD_MEDIA_PREFETCH = 63;
const CMD_MEDIA_PLAY = 64;
const CMD_MEDIA_STOP = 65;
const CMD_MEDIA_SET_VOLUME = 66;
const CMD_MEDIA_SET_MUTED = 67;
const CMD_MEDIA_SET_SEQUENCE = 68;
const CMD_MEDIA_PLAY_TONE = 69;
const CMD_MEDIA_SET_LOOP_COUNT = 70;
const CMD_LCDUI_SET_VISIBLE = 72;
const CMD_MEDIA_CREATE_PLAYER_BINARY = 73;
const CMD_TEXT_INPUT = 74;
const CMD_DATE_INPUT = 75;

const readPaddedStringEnd = (view: DataView, offset: number): number => {
  const len = view.getUint32(offset, true);
  return offset + 4 + ((len + 3) & ~3);
};

const skipGraphicsState = (offset: number): number => offset + 48;

const parseCommandIds = (data: Uint8Array, len: number): number[] => {
  const view = new DataView(data.buffer, data.byteOffset, len);
  const ids: number[] = [];
  let offset = 0;
  while (offset < len) {
    const cmd = view.getUint8(offset);
    ids.push(cmd);
    offset += 1;
    switch (cmd) {
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
        offset = next + 8 + count * 12;
        break;
      }
      case CMD_DRAW_STRING:
        offset = readPaddedStringEnd(view, skipGraphicsState(offset)) + 12;
        break;
      case CMD_DRAW_RGBA: {
        const next = skipGraphicsState(offset);
        const pixelCount = view.getUint32(next + 16, true);
        offset = next + 20 + pixelCount * 4;
        break;
      }
      case CMD_DRAW_REGION:
        offset = skipGraphicsState(offset) + 36;
        break;
      case CMD_COPY_AREA:
        offset = skipGraphicsState(offset) + 28;
        break;
      case CMD_FLUSH:
        offset += 4;
        break;
      case CMD_CREATE_SURFACE:
        offset += 12;
        break;
      case CMD_DESTROY_SURFACE:
      case CMD_PRINT_CHAR:
      case CMD_DISPLAY_GET_WIDTH:
      case CMD_DISPLAY_GET_HEIGHT:
      case CMD_MEDIA_CLOSE:
      case CMD_MEDIA_REALIZE:
      case CMD_MEDIA_PREFETCH:
      case CMD_MEDIA_STOP:
        offset += 4;
        break;
      case CMD_PRINTLN:
      case CMD_RMS_LOAD:
      case CMD_RMS_KEYS:
        offset = readPaddedStringEnd(view, offset);
        break;
      case CMD_DISPLAY_SIZE_CHANGED:
      case CMD_MEDIA_SET_VOLUME:
      case CMD_MEDIA_SET_MUTED:
      case CMD_MEDIA_SET_SEQUENCE:
      case CMD_MEDIA_SET_LOOP_COUNT:
        offset += 8;
        break;
      case CMD_MENU_UPDATE_COMMANDS:
        offset += 4;
        offset = readPaddedStringEnd(view, offset);
        offset = readPaddedStringEnd(view, offset);
        break;
      case CMD_MEDIA_CREATE_PLAYER: {
        const locatorLen = view.getUint32(offset + 4, true);
        offset += 8 + ((locatorLen + 3) & ~3);
        break;
      }
      case CMD_MEDIA_PLAY:
      case CMD_MEDIA_PLAY_TONE:
        offset += 12;
        break;
      case CMD_LCDUI_SET_VISIBLE:
        offset += 3;
        break;
      case CMD_MEDIA_CREATE_PLAYER_BINARY: {
        const contentTypeLen = view.getUint32(offset + 12, true);
        const dataLenOffset = offset + 16 + ((contentTypeLen + 3) & ~3);
        const dataLen = view.getUint32(dataLenOffset, true);
        offset = dataLenOffset + 4 + ((dataLen + 3) & ~3);
        break;
      }
      case CMD_TEXT_INPUT:
      case CMD_DATE_INPUT:
        offset += 4;
        break;
      default:
        throw new Error(`Unknown command ${cmd} at offset ${offset - 1}`);
    }
  }
  return ids;
};

const resolvePendingAsync = (vm: JvmInstance): number => {
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
  return pending.length;
};

describe('AdvancedUISuite LCDUI', () => {
  it('should Java-paint the initial List on startup', async () => {
    const jarPath = resolve(
      __dirname,
      '../tests/fixtures/demo-jars/AdvancedUISuite.jar',
    );
    const jarData = new Uint8Array(readFileSync(jarPath));
    const vm = await JvmInstance.create(false);
    vm.loadJar(jarData);
    let startResult = vm.startMidlet(1);
    for (let i = 0; i < 20 && startResult === 2; i++) {
      resolvePendingAsync(vm);
      startResult = vm.resumeAsync();
    }
    expect(startResult).not.toBe(-1);

    for (let i = 0; i < 20; i++) {
      const result = vm.step(16);
      resolvePendingAsync(vm);
      if (result === STEP_DEAD) break;
      if (result === STEP_IDLE) vm.step(0);
      if (result === STEP_OK) vm.step(0);
    }

    const result = vm.flushCommandBuffer();
    expect(result).not.toBeNull();
    expect(result!.len).toBeGreaterThan(0);

    const { data, len } = result!;
    const commandIds = parseCommandIds(data, len);
    const sawOldLcduiRender = commandIds.includes(OLD_CMD_LCDUI_RENDER);
    const sawGraphicsDraw = commandIds.some(
      (id) =>
        id === CMD_LCDUI_DRAW_SHAPE_STREAM ||
        id === CMD_DRAW_STRING ||
        id === CMD_DRAW_IMAGE_STREAM,
    );
    expect(sawOldLcduiRender).toBe(false);
    expect(sawGraphicsDraw).toBe(true);

    vm.destroy();
  });
});
