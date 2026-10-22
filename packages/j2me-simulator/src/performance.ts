export type J2MEPerformanceStage =
  | 'vmStep'
  | 'bridgeFlush'
  | 'commandDispatch'
  | 'lcdUiEncode'
  | 'm3gEncode'
  | 'textRaster'
  | 'present'
  | 'readbackWait';

export interface J2MEPerformanceOptions {
  enabled?: boolean;
  targetFps?: number;
  maxFrameSamples?: number;
}

export interface J2MEPerformanceEnvironment {
  backend: 'rust';
  userAgent: string;
  logicalWidth: number;
  logicalHeight: number;
  physicalWidth: number;
  physicalHeight: number;
  renderScale: number;
  devicePixelRatio: number;
  gpuTimestampSupported: boolean;
}

export interface J2MEPerformanceTiming {
  totalMs: number;
  count: number;
  maxMs: number;
}

export interface J2MEPerformanceSnapshot {
  schemaVersion: 1;
  startedAt: number;
  durationMs: number;
  targetFps: number;
  environment: J2MEPerformanceEnvironment;
  experience: {
    presentedFrames: number;
    presentFps: number;
    frameIntervalMs: {
      p50: number;
      p95: number;
      p99: number;
      max: number;
    };
    missedDeadlineRatio: number;
    longFrameCount: number;
    timeToFirstPresentMs?: number;
    tickCount: number;
    tickRate: number;
  };
  cpu: Record<J2MEPerformanceStage, J2MEPerformanceTiming>;
  commands: {
    flushes: number;
    bytes: number;
    maxBufferBytes: number;
    count: number;
    byId: Record<number, number>;
    shapeVertices: number;
    drawRgbPixels: number;
  };
  gpu: {
    submits: number;
    renderPasses: number;
    drawCalls: number;
    buffersCreated: number;
    bufferBytesAllocated: number;
    texturesCreated: number;
    textureBytesAllocated: number;
    bindGroupsCreated: number;
    writeBufferBytes: number;
    writeTextureBytes: number;
    readbacks: number;
    readbackBytes: number;
  };
  memory: {
    jsHeapUsedBytes?: number;
    estimatedGpuBytes: number;
  };
}

const STAGES: J2MEPerformanceStage[] = [
  'vmStep',
  'bridgeFlush',
  'commandDispatch',
  'lcdUiEncode',
  'm3gEncode',
  'textRaster',
  'present',
  'readbackWait',
];

const emptyTimings = (): Record<J2MEPerformanceStage, J2MEPerformanceTiming> =>
  Object.fromEntries(
    STAGES.map((stage) => [stage, { totalMs: 0, count: 0, maxMs: 0 }]),
  ) as Record<J2MEPerformanceStage, J2MEPerformanceTiming>;

const percentile = (sorted: number[], ratio: number): number => {
  if (sorted.length === 0) return 0;
  const index = Math.min(
    sorted.length - 1,
    Math.max(0, Math.ceil(sorted.length * ratio) - 1),
  );
  return sorted[index];
};

export class J2MEPerformanceCollector {
  private readonly targetFps: number;
  private readonly maxFrameSamples: number;
  private startedAt = performance.now();
  private firstStartedAt = this.startedAt;
  private firstPresentAt: number | undefined;
  private lastPresentAt: number | undefined;
  private presentFrameRequest: number | null = null;
  private presentFrameEpoch = 0;
  private presentedFrames = 0;
  private tickCount = 0;
  private frameIntervals: number[] = [];
  private timings = emptyTimings();
  private commandFlushes = 0;
  private commandBytes = 0;
  private maxCommandBufferBytes = 0;
  private commandCount = 0;
  private commandById: Record<number, number> = {};
  private shapeVertices = 0;
  private drawRgbPixels = 0;
  private gpu = {
    submits: 0,
    renderPasses: 0,
    drawCalls: 0,
    buffersCreated: 0,
    bufferBytesAllocated: 0,
    texturesCreated: 0,
    textureBytesAllocated: 0,
    bindGroupsCreated: 0,
    writeBufferBytes: 0,
    writeTextureBytes: 0,
    readbacks: 0,
    readbackBytes: 0,
  };
  private estimatedGpuBytes = 0;
  private environment: J2MEPerformanceEnvironment = {
    backend: 'rust',
    userAgent:
      typeof navigator === 'undefined' ? 'unknown' : navigator.userAgent,
    logicalWidth: 0,
    logicalHeight: 0,
    physicalWidth: 0,
    physicalHeight: 0,
    renderScale: 1,
    devicePixelRatio:
      typeof window === 'undefined' ? 1 : window.devicePixelRatio || 1,
    gpuTimestampSupported: false,
  };

  constructor(options: Omit<J2MEPerformanceOptions, 'enabled'> = {}) {
    this.targetFps = Math.max(1, options.targetFps ?? 60);
    this.maxFrameSamples = Math.max(120, options.maxFrameSamples ?? 4096);
  }

  configureEnvironment(environment: Partial<J2MEPerformanceEnvironment>): void {
    this.environment = { ...this.environment, ...environment };
  }

  reset(): void {
    if (
      this.presentFrameRequest !== null &&
      typeof cancelAnimationFrame === 'function'
    ) {
      cancelAnimationFrame(this.presentFrameRequest);
    }
    this.presentFrameRequest = null;
    this.presentFrameEpoch++;
    this.startedAt = performance.now();
    this.lastPresentAt = undefined;
    this.presentedFrames = 0;
    this.tickCount = 0;
    this.frameIntervals = [];
    this.timings = emptyTimings();
    this.commandFlushes = 0;
    this.commandBytes = 0;
    this.maxCommandBufferBytes = 0;
    this.commandCount = 0;
    this.commandById = {};
    this.shapeVertices = 0;
    this.drawRgbPixels = 0;
    for (const key of Object.keys(this.gpu) as Array<keyof typeof this.gpu>) {
      this.gpu[key] = 0;
    }
  }

  now(): number {
    return performance.now();
  }

  addTiming(stage: J2MEPerformanceStage, startedAt: number): void {
    const elapsed = performance.now() - startedAt;
    const timing = this.timings[stage];
    timing.totalMs += elapsed;
    timing.count++;
    timing.maxMs = Math.max(timing.maxMs, elapsed);
  }

  recordTick(): void {
    this.tickCount++;
  }

  recordPresent(timestamp = performance.now()): void {
    if (typeof requestAnimationFrame === 'function') {
      if (this.presentFrameRequest !== null) return;
      const epoch = this.presentFrameEpoch;
      this.presentFrameRequest = requestAnimationFrame((frameTimestamp) => {
        if (epoch !== this.presentFrameEpoch) return;
        this.presentFrameRequest = null;
        this.commitPresent(frameTimestamp);
      });
      return;
    }
    this.commitPresent(timestamp);
  }

  private commitPresent(timestamp: number): void {
    this.firstPresentAt ??= timestamp;
    if (this.lastPresentAt !== undefined) {
      if (this.frameIntervals.length >= this.maxFrameSamples) {
        this.frameIntervals.shift();
      }
      this.frameIntervals.push(timestamp - this.lastPresentAt);
    }
    this.lastPresentAt = timestamp;
    this.presentedFrames++;
  }

  recordCommandBuffer(bytes: number): void {
    this.commandFlushes++;
    this.commandBytes += bytes;
    this.maxCommandBufferBytes = Math.max(this.maxCommandBufferBytes, bytes);
  }

  recordCommand(id: number): void {
    this.commandCount++;
    this.commandById[id] = (this.commandById[id] ?? 0) + 1;
  }

  recordShapeVertices(count: number): void {
    this.shapeVertices += count;
  }

  recordDrawRgbPixels(count: number): void {
    this.drawRgbPixels += count;
  }

  addGpuCounter(name: keyof typeof this.gpu, amount = 1): void {
    this.gpu[name] += amount;
  }

  addEstimatedGpuBytes(bytes: number): void {
    this.estimatedGpuBytes = Math.max(0, this.estimatedGpuBytes + bytes);
  }

  snapshot(): J2MEPerformanceSnapshot {
    const now = performance.now();
    const durationMs = Math.max(0.001, now - this.startedAt);
    const intervals = [...this.frameIntervals].sort((a, b) => a - b);
    const targetInterval = 1000 / this.targetFps;
    const missed = intervals.filter(
      (interval) => interval > targetInterval * 1.5,
    ).length;
    const memory = performance as Performance & {
      memory?: { usedJSHeapSize?: number };
    };
    const snapshot: J2MEPerformanceSnapshot = {
      schemaVersion: 1,
      startedAt: this.startedAt,
      durationMs,
      targetFps: this.targetFps,
      environment: { ...this.environment },
      experience: {
        presentedFrames: this.presentedFrames,
        presentFps: (this.presentedFrames * 1000) / durationMs,
        frameIntervalMs: {
          p50: percentile(intervals, 0.5),
          p95: percentile(intervals, 0.95),
          p99: percentile(intervals, 0.99),
          max: intervals.length === 0 ? 0 : intervals[intervals.length - 1],
        },
        missedDeadlineRatio:
          intervals.length === 0 ? 0 : missed / intervals.length,
        longFrameCount: intervals.filter((interval) => interval > 50).length,
        timeToFirstPresentMs:
          this.firstPresentAt === undefined
            ? undefined
            : this.firstPresentAt - this.firstStartedAt,
        tickCount: this.tickCount,
        tickRate: (this.tickCount * 1000) / durationMs,
      },
      cpu: Object.fromEntries(
        STAGES.map((stage) => [stage, { ...this.timings[stage] }]),
      ) as Record<J2MEPerformanceStage, J2MEPerformanceTiming>,
      commands: {
        flushes: this.commandFlushes,
        bytes: this.commandBytes,
        maxBufferBytes: this.maxCommandBufferBytes,
        count: this.commandCount,
        byId: { ...this.commandById },
        shapeVertices: this.shapeVertices,
        drawRgbPixels: this.drawRgbPixels,
      },
      gpu: { ...this.gpu },
      memory: {
        jsHeapUsedBytes: memory.memory?.usedJSHeapSize,
        estimatedGpuBytes: this.estimatedGpuBytes,
      },
    };
    return snapshot;
  }
}
