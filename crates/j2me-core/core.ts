// 平台无关的 WASM 接口和 JvmInstance 实现
// Node.js 和 Browser 入口各自提供 wasmLoader，然后共用此核心逻辑

// ---- Stable cheat code types (方案 B) ----

export interface CheatCodeDesc {
  kind: 'static';
  class_name: string;
  field_name: string;
  descriptor: string;
  value_type: string;
  id: string;
}

export interface CheatCodeEntry extends CheatCodeDesc {
  label: string;
  action: 'none' | 'write' | 'freeze';
  value?: number;
}

export interface CheatCodeResolveResult {
  id: string;
  addr?: number;
  value_type?: string;
  ok: boolean;
  error?: string;
}

// ---- Cheat engine types ----

export interface CheatCandidate {
  addr: number;
  types: string[];
  value: number | bigint;
  confirmed_type?: string;
  field?: string;
}

export interface CheatSearchResult {
  addrs: CheatCandidate[];
  count: number;
  truncated: boolean;
}

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

export interface CheatFreezeEntry {
  addr: number;
  type: string;
  value: number | bigint;
  enabled: boolean;
  stale: boolean;
}

export interface CheatWatchEntry {
  addr: number;
  type: string;
  value: number | bigint;
  label: string;
  changed: boolean;
  stale: boolean;
}

const VALUE_TYPE_NAMES: Record<number, string> = {
  0: 'i8',
  1: 'i16',
  2: 'i32',
  3: 'i64',
  4: 'f32',
  5: 'f64',
};

/** Read a cheat value from 8 bytes at `pos` in a DataView, based on vtype tag. */
function readCheatValue(
  dv: DataView,
  pos: number,
  vtype: number,
): number | bigint {
  switch (vtype) {
    case 0:
      return dv.getInt8(pos);
    case 1:
      return dv.getInt16(pos, true);
    case 2:
      return dv.getInt32(pos, true);
    case 3:
      return dv.getBigInt64(pos, true);
    case 4:
      return dv.getFloat32(pos, true);
    case 5:
      return dv.getFloat64(pos, true);
    default:
      return dv.getInt32(pos, true);
  }
}

/** Encode a JS number/bigint as little-endian bytes for the given value type tag. */
function cheatValueToBytes(
  valueType: number,
  value: number | bigint,
): Uint8Array {
  const n = Number(value);
  const b = BigInt(typeof value === 'bigint' ? value : Math.trunc(value));
  switch (valueType) {
    case 0: {
      const v = new Int8Array(1);
      v[0] = n;
      return new Uint8Array(v.buffer);
    }
    case 1: {
      const v = new Int16Array(1);
      v[0] = n;
      return new Uint8Array(v.buffer);
    }
    case 2: {
      const v = new Int32Array(1);
      v[0] = n;
      return new Uint8Array(v.buffer);
    }
    case 3: {
      const v = new BigInt64Array(1);
      v[0] = b;
      return new Uint8Array(v.buffer);
    }
    case 4: {
      const v = new Float32Array(1);
      v[0] = n;
      return new Uint8Array(v.buffer);
    }
    case 5: {
      const v = new Float64Array(1);
      v[0] = n;
      return new Uint8Array(v.buffer);
    }
    default: {
      const v = new Int32Array(1);
      v[0] = n;
      return new Uint8Array(v.buffer);
    }
  }
}

export interface WasmExports {
  memory: WebAssembly.Memory;
  jvm_alloc: (len: number) => number;
  jvm_free: (ptr: number, len: number) => void;
  jvm_create: (debug: number) => number;
  jvm_set_display_size: (ptr: number, width: number, height: number) => void;
  jvm_create_named: (namePtr: number, nameLen: number, debug: number) => number;
  jvm_destroy: (ptr: number) => void;
  jvm_is_debug: (ptr: number) => number;
  jvm_set_gc_enabled: (ptr: number, enabled: number) => void;
  jvm_load_class: (ptr: number, dataPtr: number, len: number) => number;
  jvm_load_jar: (ptr: number, dataPtr: number, len: number) => number;
  jvm_load_resource: (
    ptr: number,
    namePtr: number,
    nameLen: number,
    dataPtr: number,
    dataLen: number,
  ) => void;
  jvm_start_midlet: (ptr: number, launchMode: number) => number;
  jvm_start_main: (
    ptr: number,
    classNamePtr: number,
    classNameLen: number,
    argsPtr: number,
    argsLen: number,
  ) => number;
  jvm_start_main_from_manifest: (
    ptr: number,
    argsPtr: number,
    argsLen: number,
  ) => number;
  jvm_pause_midlet: (ptr: number) => number;
  jvm_destroy_midlet: (ptr: number, unconditional: number) => number;
  jvm_get_foreground_display_id: (ptr: number) => number;
  jvm_step: (ptr: number, timeMs: number) => number;
  jvm_get_output: (ptr: number, outLenPtr: number) => number;
  jvm_dump_threads: (ptr: number, outLenPtr: number) => number;
  jvm_idle_delay: (ptr: number) => number;
  jvm_save_state: (
    ptr: number,
    displayWidth: number,
    displayHeight: number,
    pngDataPtr: number,
    pngDataLen: number,
    outLenPtr: number,
  ) => number;
  jvm_save_state_fast: (ptr: number, outLenPtr: number) => number;
  jvm_collect_image_surface_snapshot_targets: (
    ptr: number,
    outLenPtr: number,
  ) => number;
  jvm_collect_rewind_image_surface_snapshot_targets: (
    ptr: number,
    outLenPtr: number,
  ) => number;
  jvm_clear_image_surface_snapshots: (ptr: number) => void;
  jvm_add_image_surface_snapshot: (
    ptr: number,
    surfaceId: number,
    width: number,
    height: number,
    dataPtr: number,
    dataLen: number,
  ) => void;
  jvm_load_state: (ptr: number, dataPtr: number, len: number) => void;
  jvm_rewind_capture: (ptr: number) => number;
  jvm_rewind_restore: (ptr: number, snapshotPtr: number) => number;
  jvm_rewind_free: (snapshotPtr: number) => void;
  jvm_save_state_version: () => number;
  jvm_save_meta_size: () => number;
  jvm_parse_save_meta: (
    dataPtr: number,
    len: number,
    outMeta: number,
  ) => number;
  jvm_take_pending_resource_image_restores: (
    ptr: number,
    outLenPtr: number,
  ) => number;
  jvm_take_pending_derived_image_restores: (
    ptr: number,
    outLenPtr: number,
  ) => number;
  jvm_claim_pending_async: (
    ptr: number,
    maxCount: number,
    outLenPtr: number,
  ) => number;
  jvm_get_async_state_counts: (ptr: number, outCountsPtr: number) => void;
  jvm_get_command_buffer: (ptr: number, outLenPtr: number) => number;
  jvm_get_async_image_bytes: (
    ptr: number,
    requestId: number,
    outLenPtr: number,
  ) => number;
  jvm_resolve_async_image: (
    ptr: number,
    requestId: number,
    width: number,
    height: number,
  ) => void;
  jvm_resolve_async_pixels: (
    ptr: number,
    requestId: number,
    dataPtr: number,
    dataLen: number,
  ) => void;
  jvm_resolve_async_m3g_image: (
    ptr: number,
    requestId: number,
    width: number,
    height: number,
    dataPtr: number,
    dataLen: number,
  ) => void;
  jvm_resolve_async_rms_load: (
    ptr: number,
    requestId: number,
    dataPtr: number,
    dataLen: number,
  ) => void;
  jvm_resolve_async_rms_keys: (
    ptr: number,
    requestId: number,
    keysPtr: number,
    keysLen: number,
  ) => void;
  jvm_resolve_async_text_input: (
    ptr: number,
    requestId: number,
    textPtr: number,
    textLen: number,
  ) => void;
  jvm_resolve_async_date_input: (
    ptr: number,
    requestId: number,
    committed: number,
    initialized: number,
    valueMillis: bigint,
  ) => void;
  jvm_resolve_async_bluetooth: (
    ptr: number,
    requestId: number,
    responsePtr: number,
    responseLen: number,
  ) => void;
  jvm_resume_async: (ptr: number) => number;
  jvm_push_native_event: (
    ptr: number,
    eventType: number,
    intParam1: number,
    intParam2: number,
    intParam3: number,
    intParam4: number,
  ) => void;
  jvm_set_speed_multiplier: (ptr: number, speed: number) => void;
  // Cheat engine
  jvm_cheat_search_begin: (
    ptr: number,
    searchMode: number,
    value: number,
  ) => void;
  jvm_cheat_search_refine: (
    ptr: number,
    searchMode: number,
    value: number,
  ) => void;
  jvm_cheat_get_results: (ptr: number, outLenPtr: number) => number;
  jvm_cheat_search_objects: (
    ptr: number,
    valuesPtr: number,
    valueCount: number,
    matchMode: number,
    outLenPtr: number,
  ) => number;
  jvm_cheat_refine_objects: (
    ptr: number,
    valuesPtr: number,
    valueCount: number,
    matchMode: number,
    outLenPtr: number,
  ) => number;
  jvm_cheat_object_search_clear: (ptr: number) => void;
  jvm_cheat_search_clear: (ptr: number) => void;
  jvm_cheat_read: (
    ptr: number,
    address: number,
    valueType: number,
    outLenPtr: number,
  ) => number;
  jvm_cheat_write: (
    ptr: number,
    address: number,
    valueType: number,
    valuePtr: number,
    valueLen: number,
  ) => void;
  jvm_cheat_freeze_add: (
    ptr: number,
    address: number,
    valueType: number,
    valuePtr: number,
    valueLen: number,
  ) => void;
  jvm_cheat_freeze_remove: (
    ptr: number,
    address: number,
    valueType: number,
  ) => void;
  jvm_cheat_freeze_clear: (ptr: number) => void;
  jvm_cheat_freeze_set_enabled: (
    ptr: number,
    address: number,
    valueType: number,
    enabled: number,
  ) => void;
  jvm_cheat_freeze_list: (ptr: number, outLenPtr: number) => number;
  // Stable cheat code (方案 B)
  jvm_cheat_code_from_candidate: (
    ptr: number,
    address: number,
    valueType: number,
    outLenPtr: number,
  ) => number;
  jvm_cheat_code_resolve: (
    ptr: number,
    codePtr: number,
    codeLen: number,
    outLenPtr: number,
  ) => number;
  // Watch list
  jvm_cheat_watch_add_batch: (
    ptr: number,
    bufPtr: number,
    bufLen: number,
  ) => void;
  jvm_cheat_watch_remove: (
    ptr: number,
    address: number,
    valueType: number,
  ) => void;
  jvm_cheat_watch_clear: (ptr: number) => void;
  jvm_cheat_watch_list: (ptr: number, outLenPtr: number) => number;
  jvm_cheat_watch_write: (
    ptr: number,
    address: number,
    valueType: number,
    valuePtr: number,
    valueLen: number,
  ) => void;
  jvm_force_gc: (ptr: number) => void;
  jvm_repaint: (ptr: number) => number;
}

export type CreateSurfaceCallback = (
  surfaceId: number,
  width: number,
  height: number,
) => void;
export type RmsLoadCallback = (key: string) => ArrayBuffer | null;
export type RmsSaveCallback = (key: string, data: ArrayBuffer) => void;
export type RmsRemoveCallback = (key: string) => void;
export type RmsKeysCallback = () => string[];
export type MeasureTextCallback = (
  text: string,
  face: number,
  style: number,
  size: number,
) => number;
export type FontMetricsCallback = (
  face: number,
  style: number,
  size: number,
) => { height: number; baseline: number };

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

/** Opaque in-WASM snapshot used by the low-latency rewind path. */
export class JvmRewindSnapshot {
  private ptr: number;

  /** @internal Created by JvmInstance.captureRewindSnapshot(). */
  constructor(
    private readonly wasm: WasmExports,
    ptr: number,
  ) {
    this.ptr = ptr;
  }

  get disposed(): boolean {
    return this.ptr === 0;
  }

  dispose(): void {
    if (this.ptr !== 0) {
      this.wasm.jvm_rewind_free(this.ptr);
      this.ptr = 0;
    }
  }

  /** @internal Transfers ownership to Rust for restoration. */
  consume(wasm: WasmExports): number {
    if (this.wasm !== wasm) {
      throw new Error('Rewind snapshot belongs to a different WASM instance');
    }
    if (this.ptr === 0) {
      throw new Error('Rewind snapshot has already been consumed or disposed');
    }
    const ptr = this.ptr;
    this.ptr = 0;
    return ptr;
  }
}

let createSurfaceCallback: CreateSurfaceCallback | null = null;
let rmsLoadCallback: RmsLoadCallback | null = null;
let rmsSaveCallback: RmsSaveCallback | null = null;
let rmsRemoveCallback: RmsRemoveCallback | null = null;
let rmsKeysCallback: RmsKeysCallback | null = null;
let measureTextCallback: MeasureTextCallback | null = null;
let fontMetricsCallback: FontMetricsCallback | null = null;
let mediaTimeCallback: ((handle: number) => number) | null = null;
let mediaDurationCallback: ((handle: number) => number) | null = null;
let setMediaTimeCallback: ((handle: number, timeMs: number) => void) | null =
  null;

let wasmExports: WasmExports | null = null;

/** 由平台入口注入的 WASM 加载函数 */
let wasmLoader: (() => Promise<BufferSource>) | null = null;

export const setWasmLoader = (loader: () => Promise<BufferSource>): void => {
  wasmLoader = loader;
};

/** 获取 WASM 原始字节（供 Worker 模式使用） */
export const getWasmBytes = async (): Promise<ArrayBuffer> => {
  if (!wasmLoader)
    throw new Error(
      '@jsjvm/j2me-core: No WASM loader configured. Use setWasmLoader().',
    );
  const bytes = await wasmLoader();
  if (bytes instanceof ArrayBuffer) return bytes;
  // BufferSource 可能是 ArrayBufferView
  return (bytes as ArrayBufferView).buffer as ArrayBuffer;
};

const buildImports = (): WebAssembly.Imports => ({
  env: {
    host_create_surface: (
      surfaceId: number,
      width: number,
      height: number,
    ): void => {
      if (createSurfaceCallback)
        createSurfaceCallback(surfaceId, width, height);
    },
    host_rms_load: (
      keyPtr: number,
      keyLen: number,
      bufPtr: number,
      bufCap: number,
    ): number => {
      const cb = rmsLoadCallback;
      if (!cb) return -1;
      const mem = new Uint8Array(wasmExports!.memory.buffer);
      const key = new TextDecoder().decode(mem.slice(keyPtr, keyPtr + keyLen));
      const result = cb(key);
      if (result === null || result === undefined) return -1;
      const src = new Uint8Array(result);
      const toWrite = Math.min(src.length, bufCap);
      new Uint8Array(wasmExports!.memory.buffer, bufPtr, toWrite).set(
        src.subarray(0, toWrite),
      );
      return src.length;
    },
    host_rms_save: (
      keyPtr: number,
      keyLen: number,
      dataPtr: number,
      dataLen: number,
    ): void => {
      const cb = rmsSaveCallback;
      if (!cb) return;
      const mem = new Uint8Array(wasmExports!.memory.buffer);
      const key = new TextDecoder().decode(mem.slice(keyPtr, keyPtr + keyLen));
      const src = new Uint8Array(wasmExports!.memory.buffer, dataPtr, dataLen);
      const copy = new ArrayBuffer(dataLen);
      new Uint8Array(copy).set(src);
      cb(key, copy);
    },
    host_rms_remove: (keyPtr: number, keyLen: number): void => {
      const cb = rmsRemoveCallback;
      if (!cb) return;
      const key = new TextDecoder().decode(
        new Uint8Array(wasmExports!.memory.buffer).slice(
          keyPtr,
          keyPtr + keyLen,
        ),
      );
      cb(key);
    },
    host_rms_keys: (bufPtr: number, bufCap: number): number => {
      const cb = rmsKeysCallback;
      if (!cb) return 0;
      const keys = cb();
      if (keys.length === 0) return 0;
      const encoded = new TextEncoder().encode(keys.join('\n'));
      const toWrite = Math.min(encoded.length, bufCap);
      new Uint8Array(wasmExports!.memory.buffer, bufPtr, toWrite).set(
        encoded.subarray(0, toWrite),
      );
      return toWrite;
    },
    host_current_time_millis: (): number => Date.now(),
    host_mono_time_millis: (): number => performance.now(),
    host_get_locale: (bufPtr: number, bufCap: number): number => {
      const locale =
        typeof navigator !== 'undefined' && navigator.language
          ? navigator.language
          : 'zh-CN';
      const encoded = new TextEncoder().encode(locale);
      const toWrite = Math.min(encoded.length, bufCap);
      new Uint8Array(wasmExports!.memory.buffer, bufPtr, toWrite).set(
        encoded.subarray(0, toWrite),
      );
      return encoded.length;
    },
    host_measure_text: (
      textPtr: number,
      textLen: number,
      face: number,
      style: number,
      size: number,
    ): number => {
      const cb = measureTextCallback;
      if (!cb) return 0;
      const mem = new Uint8Array(wasmExports!.memory.buffer);
      const text = new TextDecoder().decode(
        mem.slice(textPtr, textPtr + textLen),
      );
      return cb(text, face, style, size);
    },
    host_font_metrics: (face: number, style: number, size: number): number => {
      const cb = fontMetricsCallback;
      if (!cb) return (16 << 16) | 12; // fallback: height=16, baseline=12
      const m = cb(face, style, size);
      return ((m.height & 0xffff) << 16) | (m.baseline & 0xffff);
    },
    host_get_media_time: (handle: number): bigint => {
      const cb = mediaTimeCallback;
      return BigInt(Math.floor(cb ? cb(handle) : 0));
    },
    host_get_media_duration: (handle: number): bigint => {
      const cb = mediaDurationCallback;
      return BigInt(Math.floor(cb ? cb(handle) : 0));
    },
    host_set_media_time: (handle: number, timeMs: bigint): void => {
      const cb = setMediaTimeCallback;
      if (cb) cb(handle, Number(timeMs));
    },
  },
});

const ensureWasm = async (): Promise<WasmExports> => {
  if (wasmExports) return wasmExports;
  if (!wasmLoader)
    throw new Error(
      '@jsjvm/j2me-core: No WASM loader configured. Use setWasmLoader().',
    );
  const wasmBytes = await wasmLoader();
  const result = await WebAssembly.instantiate(wasmBytes, buildImports());
  wasmExports = result.instance.exports as unknown as WasmExports;
  return wasmExports;
};

const writeBytes = (w: WasmExports, data: Uint8Array): [number, number] => {
  const ptr = w.jvm_alloc(data.length);
  new Uint8Array(w.memory.buffer, ptr, data.length).set(data);
  return [ptr, data.length];
};

const writeString = (w: WasmExports, str: string): [number, number] =>
  writeBytes(w, new TextEncoder().encode(str));

const readString = (w: WasmExports, ptr: number, len: number): string => {
  if (ptr === 0 || len === 0) return '';
  const str = new TextDecoder().decode(
    new Uint8Array(w.memory.buffer, ptr, len),
  );
  w.jvm_free(ptr, len);
  return str;
};

export class JvmInstance {
  private ptr: number = 0;
  private w: WasmExports | null = null;

  static async create(debug = false): Promise<JvmInstance> {
    const inst = new JvmInstance();
    inst.w = await ensureWasm();
    inst.ptr = inst.w.jvm_create(debug ? 1 : 0);
    return inst;
  }

  static setCreateSurfaceCallback(cb: CreateSurfaceCallback | null): void {
    createSurfaceCallback = cb;
  }
  static setRmsLoadCallback(cb: RmsLoadCallback | null): void {
    rmsLoadCallback = cb;
  }
  static setRmsSaveCallback(cb: RmsSaveCallback | null): void {
    rmsSaveCallback = cb;
  }
  static setRmsRemoveCallback(cb: RmsRemoveCallback | null): void {
    rmsRemoveCallback = cb;
  }
  static setRmsKeysCallback(cb: RmsKeysCallback | null): void {
    rmsKeysCallback = cb;
  }
  static setMeasureTextCallback(cb: MeasureTextCallback | null): void {
    measureTextCallback = cb;
  }
  static setFontMetricsCallback(cb: FontMetricsCallback | null): void {
    fontMetricsCallback = cb;
  }
  static setMediaTimeCallback(cb: ((handle: number) => number) | null): void {
    mediaTimeCallback = cb;
  }
  static setMediaDurationCallback(
    cb: ((handle: number) => number) | null,
  ): void {
    mediaDurationCallback = cb;
  }
  static setSetMediaTimeCallback(
    cb: ((handle: number, timeMs: number) => void) | null,
  ): void {
    setMediaTimeCallback = cb;
  }

  destroy(): void {
    if (this.w && this.ptr) {
      this.w.jvm_destroy(this.ptr);
      this.ptr = 0;
    }
  }

  isDebug(): boolean {
    return this.w!.jvm_is_debug(this.ptr) !== 0;
  }
  setGcEnabled(enabled: boolean): void {
    this.w!.jvm_set_gc_enabled(this.ptr, enabled ? 1 : 0);
  }
  forceGc(): void {
    this.w!.jvm_force_gc(this.ptr);
  }
  repaint(): number {
    return this.w!.jvm_repaint(this.ptr);
  }
  setDisplaySize(width: number, height: number): void {
    this.w!.jvm_set_display_size(this.ptr, width, height);
  }

  loadClass(data: Uint8Array): boolean {
    const [ptr, len] = writeBytes(this.w!, data);
    const result = this.w!.jvm_load_class(this.ptr, ptr, len);
    this.w!.jvm_free(ptr, len);
    return result === 0;
  }

  loadJar(data: Uint8Array): number {
    const [ptr, len] = writeBytes(this.w!, data);
    const result = this.w!.jvm_load_jar(this.ptr, ptr, len);
    this.w!.jvm_free(ptr, len);
    return result;
  }

  startMidlet(launchMode: number): number {
    return this.w!.jvm_start_midlet(this.ptr, launchMode);
  }

  startMain(className: string, args: string[] = []): number {
    const [classNamePtr, classNameLen] = writeString(this.w!, className);
    const [argsPtr, argsLen] = writeString(this.w!, args.join('\0'));
    const result = this.w!.jvm_start_main(
      this.ptr,
      classNamePtr,
      classNameLen,
      argsPtr,
      argsLen,
    );
    this.w!.jvm_free(classNamePtr, classNameLen);
    this.w!.jvm_free(argsPtr, argsLen);
    return result;
  }

  startMainFromManifest(args: string[] = []): number {
    const [argsPtr, argsLen] = writeString(this.w!, args.join('\0'));
    const result = this.w!.jvm_start_main_from_manifest(
      this.ptr,
      argsPtr,
      argsLen,
    );
    this.w!.jvm_free(argsPtr, argsLen);
    return result;
  }

  pauseMidlet(): void {
    const result = this.w!.jvm_pause_midlet(this.ptr);
    if (result !== 0) throw new Error('Failed to pause suite');
  }

  destroyMidlet(unconditional = true): number {
    return this.w!.jvm_destroy_midlet(this.ptr, unconditional ? 1 : 0);
  }

  foregroundDisplayId(): number {
    return this.w!.jvm_get_foreground_display_id(this.ptr);
  }

  flushOutput(): string {
    return this.getOutput();
  }

  /** Flush the binary command buffer. Returns a copied Uint8Array or null if empty. */
  flushCommandBuffer(): { data: Uint8Array; len: number } | null {
    const outLenPtr = this.w!.jvm_alloc(4);
    const bufPtr = this.w!.jvm_get_command_buffer(this.ptr, outLenPtr);
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (bufPtr === 0 || outLen === 0) return null;
    const data = new Uint8Array(this.w!.memory.buffer, bufPtr, outLen).slice();
    this.w!.jvm_free(bufPtr, outLen);
    return { data, len: outLen };
  }

  /** Returns: 0 = terminated, 1 = stepped (has runnable threads), 2 = alive but idle */
  step(timeMs = 1): number {
    return this.w!.jvm_step(this.ptr, timeMs);
  }

  /** Returns ms until the nearest sleeping thread wakes up. 0 = no pending sleepers. */
  idleDelay(): number {
    return this.w!.jvm_idle_delay(this.ptr);
  }

  run(maxSteps = 10000, timePerStep = 1): string {
    for (let i = 0; i < maxSteps; i++) {
      if (this.step(timePerStep) === 0) break;
    }
    return this.getOutput();
  }

  getOutput(): string {
    const outLenPtr = this.w!.jvm_alloc(4);
    const strPtr = this.w!.jvm_get_output(this.ptr, outLenPtr);
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    return readString(this.w!, strPtr, outLen);
  }

  dumpThreads(): string {
    if (!this.w!.jvm_dump_threads) return '';
    const outLenPtr = this.w!.jvm_alloc(4);
    const strPtr = this.w!.jvm_dump_threads(this.ptr, outLenPtr);
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    return readString(this.w!, strPtr, outLen);
  }

  loadResource(name: string, data: Uint8Array): void {
    const [namePtr, nameLen] = writeString(this.w!, name);
    const [dataPtr, dataLen] = writeBytes(this.w!, data);
    this.w!.jvm_load_resource(this.ptr, namePtr, nameLen, dataPtr, dataLen);
    this.w!.jvm_free(namePtr, nameLen);
    this.w!.jvm_free(dataPtr, dataLen);
  }

  loadEntries(
    entries: Array<{ name: string; data: ArrayBuffer | Uint8Array }>,
  ): void {
    const classEntries: Array<{ name: string; data: Uint8Array }> = [];
    for (const entry of entries) {
      const data =
        entry.data instanceof Uint8Array
          ? entry.data
          : new Uint8Array(entry.data);
      if (entry.name.endsWith('.class')) {
        classEntries.push({ name: entry.name, data });
      } else {
        this.loadResource(entry.name, data);
      }
    }
    let remaining = classEntries;
    for (let pass = 0; pass < 10 && remaining.length > 0; pass++) {
      const failed: typeof remaining = [];
      for (const entry of remaining) {
        if (!this.loadClass(entry.data)) failed.push(entry);
      }
      if (failed.length === remaining.length) break;
      remaining = failed;
    }
  }

  takePendingResourceImageRestores(): ResourceImageRestore[] {
    const outLenPtr = this.w!.jvm_alloc(4);
    const bufPtr = this.w!.jvm_take_pending_resource_image_restores(
      this.ptr,
      outLenPtr,
    );
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (bufPtr === 0 || outLen === 0) return [];
    const view = new DataView(this.w!.memory.buffer, bufPtr, outLen);
    const decoder = new TextDecoder();
    const results: ResourceImageRestore[] = [];
    let offset = 0;
    const count = view.getUint32(offset, true);
    offset += 4;
    for (let i = 0; i < count; i++) {
      const surface_id = view.getInt32(offset, true);
      offset += 4;
      const width = view.getInt32(offset, true);
      offset += 4;
      const height = view.getInt32(offset, true);
      offset += 4;
      const nameLen = view.getUint32(offset, true);
      offset += 4;
      const resource_name = decoder.decode(
        new Uint8Array(view.buffer, view.byteOffset + offset, nameLen),
      );
      offset += (nameLen + 3) & ~3;
      results.push({ surface_id, width, height, resource_name });
    }
    this.w!.jvm_free(bufPtr, outLen);
    return results;
  }

  takePendingDerivedImageRestores(): DerivedImageRestore[] {
    const outLenPtr = this.w!.jvm_alloc(4);
    const bufPtr = this.w!.jvm_take_pending_derived_image_restores(
      this.ptr,
      outLenPtr,
    );
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (bufPtr === 0 || outLen === 0) return [];
    const view = new DataView(this.w!.memory.buffer, bufPtr, outLen);
    const results: DerivedImageRestore[] = [];
    let offset = 0;
    const count = view.getUint32(offset, true);
    offset += 4;
    for (let i = 0; i < count; i++) {
      const surface_id = view.getInt32(offset, true);
      const width = view.getInt32(offset + 4, true);
      const height = view.getInt32(offset + 8, true);
      const source_surface_id = view.getInt32(offset + 12, true);
      const x = view.getInt32(offset + 16, true);
      const y = view.getInt32(offset + 20, true);
      const region_width = view.getInt32(offset + 24, true);
      const region_height = view.getInt32(offset + 28, true);
      const transform = view.getInt32(offset + 32, true);
      offset += 36;
      results.push({
        surface_id,
        width,
        height,
        source_surface_id,
        x,
        y,
        region_width,
        region_height,
        transform,
      });
    }
    this.w!.jvm_free(bufPtr, outLen);
    return results;
  }

  // --- Async bridge ---

  claimPendingAsync(maxCount = 1024): AsyncRequest[] {
    const outLenPtr = this.w!.jvm_alloc(4);
    const bufPtr = this.w!.jvm_claim_pending_async(
      this.ptr,
      maxCount,
      outLenPtr,
    );
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (bufPtr === 0 || outLen === 0) return [];
    // Parse binary: [count:u32] then per request type-specific fields
    const view = new DataView(this.w!.memory.buffer, bufPtr, outLen);
    const decoder = new TextDecoder();
    const results: AsyncRequest[] = [];
    let offset = 0;
    const count = view.getUint32(offset, true);
    offset += 4;
    for (let i = 0; i < count; i++) {
      const id = view.getUint32(offset, true);
      offset += 4;
      const type_ = view.getUint8(offset);
      offset += 4; // type + 3 pad
      switch (type_) {
        case 0: {
          // decode_image
          const surface_id = view.getInt32(offset, true);
          offset += 4;
          const bytes_len = view.getUint32(offset, true);
          offset += 4;
          results.push({ id, type: 'decode_image', surface_id, bytes_len });
          break;
        }
        case 1: {
          // rms_load
          const keyLen = view.getUint32(offset, true);
          offset += 4;
          const store_key = decoder.decode(
            new Uint8Array(view.buffer, view.byteOffset + offset, keyLen),
          );
          offset += (keyLen + 3) & ~3; // skip key + padding
          results.push({ id, type: 'rms_load', store_key });
          break;
        }
        case 2: {
          // rms_keys
          results.push({ id, type: 'rms_keys' });
          break;
        }
        case 3: {
          // get_pixels
          const surface_id = view.getInt32(offset, true);
          offset += 4;
          const x = view.getInt32(offset, true);
          offset += 4;
          const y = view.getInt32(offset, true);
          offset += 4;
          const width = view.getInt32(offset, true);
          offset += 4;
          const height = view.getInt32(offset, true);
          offset += 4;
          results.push({
            id,
            type: 'get_pixels',
            surface_id,
            x,
            y,
            width,
            height,
          });
          break;
        }
        case 6: {
          const image_handle = view.getUint32(offset, true);
          offset += 4;
          const width = view.getInt32(offset, true);
          offset += 4;
          const height = view.getInt32(offset, true);
          offset += 4;
          results.push({
            id,
            type: 'read_m3g_image',
            image_handle,
            width,
            height,
          });
          break;
        }
        case 7: {
          const nameLen = view.getUint32(offset, true);
          offset += 4;
          const resource_name = decoder.decode(
            new Uint8Array(view.buffer, view.byteOffset + offset, nameLen),
          );
          offset += (nameLen + 3) & ~3;
          const bytes_len = view.getUint32(offset, true);
          offset += 4;
          results.push({
            id,
            type: 'decode_m3g_external_image',
            resource_name,
            bytes_len,
          });
          break;
        }
        case 4: {
          // text_input
          const text_field_handle = view.getUint32(offset, true);
          offset += 4;
          const constraints = view.getInt32(offset, true);
          offset += 4;
          const max_size = view.getInt32(offset, true);
          offset += 4;
          const textLen = view.getUint32(offset, true);
          offset += 4;
          const text = decoder.decode(
            new Uint8Array(view.buffer, view.byteOffset + offset, textLen),
          );
          offset += (textLen + 3) & ~3;
          results.push({
            id,
            type: 'text_input',
            text_field_handle,
            constraints,
            max_size,
            text,
          });
          break;
        }
        case 5: {
          // date_input
          const date_field_handle = view.getUint32(offset, true);
          offset += 4;
          const mode = view.getInt32(offset, true);
          offset += 4;
          const initialized = view.getUint8(offset) !== 0;
          offset += 4;
          const value_millis = Number(view.getBigInt64(offset, true));
          offset += 8;
          results.push({
            id,
            type: 'date_input',
            date_field_handle,
            mode,
            initialized,
            value_millis,
          });
          break;
        }
        case 8: {
          const operationLen = view.getUint32(offset, true);
          offset += 4;
          const operation = decoder.decode(
            new Uint8Array(view.buffer, view.byteOffset + offset, operationLen),
          );
          offset += (operationLen + 3) & ~3;
          const payloadLen = view.getUint32(offset, true);
          offset += 4;
          const payload = decoder.decode(
            new Uint8Array(view.buffer, view.byteOffset + offset, payloadLen),
          );
          offset += (payloadLen + 3) & ~3;
          results.push({ id, type: 'bluetooth', operation, payload });
          break;
        }
      }
    }
    this.w!.jvm_free(bufPtr, outLen);
    return results;
  }

  getAsyncStateCounts(): [number, number, number] {
    const outCountsPtr = this.w!.jvm_alloc(12);
    this.w!.jvm_get_async_state_counts(this.ptr, outCountsPtr);
    const view = new DataView(this.w!.memory.buffer, outCountsPtr, 12);
    const counts: [number, number, number] = [
      view.getUint32(0, true),
      view.getUint32(4, true),
      view.getUint32(8, true),
    ];
    this.w!.jvm_free(outCountsPtr, 12);
    return counts;
  }

  getAsyncImageBytes(requestId: number): Uint8Array | null {
    const outLenPtr = this.w!.jvm_alloc(4);
    const bytesPtr = this.w!.jvm_get_async_image_bytes(
      this.ptr,
      requestId,
      outLenPtr,
    );
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (bytesPtr === 0 || outLen === 0) return null;
    const result = new Uint8Array(
      this.w!.memory.buffer,
      bytesPtr,
      outLen,
    ).slice();
    this.w!.jvm_free(bytesPtr, outLen);
    return result;
  }

  resolveAsyncImage(requestId: number, width: number, height: number): void {
    this.w!.jvm_resolve_async_image(this.ptr, requestId, width, height);
  }

  resolveAsyncPixels(requestId: number, pixels: Int32Array): void {
    if (pixels.length === 0) {
      this.w!.jvm_resolve_async_pixels(this.ptr, requestId, 0, 0);
      return;
    }
    const ptr = this.w!.jvm_alloc(pixels.byteLength);
    new Int32Array(this.w!.memory.buffer, ptr, pixels.length).set(pixels);
    this.w!.jvm_resolve_async_pixels(this.ptr, requestId, ptr, pixels.length);
    this.w!.jvm_free(ptr, pixels.byteLength);
  }

  resolveAsyncM3gImage(
    requestId: number,
    pixels: Uint8Array,
    width = 0,
    height = 0,
  ): void {
    if (pixels.length === 0) {
      this.w!.jvm_resolve_async_m3g_image(
        this.ptr,
        requestId,
        width,
        height,
        0,
        0,
      );
      return;
    }
    const ptr = this.w!.jvm_alloc(pixels.byteLength);
    new Uint8Array(this.w!.memory.buffer, ptr, pixels.length).set(pixels);
    this.w!.jvm_resolve_async_m3g_image(
      this.ptr,
      requestId,
      width,
      height,
      ptr,
      pixels.length,
    );
    this.w!.jvm_free(ptr, pixels.byteLength);
  }

  resolveAsyncRmsLoad(requestId: number, data: ArrayBuffer | null): void {
    if (data === null || data === undefined) {
      this.w!.jvm_resolve_async_rms_load(this.ptr, requestId, 0, -1);
    } else {
      const src = new Uint8Array(data);
      const ptr = this.w!.jvm_alloc(src.length);
      new Uint8Array(this.w!.memory.buffer, ptr, src.length).set(src);
      this.w!.jvm_resolve_async_rms_load(this.ptr, requestId, ptr, src.length);
      this.w!.jvm_free(ptr, src.length);
    }
  }

  resolveAsyncRmsKeys(requestId: number, keys: string[]): void {
    if (keys.length === 0) {
      this.w!.jvm_resolve_async_rms_keys(this.ptr, requestId, 0, 0);
    } else {
      const encoded = new TextEncoder().encode(keys.join('\n'));
      const ptr = this.w!.jvm_alloc(encoded.length);
      new Uint8Array(this.w!.memory.buffer, ptr, encoded.length).set(encoded);
      this.w!.jvm_resolve_async_rms_keys(
        this.ptr,
        requestId,
        ptr,
        encoded.length,
      );
      this.w!.jvm_free(ptr, encoded.length);
    }
  }

  resolveAsyncTextInput(requestId: number, text: string | null): void {
    if (text === null) {
      this.w!.jvm_resolve_async_text_input(this.ptr, requestId, 0, -1);
      return;
    }
    const [textPtr, textLen] = writeString(this.w!, text);
    this.w!.jvm_resolve_async_text_input(this.ptr, requestId, textPtr, textLen);
    this.w!.jvm_free(textPtr, textLen);
  }

  resolveAsyncDateInput(
    requestId: number,
    result: { initialized: boolean; valueMillis: number } | null,
  ): void {
    this.w!.jvm_resolve_async_date_input(
      this.ptr,
      requestId,
      result ? 1 : 0,
      result?.initialized ? 1 : 0,
      BigInt(Math.trunc(result?.valueMillis ?? 0)),
    );
  }

  resolveAsyncBluetooth(requestId: number, response: string): void {
    const [responsePtr, responseLen] = writeString(this.w!, response);
    this.w!.jvm_resolve_async_bluetooth(
      this.ptr,
      requestId,
      responsePtr,
      responseLen,
    );
    this.w!.jvm_free(responsePtr, responseLen);
  }

  resumeAsync(): number {
    return this.w!.jvm_resume_async(this.ptr);
  }

  /** Push a native event into the JVM's event queue (wakes NativeEventMonitor). */
  pushNativeEvent(
    eventType: number,
    intParam1: number,
    intParam2: number,
    intParam3: number,
    intParam4: number,
  ): void {
    this.w!.jvm_push_native_event(
      this.ptr,
      eventType,
      intParam1,
      intParam2,
      intParam3,
      intParam4,
    );
  }

  /** Set the speed multiplier for Thread.sleep deadline scaling. */
  setSpeedMultiplier(speed: number): void {
    this.w!.jvm_set_speed_multiplier(this.ptr, speed);
  }

  /** Save the full VM state as a binary snapshot. Optionally includes display surface PNG. */
  saveState(displaySurfacePng?: {
    width: number;
    height: number;
    data: Uint8Array;
  }): Uint8Array | null {
    let pngPtr = 0;
    let pngLen = 0;
    let displayWidth = 0;
    let displayHeight = 0;
    if (
      displaySurfacePng &&
      displaySurfacePng.width > 0 &&
      displaySurfacePng.height > 0 &&
      displaySurfacePng.data.length > 0
    ) {
      displayWidth = displaySurfacePng.width;
      displayHeight = displaySurfacePng.height;
      pngLen = displaySurfacePng.data.byteLength;
      pngPtr = this.w!.jvm_alloc(pngLen);
      new Uint8Array(this.w!.memory.buffer, pngPtr, pngLen).set(
        displaySurfacePng.data,
      );
    }
    const outLenPtr = this.w!.jvm_alloc(4);
    const dataPtr = this.w!.jvm_save_state(
      this.ptr,
      displayWidth,
      displayHeight,
      pngPtr,
      pngLen,
      outLenPtr,
    );
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (pngPtr !== 0) this.w!.jvm_free(pngPtr, pngLen);
    if (dataPtr === 0 || outLen === 0) return null;
    const result = new Uint8Array(
      this.w!.memory.buffer,
      dataPtr,
      outLen,
    ).slice();
    this.w!.jvm_free(dataPtr, outLen);
    return result;
  }

  /** Save a persistable state optimized for periodic auto-save. */
  saveStateFast(): Uint8Array | null {
    const outLenPtr = this.w!.jvm_alloc(4);
    const dataPtr = this.w!.jvm_save_state_fast(this.ptr, outLenPtr);
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (dataPtr === 0 || outLen === 0) return null;
    const result = new Uint8Array(
      this.w!.memory.buffer,
      dataPtr,
      outLen,
    ).slice();
    this.w!.jvm_free(dataPtr, outLen);
    return result;
  }

  collectImageSurfaceSnapshotTargets(): ImageSurfaceSnapshotTarget[] {
    return this.readImageSurfaceSnapshotTargets(
      this.w!.jvm_collect_image_surface_snapshot_targets,
    );
  }

  collectRewindImageSurfaceSnapshotTargets(): ImageSurfaceSnapshotTarget[] {
    return this.readImageSurfaceSnapshotTargets(
      this.w!.jvm_collect_rewind_image_surface_snapshot_targets,
    );
  }

  private readImageSurfaceSnapshotTargets(
    collect: (ptr: number, outLenPtr: number) => number,
  ): ImageSurfaceSnapshotTarget[] {
    const outLenPtr = this.w!.jvm_alloc(4);
    const bufPtr = collect(this.ptr, outLenPtr);
    const outLen = new DataView(this.w!.memory.buffer).getUint32(
      outLenPtr,
      true,
    );
    this.w!.jvm_free(outLenPtr, 4);
    if (bufPtr === 0 || outLen === 0) return [];
    const view = new DataView(this.w!.memory.buffer, bufPtr, outLen);
    const results: ImageSurfaceSnapshotTarget[] = [];
    let offset = 0;
    const count = view.getUint32(offset, true);
    offset += 4;
    for (let i = 0; i < count; i++) {
      const surface_id = view.getInt32(offset, true);
      offset += 4;
      const width = view.getInt32(offset, true);
      offset += 4;
      const height = view.getInt32(offset, true);
      offset += 4;
      results.push({ surface_id, width, height });
    }
    this.w!.jvm_free(bufPtr, outLen);
    return results;
  }

  clearImageSurfaceSnapshots(): void {
    this.w!.jvm_clear_image_surface_snapshots(this.ptr);
  }

  addImageSurfaceSnapshot(
    target: ImageSurfaceSnapshotTarget,
    pixels: Int32Array,
  ): void {
    if (pixels.length === 0) return;
    const ptr = this.w!.jvm_alloc(pixels.byteLength);
    new Int32Array(this.w!.memory.buffer, ptr, pixels.length).set(pixels);
    this.w!.jvm_add_image_surface_snapshot(
      this.ptr,
      target.surface_id,
      target.width,
      target.height,
      ptr,
      pixels.length,
    );
    this.w!.jvm_free(ptr, pixels.byteLength);
  }

  /** Load a previously saved VM state snapshot. */
  loadState(data: Uint8Array): void {
    const [ptr, len] = writeBytes(this.w!, data);
    this.w!.jvm_load_state(this.ptr, ptr, len);
    this.w!.jvm_free(ptr, len);
  }

  /** Capture an in-memory snapshot without GC, serialization or compression. */
  captureRewindSnapshot(): JvmRewindSnapshot | null {
    const ptr = this.w!.jvm_rewind_capture(this.ptr);
    return ptr === 0 ? null : new JvmRewindSnapshot(this.w!, ptr);
  }

  /** Restore and consume an in-memory rewind snapshot. */
  restoreRewindSnapshot(snapshot: JvmRewindSnapshot): void {
    const snapshotPtr = snapshot.consume(this.w!);
    if (!this.w!.jvm_rewind_restore(this.ptr, snapshotPtr)) {
      throw new Error('Failed to restore rewind snapshot');
    }
  }

  /** Parse save state metadata without loading. Returns version, timestamp and section locations. */
  parseSaveMeta(data: Uint8Array): {
    version: number;
    timestamp: number;
    vmStateOffset: number;
    vmStateLength: number;
    displayPngOffset: number;
    displayPngLength: number;
    displayPngWidth: number;
    displayPngHeight: number;
  } | null {
    const metaSize = this.w!.jvm_save_meta_size();
    if (data.length < metaSize) return null;
    const meta = data.subarray(0, metaSize);
    const [dataPtr, dataLen] = writeBytes(this.w!, meta);
    const outMeta = this.w!.jvm_alloc(40);

    try {
      const ok = this.w!.jvm_parse_save_meta(dataPtr, dataLen, outMeta);
      if (!ok) return null;

      const view = new DataView(this.w!.memory.buffer, outMeta, 40);
      return {
        timestamp: Number(view.getBigUint64(0, true)),
        version: view.getUint32(8, true),
        vmStateOffset: view.getUint32(12, true),
        vmStateLength: view.getUint32(16, true),
        displayPngOffset: view.getUint32(20, true),
        displayPngLength: view.getUint32(24, true),
        displayPngWidth: view.getUint32(28, true),
        displayPngHeight: view.getUint32(32, true),
      };
    } finally {
      this.w!.jvm_free(dataPtr, dataLen);
      this.w!.jvm_free(outMeta, 40);
    }
  }

  /** Current save state compatibility version. */
  saveStateVersion(): number {
    return this.w!.jvm_save_state_version();
  }

  // ---- Cheat Engine ----

  /** search_mode tag: 0=exact 1=changed 2=unchanged 3=increased 4=decreased 5=increasedBy 6=decreasedBy 7=snapshot */
  cheatSearchBegin(searchMode: number, value?: number | bigint): void {
    const numValue = value !== undefined ? Number(value) : 0;
    this.w!.jvm_cheat_search_begin(this.ptr, searchMode, numValue);
  }

  cheatSearchRefine(searchMode: number, value?: number | bigint): void {
    const numValue = value !== undefined ? Number(value) : 0;
    this.w!.jvm_cheat_search_refine(this.ptr, searchMode, numValue);
  }

  cheatGetResults(): CheatSearchResult {
    const w = this.w!;
    const outLenPtr = w.jvm_alloc(4);
    const bufPtr = w.jvm_cheat_get_results(this.ptr, outLenPtr);
    const outLen = new DataView(w.memory.buffer).getUint32(outLenPtr, true);
    w.jvm_free(outLenPtr, 4);
    if (!bufPtr || outLen < 9) {
      if (bufPtr) w.jvm_free(bufPtr, outLen);
      return { addrs: [], count: 0, truncated: false };
    }

    const buf = new Uint8Array(w.memory.buffer, bufPtr, outLen).slice();
    w.jvm_free(bufPtr, outLen);
    const dv = new DataView(buf.buffer);
    const dec = new TextDecoder();

    const count = dv.getUint32(0, true);
    const totalCandidates = dv.getUint32(4, true);
    const flags = buf[8];
    const truncated = (flags & 1) !== 0;

    const addrs: CheatCandidate[] = [];
    let pos = 9;
    for (let i = 0; i < count; i++) {
      if (pos + 5 > buf.length) break;
      const addr = dv.getUint32(pos, true);
      pos += 4;
      const typeCount = buf[pos];
      pos += 1;
      if (pos + typeCount + 1 + 8 + 2 > buf.length) break;
      const types: string[] = [];
      for (let t = 0; t < typeCount; t++) {
        types.push(VALUE_TYPE_NAMES[buf[pos + t]] ?? 'i32');
      }
      const firstTag = typeCount > 0 ? buf[pos] : 2;
      pos += typeCount;
      const confirmedTag = buf[pos];
      pos += 1;
      const displayTag = confirmedTag !== 0xff ? confirmedTag : firstTag;
      const value = readCheatValue(dv, pos, displayTag);
      pos += 8;
      const fieldLen = dv.getUint16(pos, true);
      pos += 2;
      const field =
        fieldLen > 0
          ? dec.decode(buf.subarray(pos, pos + fieldLen))
          : undefined;
      pos += fieldLen;
      const confirmed_type =
        confirmedTag !== 0xff ? VALUE_TYPE_NAMES[confirmedTag] : undefined;
      addrs.push({ addr, types, value, confirmed_type, field });
    }
    return { addrs, count: totalCandidates, truncated };
  }

  /** Search numeric values within one object only. matchMode: 0=contains, 1=consecutive. */
  cheatSearchObjects(values: number[], matchMode = 0): CheatObjectSearchResult {
    return this.cheatSearchObjectsInternal(values, matchMode, false);
  }

  /** Refine previous candidates, comparing new values with their original matched slots. */
  cheatRefineObjects(values: number[], matchMode = 0): CheatObjectSearchResult {
    return this.cheatSearchObjectsInternal(values, matchMode, true);
  }

  cheatClearObjectSearch(): void {
    this.w!.jvm_cheat_object_search_clear(this.ptr);
  }

  private cheatSearchObjectsInternal(
    values: number[],
    matchMode: number,
    refine: boolean,
  ): CheatObjectSearchResult {
    if (
      values.length === 0 ||
      values.some((value) => !Number.isFinite(value))
    ) {
      return { objects: [], count: 0, truncated: false };
    }
    const w = this.w!;
    const valueBytes = new Uint8Array(values.length * 8);
    const valueView = new DataView(valueBytes.buffer);
    values.forEach((value, index) => {
      valueView.setFloat64(index * 8, value, true);
    });
    const [valuesPtr, valuesLen] = writeBytes(w, valueBytes);
    const outLenPtr = w.jvm_alloc(4);
    const search = refine
      ? w.jvm_cheat_refine_objects
      : w.jvm_cheat_search_objects;
    const bufPtr = search(
      this.ptr,
      valuesPtr,
      values.length,
      matchMode,
      outLenPtr,
    );
    const outLen = new DataView(w.memory.buffer).getUint32(outLenPtr, true);
    w.jvm_free(valuesPtr, valuesLen);
    w.jvm_free(outLenPtr, 4);
    if (!bufPtr || outLen < 9) {
      if (bufPtr) w.jvm_free(bufPtr, outLen);
      return { objects: [], count: 0, truncated: false };
    }

    const buf = new Uint8Array(w.memory.buffer, bufPtr, outLen).slice();
    w.jvm_free(bufPtr, outLen);
    const dv = new DataView(buf.buffer);
    const decoder = new TextDecoder();
    const count = dv.getUint32(0, true);
    const total = dv.getUint32(4, true);
    const truncated = (buf[8] & 1) !== 0;
    let position = 9;
    const readString = (): string | null => {
      if (position + 2 > buf.length) return null;
      const length = dv.getUint16(position, true);
      position += 2;
      if (position + length > buf.length) return null;
      const value = decoder.decode(buf.subarray(position, position + length));
      position += length;
      return value;
    };

    const objects: CheatObjectCandidate[] = [];
    for (let index = 0; index < count; index += 1) {
      if (position + 5 > buf.length) break;
      const object_addr = dv.getUint32(position, true);
      position += 4;
      const kindTag = buf[position++];
      const class_name = readString();
      if (class_name === null || position + 2 > buf.length) break;
      const fieldCount = dv.getUint16(position, true);
      position += 2;
      const fields: CheatObjectField[] = [];
      let valid = true;
      for (let fieldIndex = 0; fieldIndex < fieldCount; fieldIndex += 1) {
        if (position + 6 > buf.length) {
          valid = false;
          break;
        }
        const addr = dv.getUint32(position, true);
        position += 4;
        const typeTag = buf[position++];
        const matched = (buf[position++] & 1) !== 0;
        const name = readString();
        const descriptor = readString();
        if (name === null || descriptor === null || position + 8 > buf.length) {
          valid = false;
          break;
        }
        const value = readCheatValue(dv, position, typeTag);
        position += 8;
        fields.push({
          addr,
          name,
          descriptor,
          type: VALUE_TYPE_NAMES[typeTag] ?? 'i32',
          value,
          matched,
        });
      }
      if (!valid) break;
      objects.push({
        object_addr,
        kind: kindTag === 2 ? 'array' : kindTag === 1 ? 'static' : 'instance',
        class_name,
        fields,
      });
    }
    return { objects, count: total, truncated };
  }

  cheatSearchClear(): void {
    this.w!.jvm_cheat_search_clear(this.ptr);
  }

  cheatRead(address: number, valueType: number): number | bigint | null {
    const w = this.w!;
    const outLenPtr = w.jvm_alloc(4);
    const bufPtr = w.jvm_cheat_read(this.ptr, address, valueType, outLenPtr);
    const outLen = new DataView(w.memory.buffer).getUint32(outLenPtr, true);
    w.jvm_free(outLenPtr, 4);
    if (!bufPtr || outLen < 9) {
      if (bufPtr) w.jvm_free(bufPtr, outLen);
      return null;
    }
    const dv = new DataView(w.memory.buffer, bufPtr, 9);
    const vtype = dv.getUint8(0);
    const value = readCheatValue(dv, 1, vtype);
    w.jvm_free(bufPtr, outLen);
    return value;
  }

  cheatWrite(address: number, valueType: number, value: number | bigint): void {
    const w = this.w!;
    const buf = cheatValueToBytes(valueType, value);
    const [valuePtr, valueLen] = writeBytes(w, buf);
    w.jvm_cheat_write(this.ptr, address, valueType, valuePtr, valueLen);
    w.jvm_free(valuePtr, valueLen);
  }

  cheatFreezeAdd(
    address: number,
    valueType: number,
    value: number | bigint,
  ): void {
    const w = this.w!;
    const buf = cheatValueToBytes(valueType, value);
    const [valuePtr, valueLen] = writeBytes(w, buf);
    w.jvm_cheat_freeze_add(this.ptr, address, valueType, valuePtr, valueLen);
    w.jvm_free(valuePtr, valueLen);
  }

  cheatFreezeRemove(address: number, valueType: number): void {
    this.w!.jvm_cheat_freeze_remove(this.ptr, address, valueType);
  }

  cheatFreezeClear(): void {
    this.w!.jvm_cheat_freeze_clear(this.ptr);
  }

  cheatFreezeSetEnabled(
    address: number,
    valueType: number,
    enabled: boolean,
  ): void {
    this.w!.jvm_cheat_freeze_set_enabled(
      this.ptr,
      address,
      valueType,
      enabled ? 1 : 0,
    );
  }

  cheatFreezeList(): CheatFreezeEntry[] {
    const w = this.w!;
    const outLenPtr = w.jvm_alloc(4);
    const bufPtr = w.jvm_cheat_freeze_list(this.ptr, outLenPtr);
    const outLen = new DataView(w.memory.buffer).getUint32(outLenPtr, true);
    w.jvm_free(outLenPtr, 4);
    if (!bufPtr || outLen < 4) {
      if (bufPtr) w.jvm_free(bufPtr, outLen);
      return [];
    }

    const buf = new Uint8Array(w.memory.buffer, bufPtr, outLen).slice();
    w.jvm_free(bufPtr, outLen);
    const dv = new DataView(buf.buffer);
    const count = dv.getUint32(0, true);
    const result: CheatFreezeEntry[] = [];
    let pos = 4;
    for (let i = 0; i < count; i++) {
      if (pos + 14 > buf.length) break;
      const addr = dv.getUint32(pos, true);
      pos += 4;
      const vtype = buf[pos];
      pos += 1;
      const flags = buf[pos];
      pos += 1;
      const enabled = (flags & 1) !== 0;
      const stale = (flags & 2) !== 0;
      const value = readCheatValue(dv, pos, vtype);
      pos += 8;
      result.push({
        addr,
        type: VALUE_TYPE_NAMES[vtype] ?? 'i32',
        value,
        enabled,
        stale,
      });
    }
    return result;
  }

  // ---- Watch list ----

  /**
   * Add or update watch entries (batch).
   * Each entry: { addr, valueType (0–5), label? }
   *
   * Wire format sent to WASM:
   *   [count: u32 LE] per entry: [addr: u32 LE][vtype: u8][label_len: u16 LE][label: UTF-8]
   */
  cheatWatchAdd(
    entries: { addr: number; valueType: number; label?: string }[],
  ): void {
    const w = this.w!;
    const enc = new TextEncoder();
    const labelBufs = entries.map((e) =>
      e.label ? enc.encode(e.label) : new Uint8Array(0),
    );
    const totalLen =
      4 +
      entries.reduce((acc, _, i) => acc + 4 + 1 + 2 + labelBufs[i].length, 0);
    const buf = new Uint8Array(totalLen);
    const dv = new DataView(buf.buffer);
    dv.setUint32(0, entries.length, true);
    let pos = 4;
    for (let i = 0; i < entries.length; i++) {
      dv.setUint32(pos, entries[i].addr, true);
      pos += 4;
      dv.setUint8(pos, entries[i].valueType);
      pos += 1;
      dv.setUint16(pos, labelBufs[i].length, true);
      pos += 2;
      buf.set(labelBufs[i], pos);
      pos += labelBufs[i].length;
    }
    const [bufPtr, bufLen] = writeBytes(w, buf);
    w.jvm_cheat_watch_add_batch(this.ptr, bufPtr, bufLen);
    w.jvm_free(bufPtr, bufLen);
  }

  cheatWatchRemove(address: number, valueType: number): void {
    this.w!.jvm_cheat_watch_remove(this.ptr, address, valueType);
  }

  cheatWatchClear(): void {
    this.w!.jvm_cheat_watch_clear(this.ptr);
  }

  /**
   * Returns all watch entries parsed from the binary response.
   *
   * Wire format from WASM:
   *   [count: u32 LE] per entry: [addr: u32 LE][vtype: u8][flags: u8][value: 8 bytes LE][label_len: u16 LE][label: UTF-8]
   */
  cheatWatchList(): CheatWatchEntry[] {
    const w = this.w!;
    const outLenPtr = w.jvm_alloc(4);
    const bufPtr = w.jvm_cheat_watch_list(this.ptr, outLenPtr);
    const outLen = new DataView(w.memory.buffer).getUint32(outLenPtr, true);
    w.jvm_free(outLenPtr, 4);
    if (!bufPtr || outLen < 4) {
      w.jvm_free(bufPtr, outLen);
      return [];
    }

    const buf = new Uint8Array(w.memory.buffer, bufPtr, outLen).slice();
    w.jvm_free(bufPtr, outLen);

    const dv = new DataView(buf.buffer);
    const count = dv.getUint32(0, true);
    const dec = new TextDecoder();
    const result: CheatWatchEntry[] = [];
    let pos = 4;
    for (let i = 0; i < count; i++) {
      if (pos + 16 > buf.length) break;
      const addr = dv.getUint32(pos, true);
      pos += 4;
      const vtype = buf[pos];
      pos += 1;
      const flags = buf[pos];
      pos += 1;
      const changed = (flags & 1) !== 0;
      const stale = (flags & 2) !== 0;
      const value = readCheatValue(dv, pos, vtype);
      pos += 8;
      const labelLen = dv.getUint16(pos, true);
      pos += 2;
      const label = dec.decode(buf.subarray(pos, pos + labelLen));
      pos += labelLen;
      result.push({
        addr,
        type: VALUE_TYPE_NAMES[vtype] ?? 'i32',
        value,
        label,
        changed,
        stale,
      });
    }
    return result;
  }

  /** Write a value directly to any address. */
  cheatWatchWrite(
    address: number,
    valueType: number,
    value: number | bigint,
  ): void {
    const w = this.w!;
    const buf = cheatValueToBytes(valueType, value);
    const [valuePtr, valueLen] = writeBytes(w, buf);
    w.jvm_cheat_watch_write(this.ptr, address, valueType, valuePtr, valueLen);
    w.jvm_free(valuePtr, valueLen);
  }

  // ---- Stable cheat code (方案 B) ----

  /** Inspect a heap address from method-A results and return a stable code descriptor if it's a static field. */
  cheatCodeFromCandidate(
    address: number,
    valueTypeTag: number,
  ): CheatCodeDesc | { error: string } {
    const w = this.w!;
    const outLenPtr = w.jvm_alloc(4);
    const bufPtr = w.jvm_cheat_code_from_candidate(
      this.ptr,
      address,
      valueTypeTag,
      outLenPtr,
    );
    const outLen = new DataView(w.memory.buffer).getUint32(outLenPtr, true);
    w.jvm_free(outLenPtr, 4);
    if (!bufPtr || outLen < 1) {
      if (bufPtr) w.jvm_free(bufPtr, outLen);
      return { error: 'empty response' };
    }

    const buf = new Uint8Array(w.memory.buffer, bufPtr, outLen).slice();
    w.jvm_free(bufPtr, outLen);
    const dv = new DataView(buf.buffer);
    const dec = new TextDecoder();
    const status = buf[0];

    if (status === 0) {
      // error
      const errLen = dv.getUint16(1, true);
      const error = dec.decode(buf.subarray(3, 3 + errLen));
      return { error };
    }

    // ok: [status:1][vtype:1][id_len:2][id][class_name_len:2][class_name][field_name_len:2][field_name][descriptor_len:2][descriptor]
    let pos = 1;
    const vtype = buf[pos];
    pos += 1;
    const readStr = (): string => {
      const len = dv.getUint16(pos, true);
      pos += 2;
      const s = dec.decode(buf.subarray(pos, pos + len));
      pos += len;
      return s;
    };
    const id = readStr();
    const class_name = readStr();
    const field_name = readStr();
    const descriptor = readStr();
    return {
      kind: 'static',
      class_name,
      field_name,
      descriptor,
      value_type: VALUE_TYPE_NAMES[vtype] ?? 'i32',
      id,
    };
  }

  /** Resolve an array of stable cheat code entries, applying write/freeze actions as specified. */
  cheatCodeResolve(entries: CheatCodeEntry[]): CheatCodeResolveResult[] {
    const w = this.w!;
    const enc = new TextEncoder();
    const ACTION_MAP: Record<string, number> = { none: 0, write: 1, freeze: 2 };
    const VALUE_TYPE_TAG_MAP: Record<string, number> = {
      i8: 0,
      i16: 1,
      i32: 2,
      i64: 3,
      f32: 4,
      f64: 5,
    };

    // Encode binary input: [count: u32] per entry: [id][class_name][field_name][descriptor][vtype:u8][action:u8][has_value:u8][value:8B]
    // First pass: compute total size
    const encodedEntries = entries.map((e) => ({
      id: enc.encode(e.id),
      class_name: enc.encode(e.class_name),
      field_name: enc.encode(e.field_name),
      descriptor: enc.encode(e.descriptor),
      vtype: VALUE_TYPE_TAG_MAP[e.value_type] ?? 2,
      action: ACTION_MAP[e.action] ?? 0,
      value: e.value,
    }));
    let totalLen = 4;
    for (const e of encodedEntries) {
      totalLen +=
        2 +
        e.id.length +
        (2 + e.class_name.length) +
        (2 + e.field_name.length) +
        (2 + e.descriptor.length) +
        1 +
        1 +
        1 +
        8;
    }

    const inputBuf = new Uint8Array(totalLen);
    const inputDv = new DataView(inputBuf.buffer);
    inputDv.setUint32(0, entries.length, true);
    let pos = 4;
    const writeStr = (bytes: Uint8Array) => {
      inputDv.setUint16(pos, bytes.length, true);
      pos += 2;
      inputBuf.set(bytes, pos);
      pos += bytes.length;
    };
    for (const e of encodedEntries) {
      writeStr(e.id);
      writeStr(e.class_name);
      writeStr(e.field_name);
      writeStr(e.descriptor);
      inputBuf[pos] = e.vtype;
      pos += 1;
      inputBuf[pos] = e.action;
      pos += 1;
      const hasValue = e.value !== undefined ? 1 : 0;
      inputBuf[pos] = hasValue;
      pos += 1;
      if (hasValue && e.value !== undefined) {
        const valBytes = cheatValueToBytes(e.vtype, e.value);
        inputBuf.set(valBytes, pos);
        // Zero-pad to 8 bytes (valBytes may be < 8)
      }
      pos += 8;
    }

    const [codePtr, codeLen] = writeBytes(w, inputBuf);
    const outLenPtr = w.jvm_alloc(4);
    const bufPtr = w.jvm_cheat_code_resolve(
      this.ptr,
      codePtr,
      codeLen,
      outLenPtr,
    );
    const outLen = new DataView(w.memory.buffer).getUint32(outLenPtr, true);
    w.jvm_free(codePtr, codeLen);
    w.jvm_free(outLenPtr, 4);
    if (!bufPtr || outLen < 4) {
      if (bufPtr) w.jvm_free(bufPtr, outLen);
      return [];
    }

    const buf = new Uint8Array(w.memory.buffer, bufPtr, outLen).slice();
    w.jvm_free(bufPtr, outLen);
    const dv = new DataView(buf.buffer);
    const dec = new TextDecoder();

    const count = dv.getUint32(0, true);
    const results: CheatCodeResolveResult[] = [];
    let rpos = 4;
    for (let i = 0; i < count; i++) {
      if (rpos >= buf.length) break;
      const status = buf[rpos];
      rpos += 1;
      const idLen = dv.getUint16(rpos, true);
      rpos += 2;
      const id = dec.decode(buf.subarray(rpos, rpos + idLen));
      rpos += idLen;
      if (status === 1) {
        const addr = dv.getUint32(rpos, true);
        rpos += 4;
        const vtype = buf[rpos];
        rpos += 1;
        results.push({
          id,
          addr,
          value_type: VALUE_TYPE_NAMES[vtype] ?? 'i32',
          ok: true,
        });
      } else {
        const errLen = dv.getUint16(rpos, true);
        rpos += 2;
        const error = dec.decode(buf.subarray(rpos, rpos + errLen));
        rpos += errLen;
        results.push({ id, ok: false, error });
      }
    }
    return results;
  }
}
