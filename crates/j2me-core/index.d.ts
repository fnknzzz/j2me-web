export type CreateSurfaceCallback = (
  surfaceId: number,
  width: number,
  height: number,
) => void;
export type RmsLoadCallback = (key: string) => ArrayBuffer | null;
export type RmsSaveCallback = (key: string, data: ArrayBuffer) => void;
export type RmsRemoveCallback = (key: string) => void;
export type RmsKeysCallback = () => string[];
export interface CheatObjectField {
  addr: number;
  name: string;
  descriptor: string;
  type: string;
  value: number | bigint;
  matched: boolean;
}
export interface CheatObjectCandidate {
  object_addr: number;
  kind: 'instance' | 'static' | 'array';
  class_name: string;
  fields: CheatObjectField[];
}
export interface CheatObjectSearchResult {
  objects: CheatObjectCandidate[];
  count: number;
  truncated: boolean;
}
export type AsyncRequest =
  // Decode image bytes on the host and create the target image surface.
  | { id: number; type: 'decode_image'; surface_id: number; bytes_len: number }
  // Read ARGB pixels from a surface through an async host renderer.
  | {
      id: number;
      type: 'get_pixels';
      surface_id: number;
      x: number;
      y: number;
      width: number;
      height: number;
    }
  | {
      id: number;
      type: 'read_m3g_image';
      image_handle: number;
      width: number;
      height: number;
    }
  | {
      id: number;
      type: 'decode_m3g_external_image';
      resource_name: string;
      bytes_len: number;
    }
  // Load a single RMS record by key from the host storage layer.
  | { id: number; type: 'rms_load'; store_key: string }
  // Enumerate all RMS keys from the host storage layer.
  | { id: number; type: 'rms_keys' }
  | {
      id: number;
      type: 'text_input';
      text_field_handle: number;
      constraints: number;
      max_size: number;
      text: string;
    }
  | {
      id: number;
      type: 'date_input';
      date_field_handle: number;
      mode: number;
      initialized: boolean;
      value_millis: number;
    }
  | { id: number; type: 'bluetooth'; operation: string; payload: string };

export interface ResourceImageRestore {
  surface_id: number;
  width: number;
  height: number;
  resource_name: string;
}

export interface DerivedImageRestore {
  surface_id: number;
  width: number;
  height: number;
  source_surface_id: number;
  x: number;
  y: number;
  region_width: number;
  region_height: number;
  transform: number;
}

export interface ImageSurfaceSnapshotTarget {
  surface_id: number;
  width: number;
  height: number;
}

export declare class JvmRewindSnapshot {
  readonly disposed: boolean;
  dispose(): void;
}

export declare const setWasmLoader: (
  loader: () => Promise<BufferSource>,
) => void;

/** 获取 WASM 原始字节（供 Worker 模式使用） */
export declare const getWasmBytes: () => Promise<ArrayBuffer>;

export declare class JvmInstance {
  static create(debug?: boolean): Promise<JvmInstance>;
  static setCreateSurfaceCallback(cb: CreateSurfaceCallback | null): void;
  static setRmsLoadCallback(cb: RmsLoadCallback | null): void;
  static setRmsSaveCallback(cb: RmsSaveCallback | null): void;
  static setRmsRemoveCallback(cb: RmsRemoveCallback | null): void;
  static setRmsKeysCallback(cb: RmsKeysCallback | null): void;

  destroy(): void;
  isDebug(): boolean;
  setGcEnabled(enabled: boolean): void;
  forceGc(): void;
  setDisplaySize(width: number, height: number): void;
  loadClass(data: Uint8Array): boolean;
  loadJar(data: Uint8Array): number;
  startMidlet(launchMode: number): number;
  startMain(className: string, args?: string[]): number;
  startMainFromManifest(args?: string[]): number;
  pauseMidlet(): void;
  destroyMidlet(unconditional?: boolean): number;
  foregroundDisplayId(): number;
  flushOutput(): string;
  flushCommandBuffer(): {
    data: Uint8Array;
    len: number;
  } | null;
  step(timeMs?: number): boolean;
  run(maxSteps?: number, timePerStep?: number): string;
  getOutput(): string;
  dumpThreads(): string;
  takePendingResourceImageRestores(): ResourceImageRestore[];
  takePendingDerivedImageRestores(): DerivedImageRestore[];
  loadResource(name: string, data: Uint8Array): void;
  loadEntries(
    entries: Array<{ name: string; data: ArrayBuffer | Uint8Array }>,
  ): void;
  collectImageSurfaceSnapshotTargets(): ImageSurfaceSnapshotTarget[];
  collectRewindImageSurfaceSnapshotTargets(): ImageSurfaceSnapshotTarget[];
  clearImageSurfaceSnapshots(): void;
  addImageSurfaceSnapshot(
    target: ImageSurfaceSnapshotTarget,
    pixels: Int32Array,
  ): void;
  claimPendingAsync(maxCount?: number): AsyncRequest[];
  getAsyncStateCounts(): [number, number, number];
  getAsyncImageBytes(requestId: number): Uint8Array | null;
  resolveAsyncImage(requestId: number, width: number, height: number): void;
  resolveAsyncPixels(requestId: number, pixels: Int32Array): void;
  resolveAsyncM3gImage(
    requestId: number,
    pixels: Uint8Array,
    width?: number,
    height?: number,
  ): void;
  resolveAsyncTextInput(requestId: number, text: string | null): void;
  resolveAsyncDateInput(
    requestId: number,
    result: { initialized: boolean; valueMillis: number } | null,
  ): void;
  resolveAsyncBluetooth(requestId: number, response: string): void;
  saveState(displaySurfacePng?: {
    width: number;
    height: number;
    data: Uint8Array;
  }): Uint8Array | null;
  saveStateFast(): Uint8Array | null;
  loadState(data: Uint8Array): void;
  captureRewindSnapshot(): JvmRewindSnapshot | null;
  restoreRewindSnapshot(snapshot: JvmRewindSnapshot): void;
  parseSaveMeta(data: Uint8Array): {
    version: number;
    timestamp: number;
    vmStateOffset: number;
    vmStateLength: number;
    displayPngOffset: number;
    displayPngLength: number;
    displayPngWidth: number;
    displayPngHeight: number;
  } | null;
  resumeAsync(): number;
  pushNativeEvent(
    eventType: number,
    intParam1: number,
    intParam2: number,
    intParam3: number,
    intParam4: number,
  ): void;
  cheatSearchObjects(
    values: number[],
    matchMode?: number,
  ): CheatObjectSearchResult;
  cheatRefineObjects(
    values: number[],
    matchMode?: number,
  ): CheatObjectSearchResult;
  cheatClearObjectSearch(): void;
}
