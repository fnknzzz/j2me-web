import type { J2MPlatformBridge } from './env';
import type { JarEntry } from './jar';
import type { J2MEPerformanceCollector } from './performance';

export interface J2MESimulatorOptions {
  jar: Uint8Array | JarEntry[];
  hooks?: J2MPlatformBridge;
  midletNumber?: number;
  performanceCollector?: J2MEPerformanceCollector;
}
