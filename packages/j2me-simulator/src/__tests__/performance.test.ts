import { afterEach, describe, expect, it, vi } from 'vitest';
import { J2MEPerformanceCollector } from '../performance';

describe('J2MEPerformanceCollector', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('coalesces multiple submits into one presented frame per RAF', () => {
    const callbacks: FrameRequestCallback[] = [];
    const requestAnimationFrame = vi.fn((callback: FrameRequestCallback) => {
      callbacks.push(callback);
      return callbacks.length;
    });
    vi.stubGlobal('requestAnimationFrame', requestAnimationFrame);

    const collector = new J2MEPerformanceCollector();
    collector.recordPresent();
    collector.recordPresent();
    collector.recordPresent();

    expect(requestAnimationFrame).toHaveBeenCalledTimes(1);
    expect(collector.snapshot().experience.presentedFrames).toBe(0);

    callbacks[0](100);
    expect(collector.snapshot().experience).toMatchObject({
      presentedFrames: 1,
      frameIntervalMs: { p50: 0, p95: 0, p99: 0, max: 0 },
    });

    collector.recordPresent();
    collector.recordPresent();
    expect(requestAnimationFrame).toHaveBeenCalledTimes(2);
    callbacks[1](116.5);

    expect(collector.snapshot().experience).toMatchObject({
      presentedFrames: 2,
      frameIntervalMs: {
        p50: 16.5,
        p95: 16.5,
        p99: 16.5,
        max: 16.5,
      },
    });
  });

  it('does not count a pre-reset pending frame in the new sample window', () => {
    const callbacks: FrameRequestCallback[] = [];
    vi.stubGlobal(
      'requestAnimationFrame',
      vi.fn((callback: FrameRequestCallback) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
    );
    const cancelAnimationFrame = vi.fn();
    vi.stubGlobal('cancelAnimationFrame', cancelAnimationFrame);

    const collector = new J2MEPerformanceCollector();
    collector.recordPresent();
    collector.reset();

    expect(cancelAnimationFrame).toHaveBeenCalledWith(1);
    callbacks[0](100);
    expect(collector.snapshot().experience.presentedFrames).toBe(0);
  });

  it('records presents immediately when RAF is unavailable', () => {
    vi.stubGlobal('requestAnimationFrame', undefined);
    const collector = new J2MEPerformanceCollector();

    collector.recordPresent(100);
    collector.recordPresent(120);

    expect(collector.snapshot().experience).toMatchObject({
      presentedFrames: 2,
      frameIntervalMs: { p50: 20, p95: 20, p99: 20, max: 20 },
    });
  });
});
