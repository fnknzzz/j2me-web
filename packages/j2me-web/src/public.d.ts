/** Public browser API for j2me-web. */

export interface JarEntry {
  name: string;
  data: Uint8Array;
}

export interface J2MEBluetoothHost {
  isSupported?: () => boolean;
}

export interface J2MEPerformanceOptions {
  enabled?: boolean;
  targetFps?: number;
  maxFrameSamples?: number;
}

export interface J2MEPerformanceSnapshot {
  schemaVersion: 1;
  startedAt: number;
  durationMs: number;
  targetFps: number;
  experience: {
    presentedFrames: number;
    presentFps: number;
    missedDeadlineRatio: number;
    longFrameCount: number;
  };
}

export interface JvmRewindSnapshot {
  readonly byteLength: number;
}

export interface J2MESoftKeyLabels {
  left?: string;
  right?: string;
}

export interface FontConfig {
  families?: Partial<FontFamilies>;
  sizes?: Partial<FontSizes>;
}

export interface FontFamilies {
  system: string;
  proportional: string;
  monospace: string;
}

export interface FontSizes {
  small: number;
  medium: number;
  large: number;
}

export interface HttpRequestInfo {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: Uint8Array;
}

export interface HttpResponseData {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: Uint8Array;
}

export type NetworkInterceptor = (
  request: HttpRequestInfo,
) => Promise<HttpResponseData | undefined> | HttpResponseData | undefined;

export interface NetworkHooksOptions {
  interceptor?: NetworkInterceptor;
}

export interface J2MEWebLifecycleCallbacks {
  onDestroyed?: () => void;
  onPaused?: () => void;
  onResumed?: () => void;
}

export interface J2MEWebOptions {
  canvas?: HTMLCanvasElement;
  container?: HTMLElement;
  jar: ArrayBuffer | JarEntry[];
  autoStart?: boolean;
  midletId?: string;
  lifecycleCallbacks?: J2MEWebLifecycleCallbacks;
  midletNumber?: number;
  network?: NetworkHooksOptions;
  bluetooth?: J2MEBluetoothHost;
  font?: FontConfig;
  performanceMonitor?: J2MEPerformanceOptions;
  onSoftKeyLabelsChanged?: (labels: J2MESoftKeyLabels) => void;
}

export declare enum J2MEGameAction {
  UP = 1,
  LEFT = 2,
  RIGHT = 5,
  DOWN = 6,
  FIRE = 8,
}

export declare enum J2MEKeyCode {
  UP = -1,
  DOWN = -2,
  LEFT = -3,
  RIGHT = -4,
  FIRE = -5,
  SOFT_LEFT = -6,
  SOFT_RIGHT = -7,
}

export declare class J2MEWeb {
  constructor(options: J2MEWebOptions);
  start(): Promise<void>;
  pause(): Promise<void>;
  resume(): Promise<void>;
  suspendExecution(): Promise<void>;
  resumeExecution(): Promise<void>;
  stop(): Promise<void>;
  getPerformanceSnapshot(): J2MEPerformanceSnapshot | null;
  resetPerformanceCounters(): void;
  readonly input: {
    triggerKeyPressed(keyCode: number): void;
    triggerKeyReleased(keyCode: number): void;
    triggerPointerPressed(x: number, y: number): void;
    triggerPointerReleased(x: number, y: number): void;
    triggerPointerDragged(x: number, y: number): void;
  };
  screenshot(): string;
  downloadScreenshot(filename?: string): void;
  getCanvas(): HTMLCanvasElement;
  toggleFullscreen(): Promise<void>;
  isFullscreen(): boolean;
  setSpeed(multiplier: number): void;
  getSpeed(): number;
  setMusicFollowSpeed(enabled: boolean): void;
  getMusicFollowSpeed(): boolean;
  getSoftKeyLabels(): J2MESoftKeyLabels;
  onSoftKeyLabelsChanged(
    handler: (labels: J2MESoftKeyLabels) => void,
  ): () => void;
  dumpThreads(): string;
  saveState(): Promise<Uint8Array | null>;
  saveStateFast(): Promise<Uint8Array | null>;
  captureRewindSnapshot(): Promise<JvmRewindSnapshot | null>;
  loadState(data: Uint8Array): void;
  restoreRewindSnapshot(snapshot: JvmRewindSnapshot): Promise<void>;
}
