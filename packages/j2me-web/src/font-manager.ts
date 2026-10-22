import { rasterizeFontMetrics } from './font-metrics-raster';
import {
  createEmptyFontMetricsState,
  isCompatibleFontMetricsState,
  loadFontMetricsState,
  type StoredFontMetricsEntry,
  type StoredFontMetricsState,
  saveFontMetricsState,
} from './font-metrics-store';

const FACE = {
  SYSTEM: 0,
  MONOSPACE: 32,
  PROPORTIONAL: 64,
} as const;

const STYLE = {
  PLAIN: 0,
  BOLD: 1,
  ITALIC: 2,
  UNDERLINED: 4,
} as const;

const SIZE = {
  MEDIUM: 0,
  SMALL: 8,
  LARGE: 16,
} as const;

interface FontMetricsCache {
  height: number;
  baseline: number;
}

export interface FontFamilies {
  system: string;
  monospace: string;
  proportional: string;
}

export interface FontSizes {
  small: number;
  medium: number;
  large: number;
}

export interface FontConfig {
  families?: Partial<FontFamilies>;
  sizes?: Partial<FontSizes>;
  fontSize?: number;
}

const DEFAULT_FONT_FAMILIES: FontFamilies = {
  system: 'system-ui, -apple-system, sans-serif',
  monospace: '"Courier New", Courier, monospace',
  proportional: 'Arial, Helvetica, sans-serif',
};

const DEFAULT_FONT_SIZES: FontSizes = {
  small: 9,
  medium: 12,
  large: 14,
};

const FONT_METRICS_STORAGE_KEY = 'j2me.fontMetrics.v1';
const FONT_METRICS_STORAGE_VERSION = 5;
const FONT_METRICS_PROBE_TEXT = 'Hg|jpqyÉÀ汉字，。！？';
const FONT_METRICS_ALPHA_THRESHOLD = 16;

export class FontManager {
  private ctx: CanvasRenderingContext2D;
  private widthCache = new Map<string, number>();
  private metricsCache = new Map<string, FontMetricsCache>();
  private families: FontFamilies;
  private sizes: FontSizes;
  private storedMetricsState: StoredFontMetricsState | null | undefined =
    undefined;
  private prewarmStarted = false;

  constructor(ctx: CanvasRenderingContext2D, config?: FontConfig) {
    this.ctx = ctx;
    this.families = {
      ...DEFAULT_FONT_FAMILIES,
      ...config?.families,
    };
    const computedSizes = this.computeDefaultSizes(config);
    this.sizes = {
      ...computedSizes,
      ...config?.sizes,
    };
  }

  private computeDefaultSizes(config?: FontConfig): FontSizes {
    const explicitFontSize =
      typeof config?.fontSize === 'number' && Number.isFinite(config.fontSize)
        ? config.fontSize
        : null;

    let logicalCanvasHeight = 0;
    try {
      const transform = this.ctx.getTransform?.();
      const scaleY =
        transform && Number.isFinite(transform.d) && transform.d > 0
          ? transform.d
          : 1;
      const backingHeight = this.ctx.canvas?.height ?? 0;
      logicalCanvasHeight = Math.round(backingHeight / scaleY);
    } catch {
      logicalCanvasHeight = 0;
    }

    const autoBase =
      logicalCanvasHeight > 0
        ? Math.max(19, (logicalCanvasHeight / 35) | 0)
        : 0;

    const baseSizeRaw = explicitFontSize ?? autoBase;
    const baseSize =
      Number.isFinite(baseSizeRaw) && baseSizeRaw > 0
        ? Math.round(baseSizeRaw)
        : DEFAULT_FONT_SIZES.medium;

    return {
      small: Math.max(1, Math.round(baseSize / 1.25)),
      medium: Math.max(1, baseSize),
      large: Math.max(1, Math.round(baseSize * 1.25)),
    };
  }

  async prewarmMetricsOnce(): Promise<void> {
    if (this.prewarmStarted) {
      return;
    }
    this.prewarmStarted = true;

    const fontSet =
      typeof document !== 'undefined' ? (document as any).fonts : null;
    if (fontSet?.ready) {
      try {
        await fontSet.ready;
      } catch {
        return;
      }
    }

    const state = this.getOrCreateStoredMetricsState(true);
    if (!state) {
      return;
    }

    const faces = [FACE.SYSTEM, FACE.MONOSPACE, FACE.PROPORTIONAL];
    const sizes = [SIZE.SMALL, SIZE.MEDIUM, SIZE.LARGE];
    const styles = [
      STYLE.PLAIN,
      STYLE.BOLD,
      STYLE.ITALIC,
      STYLE.BOLD | STYLE.ITALIC,
    ];

    for (const face of faces) {
      for (const size of sizes) {
        for (const style of styles) {
          const cssFont = this.getCSSFont(face, style, size);
          if (state.entries[cssFont]) {
            const entry = state.entries[cssFont];
            const cacheKey = this.getFontKey(face, style, size);
            this.metricsCache.set(cacheKey, entry);
            continue;
          }

          const scanned = rasterizeFontMetrics({
            cssFont,
            probeText: FONT_METRICS_PROBE_TEXT,
            dpr: this.getDevicePixelRatio(),
            alphaThreshold: FONT_METRICS_ALPHA_THRESHOLD,
          });

          const result =
            scanned ?? this.computeMetricsFromMeasureText(face, style, size);

          state.entries[cssFont] = result;
          const cacheKey = this.getFontKey(face, style, size);
          this.metricsCache.set(cacheKey, result);
        }
      }
    }

    saveFontMetricsState(FONT_METRICS_STORAGE_KEY, state);
  }

  private getFontKey(face: number, style: number, size: number): string {
    const normalizedStyle = style & (STYLE.BOLD | STYLE.ITALIC);
    return `${face}-${normalizedStyle}-${size}`;
  }

  private getTextKey(
    text: string,
    face: number,
    style: number,
    size: number,
  ): string {
    return `${this.getFontKey(face, style, size)}:${text}`;
  }

  clearCache(): void {
    this.widthCache.clear();
    this.metricsCache.clear();
  }

  getCSSFont(face: number, style: number, size: number): string {
    const parts: string[] = [];

    if (style & STYLE.ITALIC) {
      parts.push('italic');
    }

    if (style & STYLE.BOLD) {
      parts.push('bold');
    }

    const pixelSize = this.getFontPixelSize(size);
    parts.push(`${pixelSize}px`);

    const family = this.getFontFamily(face);
    parts.push(family);

    return parts.join(' ');
  }

  private getDevicePixelRatio(): number {
    const dpr =
      typeof window !== 'undefined' &&
      typeof window.devicePixelRatio === 'number'
        ? window.devicePixelRatio
        : 1;
    return Number.isFinite(dpr) && dpr > 0 ? dpr : 1;
  }

  private shouldPersistMetrics(cssFont: string): boolean {
    const fontSet =
      typeof document !== 'undefined' ? (document as any).fonts : null;
    if (!fontSet) {
      return true;
    }
    if (fontSet.status && fontSet.status !== 'loaded') {
      return false;
    }
    try {
      return fontSet.check(cssFont, FONT_METRICS_PROBE_TEXT);
    } catch {
      return false;
    }
  }

  private getOrCreateStoredMetricsState(
    createIfMissing: boolean,
  ): StoredFontMetricsState | null {
    const options = {
      version: FONT_METRICS_STORAGE_VERSION,
      dpr: this.getDevicePixelRatio(),
      probeText: FONT_METRICS_PROBE_TEXT,
      alphaThreshold: FONT_METRICS_ALPHA_THRESHOLD,
    };

    if (this.storedMetricsState === undefined) {
      const loaded = loadFontMetricsState(FONT_METRICS_STORAGE_KEY);
      this.storedMetricsState =
        loaded && isCompatibleFontMetricsState(loaded, options) ? loaded : null;
    }

    if (
      this.storedMetricsState &&
      !isCompatibleFontMetricsState(this.storedMetricsState, options)
    ) {
      this.storedMetricsState = null;
    }

    if (this.storedMetricsState) {
      return this.storedMetricsState;
    }

    if (!createIfMissing) {
      return null;
    }

    const fresh = createEmptyFontMetricsState(options);
    this.storedMetricsState = fresh;
    return fresh;
  }

  private getFontPixelSize(size: number): number {
    switch (size) {
      case SIZE.SMALL:
        return this.sizes.small;
      case SIZE.LARGE:
        return this.sizes.large;
      default:
        return this.sizes.medium;
    }
  }

  private getFontFamily(face: number): string {
    switch (face) {
      case FACE.MONOSPACE:
        return this.families.monospace;
      case FACE.PROPORTIONAL:
        return this.families.proportional;
      default:
        return this.families.system;
    }
  }

  stringWidth(text: string, face: number, style: number, size: number): number {
    const key = this.getTextKey(text, face, style, size);

    const cached = this.widthCache.get(key);
    if (cached !== undefined) {
      return cached;
    }

    this.ctx.font = this.getCSSFont(face, style, size);
    const metrics = this.ctx.measureText(text);
    const width = Math.ceil(metrics.width);

    this.widthCache.set(key, width);
    return width;
  }

  charWidth(
    charCode: number,
    face: number,
    style: number,
    size: number,
  ): number {
    const char = String.fromCharCode(charCode);
    return this.stringWidth(char, face, style, size);
  }

  charsWidth(
    charCodes: number[],
    face: number,
    style: number,
    size: number,
  ): number {
    const text = String.fromCharCode(...charCodes);
    return this.stringWidth(text, face, style, size);
  }

  getHeight(face: number, style: number, size: number): number {
    const key = this.getFontKey(face, style, size);
    const cached = this.metricsCache.get(key);

    if (cached !== undefined) {
      return cached.height;
    }

    return this.computeAndCacheMetrics(face, style, size).height;
  }

  getBaselinePosition(face: number, style: number, size: number): number {
    const key = this.getFontKey(face, style, size);
    const cached = this.metricsCache.get(key);

    if (cached !== undefined) {
      return cached.baseline;
    }

    return this.computeAndCacheMetrics(face, style, size).baseline;
  }

  private computeMetricsFromMeasureText(
    face: number,
    style: number,
    size: number,
  ): FontMetricsCache {
    this.ctx.font = this.getCSSFont(face, style, size);
    const metrics = this.ctx.measureText('Ég') as any;

    let height = 0;
    let baseline = 0;

    if (
      'fontBoundingBoxAscent' in metrics &&
      'fontBoundingBoxDescent' in metrics
    ) {
      height = metrics.fontBoundingBoxAscent + metrics.fontBoundingBoxDescent;
      baseline = metrics.fontBoundingBoxAscent;
    } else if (
      metrics.actualBoundingBoxAscent !== undefined &&
      metrics.actualBoundingBoxDescent !== undefined
    ) {
      height =
        metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
      baseline = metrics.actualBoundingBoxAscent;
    }

    return {
      height: Math.ceil(
        height > 0 ? height : this.getFontPixelSize(size) * 1.2,
      ),
      baseline: Math.ceil(
        baseline > 0 ? baseline : this.getFontPixelSize(size) * 0.8,
      ),
    };
  }

  private computeAndCacheMetrics(
    face: number,
    style: number,
    size: number,
  ): FontMetricsCache {
    const cssFont = this.getCSSFont(face, style, size);
    const storedState = this.getOrCreateStoredMetricsState(false);
    const stored = storedState?.entries[cssFont];
    if (stored) {
      const cacheKey = this.getFontKey(face, style, size);
      this.metricsCache.set(cacheKey, stored);
      return stored;
    }

    const scanned = rasterizeFontMetrics({
      cssFont,
      probeText: FONT_METRICS_PROBE_TEXT,
      dpr: this.getDevicePixelRatio(),
      alphaThreshold: FONT_METRICS_ALPHA_THRESHOLD,
    });

    const result: FontMetricsCache =
      scanned ?? this.computeMetricsFromMeasureText(face, style, size);

    if (this.shouldPersistMetrics(cssFont)) {
      const state = this.getOrCreateStoredMetricsState(true);
      if (state) {
        state.entries[cssFont] = result as StoredFontMetricsEntry;
        saveFontMetricsState(FONT_METRICS_STORAGE_KEY, state);
      }
    }

    const key = this.getFontKey(face, style, size);
    this.metricsCache.set(key, result);

    return result;
  }
}
