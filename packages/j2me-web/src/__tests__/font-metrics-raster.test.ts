import { describe, expect, it } from 'vitest';
import { findVerticalInkBounds } from '../font-metrics-raster';

describe('font-metrics-raster', () => {
  it('finds top and bottom rows using alpha threshold', () => {
    const width = 4;
    const height = 6;
    const data = new Uint8ClampedArray(width * height * 4);

    const setAlpha = (x: number, y: number, a: number) => {
      data[(y * width + x) * 4 + 3] = a;
    };

    setAlpha(1, 1, 8);
    setAlpha(2, 1, 20);
    setAlpha(1, 4, 16);

    const bounds = findVerticalInkBounds({
      data,
      width,
      height,
      alphaThreshold: 16,
    });

    expect(bounds).toEqual({ top: 1, bottom: 4 });
  });

  it('falls back to alpha > 0 when threshold finds nothing', () => {
    const width = 3;
    const height = 3;
    const data = new Uint8ClampedArray(width * height * 4);
    data[(2 * width + 1) * 4 + 3] = 1;

    const bounds = findVerticalInkBounds({
      data,
      width,
      height,
      alphaThreshold: 200,
    });

    expect(bounds).toEqual({ top: 2, bottom: 2 });
  });
});
