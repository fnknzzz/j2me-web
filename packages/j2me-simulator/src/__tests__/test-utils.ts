/// <reference path="../env.d.ts" />
import type { J2MPlatformBridge } from '../env';
import { type JarEntry, parseJarBuffer } from '../jar';
import { J2MESimulator, type J2MESimulatorOptions } from '../simulator';

interface MIDletRunResult {
  output: string;
  error?: Error;
}

export interface HookCall {
  category: string;
  method: string;
  args: unknown[];
  result?: unknown;
}

export class HooksRecorder {
  private calls: HookCall[] = [];

  record(category: string, method: string, args: unknown[], result?: unknown) {
    this.calls.push({ category, method, args, result });
  }

  getCalls(): HookCall[] {
    return this.calls;
  }

  getSnapshot(): string {
    const handleMap = new Map<number, string>();
    let handleCounter = 0;
    const surfaceIdMap = new Map<number, string>();
    let surfaceIdCounter = 0;

    const normalizeHandle = (value: number): string => {
      if (!handleMap.has(value)) {
        handleMap.set(value, `<handle-${++handleCounter}>`);
      }
      return handleMap.get(value)!;
    };

    const normalizeSurfaceId = (value: number): string => {
      if (!surfaceIdMap.has(value)) {
        surfaceIdMap.set(value, `<surface-${++surfaceIdCounter}>`);
      }
      return surfaceIdMap.get(value)!;
    };

    const normalizeValue = (
      value: unknown,
      isHandleContext = false,
      isSurfaceIdContext = false,
    ): unknown => {
      if (typeof value === 'number' && isHandleContext) {
        return normalizeHandle(value);
      }
      if (typeof value === 'number' && isSurfaceIdContext) {
        return normalizeSurfaceId(value);
      }
      if (typeof value === 'object' && value !== null) {
        if (Array.isArray(value)) {
          return value.map((v) => normalizeValue(v, false, false));
        }
        const normalized: Record<string, unknown> = {};
        for (const [key, val] of Object.entries(value)) {
          const isHandle = key.toLowerCase().includes('handle');
          const isSurfaceId = key === 'surfaceId';
          normalized[key] = normalizeValue(val, isHandle, isSurfaceId);
        }
        return normalized;
      }
      return value;
    };

    const lines: string[] = [];

    for (const call of this.calls) {
      const isHandleMethod =
        call.method.includes('Font') || call.method.includes('Image');

      const isMediaPlayerMethod =
        call.category === 'media' &&
        (call.method.includes('Player') ||
          call.method.includes('realize') ||
          call.method.includes('prefetch') ||
          call.method.includes('start') ||
          call.method.includes('stop') ||
          call.method.includes('deallocate') ||
          call.method.includes('close') ||
          call.method.includes('Duration') ||
          call.method.includes('MediaTime') ||
          call.method.includes('VolumeLevel') ||
          call.method.includes('VolumeMute') ||
          call.method.includes('ContentType'));

      const isSurfaceIdMethod =
        call.category === 'graphics' &&
        (call.method === 'createSurface' ||
          call.method === 'flush' ||
          call.method === 'drawImage' ||
          call.method === 'drawRegion');

      const normalizedArgs = call.args.map((arg, index) => {
        if (isSurfaceIdMethod && index === 0 && typeof arg === 'number') {
          return normalizeSurfaceId(arg);
        }
        if (isHandleMethod && index === 0 && typeof arg === 'number') {
          return normalizeHandle(arg);
        }
        if (isMediaPlayerMethod && index === 0 && typeof arg === 'number') {
          return normalizeHandle(arg);
        }
        return normalizeValue(arg, false, false);
      });

      const argsStr = normalizedArgs
        .map((arg) => JSON.stringify(arg))
        .join(', ');

      let line = `[${call.category}] ${call.method}(${argsStr})`;

      if (call.result !== undefined) {
        line += ` => ${JSON.stringify(normalizeValue(call.result, false, false))}`;
      }

      lines.push(line);
    }

    return lines.join('\n');
  }

  clear() {
    this.calls = [];
  }
}

export function createRecordingHooks(
  recorder: HooksRecorder,
): J2MPlatformBridge {
  const rmsStorage = new Map<string, ArrayBuffer>();
  let nextHandle = 1;
  const surfaceCanvasMap = new Map<number, HTMLCanvasElement>();
  const resolveSurfaceId = (value: unknown): number | null => {
    for (const [surfaceId, canvas] of surfaceCanvasMap.entries()) {
      if (canvas === value) {
        return surfaceId;
      }
    }
    return null;
  };

  return {
    print: (msg: string) => {
      recorder.record('print', 'print', [msg]);
    },
    println: (msg: string) => {
      recorder.record('print', 'println', [msg]);
    },

    j2me: {
      graphics: {
        createSurface: (surfaceId, width, height) => {
          recorder.record('graphics', 'createSurface', [
            surfaceId,
            width,
            height,
          ]);
          const canvas = {
            width,
            height,
            getContext: () => ({
              save: () => {},
              restore: () => {},
              setTransform: () => {},
              getImageData: (
                _gx: number,
                _gy: number,
                gw: number,
                gh: number,
              ) => ({
                data: new Uint8ClampedArray(gw * gh * 4).fill(255),
              }),
              clearRect: () => {},
              drawImage: () => {},
            }),
          } as unknown as HTMLCanvasElement;
          surfaceCanvasMap.set(surfaceId, canvas);
        },
        getSurfaceCanvas: (surfaceId: number) => {
          return surfaceCanvasMap.get(surfaceId);
        },
        flush: (surfaceId) => {
          recorder.record('graphics', 'flush', [surfaceId]);
        },
        drawString: (text, x, y, anchor, state) => {
          recorder.record('graphics', 'drawString', [
            text,
            x,
            y,
            anchor,
            state,
          ]);
        },
        drawShapeBatch: (pipeline, stride, vertices, state) => {
          recorder.record('graphics', 'drawShapeBatch', [
            pipeline,
            stride,
            `[${vertices.length / stride} vertices]`,
            state,
          ]);
        },
        drawLine: (x1, y1, x2, y2, state) => {
          recorder.record('graphics', 'drawLine', [x1, y1, x2, y2, state]);
        },
        drawRect: (x, y, w, h, state) => {
          recorder.record('graphics', 'drawRect', [x, y, w, h, state]);
        },
        fillRect: (x, y, w, h, state) => {
          recorder.record('graphics', 'fillRect', [x, y, w, h, state]);
        },
        copyArea: (x, y, width, height, dx, dy, anchor, state) => {
          recorder.record('graphics', 'copyArea', [
            x,
            y,
            width,
            height,
            dx,
            dy,
            anchor,
            state,
          ]);
        },
        drawRGBA: (rgbaData, x, y, width, height, state) => {
          recorder.record('graphics', 'drawRGBA', [
            `[${rgbaData.byteLength / 4} pixels]`,
            x,
            y,
            width,
            height,
            state,
          ]);
        },
        fillTriangle: (x1, y1, x2, y2, x3, y3, state) => {
          recorder.record('graphics', 'fillTriangle', [
            x1,
            y1,
            x2,
            y2,
            x3,
            y3,
            state,
          ]);
        },
        drawPolygon: (xPoints, xOffset, yPoints, yOffset, nPoints, state) => {
          recorder.record('graphics', 'drawPolygon', [
            xPoints,
            xOffset,
            yPoints,
            yOffset,
            nPoints,
            state,
          ]);
        },
        fillPolygon: (xPoints, xOffset, yPoints, yOffset, nPoints, state) => {
          recorder.record('graphics', 'fillPolygon', [
            xPoints,
            xOffset,
            yPoints,
            yOffset,
            nPoints,
            state,
          ]);
        },
        drawPixels: (
          pixels,
          transparency,
          offset,
          scanlength,
          x,
          y,
          width,
          height,
          state,
        ) => {
          recorder.record('graphics', 'drawPixels', [
            pixels,
            transparency,
            offset,
            scanlength,
            x,
            y,
            width,
            height,
            state,
          ]);
        },
        getPixels: (pixels, offset, scanlength, x, y, width, height, state) => {
          recorder.record('graphics', 'getPixels', [
            `[${pixels.length} pixels]`,
            offset,
            scanlength,
            x,
            y,
            width,
            height,
            state,
          ]);
        },
        drawRoundRect: (x, y, w, h, arcWidth, arcHeight, state) => {
          recorder.record('graphics', 'drawRoundRect', [
            x,
            y,
            w,
            h,
            arcWidth,
            arcHeight,
            state,
          ]);
        },
        fillRoundRect: (x, y, w, h, arcWidth, arcHeight, state) => {
          recorder.record('graphics', 'fillRoundRect', [
            x,
            y,
            w,
            h,
            arcWidth,
            arcHeight,
            state,
          ]);
        },
        drawArc: (x, y, w, h, startAngle, arcAngle, state) => {
          recorder.record('graphics', 'drawArc', [
            x,
            y,
            w,
            h,
            startAngle,
            arcAngle,
            state,
          ]);
        },
        fillArc: (x, y, w, h, startAngle, arcAngle, state) => {
          recorder.record('graphics', 'fillArc', [
            x,
            y,
            w,
            h,
            startAngle,
            arcAngle,
            state,
          ]);
        },
        drawRegion: (
          imageHandle,
          sx,
          sy,
          width,
          height,
          transform,
          dx,
          dy,
          anchor,
          state,
        ) => {
          const sourceSurfaceId = resolveSurfaceId(imageHandle);
          recorder.record('graphics', 'drawRegion', [
            sourceSurfaceId ?? imageHandle,
            sx,
            sy,
            width,
            height,
            transform,
            dx,
            dy,
            anchor,
            state,
          ]);
        },

        drawImageBatch: (imageHandle, draws, state) => {
          const sourceSurfaceId = resolveSurfaceId(imageHandle);
          for (let i = 0; i < draws.length; i += 3) {
            recorder.record('graphics', 'drawImage', [
              sourceSurfaceId ?? imageHandle,
              draws[i],
              draws[i + 1],
              draws[i + 2],
              state,
            ]);
          }
        },
      },

      font: {
        stringWidth: (text) => {
          const result = text.length * 8;
          recorder.record('font', 'stringWidth', [text], result);
          return result;
        },
        charWidth: (charCode) => {
          const result = 8;
          recorder.record('font', 'charWidth', [charCode], result);
          return result;
        },
        charsWidth: (charCodes) => {
          const result = charCodes.length * 8;
          recorder.record('font', 'charsWidth', [charCodes], result);
          return result;
        },
        getHeight: () => {
          const result = 16;
          recorder.record('font', 'getHeight', [], result);
          return result;
        },
        getBaselinePosition: () => {
          const result = 12;
          recorder.record('font', 'getBaselinePosition', [], result);
          return result;
        },
      },

      image: {
        platformCreateCanvas: (width, height) => {
          const handle = nextHandle++;
          recorder.record('image', 'createImage', [handle, height]);
          return { width, height, getContext: () => ({}) } as any;
        },
        platformLoadImage: async (_url) => {
          return { width: 100, height: 100 } as any;
        },
        platformDecodeImageData: async (_data, _mimeType) => {
          return { width: 100, height: 100 } as any;
        },
        platformCreateImageFromRGB: (
          width,
          height,
          _rgbData,
          _processAlpha,
        ) => {
          return { width, height, getContext: () => ({}) } as any;
        },
        platformGetImageRGB: (_source, _x, _y, width, height) => {
          return new Int32Array(width * height).fill(0xff000000);
        },
        createImageFromResource: (resourceName) => {
          recorder.record('image', 'createImageFromResource', [resourceName]);
          return Promise.resolve({ width: 100, height: 100 } as any);
        },
      },

      display: {
        getWidth: () => {
          const result = 240;
          recorder.record('display', 'getWidth', [], result);
          return result;
        },
        getHeight: () => {
          const result = 320;
          recorder.record('display', 'getHeight', [], result);
          return result;
        },
        sizeChanged: (width, height) => {
          recorder.record('display', 'sizeChanged', [width, height]);
        },
        isColor: () => {
          const result = true;
          recorder.record('display', 'isColor', [], result);
          return result;
        },
        numColors: () => {
          const result = 65536;
          recorder.record('display', 'numColors', [], result);
          return result;
        },
        numAlphaLevels: () => {
          const result = 256;
          recorder.record('display', 'numAlphaLevels', [], result);
          return result;
        },
        getBestImageWidth: (imageType) => {
          const result = 48;
          recorder.record('display', 'getBestImageWidth', [imageType], result);
          return result;
        },
        getBestImageHeight: (imageType) => {
          const result = 48;
          recorder.record('display', 'getBestImageHeight', [imageType], result);
          return result;
        },
        getBorderStyle: (highlighted) => {
          const result = 0;
          recorder.record('display', 'getBorderStyle', [highlighted], result);
          return result;
        },
        getColor: (colorSpecifier) => {
          const result = 0xffffff;
          recorder.record('display', 'getColor', [colorSpecifier], result);
          return result;
        },
        flashBacklight: (duration) => {
          const result = true;
          recorder.record('display', 'flashBacklight', [duration], result);
          return result;
        },
        vibrate: (duration) => {
          const result = true;
          recorder.record('display', 'vibrate', [duration], result);
          return result;
        },
      },

      canvas: {
        getGameAction: (keyCode) => {
          const result = 0;
          recorder.record('canvas', 'getGameAction', [keyCode], result);
          return result;
        },
        getKeyCode: (gameAction) => {
          const result = 0;
          recorder.record('canvas', 'getKeyCode', [gameAction], result);
          return result;
        },
        getKeyName: (keyCode) => {
          const result = `Key${keyCode}`;
          recorder.record('canvas', 'getKeyName', [keyCode], result);
          return result;
        },
        hasPointerEvents: () => {
          const result = true;
          recorder.record('canvas', 'hasPointerEvents', [], result);
          return result;
        },
        hasPointerMotionEvents: () => {
          const result = true;
          recorder.record('canvas', 'hasPointerMotionEvents', [], result);
          return result;
        },
        hasRepeatEvents: () => {
          const result = true;
          recorder.record('canvas', 'hasRepeatEvents', [], result);
          return result;
        },
      },

      gameCanvas: {
        flushGraphics: () => {
          recorder.record('gameCanvas', 'flushGraphics', []);
        },
        flushGraphicsRegion: (x, y, width, height) => {
          recorder.record('gameCanvas', 'flushGraphicsRegion', [
            x,
            y,
            width,
            height,
          ]);
        },
        getKeyStates: () => {
          const result = 0;
          recorder.record('gameCanvas', 'getKeyStates', [], result);
          return result;
        },
      },

      midlet: {
        onStateChanged: (state: 'active' | 'paused' | 'destroyed') => {
          recorder.record('midlet', 'onStateChanged', [state]);
        },
      },

      input: {
        keyPressed: (keyCode) => {
          recorder.record('input', 'keyPressed', [keyCode]);
        },
        keyReleased: (keyCode) => {
          recorder.record('input', 'keyReleased', [keyCode]);
        },
        keyRepeated: (keyCode) => {
          recorder.record('input', 'keyRepeated', [keyCode]);
        },
        pointerPressed: (x, y) => {
          recorder.record('input', 'pointerPressed', [x, y]);
        },
        pointerReleased: (x, y) => {
          recorder.record('input', 'pointerReleased', [x, y]);
        },
        pointerDragged: (x, y) => {
          recorder.record('input', 'pointerDragged', [x, y]);
        },
        setFullScreenMode: (fullScreen) => {
          recorder.record('input', 'setFullScreenMode', [fullScreen]);
        },
      },

      rms: {
        load: (key) => {
          const result = rmsStorage.get(key) ?? null;
          recorder.record('rms', 'load', [key], result);
          return result;
        },
        save: (key, value: ArrayBuffer) => {
          rmsStorage.set(key, value);
          recorder.record('rms', 'save', [
            key,
            `<ArrayBuffer:${value.byteLength}bytes>`,
          ]);
        },
        remove: (key) => {
          rmsStorage.delete(key);
          recorder.record('rms', 'remove', [key]);
        },
        keys: () => {
          const result = Array.from(rmsStorage.keys());
          recorder.record('rms', 'keys', [], result);
          return result;
        },
      },

      media: {
        createPlayer: async (request) => {
          const loggedRequest = { ...request } as Record<string, unknown>;
          delete loggedRequest.handle;
          recorder.record('media', 'createPlayer', [loggedRequest]);
          let volume = 100;
          let muted = false;
          return {
            play: async () => {
              recorder.record('media', 'play', []);
            },
            pause: () => {
              recorder.record('media', 'pause', []);
            },
            stop: () => {
              recorder.record('media', 'stop', []);
            },
            close: () => {
              recorder.record('media', 'close', []);
            },
            getDuration: () => 1000,
            getCurrentTime: () => 0,
            setCurrentTime: (time: number) => {
              recorder.record('media', 'setCurrentTime', [time]);
            },
            getVolume: () => volume,
            setVolume: (vol: number) => {
              volume = vol;
              recorder.record('media', 'setVolume', [vol]);
            },
            isMuted: () => muted,
            setMuted: (m: boolean) => {
              muted = m;
              recorder.record('media', 'setMuted', [m]);
            },
            setLoopCount: (count: number) => {
              recorder.record('media', 'setLoopCount', [count]);
            },
            getContentType: () => 'audio/midi',
          };
        },
        getSupportedContentTypes: (protocol) => {
          const result: string[] = [];
          recorder.record(
            'media',
            'getSupportedContentTypes',
            [protocol],
            result,
          );
          return result;
        },
        getSupportedProtocols: (contentType) => {
          const result: string[] = [];
          recorder.record(
            'media',
            'getSupportedProtocols',
            [contentType],
            result,
          );
          return result;
        },
        playTone: (note, duration, volume) => {
          recorder.record('media', 'playTone', [note, duration, volume]);
        },
        onPlayerStateChange: (playerHandle, state) => {
          recorder.record('media', 'onPlayerStateChange', [
            playerHandle,
            state,
          ]);
        },
        onPlayerEvent: (playerHandle, event, eventData) => {
          recorder.record('media', 'onPlayerEvent', [
            playerHandle,
            event,
            eventData,
          ]);
        },
      },

      pki: {
        getIssuer: () => {
          const result = 'Test Issuer';
          recorder.record('pki', 'getIssuer', [], result);
          return result;
        },
        getNotAfter: () => {
          const result = Date.now() + 365 * 24 * 60 * 60 * 1000;
          recorder.record('pki', 'getNotAfter', [], result);
          return result;
        },
        getNotBefore: () => {
          const result = Date.now();
          recorder.record('pki', 'getNotBefore', [], result);
          return result;
        },
        getSerialNumber: () => {
          const result = '123456';
          recorder.record('pki', 'getSerialNumber', [], result);
          return result;
        },
        getSigAlgName: () => {
          const result = 'SHA256withRSA';
          recorder.record('pki', 'getSigAlgName', [], result);
          return result;
        },
        getSubject: () => {
          const result = 'Test Subject';
          recorder.record('pki', 'getSubject', [], result);
          return result;
        },
        getType: () => {
          const result = 'X.509';
          recorder.record('pki', 'getType', [], result);
          return result;
        },
        getVersion: () => {
          const result = '3';
          recorder.record('pki', 'getVersion', [], result);
          return result;
        },
      },
    },
  };
}

export async function runMIDlet(
  options: J2MESimulatorOptions,
): Promise<MIDletRunResult> {
  let entries: JarEntry[];
  if (Array.isArray(options.jar)) {
    entries = options.jar;
  } else {
    entries = await parseJarBuffer(options.jar);
  }
  return runMIDletFromEntries(entries, options.hooks);
}

export async function runMIDletFromEntries(
  entries: JarEntry[],
  hooks?: J2MPlatformBridge,
): Promise<MIDletRunResult> {
  try {
    const sim = new J2MESimulator({
      jar: entries,
      hooks,
    });
    await sim.initialize();
    await sim.start();
    await drainRustMIDlet(sim);
    await sim.destroy(true);
    return { output: '' };
  } catch (error) {
    return { output: '', error: error as Error };
  }
}

const drainRustMIDlet = async (sim: J2MESimulator) => {
  const backend = (sim as unknown as { backend?: unknown }).backend as
    | {
        flushOutputToHooks?: () => Promise<void>;
        getAsyncStateCounts?: () => [number, number, number];
        runAsyncPumpToIdle?: (
          epoch?: number,
          initialClaimed?: unknown[],
        ) => Promise<void>;
        stopRunLoop?: () => void;
      }
    | undefined;
  const jvm = sim.getJvm();
  if (!jvm) {
    return;
  }

  backend?.stopRunLoop?.();
  let lastStatus = -1;
  for (let i = 0; i < 1000; i++) {
    await backend?.flushOutputToHooks?.();
    lastStatus = jvm.step(1);
    await backend?.runAsyncPumpToIdle?.();
    if (lastStatus === 0) {
      await backend?.flushOutputToHooks?.();
      return;
    }
    const asyncState = backend?.getAsyncStateCounts?.();
    if (lastStatus === 2 && asyncState?.every((count) => count === 0)) {
      await backend?.flushOutputToHooks?.();
      return;
    }
  }
  await backend?.flushOutputToHooks?.();
};
