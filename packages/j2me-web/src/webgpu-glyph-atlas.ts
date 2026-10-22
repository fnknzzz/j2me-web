import type { J2MEPerformanceCollector } from '@jsjvm/j2me-simulator';
import { READBACK_CONTEXT_OPTIONS } from './canvas-context-options';
import type { FontManager } from './font-manager';

const ATLAS_SIZE = 1024;
// Linear sampling may read beyond a glyph's UV rectangle when the quad lands
// between physical pixels. Upload an explicit transparent gutter so that the
// filter can never reach a neighbouring glyph.
const ATLAS_PADDING = 2;
// Transparent pixels that are part of the sampled glyph bitmap itself. The
// atlas gutter above separates allocations; this inner border ensures the
// glyph's own edge texels are transparent as well.
const GLYPH_BITMAP_PADDING = 2;
// Keep glyph masks above the surface resolution without making the final GPU
// minification do too much antialiasing work. The raster stage still uses 4x
// for regular text and 2x for bold text; retaining 1.5x lets Canvas perform
// most of the high-quality downscale while preserving subpixel detail for GPU
// positioning. This costs roughly 2.25x atlas area versus a 1x bitmap.
const GLYPH_ATLAS_SCALE = 1.5;
const MAX_ATLAS_PAGES = 8;
const SAFE_GLYPH =
  /^(?:[\u0020-\u007e]|\p{Script=Latin}|\p{Number}|\p{Script=Han}|\p{Script=Hiragana}|\p{Script=Katakana}|\p{Script=Hangul}|[\u3000-\u303f\uff01-\uff65])$/u;
const GraphemeSegmenter = (
  Intl as unknown as { Segmenter?: new (...args: any[]) => any }
).Segmenter;
const graphemeSegmenter = GraphemeSegmenter
  ? new GraphemeSegmenter(undefined, { granularity: 'grapheme' })
  : null;

export interface GlyphFontState {
  fontFace: number;
  fontStyle: number;
  fontSize: number;
}

export interface GlyphAtlasPage {
  texture: GPUTexture;
}

interface MutableGlyphAtlasPage extends GlyphAtlasPage {
  cursorX: number;
  cursorY: number;
  rowHeight: number;
}

export interface CachedGlyphEntry {
  page: GlyphAtlasPage;
  u0: number;
  v0: number;
  u1: number;
  v1: number;
  logicalWidth: number;
  logicalHeight: number;
  offsetX: number;
  offsetY: number;
  baseline: number;
  advance: number;
}

export interface PositionedGlyph {
  entry: CachedGlyphEntry;
  x: number;
}

export interface GlyphAtlasLayout {
  glyphs: PositionedGlyph[];
  logicalWidth: number;
  fontHeight: number;
  fontBaseline: number;
}

export class WebGPUGlyphAtlas {
  private cache = new Map<string, CachedGlyphEntry>();
  private pages: MutableGlyphAtlasPage[] = [];
  private rasterCanvas: HTMLCanvasElement | null = null;
  private rasterContext: CanvasRenderingContext2D | null = null;
  private uploadCanvas: HTMLCanvasElement | null = null;
  private uploadContext: CanvasRenderingContext2D | null = null;

  constructor(
    private device: GPUDevice,
    private fontManager: FontManager,
    private performanceCollector: J2MEPerformanceCollector | null,
  ) {}

  layout(
    text: string,
    state: GlyphFontState,
    scale: number,
  ): GlyphAtlasLayout | null {
    const glyphs = this.getSafeGlyphs(text, state.fontStyle);
    if (!glyphs) return null;

    const positioned: PositionedGlyph[] = [];
    let advance = 0;
    for (const glyph of glyphs) {
      const entry = this.rasterizeGlyph(glyph, state, scale);
      if (!entry) return null;
      positioned.push({ entry, x: advance });
      advance += entry.advance;
    }

    return {
      glyphs: positioned,
      logicalWidth: this.fontManager.stringWidth(
        text,
        state.fontFace,
        state.fontStyle,
        state.fontSize,
      ),
      fontHeight: this.fontManager.getHeight(
        state.fontFace,
        state.fontStyle,
        state.fontSize,
      ),
      fontBaseline: this.fontManager.getBaselinePosition(
        state.fontFace,
        state.fontStyle,
        state.fontSize,
      ),
    };
  }

  private getSafeGlyphs(text: string, fontStyle: number): string[] | null {
    // Underlining belongs to the whole string, so it remains on the fallback
    // path until the decoration is emitted as separate geometry.
    if (fontStyle & 4) return null;
    const glyphs: string[] = [];
    if (graphemeSegmenter) {
      for (const item of graphemeSegmenter.segment(text)) {
        const glyph = item.segment as string;
        if ([...glyph].length !== 1 || !SAFE_GLYPH.test(glyph)) return null;
        glyphs.push(glyph);
      }
      return glyphs;
    }
    for (const glyph of text) {
      if (!SAFE_GLYPH.test(glyph)) return null;
      glyphs.push(glyph);
    }
    return glyphs;
  }

  private rasterizeGlyph(
    glyph: string,
    state: GlyphFontState,
    scale: number,
  ): CachedGlyphEntry | null {
    const cacheKey = `${glyph}\x00${state.fontFace}\x00${state.fontStyle}\x00${state.fontSize}\x00${scale}`;
    const cached = this.cache.get(cacheKey);
    if (cached) return cached;

    const rasterStartedAt = this.performanceCollector?.now() ?? 0;
    const contexts = this.getContexts();
    if (!contexts) return null;
    const { rasterCanvas, rasterContext, uploadCanvas, uploadContext } =
      contexts;
    const logicalHeight = Math.max(
      1,
      this.fontManager.getHeight(
        state.fontFace,
        state.fontStyle,
        state.fontSize,
      ),
    );
    const logicalBaseline = this.fontManager.getBaselinePosition(
      state.fontFace,
      state.fontStyle,
      state.fontSize,
    );
    const supersampleScale = state.fontStyle & 1 ? 2 : 4;
    const rasterScale = scale * supersampleScale;
    const rasterFont = this.fontManager
      .getCSSFont(state.fontFace, state.fontStyle, state.fontSize)
      .replace(/(\d+)px/, (_, size) => `${Number(size) * rasterScale}px`);
    rasterContext.font = rasterFont;
    const metrics = rasterContext.measureText(glyph);
    const actualLeft = Number.isFinite(metrics.actualBoundingBoxLeft)
      ? metrics.actualBoundingBoxLeft
      : 0;
    const actualRight = Number.isFinite(metrics.actualBoundingBoxRight)
      ? metrics.actualBoundingBoxRight
      : metrics.width;
    const inkLeft = Math.min(0, -actualLeft);
    const inkRight = Math.max(metrics.width, actualRight);
    // Preserve two final texture pixels around the actual ink bounds. A
    // fixed two raster pixels would shrink to only half a pixel at 4x
    // supersampling and clips italic overhang during Canvas downscaling.
    const rasterGuard = supersampleScale * 2;
    const rasterPenX = rasterGuard - inkLeft;
    const rasterWidth = Math.max(
      1,
      Math.ceil(inkRight - inkLeft + rasterGuard * 2),
    );
    const rasterHeight = Math.max(
      1,
      Math.ceil((logicalHeight + 4) * rasterScale),
    );

    this.ensureCanvasCapacity(rasterCanvas, rasterWidth, rasterHeight);
    rasterContext.font = rasterFont;
    rasterContext.textAlign = 'left';
    rasterContext.textBaseline = 'alphabetic';
    // The scratch canvas is reused for differently-sized glyphs. Clear a
    // small filter guard as well, otherwise high-quality downscaling can pull
    // stale alpha from the previous, wider glyph at the source rectangle edge.
    rasterContext.clearRect(
      0,
      0,
      Math.min(rasterCanvas.width, rasterWidth + supersampleScale * 2),
      Math.min(rasterCanvas.height, rasterHeight + supersampleScale * 2),
    );
    rasterContext.fillStyle = 'white';
    rasterContext.fillText(glyph, rasterPenX, logicalBaseline * rasterScale);

    const atlasDownsample = supersampleScale / GLYPH_ATLAS_SCALE;
    const physicalWidth = Math.max(1, Math.ceil(rasterWidth / atlasDownsample));
    const physicalHeight = Math.max(
      1,
      Math.ceil(rasterHeight / atlasDownsample),
    );
    const bitmapPadding = GLYPH_BITMAP_PADDING * GLYPH_ATLAS_SCALE;
    const bitmapWidth = physicalWidth + bitmapPadding * 2;
    const bitmapHeight = physicalHeight + bitmapPadding * 2;
    const uploadWidth = bitmapWidth + ATLAS_PADDING * 2;
    const uploadHeight = bitmapHeight + ATLAS_PADDING * 2;
    this.ensureCanvasCapacity(uploadCanvas, uploadWidth, uploadHeight);
    uploadContext.clearRect(0, 0, uploadWidth, uploadHeight);
    uploadContext.imageSmoothingEnabled = true;
    uploadContext.imageSmoothingQuality = 'high';
    uploadContext.drawImage(
      rasterCanvas,
      0,
      0,
      rasterWidth,
      rasterHeight,
      ATLAS_PADDING + bitmapPadding,
      ATLAS_PADDING + bitmapPadding,
      physicalWidth,
      physicalHeight,
    );

    const placement = this.allocateRegion(bitmapWidth, bitmapHeight);
    if (!placement) return null;
    this.device.queue.copyExternalImageToTexture(
      { source: uploadCanvas, origin: { x: 0, y: 0 } },
      {
        texture: placement.page.texture,
        origin: {
          x: placement.x - ATLAS_PADDING,
          y: placement.y - ATLAS_PADDING,
        },
      },
      [uploadWidth, uploadHeight],
    );
    this.performanceCollector?.addGpuCounter(
      'writeTextureBytes',
      uploadWidth * uploadHeight * 4,
    );

    const entry: CachedGlyphEntry = {
      page: placement.page,
      // The sampled bitmap already includes transparent inner padding and an
      // explicitly-uploaded outer gutter, so bilinear filtering cannot reach
      // a neighbouring glyph even when sampling at the allocation edges.
      u0: placement.x / ATLAS_SIZE,
      v0: placement.y / ATLAS_SIZE,
      u1: (placement.x + bitmapWidth) / ATLAS_SIZE,
      v1: (placement.y + bitmapHeight) / ATLAS_SIZE,
      logicalWidth: bitmapWidth / (scale * GLYPH_ATLAS_SCALE),
      logicalHeight: bitmapHeight / (scale * GLYPH_ATLAS_SCALE),
      offsetX:
        -(rasterPenX / atlasDownsample + bitmapPadding) /
        (scale * GLYPH_ATLAS_SCALE),
      offsetY: -bitmapPadding / (scale * GLYPH_ATLAS_SCALE),
      baseline: logicalBaseline,
      advance: metrics.width / rasterScale,
    };
    this.cache.set(cacheKey, entry);
    this.performanceCollector?.addTiming('textRaster', rasterStartedAt);
    return entry;
  }

  private getContexts(): {
    rasterCanvas: HTMLCanvasElement;
    rasterContext: CanvasRenderingContext2D;
    uploadCanvas: HTMLCanvasElement;
    uploadContext: CanvasRenderingContext2D;
  } | null {
    if (!this.rasterCanvas) {
      this.rasterCanvas = document.createElement('canvas');
      this.rasterContext = this.rasterCanvas.getContext(
        '2d',
        READBACK_CONTEXT_OPTIONS,
      );
      this.uploadCanvas = document.createElement('canvas');
      this.uploadContext = this.uploadCanvas.getContext('2d');
    }
    if (
      !this.rasterCanvas ||
      !this.rasterContext ||
      !this.uploadCanvas ||
      !this.uploadContext
    ) {
      return null;
    }
    return {
      rasterCanvas: this.rasterCanvas,
      rasterContext: this.rasterContext,
      uploadCanvas: this.uploadCanvas,
      uploadContext: this.uploadContext,
    };
  }

  private allocateRegion(
    width: number,
    height: number,
  ): { page: MutableGlyphAtlasPage; x: number; y: number } | null {
    const slotWidth = width + ATLAS_PADDING * 2;
    const slotHeight = height + ATLAS_PADDING * 2;
    if (slotWidth > ATLAS_SIZE || slotHeight > ATLAS_SIZE) return null;

    for (;;) {
      let page: MutableGlyphAtlasPage | null =
        this.pages[this.pages.length - 1] ?? null;
      if (!page) {
        page = this.createPage();
        if (!page) return null;
      }
      if (page.cursorX + slotWidth > ATLAS_SIZE) {
        page.cursorX = 0;
        page.cursorY += page.rowHeight;
        page.rowHeight = 0;
      }
      if (page.cursorY + slotHeight > ATLAS_SIZE) {
        page = this.createPage();
        if (!page) return null;
      }
      const x = page.cursorX + ATLAS_PADDING;
      const y = page.cursorY + ATLAS_PADDING;
      page.cursorX += slotWidth;
      page.rowHeight = Math.max(page.rowHeight, slotHeight);
      return { page, x, y };
    }
  }

  private createPage(): MutableGlyphAtlasPage | null {
    if (this.pages.length >= MAX_ATLAS_PAGES) return null;
    const texture = this.device.createTexture({
      size: [ATLAS_SIZE, ATLAS_SIZE],
      format: 'rgba8unorm',
      usage:
        GPUTextureUsage.TEXTURE_BINDING |
        GPUTextureUsage.COPY_DST |
        GPUTextureUsage.RENDER_ATTACHMENT,
    });
    const bytes = ATLAS_SIZE * ATLAS_SIZE * 4;
    this.performanceCollector?.addGpuCounter('texturesCreated');
    this.performanceCollector?.addGpuCounter('textureBytesAllocated', bytes);
    this.performanceCollector?.addEstimatedGpuBytes(bytes);
    const page = { texture, cursorX: 0, cursorY: 0, rowHeight: 0 };
    this.pages.push(page);
    return page;
  }

  private ensureCanvasCapacity(
    canvas: HTMLCanvasElement,
    requiredWidth: number,
    requiredHeight: number,
  ): void {
    if (canvas.width >= requiredWidth && canvas.height >= requiredHeight)
      return;
    canvas.width = Math.max(canvas.width, Math.ceil(requiredWidth / 64) * 64);
    canvas.height = Math.max(
      canvas.height,
      Math.ceil(requiredHeight / 64) * 64,
    );
  }
}
