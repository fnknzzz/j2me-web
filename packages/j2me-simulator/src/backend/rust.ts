import type {
  AsyncRequest,
  DerivedImageRestore,
  ImageSurfaceSnapshotTarget,
  JvmRewindSnapshot,
} from '@jsjvm/j2me-core';
import { JvmInstance } from '@jsjvm/j2me-core';
import m3gWebgpuWireSchema from '@jsjvm/j2me-core/m3g-wire';
import { parseJar } from '../jar';
import { MIDletState } from '../midlet-state';
import type { J2MEPerformanceCollector } from '../performance';
import type { J2MESimulatorOptions } from '../types';
import { resolveSizedWireLayout } from '../wire-layout';
import { BluetoothHostBridge } from './bluetooth-host-bridge';
import {
  MediaCommandExecutor,
  type MediaCommandSnapshot,
} from './media-command-executor';
import {
  unwrapStateWithMediaSnapshot,
  wrapStateWithMediaSnapshot,
} from './media-snapshot-envelope';

type DecodeImageRequest = Extract<AsyncRequest, { type: 'decode_image' }>;
type DecodeM3gExternalImageRequest = Extract<
  AsyncRequest,
  { type: 'decode_m3g_external_image' }
>;
type GetPixelsRequest = Extract<AsyncRequest, { type: 'get_pixels' }>;
type ReadM3gImageRequest = Extract<AsyncRequest, { type: 'read_m3g_image' }>;
type RmsLoadRequest = Extract<AsyncRequest, { type: 'rms_load' }>;
type RmsKeysRequest = Extract<AsyncRequest, { type: 'rms_keys' }>;
type TextInputRequest = Extract<AsyncRequest, { type: 'text_input' }>;
type DateInputRequest = Extract<AsyncRequest, { type: 'date_input' }>;
type BluetoothRequest = Extract<AsyncRequest, { type: 'bluetooth' }>;

const READBACK_CONTEXT_OPTIONS: CanvasRenderingContext2DSettings = {
  willReadFrequently: true,
};
const rawM3gDrawPacket = m3gWebgpuWireSchema.drawPacket;
const M3G_DRAW_PACKET_HEADER = resolveSizedWireLayout(
  rawM3gDrawPacket.header,
  'M3G draw packet header',
);
const M3G_DRAW_PACKET_INSTANCE = resolveSizedWireLayout(
  rawM3gDrawPacket.instance,
  'M3G draw packet instance',
);
const M3G_DRAW_PACKET = {
  commandId: rawM3gDrawPacket.commandId,
  headerBytes: M3G_DRAW_PACKET_HEADER.bytes,
  instanceBytes: M3G_DRAW_PACKET_INSTANCE.bytes,
  offset: M3G_DRAW_PACKET_HEADER.offset,
};
const rawM3gDerivedIndexBuffer = m3gWebgpuWireSchema.derivedIndexBuffer;
const M3G_DERIVED_INDEX_BUFFER_HEADER = resolveSizedWireLayout(
  rawM3gDerivedIndexBuffer.header,
  'M3G derived index buffer header',
);
const M3G_DERIVED_INDEX_BUFFER = {
  commandId: rawM3gDerivedIndexBuffer.commandId,
  headerBytes: M3G_DERIVED_INDEX_BUFFER_HEADER.bytes,
  offset: M3G_DERIVED_INDEX_BUFFER_HEADER.offset,
};
const rawM3gClear = m3gWebgpuWireSchema.clear;
const M3G_CLEAR_HEADER = resolveSizedWireLayout(
  rawM3gClear.header,
  'M3G clear header',
);
const M3G_CLEAR = {
  commandId: rawM3gClear.commandId,
  headerBytes: M3G_CLEAR_HEADER.bytes,
};

// Binary command IDs (must match Rust vm::cmd constants)
// Graphics (1-19)
const CMD_DRAW_STRING = 4;
const CMD_DRAW_RGBA = 16;
const CMD_DRAW_REGION = 12;
const CMD_COPY_AREA = 13;
const CMD_FLUSH = 14;
const CMD_CREATE_SURFACE = 15;
const CMD_DESTROY_SURFACE = 17;
const CMD_LCDUI_DRAW_SHAPE_STREAM = 18;
const CMD_DRAW_IMAGE_STREAM = 19;
// Text output (20-21)
const CMD_PRINTLN = 20;
const CMD_PRINT_CHAR = 21;
// Display (30-32)
const CMD_DISPLAY_GET_WIDTH = 30;
const CMD_DISPLAY_GET_HEIGHT = 31;
const CMD_DISPLAY_SIZE_CHANGED = 32;
// Menu (40)
const CMD_MENU_UPDATE_COMMANDS = 40;
// RMS (50-51)
const CMD_RMS_LOAD = 50;
const CMD_RMS_KEYS = 51;
// Media (60-69)
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
// LCDUI visibility (72)
const CMD_LCDUI_SET_VISIBLE = 72;
// Binary media (73)
const CMD_MEDIA_CREATE_PLAYER_BINARY = 73;
// Host input overlays (74-75)
const CMD_TEXT_INPUT = 74;
const CMD_DATE_INPUT = 75;

// M3G 3D commands (128+)
const CMD_M3G_CLEAR = M3G_CLEAR.commandId;
const CMD_M3G_BIND_TARGET = 135;
const CMD_M3G_RELEASE_TARGET = 136;
const CMD_M3G_CREATE_VERTEX_ARRAY = 140;
const CMD_M3G_CREATE_INDEX_BUFFER = 150;
const CMD_M3G_CREATE_BACKGROUND = 175;
const CMD_M3G_BG_SET_COLOR = 176;
const CMD_M3G_BG_SET_ENABLE = 178;
const CMD_MASCOT_BIND_TARGET = 190;
const CMD_MASCOT_RELEASE_TARGET = 191;
const CMD_MASCOT_DRAW_PRIMITIVES = 192;
const CMD_MASCOT_FLUSH = 193;
const CMD_MASCOT_UPLOAD_TEXTURE = 194;
const CMD_MASCOT_DRAW_TEXTURED = 195;
const CMD_MASCOT_DELETE_TEXTURE = 196;
const CMD_MASCOT_RESET_TEXTURES = 197;
const CMD_MASCOT_DRAW_COLOR_MATERIAL = 198;
const CMD_MASCOT_CLEAR_DEPTH = 199;

const readBinaryState = (view: DataView, offset: number) => {
  const surfaceId = view.getUint32(offset, true);
  const clipX = view.getInt32(offset + 4, true);
  const clipY = view.getInt32(offset + 8, true);
  const clipWidth = view.getInt32(offset + 12, true);
  const clipHeight = view.getInt32(offset + 16, true);
  const translateX = view.getInt32(offset + 20, true);
  const translateY = view.getInt32(offset + 24, true);
  const color = view.getInt32(offset + 28, true);
  const strokeStyle = view.getInt32(offset + 32, true);
  const fontFace = view.getInt32(offset + 36, true);
  const fontStyle = view.getInt32(offset + 40, true);
  const fontSize = view.getInt32(offset + 44, true);
  return {
    state: {
      surfaceId,
      color,
      clipX,
      clipY,
      clipWidth,
      clipHeight,
      translateX,
      translateY,
      strokeStyle,
      fontHandle: 0,
      fontFace,
      fontStyle,
      fontSize,
    },
    next: offset + 48,
  };
};

const textDecoder = new TextDecoder();
const nativeLittleEndian = new Uint8Array(new Uint32Array([1]).buffer)[0] === 1;

const readBinaryString = (
  view: DataView,
  offset: number,
): { value: string; next: number } => {
  const len = view.getUint32(offset, true);
  const start = offset + 4;
  const bytes = new Uint8Array(view.buffer, view.byteOffset + start, len);
  const value = textDecoder.decode(bytes);
  // Align text length to 4 bytes (relative padding, not absolute alignment)
  const next = start + ((len + 3) & ~3);
  return { value, next };
};

export class RustJ2MESimulatorBackend {
  private jvm: JvmInstance | null = null;
  private started = false;
  private state: MIDletState = MIDletState.DESTROYED;
  private readonly mediaCommands: MediaCommandExecutor;
  private readonly rewindMediaSnapshots = new WeakMap<
    JvmRewindSnapshot,
    MediaCommandSnapshot
  >();
  private running = false;
  // Write-through cache for RMS: sync save is immediately visible to async load
  private rmsCache = new Map<string, ArrayBuffer>();
  // Accumulator for CMD_PRINT_CHAR until newline
  private printCharBuf = '';
  private foregroundDisplayId = 0;
  // Decoded images waiting to be drawn onto their surface once host_create_surface fires
  private pendingDecodedImages = new Map<number, CanvasImageSource>();
  private jarResources = new Map<string, Uint8Array>();
  private speedMultiplier = 1;
  private stateLoadEpoch = 0;
  private asyncPumpPromise: Promise<void> | null = null;
  private gpuFencePromise: Promise<void> | null = null;
  private scheduleTick: (() => void) | null = null;
  private readonly bluetoothBridge: BluetoothHostBridge;
  private readonly performanceCollector: J2MEPerformanceCollector | null;
  constructor(private options: J2MESimulatorOptions) {
    this.bluetoothBridge = new BluetoothHostBridge(
      options.hooks?.j2me?.bluetooth,
    );
    this.performanceCollector = options.performanceCollector ?? null;
    this.mediaCommands = new MediaCommandExecutor({
      createPlayer: options.hooks?.j2me?.media?.createPlayer,
      onPlayerEnded: (handle) => {
        if (this.jvm) this.pushEvent(45, handle, 0, 0, 1);
      },
    });
  }

  private stepJvmTimed(timeMs: number): number {
    const startedAt = this.performanceCollector?.now() ?? 0;
    const status = this.jvm!.step(timeMs);
    this.performanceCollector?.addTiming('vmStep', startedAt);
    return status;
  }

  private dispatchCmdTimed(
    view: DataView,
    offset: number,
    cmdId: number,
  ): { offset: number; asyncOp?: () => Promise<void> } {
    const startedAt = this.performanceCollector?.now() ?? 0;
    const result = this.dispatchCmd(view, offset, cmdId);
    if (cmdId <= CMD_DRAW_IMAGE_STREAM) {
      this.performanceCollector?.addTiming('lcdUiEncode', startedAt);
    } else if (cmdId >= 128) {
      this.performanceCollector?.addTiming('m3gEncode', startedAt);
    }
    return result;
  }

  private guessImageMimeType(resourceName: string): string {
    const lower = resourceName.toLowerCase();
    if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
    if (lower.endsWith('.gif')) return 'image/gif';
    if (lower.endsWith('.webp')) return 'image/webp';
    return 'image/png';
  }

  /** Push a native event and immediately wake the run loop if idle. */
  private pushEvent(
    eventType: number,
    p1: number,
    p2: number,
    p3: number,
    p4: number,
  ): void {
    this.jvm!.pushNativeEvent(eventType, p1, p2, p3, p4);
    this.scheduleTick?.();
  }

  private getForegroundDisplayId(): number {
    return this.jvm?.foregroundDisplayId() ?? this.foregroundDisplayId;
  }

  private drawDecodedImageToSurface(
    surfaceId: number,
    source: CanvasImageSource,
  ): boolean | Promise<boolean> {
    const gfxHooks = this.options.hooks?.j2me?.graphics;
    if (gfxHooks?.importImageToSurface) {
      const result = gfxHooks.importImageToSurface(surfaceId, source as any);
      if (result && typeof (result as Promise<void>).then === 'function') {
        return (result as Promise<void>).then(() => true);
      }
      return true;
    }
    const canvas = gfxHooks?.getSurfaceCanvas?.(surfaceId);
    if (!canvas) return false;
    const ctx = (canvas as unknown as HTMLCanvasElement).getContext(
      '2d',
      READBACK_CONTEXT_OPTIONS,
    );
    if (!ctx) return false;
    ctx.clearRect(
      0,
      0,
      (canvas as unknown as HTMLCanvasElement).width,
      (canvas as unknown as HTMLCanvasElement).height,
    );
    ctx.drawImage(source, 0, 0);
    return true;
  }

  private queueOrDrawDecodedImage(
    surfaceId: number,
    source: CanvasImageSource,
  ): void {
    const result = this.drawDecodedImageToSurface(surfaceId, source);
    if (result && typeof (result as Promise<boolean>).then === 'function') {
      (result as Promise<boolean>)
        .then((drawn) => {
          if (drawn) this.pendingDecodedImages.delete(surfaceId);
        })
        .catch(() => {
          this.pendingDecodedImages.set(surfaceId, source);
        });
      return;
    }
    if (result) {
      this.pendingDecodedImages.delete(surfaceId);
      return;
    }
    this.pendingDecodedImages.set(surfaceId, source);
  }

  private createSurfaceRenderState(
    surfaceId: number,
    width: number,
    height: number,
  ) {
    return {
      surfaceId,
      color: 0,
      clipX: 0,
      clipY: 0,
      clipWidth: width,
      clipHeight: height,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };
  }

  private tryRestoreDerivedImage(restore: DerivedImageRestore): boolean {
    const gfxHooks = this.options.hooks?.j2me?.graphics;
    const source = gfxHooks?.getSurfaceCanvas?.(restore.source_surface_id);
    if (!source) return false;
    gfxHooks?.drawRegion?.(
      source,
      restore.x,
      restore.y,
      restore.region_width,
      restore.region_height,
      restore.transform,
      0,
      0,
      20,
      this.createSurfaceRenderState(
        restore.surface_id,
        restore.width,
        restore.height,
      ) as any,
    );
    return true;
  }

  private async restorePendingDerivedImages(epoch: number): Promise<void> {
    const restores = this.jvm?.takePendingDerivedImageRestores() ?? [];
    if (restores.length === 0) return;
    const pending = [...restores];
    for (let pass = 0; pass < restores.length; pass += 1) {
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      let progressed = false;
      for (let i = pending.length - 1; i >= 0; i -= 1) {
        if (this.tryRestoreDerivedImage(pending[i])) {
          pending.splice(i, 1);
          progressed = true;
        }
      }
      if (pending.length === 0 || !progressed) break;
      await Promise.resolve();
    }
  }

  private async restorePendingResourceImages(epoch: number): Promise<void> {
    const restores = this.jvm?.takePendingResourceImageRestores() ?? [];
    if (restores.length === 0) {
      await this.restorePendingDerivedImages(epoch);
      return;
    }
    const imageHooks = this.options.hooks?.j2me?.image;
    if (!imageHooks?.platformDecodeImageData) {
      await this.restorePendingDerivedImages(epoch);
      return;
    }

    for (const restore of restores) {
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      const bytes = this.jarResources.get(restore.resource_name);
      if (!bytes) continue;
      try {
        const source = await imageHooks.platformDecodeImageData(
          bytes,
          this.guessImageMimeType(restore.resource_name),
        );
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
        this.queueOrDrawDecodedImage(
          restore.surface_id,
          source as CanvasImageSource,
        );
      } catch {
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      }
    }
    await this.restorePendingDerivedImages(epoch);
  }

  async initialize(): Promise<void> {
    const entries = await parseJar(this.options.jar);
    this.jarResources = new Map(
      entries
        .filter((entry) => !entry.name.endsWith('.class'))
        .map((entry) => [
          entry.name,
          entry.data instanceof Uint8Array
            ? entry.data
            : new Uint8Array(entry.data),
        ]),
    );

    const gfxHooks = this.options.hooks?.j2me?.graphics;
    JvmInstance.setCreateSurfaceCallback(
      (surfaceId: number, width: number, height: number) => {
        gfxHooks?.createSurface?.(surfaceId, width, height);
        // If a decoded image is waiting for this surface, draw it now
        const source = this.pendingDecodedImages.get(surfaceId);
        if (source) {
          this.pendingDecodedImages.delete(surfaceId);
          this.queueOrDrawDecodedImage(surfaceId, source);
        }
      },
    );

    // RMS: load and keys are async (via Rust-side claim/resume state machine),
    // save and remove are fire-and-forget (sync WASM FFI, adapter may return Promise).
    // A write-through cache ensures that a synchronous save is immediately visible
    // to a subsequent async load, avoiding read-after-write race conditions.
    JvmInstance.setRmsLoadCallback(() => null); // unused; load goes through async path
    JvmInstance.setRmsSaveCallback((key: string, data: ArrayBuffer) => {
      this.rmsCache.set(key, data);
      // Flush pending commands so that save appears after preceding output
      this.flushPendingCommands();
      this.options.hooks?.j2me?.rms?.save?.(key, data);
    });
    JvmInstance.setRmsRemoveCallback((key: string) => {
      this.rmsCache.delete(key);
      // Flush pending commands so that remove appears after preceding output
      this.flushPendingCommands();
      this.options.hooks?.j2me?.rms?.remove?.(key);
    });
    JvmInstance.setRmsKeysCallback(() => []); // unused; keys goes through async path

    const fontHooks = this.options.hooks?.j2me?.font;
    JvmInstance.setMeasureTextCallback(
      (text: string, face: number, style: number, size: number) => {
        return fontHooks?.stringWidth?.(text, face, style, size) ?? 0;
      },
    );
    JvmInstance.setFontMetricsCallback(
      (face: number, style: number, size: number) => {
        const height = fontHooks?.getHeight?.(face, style, size) ?? 16;
        const baseline =
          fontHooks?.getBaselinePosition?.(face, style, size) ?? 12;
        return { height, baseline };
      },
    );
    JvmInstance.setMediaTimeCallback((handle: number) => {
      return this.mediaCommands.getCurrentTime(handle);
    });
    JvmInstance.setMediaDurationCallback((handle: number) => {
      return this.mediaCommands.getDuration(handle);
    });
    JvmInstance.setSetMediaTimeCallback((handle: number, timeMs: number) => {
      this.mediaCommands.setCurrentTime(handle, timeMs);
    });

    const jvm = await JvmInstance.create(false);

    jvm.loadEntries(
      entries.map((e) => ({
        name: e.name,
        data: new Uint8Array(e.data),
      })),
    );

    this.jvm = jvm;

    // Expose forceGc on window for debugging
    if (typeof window !== 'undefined') {
      (window as any).__jvmForceGc = () => {
        this.jvm?.forceGc();
      };
      // Expose repaint for testing host-triggered canvas repaint
      (window as any).__jvmRepaint = () => {
        if (!this.jvm) return;
        this.jvm.repaint();
        this.flushOutputToHooksSync();
      };
    }

    const displayWidth = this.options.hooks?.j2me?.display?.getWidth?.() ?? 240;
    const displayHeight =
      this.options.hooks?.j2me?.display?.getHeight?.() ?? 320;
    jvm.setDisplaySize(displayWidth, displayHeight);
    this.started = false;
    this.state = MIDletState.PAUSED;
  }

  getMIDletState(): MIDletState {
    return this.state;
  }

  async start(): Promise<void> {
    if (!this.jvm) {
      throw new Error('Simulator not initialized. Call initialize() first.');
    }
    if (this.state === MIDletState.ACTIVE) {
      return;
    }

    const launchMode = this.options.midletNumber ?? 0;
    const result = this.jvm.startMidlet(launchMode);
    await this.flushOutputToHooks();
    await this.bluetoothBridge.close();

    if (result === 2) {
      // ASYNC_PENDING — process async requests in a loop
      await this.processAsyncLoop();
    } else if (result < 0) {
      throw new Error(`Failed to start suite (launchMode=${launchMode})`);
    }

    this.started = true;
    this.state = MIDletState.ACTIVE;
    this.startRunLoop();
  }

  async pause(): Promise<void> {
    if (!this.jvm || !this.started) {
      throw new Error('Simulator not started. Call start() first.');
    }
    if (this.state === MIDletState.PAUSED) {
      return;
    }
    this.stopRunLoop();
    this.jvm.pauseMidlet();
    await this.flushOutputToHooks();
    this.state = MIDletState.PAUSED;
  }

  async suspendExecution(): Promise<void> {
    if (!this.jvm || !this.started) {
      throw new Error('Simulator not started. Call start() first.');
    }
    this.stopRunLoop();
  }

  async resumeExecution(): Promise<void> {
    if (!this.jvm || !this.started) {
      throw new Error('Simulator not started. Call start() first.');
    }
    this.startRunLoop();
  }

  async destroy(unconditional = true): Promise<void> {
    this.stopRunLoop();
    if (!this.jvm || !this.started) {
      this.state = MIDletState.DESTROYED;
      return;
    }
    const status = this.jvm.destroyMidlet(unconditional);
    if (status === 2) {
      // ASYNC_PENDING — destroyApp needs async operations resolved
      await this.processAsyncLoop();
    }
    await this.flushOutputToHooks();
    this.started = false;
    this.state = MIDletState.DESTROYED;
    JvmInstance.setCreateSurfaceCallback(null);
  }

  // --- MIDP Event Constants ---
  // EventTypes: KEY_EVENT=1, PEN_EVENT=2
  // EventConstants: PRESSED=1, RELEASED=2, REPEATED=3, DRAGGED=3
  // NativeEvent fields: type=eventType, intParam1=action, intParam2=keyCode/x, intParam3=y, intParam4=displayId

  async keyPressed(keyCode: number): Promise<void> {
    if (!this.jvm || !this.started) return;

    this.pushEvent(1, 1, keyCode, 0, this.getForegroundDisplayId()); // KEY_EVENT, PRESSED, keyCode
  }

  async keyReleased(keyCode: number): Promise<void> {
    if (!this.jvm || !this.started) return;
    this.pushEvent(1, 2, keyCode, 0, this.getForegroundDisplayId()); // KEY_EVENT, RELEASED, keyCode
  }

  async keyRepeated(keyCode: number): Promise<void> {
    if (!this.jvm || !this.started) return;
    this.pushEvent(1, 3, keyCode, 0, this.getForegroundDisplayId()); // KEY_EVENT, REPEATED, keyCode
  }

  async pointerPressed(x: number, y: number): Promise<void> {
    if (!this.jvm || !this.started) return;
    this.pushEvent(2, 1, x, y, this.getForegroundDisplayId()); // PEN_EVENT, PRESSED, x, y
  }

  async pointerDragged(x: number, y: number): Promise<void> {
    if (!this.jvm || !this.started) return;
    this.pushEvent(2, 3, x, y, this.getForegroundDisplayId()); // PEN_EVENT, DRAGGED, x, y
  }

  async pointerReleased(x: number, y: number): Promise<void> {
    if (!this.jvm || !this.started) return;
    this.pushEvent(2, 2, x, y, this.getForegroundDisplayId()); // PEN_EVENT, RELEASED, x, y
  }
  async showNotify(): Promise<void> {}
  async hideNotify(): Promise<void> {}
  async sizeChanged(_width: number, _height: number): Promise<void> {}
  async commandAction(commandId: number): Promise<void> {
    if (!this.jvm || !this.started) return;
    // COMMAND_EVENT=3, intParam1=commandId, intParam4=displayId
    this.pushEvent(3, commandId, 0, 0, this.getForegroundDisplayId());
  }

  private clearHostOverlayState(): void {
    const lcdUi = this.options.hooks?.j2me?.lcdUi;
    lcdUi?.updateSoftKeyLabels?.({});
    lcdUi?.textInput?.({ show: false });
    lcdUi?.dateInput?.({ show: false });
  }

  async itemCommandAction(
    _commandHandle: number,
    _itemHandle: number,
  ): Promise<void> {}
  async itemStateChanged(_itemHandle: number): Promise<void> {}
  async repaint(): Promise<void> {}
  async repaintRegion(
    _x: number,
    _y: number,
    _width: number,
    _height: number,
  ): Promise<void> {}
  async serviceRepaints(): Promise<void> {}

  dumpThreads(): string {
    return this.jvm?.dumpThreads() ?? '';
  }

  setSpeed(multiplier: number): void {
    this.speedMultiplier = multiplier;
    // Sync to Rust/WASM so Thread.sleep deadlines use the new speed
    if (this.jvm) {
      this.jvm.setSpeedMultiplier(multiplier);
    }
  }

  getSpeed(): number {
    return this.speedMultiplier;
  }

  async saveState(): Promise<Uint8Array | null> {
    if (!this.jvm) return null;
    const wasRunning = this.running;
    if (wasRunning) {
      this.stopRunLoop();
    }
    try {
      const drained = await this.waitForSnapshotSafePoint();
      if (!drained) {
        return null;
      }
      this.flushPendingCommands();
      const displaySurfacePngSnapshot =
        await this.captureDisplaySurfacePngSnapshotForSaveState();
      await this.captureImageSurfaceSnapshotsForSaveState();

      const vmState = displaySurfacePngSnapshot
        ? this.jvm.saveState(displaySurfacePngSnapshot)
        : this.jvm.saveState();
      return vmState
        ? wrapStateWithMediaSnapshot(
            vmState,
            this.mediaCommands.captureSnapshot(),
          )
        : null;
    } finally {
      if (wasRunning) {
        this.startRunLoop();
      }
    }
  }

  async captureRewindSnapshot(): Promise<JvmRewindSnapshot | null> {
    if (!this.jvm) return null;

    // Keep the emulator advancing while GPU readback completes. The VM is
    // paused only for the final in-memory clone, avoiding a visible multi-frame
    // freeze on WebGPU implementations where mapAsync takes ~30 ms.
    await this.captureImageSurfaceSnapshotsForSaveState(true);

    const wasRunning = this.running;
    if (wasRunning) {
      this.stopRunLoop();
    }
    try {
      const drained = await this.waitForSnapshotSafePoint();
      if (!drained) {
        return null;
      }
      this.flushPendingCommands();
      const snapshot = this.jvm.captureRewindSnapshot();
      if (snapshot) {
        this.rewindMediaSnapshots.set(
          snapshot,
          this.mediaCommands.captureSnapshot(),
        );
      }
      return snapshot;
    } finally {
      if (wasRunning) {
        this.startRunLoop();
      }
    }
  }

  async saveStateFast(): Promise<Uint8Array | null> {
    if (!this.jvm) return null;

    // GPU readback can span multiple frames. Do it while the run loop is still
    // active, then pause only for the short, synchronous VM snapshot itself.
    await this.captureImageSurfaceSnapshotsForSaveState(true);

    const wasRunning = this.running;
    if (wasRunning) {
      this.stopRunLoop();
    }
    try {
      const drained = await this.waitForSnapshotSafePoint();
      if (!drained) {
        return null;
      }
      this.flushPendingCommands();
      const vmState = this.jvm.saveStateFast();
      return vmState
        ? wrapStateWithMediaSnapshot(
            vmState,
            this.mediaCommands.captureSnapshot(),
          )
        : null;
    } finally {
      if (wasRunning) {
        this.startRunLoop();
      }
    }
  }

  getJvm(): JvmInstance | null {
    return this.jvm;
  }

  loadState(data: Uint8Array): void {
    if (!this.jvm) return;
    const { vmState, mediaSnapshot } = unwrapStateWithMediaSnapshot(data);
    const meta = this.jvm.parseSaveMeta(vmState);
    const displayPng =
      meta && meta.displayPngLength > 0
        ? vmState
            .subarray(
              meta.displayPngOffset,
              meta.displayPngOffset + meta.displayPngLength,
            )
            .slice()
        : null;
    const wasRunning = this.running;
    this.stopRunLoop();
    this.stateLoadEpoch += 1;
    const epoch = this.stateLoadEpoch;
    this.rmsCache.clear();
    this.pendingDecodedImages.clear();
    this.asyncPumpPromise = null;
    this.flushPrintCharBuf();
    this.mediaCommands.restoreSnapshot(mediaSnapshot);
    this.clearHostOverlayState();
    this.jvm.loadState(vmState);

    // Use async flush so media player create/play commands are actually executed
    const restorePromise = this.flushOutputToHooks()
      .then(() => this.mediaCommands.finishRestore())
      .then(() => this.restorePendingResourceImages(epoch))
      .then(async () => {
        // Repaint only after all image surfaces (resource + derived) are fully
        // populated.  The Rust-side load_state intentionally does NOT repaint
        // because derived image canvases are still empty at that point — their
        // pixels are filled asynchronously above.
        if (epoch === this.stateLoadEpoch && this.jvm) {
          this.jvm.repaint();
          this.flushOutputToHooksSync();
        }
        if (epoch === this.stateLoadEpoch && displayPng && meta) {
          await this.restoreDisplaySurfacePng(
            displayPng,
            meta.displayPngWidth,
            meta.displayPngHeight,
          );
        }
      });
    if (wasRunning) {
      void restorePromise.finally(() => {
        if (epoch === this.stateLoadEpoch) {
          this.startRunLoop();
        }
      });
    } else {
      void restorePromise;
    }
  }

  restoreRewindSnapshot(snapshot: JvmRewindSnapshot): Promise<void> {
    if (!this.jvm) {
      snapshot.dispose();
      return Promise.resolve();
    }
    const wasRunning = this.running;
    this.stopRunLoop();
    this.stateLoadEpoch += 1;
    const epoch = this.stateLoadEpoch;
    this.rmsCache.clear();
    this.pendingDecodedImages.clear();
    this.asyncPumpPromise = null;
    this.flushPrintCharBuf();
    const mediaSnapshot = this.rewindMediaSnapshots.get(snapshot);
    this.rewindMediaSnapshots.delete(snapshot);
    this.mediaCommands.restoreSnapshot(mediaSnapshot);
    this.clearHostOverlayState();
    this.jvm.restoreRewindSnapshot(snapshot);

    const restorePromise = this.flushOutputToHooks()
      .then(() => this.mediaCommands.finishRestore())
      .then(() => this.restorePendingResourceImages(epoch))
      .then(() => {
        if (epoch === this.stateLoadEpoch && this.jvm) {
          this.jvm.repaint();
          this.flushOutputToHooksSync();
        }
      });
    if (wasRunning) {
      return restorePromise.finally(() => {
        if (epoch === this.stateLoadEpoch) {
          this.startRunLoop();
        }
      });
    }
    return restorePromise;
  }

  private async restoreDisplaySurfacePng(
    data: Uint8Array,
    width: number,
    height: number,
  ): Promise<void> {
    const gfx = this.options.hooks?.j2me?.graphics;
    const image = this.options.hooks?.j2me?.image;
    if (
      !gfx?.importImageToSurface ||
      !image?.platformDecodeImageData ||
      width <= 0 ||
      height <= 0
    )
      return;
    const source = await image.platformDecodeImageData(data, 'image/png');
    await gfx.importImageToSurface(0, source as any);
    gfx.flush?.(0);
  }

  private startRunLoop(): void {
    if (this.running) return;
    if (typeof MessageChannel === 'undefined') return; // Node.js test environment
    this.running = true;
    // Sync speed multiplier to Rust/WASM side
    this.jvm!.setSpeedMultiplier(this.speedMultiplier);
    let frameCount = 0;
    const STEP_DEAD = 0; // all threads terminated
    let lastTickAt = performance.now();
    // MessageChannel for zero-delay scheduling (avoids vsync-locked rAF on 120Hz)
    const channel = new MessageChannel();
    const scheduleTick = () => {
      channel.port2.postMessage(null);
    };
    this.scheduleTick = scheduleTick;
    channel.port1.onmessage = () => tick();
    // Watchdog: if tick hasn't run for 2s, force-schedule it
    const watchdog = setInterval(() => {
      if (!this.running) {
        clearInterval(watchdog);
        return;
      }
      if (performance.now() - lastTickAt > 2000) {
        scheduleTick();
      }
    }, 2000);

    const tick = () => {
      if (!this.running || !this.jvm) {
        clearInterval(watchdog);
        return;
      }
      const now = performance.now();
      lastTickAt = now;

      try {
        frameCount++;
        this.performanceCollector?.recordTick();
        const STEP_IDLE = 2;

        let status = this.stepJvmTimed(0);

        const checkAsync = (): boolean => {
          if (this.asyncPumpPromise) {
            return true;
          }
          const claimed = this.claimPendingAsyncRequests();
          if (claimed.length > 0) {
            const epoch = this.stateLoadEpoch;
            this.startAsyncPump(epoch, claimed, scheduleTick);
            return true;
          }
          return false;
        };

        if (checkAsync()) return;

        if (status === STEP_IDLE) {
          const delay = this.jvm.idleDelay();
          this.flushOutputToHooksSync();
          if (this.waitForGpu(scheduleTick)) {
            return;
          }

          if (delay <= 4) {
            scheduleTick();
          } else {
            setTimeout(
              () => {
                if (this.running) {
                  scheduleTick();
                }
              },
              Math.max(1, delay - 2),
            );
          }
          return;
        }

        // Execute runnable threads within 12ms budget.
        const deadline = performance.now() + 12;
        while (status !== STEP_DEAD && status !== STEP_IDLE) {
          if (performance.now() >= deadline) break;
          status = this.stepJvmTimed(0);
          if (checkAsync()) return;
        }
        this.flushOutputToHooksSync();
        if (this.waitForGpu(scheduleTick)) {
          return;
        }

        if (status === STEP_IDLE) {
          const delay = this.jvm.idleDelay();
          if (delay <= 4) {
            scheduleTick();
          } else {
            setTimeout(
              () => {
                if (this.running) {
                  scheduleTick();
                }
              },
              Math.max(1, delay - 2),
            );
          }
          return;
        }

        if (status === STEP_DEAD) {
          console.error('[RunLoop] VM DEAD at frame', frameCount);
          this.running = false;
        } else {
          scheduleTick();
        }
      } catch (e) {
        console.error('[RunLoop] error at frame', frameCount, ':', e);
        const error = e as { name?: string; message?: string; stack?: string };
        if (
          error.name === 'RuntimeError' &&
          error.message?.includes('memory access out of bounds')
        ) {
          console.error('[RunLoop] fatal WASM trap:', error.stack);
          this.running = false;
          clearInterval(watchdog);
          return;
        }
        scheduleTick();
      }
    };
    scheduleTick();
  }

  private stopRunLoop(): void {
    this.running = false;
  }

  private waitForGpu(onSettled: () => void): boolean {
    if (this.gpuFencePromise) {
      return true;
    }
    const fence = this.options.hooks?.j2me?.m3g?.waitForGpu?.();
    if (!fence) {
      return false;
    }
    const pending = fence.finally(() => {
      if (this.gpuFencePromise === pending) {
        this.gpuFencePromise = null;
      }
    });
    this.gpuFencePromise = pending;
    void pending.then(
      () => {
        if (this.running) {
          onSettled();
        }
      },
      (error) => {
        console.error('[RustBackend] GPU fence failed:', error);
        if (this.running) {
          onSettled();
        }
      },
    );
    return true;
  }

  private getAsyncStateCounts(): [number, number, number] {
    return this.jvm?.getAsyncStateCounts?.() ?? [0, 0, 0];
  }

  private async runAsyncPumpToIdle(
    epoch: number,
    initialClaimed: AsyncRequest[] = [],
  ): Promise<void> {
    if (this.asyncPumpPromise) {
      await this.asyncPumpPromise;
      return;
    }

    let wrappedPromise: Promise<void> | null = null;
    const pumpPromise = this.processAsyncLoop(epoch, initialClaimed);
    wrappedPromise = pumpPromise.finally(() => {
      if (this.asyncPumpPromise === wrappedPromise) {
        this.asyncPumpPromise = null;
      }
    });
    this.asyncPumpPromise = wrappedPromise;
    await wrappedPromise;
  }

  private startAsyncPump(
    epoch: number,
    initialClaimed: AsyncRequest[],
    onSettled: () => void,
  ): void {
    const finish = () => {
      if (epoch === this.stateLoadEpoch) {
        onSettled();
      }
    };

    void this.runAsyncPumpToIdle(epoch, initialClaimed).then(
      finish,
      (error) => {
        console.error('[RustBackend] async pump failed:', error);
        finish();
      },
    );
  }

  private async waitForSnapshotSafePoint(): Promise<boolean> {
    if (!this.jvm) return false;
    const MAX_ROUNDS = 20;

    if (this.asyncPumpPromise) {
      await this.asyncPumpPromise;
      if (!this.jvm) {
        return false;
      }
    }

    for (let round = 0; round < MAX_ROUNDS; round++) {
      const [pending, claimed, resolved] = this.getAsyncStateCounts();
      if (pending === 0 && claimed === 0 && resolved === 0) {
        return true;
      }

      const epoch = this.stateLoadEpoch;
      if (claimed > 0) {
        if (this.asyncPumpPromise) {
          await this.asyncPumpPromise;
          if (!this.jvm || epoch !== this.stateLoadEpoch) {
            return false;
          }
          continue;
        }
        return false;
      }

      if (pending > 0) {
        await this.runAsyncPumpToIdle(epoch);
        if (!this.jvm || epoch !== this.stateLoadEpoch) {
          return false;
        }
        continue;
      }

      if (resolved > 0) {
        this.jvm.step(0);
        await this.flushOutputToHooks();
        if (!this.jvm || epoch !== this.stateLoadEpoch) {
          return false;
        }
      }
    }
    const [pending, claimed, resolved] = this.getAsyncStateCounts();
    return pending === 0 && claimed === 0 && resolved === 0;
  }

  private claimPendingAsyncRequests(maxCount = 1024): AsyncRequest[] {
    return this.jvm?.claimPendingAsync(maxCount) ?? [];
  }

  private async processAsyncLoop(
    epoch = this.stateLoadEpoch,
    initialClaimed: AsyncRequest[] = [],
  ): Promise<void> {
    if (!this.jvm) return;
    const MAX_ROUNDS = 100;
    let claimed = initialClaimed;

    for (let round = 0; round < MAX_ROUNDS; round++) {
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      if (claimed.length === 0) {
        claimed = this.claimPendingAsyncRequests();
      }
      if (claimed.length === 0) break;

      for (const req of claimed) {
        await this.resolveClaimedAsyncRequest(req, epoch);
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      }

      const resumeResult = this.jvm.resumeAsync();
      await this.flushOutputToHooks();

      if (resumeResult === 0) {
        break;
      } else if (resumeResult === -1) {
        console.error(
          `[RustBackend] resumeAsync failed at round ${round}, claimed requests:`,
          claimed,
        );
        break;
      }
      // resumeResult === 1: still async pending, continue loop
      claimed = [];
    }
  }

  private async resolveClaimedAsyncRequest(
    req: AsyncRequest,
    epoch: number,
  ): Promise<void> {
    switch (req.type) {
      case 'decode_image':
        await this.asyncHandlers.DecodeImage(req, epoch);
        return;
      case 'decode_m3g_external_image':
        await this.asyncHandlers.DecodeM3gExternalImage(req, epoch);
        return;
      case 'get_pixels':
        await this.asyncHandlers.GetPixels(req, epoch);
        return;
      case 'read_m3g_image':
        await this.asyncHandlers.ReadM3gImage(req, epoch);
        return;
      case 'rms_load':
        await this.asyncHandlers.RmsLoad(req, epoch);
        return;
      case 'rms_keys':
        await this.asyncHandlers.RmsKeys(req, epoch);
        return;
      case 'text_input':
        await this.asyncHandlers.TextInput(req, epoch);
        return;
      case 'date_input':
        await this.asyncHandlers.DateInput(req, epoch);
        return;
      case 'bluetooth':
        await this.asyncHandlers.Bluetooth(req, epoch);
        return;
    }
  }

  private async readSurfacePixels(
    surfaceId: number,
    x: number,
    y: number,
    width: number,
    height: number,
  ): Promise<Int32Array> {
    const gfxHooks = this.options.hooks?.j2me?.graphics;
    const argb = new Array(width * height).fill(0);
    if (gfxHooks?.getPixels && width > 0 && height > 0) {
      await Promise.resolve(
        gfxHooks.getPixels(argb, 0, width, x, y, width, height, {
          surfaceId,
          color: 0,
          clipX: 0,
          clipY: 0,
          clipWidth: width,
          clipHeight: height,
          translateX: 0,
          translateY: 0,
          strokeStyle: 0,
          fontHandle: 0,
          fontFace: 0,
          fontStyle: 0,
          fontSize: 0,
        }),
      );
    }
    return new Int32Array(argb);
  }

  private readCanvasSourceRgba(source: CanvasImageSource): Uint8Array {
    const width = Math.max(0, Math.floor((source as { width: number }).width));
    const height = Math.max(
      0,
      Math.floor((source as { height: number }).height),
    );
    if (width <= 0 || height <= 0) {
      return new Uint8Array();
    }
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d', READBACK_CONTEXT_OPTIONS);
    if (!ctx) {
      return new Uint8Array();
    }
    ctx.drawImage(source, 0, 0);
    return new Uint8Array(ctx.getImageData(0, 0, width, height).data);
  }

  private async captureImageSurfaceSnapshotsForSaveState(
    forRewind = false,
  ): Promise<void> {
    if (!this.jvm) return;
    this.jvm.clearImageSurfaceSnapshots();
    if (!this.options.hooks?.j2me?.graphics?.getPixels) return;
    const targets: ImageSurfaceSnapshotTarget[] = forRewind
      ? this.jvm.collectRewindImageSurfaceSnapshotTargets()
      : this.jvm.collectImageSurfaceSnapshotTargets();
    for (const target of targets) {
      const pixels = await this.readSurfacePixels(
        target.surface_id,
        0,
        0,
        target.width,
        target.height,
      );
      if (!this.jvm || pixels.length !== target.width * target.height) return;
      this.jvm.addImageSurfaceSnapshot(
        {
          surface_id: target.surface_id,
          width: target.width,
          height: target.height,
        },
        pixels,
      );
    }
  }

  private async captureDisplaySurfacePngSnapshotForSaveState(): Promise<{
    width: number;
    height: number;
    data: Uint8Array;
  } | null> {
    const gfx = this.options.hooks?.j2me?.graphics;
    const image = this.options.hooks?.j2me?.image;
    const surface = gfx?.getSurfaceCanvas?.(0);
    const snapshot =
      surface && image?.platformImageToPng
        ? await image.platformImageToPng(surface as any)
        : null;
    if (
      !snapshot ||
      snapshot.width <= 0 ||
      snapshot.height <= 0 ||
      snapshot.data.length === 0
    ) {
      return null;
    }
    return {
      width: snapshot.width,
      height: snapshot.height,
      data: snapshot.data,
    };
  }

  private asyncHandlers: {
    DecodeImage: (req: DecodeImageRequest, epoch: number) => Promise<void>;
    DecodeM3gExternalImage: (
      req: DecodeM3gExternalImageRequest,
      epoch: number,
    ) => Promise<void>;
    GetPixels: (req: GetPixelsRequest, epoch: number) => Promise<void>;
    ReadM3gImage: (req: ReadM3gImageRequest, epoch: number) => Promise<void>;
    RmsLoad: (req: RmsLoadRequest, epoch: number) => Promise<void>;
    RmsKeys: (req: RmsKeysRequest, epoch: number) => Promise<void>;
    TextInput: (req: TextInputRequest, epoch: number) => Promise<void>;
    DateInput: (req: DateInputRequest, epoch: number) => Promise<void>;
    Bluetooth: (req: BluetoothRequest, epoch: number) => Promise<void>;
  } = {
    DecodeImage: async (req, epoch) => {
      const imageHooks = this.options.hooks?.j2me?.image;
      const imageBytes = this.jvm!.getAsyncImageBytes(req.id);
      if (imageBytes && imageHooks?.platformDecodeImageData) {
        try {
          const source = await imageHooks.platformDecodeImageData(
            imageBytes,
            'image/png',
          );
          if (epoch !== this.stateLoadEpoch || !this.jvm) return;
          if (source.width > 0 && source.height > 0) {
            // Always queue into pendingDecodedImages instead of trying to draw
            // immediately. Rust will call host_create_surface (which replaces the
            // canvas) *after* resolveAsyncImage, so drawing to a pre-existing
            // canvas here would be overwritten by the fresh blank surface.
            this.pendingDecodedImages.set(
              req.surface_id,
              source as CanvasImageSource,
            );
          }
          this.jvm!.resolveAsyncImage(req.id, source.width, source.height);
        } catch (e) {
          if (epoch !== this.stateLoadEpoch || !this.jvm) return;
          console.error(`[DecodeImage] id=${req.id} error:`, e);
          this.jvm!.resolveAsyncImage(req.id, 0, 0);
        }
      } else {
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
        this.jvm!.resolveAsyncImage(req.id, 0, 0);
      }
    },
    DecodeM3gExternalImage: async (req, epoch) => {
      const imageHooks = this.options.hooks?.j2me?.image;
      const imageBytes = this.jvm!.getAsyncImageBytes(req.id);
      if (!imageBytes || !imageHooks?.platformDecodeImageData) {
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
        this.jvm!.resolveAsyncM3gImage(req.id, new Uint8Array(), 0, 0);
        return;
      }
      try {
        const source = await imageHooks.platformDecodeImageData(
          imageBytes,
          this.guessImageMimeType(req.resource_name),
        );
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
        const pixels = this.readCanvasSourceRgba(source as CanvasImageSource);
        this.jvm!.resolveAsyncM3gImage(
          req.id,
          pixels,
          source.width,
          source.height,
        );
      } catch (e) {
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
        console.error(`[DecodeM3gExternalImage] id=${req.id} error:`, e);
        this.jvm!.resolveAsyncM3gImage(req.id, new Uint8Array(), 0, 0);
      }
    },
    GetPixels: async (req, epoch) => {
      this.flushPendingCommands();
      const argb = await this.readSurfacePixels(
        req.surface_id,
        req.x,
        req.y,
        req.width,
        req.height,
      );
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      this.jvm.resolveAsyncPixels(req.id, argb);
    },
    ReadM3gImage: async (req, epoch) => {
      this.flushPendingCommands();
      const pixels =
        (await this.options.hooks?.j2me?.m3g?.readImageTargetPixels?.(
          req.image_handle,
          req.width,
          req.height,
        )) ?? new Uint8Array();
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      this.jvm.resolveAsyncM3gImage(req.id, pixels, req.width, req.height);
    },
    RmsLoad: async (req, epoch) => {
      // Check write-through cache first (covers recent sync saves)
      const cached = this.rmsCache.get(req.store_key);
      if (cached !== undefined) {
        this.jvm!.resolveAsyncRmsLoad(req.id, cached);
        return;
      }
      const rmsHooks = this.options.hooks?.j2me?.rms;
      if (rmsHooks?.load) {
        try {
          const data = await Promise.resolve(rmsHooks.load(req.store_key));
          if (epoch !== this.stateLoadEpoch || !this.jvm) return;
          if (data instanceof ArrayBuffer) {
            this.rmsCache.set(req.store_key, data);
          }
          this.jvm!.resolveAsyncRmsLoad(req.id, data ?? null);
        } catch {
          if (epoch !== this.stateLoadEpoch || !this.jvm) return;
          this.jvm!.resolveAsyncRmsLoad(req.id, null);
        }
      } else {
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
        this.jvm!.resolveAsyncRmsLoad(req.id, null);
      }
    },
    RmsKeys: async (req, epoch) => {
      // Merge cache keys with adapter keys to include recent sync saves
      const rmsHooks = this.options.hooks?.j2me?.rms;
      const cacheKeys = Array.from(this.rmsCache.keys());
      if (rmsHooks?.keys) {
        try {
          const adapterKeys = await Promise.resolve(rmsHooks.keys());
          if (epoch !== this.stateLoadEpoch || !this.jvm) return;
          const merged = new Set([
            ...cacheKeys,
            ...(Array.isArray(adapterKeys) ? adapterKeys : []),
          ]);
          this.jvm!.resolveAsyncRmsKeys(req.id, Array.from(merged));
        } catch {
          if (epoch !== this.stateLoadEpoch || !this.jvm) return;
          this.jvm!.resolveAsyncRmsKeys(req.id, cacheKeys);
        }
      } else {
        if (epoch !== this.stateLoadEpoch || !this.jvm) return;
        this.jvm!.resolveAsyncRmsKeys(req.id, cacheKeys);
      }
    },
    TextInput: async (req, epoch) => {
      const hook = this.options.hooks?.j2me?.lcdUi?.textInput;
      const result = hook
        ? await hook({
            show: true,
            textFieldHandle: req.text_field_handle,
            constraints: req.constraints,
            maxSize: req.max_size,
            text: req.text,
          })
        : null;
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      this.jvm.resolveAsyncTextInput(req.id, result?.text ?? null);
    },
    DateInput: async (req, epoch) => {
      const hook = this.options.hooks?.j2me?.lcdUi?.dateInput;
      const result = hook
        ? await hook({
            show: true,
            dateFieldHandle: req.date_field_handle,
            mode: req.mode,
            initialized: req.initialized,
            valueMillis: req.value_millis,
          })
        : null;
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      this.jvm.resolveAsyncDateInput(req.id, result);
    },
    Bluetooth: async (req, epoch) => {
      const response = await this.bluetoothBridge.request(
        req.operation,
        req.payload,
      );
      if (epoch !== this.stateLoadEpoch || !this.jvm) return;
      this.jvm.resolveAsyncBluetooth(req.id, response);
    },
  };

  /**
   * Dispatch a single command from the binary command buffer.
   * Returns the new offset after consuming the command's payload.
   * Returns { offset, asyncOp? } where asyncOp is a thunk for async media commands.
   */
  private dispatchCmd(
    view: DataView,
    offset: number,
    cmdId: number,
  ): { offset: number; asyncOp?: () => Promise<void> } {
    const hooks = this.options.hooks;
    const gfx = hooks?.j2me?.graphics;

    switch (cmdId) {
      // --- Graphics commands ---
      case CMD_FLUSH: {
        const sid = view.getUint32(offset, true);
        offset += 4;
        gfx?.flush?.(sid);
        break;
      }
      case CMD_CREATE_SURFACE: {
        const sid = view.getUint32(offset, true);
        const w = view.getInt32(offset + 4, true);
        const h = view.getInt32(offset + 8, true);
        offset += 12;
        gfx?.createSurface?.(sid, w, h);
        break;
      }
      case CMD_DESTROY_SURFACE: {
        const sid = view.getInt32(offset, true);
        offset += 4;
        gfx?.destroySurface?.(sid);
        break;
      }
      case CMD_LCDUI_DRAW_SHAPE_STREAM: {
        const { state, next } = readBinaryState(view, offset);
        const packetCount = view.getUint32(next, true);
        let pos = next + 4;
        for (let packet = 0; packet < packetCount; packet++) {
          const pipeline = view.getUint32(pos, true);
          const stride = view.getUint32(pos + 4, true);
          const vertexCount = view.getUint32(pos + 8, true);
          const floatCount = stride * vertexCount;
          pos += 12;
          this.performanceCollector?.recordShapeVertices(vertexCount);
          let vertices: Float32Array;
          if (nativeLittleEndian) {
            const bytes = floatCount * Float32Array.BYTES_PER_ELEMENT;
            const copy = new Uint8Array(bytes);
            copy.set(new Uint8Array(view.buffer, view.byteOffset + pos, bytes));
            vertices = new Float32Array(copy.buffer);
            pos += bytes;
          } else {
            vertices = new Float32Array(floatCount);
            for (let i = 0; i < floatCount; i++, pos += 4) {
              vertices[i] = view.getFloat32(pos, true);
            }
          }
          gfx?.drawShapeBatch?.(pipeline, stride, vertices, state);
        }
        offset = pos;
        break;
      }
      case CMD_DRAW_STRING: {
        const { state, next } = readBinaryState(view, offset);
        const { value: text, next: n2 } = readBinaryString(view, next);
        const x = view.getInt32(n2, true);
        const y = view.getInt32(n2 + 4, true);
        const anchor = view.getInt32(n2 + 8, true);
        offset = n2 + 12;
        gfx?.drawString?.(text, x, y, anchor, state as any);
        break;
      }
      case CMD_DRAW_IMAGE_STREAM: {
        const { state, next } = readBinaryState(view, offset);
        const sid = view.getInt32(next, true);
        const count = view.getUint32(next + 4, true);
        let pos = next + 8;
        const draws = new Int32Array(count * 3);
        for (let i = 0; i < draws.length; i++, pos += 4) {
          draws[i] = view.getInt32(pos, true);
        }
        offset = pos;
        const src = gfx?.getSurfaceCanvas?.(sid);
        if (src) gfx?.drawImageBatch?.(src, draws, state);
        break;
      }
      case CMD_DRAW_RGBA: {
        const { state, next } = readBinaryState(view, offset);
        const x = view.getInt32(next, true);
        const y = view.getInt32(next + 4, true);
        const width = view.getInt32(next + 8, true);
        const height = view.getInt32(next + 12, true);
        const pixelCount = view.getUint32(next + 16, true);
        const byteCount = pixelCount * 4;
        const pos = next + 20;
        this.performanceCollector?.recordDrawRgbPixels(pixelCount);
        const rgba = new Uint8Array(
          view.buffer,
          view.byteOffset + pos,
          byteCount,
        );
        offset = pos + byteCount;
        gfx?.drawRGBA?.(rgba, x, y, width, height, state);
        break;
      }
      case CMD_DRAW_REGION: {
        const { state, next } = readBinaryState(view, offset);
        const sid = view.getUint32(next, true);
        const xs = view.getInt32(next + 4, true);
        const ys = view.getInt32(next + 8, true);
        const ws = view.getInt32(next + 12, true);
        const hs = view.getInt32(next + 16, true);
        const tr = view.getInt32(next + 20, true);
        const xd = view.getInt32(next + 24, true);
        const yd = view.getInt32(next + 28, true);
        const an = view.getInt32(next + 32, true);
        offset = next + 36;
        const src2 = gfx?.getSurfaceCanvas?.(sid);
        if (src2)
          gfx?.drawRegion?.(src2, xs, ys, ws, hs, tr, xd, yd, an, state as any);
        break;
      }
      case CMD_COPY_AREA: {
        const { state, next } = readBinaryState(view, offset);
        const xs = view.getInt32(next, true);
        const ys = view.getInt32(next + 4, true);
        const w = view.getInt32(next + 8, true);
        const h = view.getInt32(next + 12, true);
        const xd = view.getInt32(next + 16, true);
        const yd = view.getInt32(next + 20, true);
        const an = view.getInt32(next + 24, true);
        offset = next + 28;
        gfx?.copyArea?.(xs, ys, w, h, xd, yd, an, state as any);
        break;
      }
      // --- Text output ---
      case CMD_PRINTLN: {
        const { value, next } = readBinaryString(view, offset);
        offset = next;
        if (hooks?.println) {
          hooks.println(value);
        } else if (hooks?.print) {
          hooks.print(`${value}\n`);
        }
        break;
      }
      case CMD_PRINT_CHAR: {
        const charCode = view.getUint32(offset, true);
        offset += 4;
        if (charCode === 10) {
          if (hooks?.println) {
            hooks.println(this.printCharBuf);
          } else if (hooks?.print) {
            hooks.print(`${this.printCharBuf}\n`);
          }
          this.printCharBuf = '';
        } else {
          this.printCharBuf += String.fromCharCode(charCode);
        }
        break;
      }

      // --- Display ---
      case CMD_DISPLAY_GET_WIDTH: {
        view.getInt32(offset, true);
        offset += 4; // read value (informational)
        hooks?.j2me?.display?.getWidth?.();
        break;
      }
      case CMD_DISPLAY_GET_HEIGHT: {
        view.getInt32(offset, true);
        offset += 4; // read value (informational)
        hooks?.j2me?.display?.getHeight?.();
        break;
      }
      case CMD_DISPLAY_SIZE_CHANGED: {
        const w = view.getInt32(offset, true);
        const h = view.getInt32(offset + 4, true);
        offset += 8;
        hooks?.j2me?.display?.sizeChanged?.(w, h);
        break;
      }

      // --- Menu ---
      case CMD_MENU_UPDATE_COMMANDS: {
        // Binary: [display_id:i32][left_label][right_label]
        this.foregroundDisplayId = view.getInt32(offset, true);
        offset += 4;
        const left = readBinaryString(view, offset);
        offset = left.next;
        const right = readBinaryString(view, offset);
        offset = right.next;
        hooks?.j2me?.lcdUi?.updateSoftKeyLabels?.({
          left: left.value || undefined,
          right: right.value || undefined,
        });
        break;
      }

      // --- RMS ---
      case CMD_RMS_LOAD: {
        const { value: argsStr, next } = readBinaryString(view, offset);
        offset = next;
        const sep = argsStr.indexOf('|');
        const key = sep >= 0 ? argsStr.slice(0, sep) : argsStr;
        hooks?.j2me?.rms?.load?.(key);
        break;
      }
      case CMD_RMS_KEYS: {
        const { next } = readBinaryString(view, offset);
        offset = next;
        hooks?.j2me?.rms?.keys?.();
        break;
      }

      // --- Media ---
      case CMD_MEDIA_CREATE_PLAYER: {
        // Binary: [handle:i32][locator_len:u32][locator_utf8][pad_to_4]
        const handle = view.getInt32(offset, true);
        offset += 4;
        const { value: locator, next } = readBinaryString(view, offset);
        offset = next;
        const operation = this.mediaCommands.submit({
          type: 'create',
          handle,
          request: { locator },
        });
        return { offset, asyncOp: () => operation };
      }
      case CMD_MEDIA_CREATE_PLAYER_BINARY: {
        // Binary format: [handle:i32][loopCount:i32][volume:i32][ctLen:u32][ct:utf8+pad][dataLen:u32][data:bytes+pad]
        const handle = view.getInt32(offset, true);
        const loopCount = view.getInt32(offset + 4, true);
        const volume = view.getInt32(offset + 8, true);
        offset += 12;
        const { value: contentType, next: ctNext } = readBinaryString(
          view,
          offset,
        );
        offset = ctNext;
        const dataLen = view.getUint32(offset, true);
        offset += 4;
        const data = new Uint8Array(
          view.buffer.slice(
            view.byteOffset + offset,
            view.byteOffset + offset + dataLen,
          ),
        );
        offset += (dataLen + 3) & ~3; // skip data + padding

        const operation = this.mediaCommands.submit({
          type: 'create',
          handle,
          request: { data, contentType },
          loopCount,
          volume,
        });
        return { offset, asyncOp: () => operation };
      }
      case CMD_MEDIA_PLAY: {
        // Binary: [handle:i32][time_ms_lo:i32][time_ms_hi:i32]
        const h = view.getInt32(offset, true);
        const timeLo = view.getInt32(offset + 4, true);
        const timeHi = view.getInt32(offset + 8, true);
        offset += 12;
        const seekTime = timeLo + timeHi * 0x100000000;
        const operation = this.mediaCommands.submit({
          type: 'play',
          handle: h,
          seekTime,
        });
        return { offset, asyncOp: () => operation };
      }
      case CMD_MEDIA_STOP: {
        // Binary: [handle:i32]
        const h = view.getInt32(offset, true);
        offset += 4;
        const operation = this.mediaCommands.submit({
          type: 'stop',
          handle: h,
        });
        return { offset, asyncOp: () => operation };
      }
      case CMD_MEDIA_CLOSE: {
        // Binary: [handle:i32]
        const playerHandle = view.getInt32(offset, true);
        offset += 4;
        const operation = this.mediaCommands.submit({
          type: 'close',
          handle: playerHandle,
        });
        return { offset, asyncOp: () => operation };
      }
      case CMD_MEDIA_REALIZE: {
        // Binary: [handle:i32]
        offset += 4;
        break;
      }
      case CMD_MEDIA_PREFETCH: {
        // Binary: [handle:i32]
        offset += 4;
        break;
      }
      case CMD_MEDIA_SET_VOLUME: {
        // Binary: [handle:i32][level:i32]
        const handle = view.getInt32(offset, true);
        const level = view.getInt32(offset + 4, true);
        offset += 8;
        const operation = this.mediaCommands.submit({
          type: 'setVolume',
          handle,
          volume: level,
        });
        return { offset, asyncOp: () => operation };
      }
      case CMD_MEDIA_SET_MUTED: {
        // Binary: [handle:i32][muted:u8][pad:3]
        const handle = view.getInt32(offset, true);
        const muted = !!view.getUint8(offset + 4);
        offset += 8;
        const operation = this.mediaCommands.submit({
          type: 'setMuted',
          handle,
          muted,
        });
        return { offset, asyncOp: () => operation };
      }
      case CMD_MEDIA_SET_SEQUENCE: {
        // Binary: [handle:i32][length:i32]
        offset += 8;
        break;
      }
      case CMD_MEDIA_PLAY_TONE: {
        // Binary: [note:i32][duration:i32][volume:i32]
        const note = view.getInt32(offset, true);
        const duration = view.getInt32(offset + 4, true);
        const volume = view.getInt32(offset + 8, true);
        offset += 12;
        const media = hooks?.j2me?.media;
        if (media?.playTone) {
          media.playTone(note, duration, volume);
        }
        break;
      }
      case CMD_MEDIA_SET_LOOP_COUNT: {
        // Binary: [handle:i32][count:i32]
        const handle = view.getInt32(offset, true);
        const count = view.getInt32(offset + 4, true);
        offset += 8;
        const operation = this.mediaCommands.submit({
          type: 'setLoopCount',
          handle,
          count,
        });
        return { offset, asyncOp: () => operation };
      }

      // --- LCDUI ---
      case CMD_LCDUI_SET_VISIBLE: {
        offset += 3; // 1 byte visible + 2 bytes padding
        break;
      }
      case CMD_TEXT_INPUT: {
        const show = view.getUint8(offset) !== 0;
        offset += 4;
        if (!show) {
          hooks?.j2me?.lcdUi?.textInput?.({ show: false });
          break;
        }
        break;
      }
      case CMD_DATE_INPUT: {
        const show = view.getUint8(offset) !== 0;
        offset += 4;
        if (!show) {
          hooks?.j2me?.lcdUi?.dateInput?.({ show: false });
          break;
        }
        break;
      }

      default:
        // M3G commands (128+) — route to m3g renderer if available
        if (cmdId >= 128) {
          const m3gHook = hooks?.j2me?.m3g;
          if (m3gHook?.handleCommand) {
            offset = m3gHook.handleCommand(cmdId, view, offset);
            break;
          }
          // No m3g handler — skip known payload sizes
          offset = this.skipM3gCommand(cmdId, view, offset);
          break;
        }
        console.error(
          '[RustBackend] unknown cmdId:',
          cmdId,
          'at offset',
          offset - 1,
        );
        // Cannot recover — we don't know the payload size. Bail out.
        return { offset: Infinity };
    }
    return { offset };
  }

  /**
   * Skip an M3G command's payload when no handler is registered.
   * Returns the new offset past the command's data.
   */
  private skipM3gCommand(
    cmdId: number,
    view: DataView,
    offset: number,
  ): number {
    switch (cmdId) {
      case CMD_M3G_BIND_TARGET:
        return offset + 4; // surfaceId
      case CMD_M3G_RELEASE_TARGET:
        return offset; // no payload
      case CMD_M3G_CLEAR:
        return offset + M3G_CLEAR.headerBytes;
      case 164: // M3G_BEGIN_WGPU_BATCH
      case 165: // M3G_END_WGPU_BATCH
        return offset;
      case M3G_DRAW_PACKET.commandId: {
        const schema = M3G_DRAW_PACKET;
        if (offset + schema.headerBytes > view.byteLength) return Infinity;
        const instanceCount = view.getUint32(
          offset + schema.offset.instanceCount,
          true,
        );
        if (
          instanceCount === 0 ||
          instanceCount >
            Math.floor(
              (view.byteLength -
                offset -
                schema.headerBytes -
                view.getUint32(offset + schema.offset.frameBytes, true)) /
                schema.instanceBytes,
            )
        ) {
          return Infinity;
        }
        const frameBytes = view.getUint32(
          offset + schema.offset.frameBytes,
          true,
        );
        return (
          offset +
          schema.headerBytes +
          frameBytes +
          instanceCount * schema.instanceBytes
        );
      }
      case CMD_M3G_CREATE_VERTEX_ARRAY: {
        // handle(4) + version(4) + numVerts(4) + numComp(4) + compSize(4) + dataLen(4) + data[dataLen]
        const dataLen = view.getUint32(offset + 20, true);
        return offset + 24 + dataLen;
      }
      case CMD_M3G_CREATE_INDEX_BUFFER: {
        // handle(4) + version(4) + implicit(1) + firstIndex(4) + stripCount(4) + strips[stripCount*4] + idxDataLen(4) + idxData[idxDataLen]
        const stripCount = view.getInt32(offset + 13, true);
        const afterStrips = offset + 17 + stripCount * 4;
        const idxDataLen = view.getUint32(afterStrips, true);
        return afterStrips + 4 + idxDataLen;
      }
      case M3G_DERIVED_INDEX_BUFFER.commandId: {
        const schema = M3G_DERIVED_INDEX_BUFFER;
        if (offset + schema.headerBytes > view.byteLength) return Infinity;
        const indexCount = view.getUint32(
          offset + schema.offset.indexCount,
          true,
        );
        return offset + schema.headerBytes + indexCount * 4;
      }
      case CMD_M3G_CREATE_BACKGROUND:
        return offset + 4; // handle
      case CMD_M3G_BG_SET_COLOR:
        return offset + 8; // handle + value
      case CMD_M3G_BG_SET_ENABLE:
        return offset + 12; // handle + which + enable
      case CMD_MASCOT_BIND_TARGET:
        return offset + 28; // surface + dimensions + clip
      case CMD_MASCOT_RELEASE_TARGET:
      case CMD_MASCOT_FLUSH:
      case CMD_MASCOT_RESET_TEXTURES:
      case CMD_MASCOT_CLEAR_DEPTH:
        return offset;
      case CMD_MASCOT_DELETE_TEXTURE:
        return offset + 4;
      case CMD_MASCOT_UPLOAD_TEXTURE: {
        const headerBytes = 16;
        if (offset + headerBytes > view.byteLength) return Infinity;
        const dataLength = view.getUint32(offset + 12, true);
        return offset + headerBytes + dataLength;
      }
      case CMD_MASCOT_DRAW_PRIMITIVES: {
        const headerBytes = 40;
        if (offset + headerBytes > view.byteLength) return Infinity;
        const vertexCount = view.getUint32(offset + 4, true);
        return offset + headerBytes + vertexCount * 16;
      }
      case CMD_MASCOT_DRAW_TEXTURED: {
        const headerBytes = 116;
        if (offset + headerBytes > view.byteLength) return Infinity;
        const vertexCount = view.getUint32(offset, true);
        return offset + headerBytes + vertexCount * 44;
      }
      case CMD_MASCOT_DRAW_COLOR_MATERIAL: {
        const headerBytes = 96;
        if (offset + headerBytes > view.byteLength) return Infinity;
        const vertexCount = view.getUint32(offset, true);
        return offset + headerBytes + vertexCount * 48;
      }
      default:
        // Unknown M3G command — cannot recover
        console.warn('[RustBackend] unknown M3G cmdId:', cmdId);
        return Infinity;
    }
  }

  /**
   * Flush the printCharBuf to hooks.
   */
  private flushPrintCharBuf(): void {
    if (this.printCharBuf.length > 0) {
      const hooks = this.options.hooks;
      if (hooks?.println) {
        hooks.println(this.printCharBuf);
      } else if (hooks?.print) {
        hooks.print(this.printCharBuf);
      }
      this.printCharBuf = '';
    }
  }

  /**
   * Synchronously flush pending command buffer.
   * Called from sync FFI callbacks (rmsSave/rmsRemove) to ensure
   * preceding output appears before the callback's hook record.
   */
  private flushPendingCommands(): void {
    if (!this.jvm) return;
    const bridgeStartedAt = this.performanceCollector?.now() ?? 0;
    const cmdResult = this.jvm.flushCommandBuffer();
    this.performanceCollector?.addTiming('bridgeFlush', bridgeStartedAt);
    if (!cmdResult) return;
    this.performanceCollector?.recordCommandBuffer(cmdResult.len);

    const view = new DataView(
      cmdResult.data.buffer,
      cmdResult.data.byteOffset,
      cmdResult.len,
    );
    const len = cmdResult.len;
    let offset = 0;
    const dispatchStartedAt = this.performanceCollector?.now() ?? 0;

    while (offset < len) {
      const cmdId = view.getUint8(offset);
      offset += 1;
      this.performanceCollector?.recordCommand(cmdId);
      const result = this.dispatchCmdTimed(view, offset, cmdId);
      offset = result.offset;
      // Skip async ops in sync context
    }
    this.performanceCollector?.addTiming('commandDispatch', dispatchStartedAt);

    this.flushPrintCharBuf();
  }

  private async flushOutputToHooks(): Promise<void> {
    if (!this.jvm) return;
    const bridgeStartedAt = this.performanceCollector?.now() ?? 0;
    const cmdResult = this.jvm.flushCommandBuffer();
    this.performanceCollector?.addTiming('bridgeFlush', bridgeStartedAt);
    const len = cmdResult?.len ?? 0;
    if (!cmdResult || len === 0) return;
    this.performanceCollector?.recordCommandBuffer(len);

    const view = new DataView(
      cmdResult.data.buffer,
      cmdResult.data.byteOffset,
      len,
    );
    let offset = 0;

    while (offset < len) {
      const cmdId = view.getUint8(offset);
      offset += 1;
      this.performanceCollector?.recordCommand(cmdId);
      const dispatchStartedAt = this.performanceCollector?.now() ?? 0;
      const result = this.dispatchCmdTimed(view, offset, cmdId);
      this.performanceCollector?.addTiming(
        'commandDispatch',
        dispatchStartedAt,
      );
      offset = result.offset;
      if (result.asyncOp) {
        await result.asyncOp();
      }
    }

    this.flushPrintCharBuf();
  }

  /** Synchronous version of flushOutputToHooks for use inside the tick loop.
   *  Async media ops are scheduled but not awaited. */
  private flushOutputToHooksSync(): void {
    if (!this.jvm) return;
    const bridgeStartedAt = this.performanceCollector?.now() ?? 0;
    const cmdResult = this.jvm.flushCommandBuffer();
    this.performanceCollector?.addTiming('bridgeFlush', bridgeStartedAt);
    const len = cmdResult?.len ?? 0;
    if (!cmdResult || len === 0) return;
    this.performanceCollector?.recordCommandBuffer(len);

    const view = new DataView(
      cmdResult.data.buffer,
      cmdResult.data.byteOffset,
      len,
    );
    let offset = 0;
    const dispatchStartedAt = this.performanceCollector?.now() ?? 0;

    while (offset < len) {
      const cmdId = view.getUint8(offset);
      offset += 1;
      this.performanceCollector?.recordCommand(cmdId);
      const result = this.dispatchCmdTimed(view, offset, cmdId);
      offset = result.offset;
      if (result.asyncOp) {
        result.asyncOp();
      }
    }
    this.performanceCollector?.addTiming('commandDispatch', dispatchStartedAt);

    this.flushPrintCharBuf();
  }
}
