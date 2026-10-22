import { beforeEach, describe, expect, it } from 'vitest';
import {
  createEmptyFontMetricsState,
  isCompatibleFontMetricsState,
  loadFontMetricsState,
  saveFontMetricsState,
} from '../font-metrics-store';

class MemoryStorage {
  private store = new Map<string, string>();

  get length(): number {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.get(key) ?? null;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
}

describe('font-metrics-store', () => {
  const storageKey = 'test.fontMetrics';

  beforeEach(() => {
    (globalThis as any).localStorage = new MemoryStorage();
  });

  it('saves and loads state', () => {
    const state = createEmptyFontMetricsState({
      version: 1,
      dpr: 2,
      probeText: 'abc',
      alphaThreshold: 16,
    });
    state.entries['12px Arial'] = { height: 12, baseline: 9 };

    saveFontMetricsState(storageKey, state);
    const loaded = loadFontMetricsState(storageKey);

    expect(loaded).not.toBeNull();
    expect(loaded?.entries['12px Arial']).toEqual({ height: 12, baseline: 9 });
  });

  it('returns null for invalid JSON', () => {
    localStorage.setItem(storageKey, '{not json');
    expect(loadFontMetricsState(storageKey)).toBeNull();
  });

  it('checks compatibility', () => {
    const state = createEmptyFontMetricsState({
      version: 1,
      dpr: 2,
      probeText: 'abc',
      alphaThreshold: 16,
    });

    expect(
      isCompatibleFontMetricsState(state, {
        version: 1,
        dpr: 2,
        probeText: 'abc',
        alphaThreshold: 16,
      }),
    ).toBe(true);

    expect(
      isCompatibleFontMetricsState(state, {
        version: 2,
        dpr: 2,
        probeText: 'abc',
        alphaThreshold: 16,
      }),
    ).toBe(false);
  });
});
