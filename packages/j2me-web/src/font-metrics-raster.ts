import { READBACK_CONTEXT_OPTIONS } from './canvas-context-options';

export interface FontMetricsRasterOptions {
  cssFont: string;
  probeText: string;
  dpr: number;
  alphaThreshold: number;
}

export interface FontMetricsRasterResult {
  height: number;
  baseline: number;
}

type AnyCanvas2DContext =
  | CanvasRenderingContext2D
  | OffscreenCanvasRenderingContext2D;

export const findVerticalInkBounds = (options: {
  data: Uint8ClampedArray;
  width: number;
  height: number;
  alphaThreshold: number;
}): { top: number; bottom: number } | null => {
  const { data, width, height, alphaThreshold } = options;
  if (width <= 0 || height <= 0) {
    return null;
  }

  const stride = width * 4;
  const hasInkInRow = (y: number, threshold: number): boolean => {
    const rowStart = y * stride;
    for (let x = 0; x < width; x++) {
      const a = data[rowStart + x * 4 + 3];
      if (a >= threshold) {
        return true;
      }
    }
    return false;
  };

  const findTopBottom = (
    threshold: number,
  ): { top: number; bottom: number } | null => {
    let top = -1;
    for (let y = 0; y < height; y++) {
      if (hasInkInRow(y, threshold)) {
        top = y;
        break;
      }
    }
    if (top === -1) {
      return null;
    }

    let bottom = -1;
    for (let y = height - 1; y >= 0; y--) {
      if (hasInkInRow(y, threshold)) {
        bottom = y;
        break;
      }
    }
    if (bottom === -1) {
      return null;
    }

    return { top, bottom };
  };

  return findTopBottom(alphaThreshold) ?? findTopBottom(1);
};

const parsePixelFontSize = (cssFont: string): number => {
  const match = cssFont.match(/(\d+(?:\.\d+)?)px/);
  if (!match) {
    return 16;
  }
  const value = Number(match[1]);
  return Number.isFinite(value) && value > 0 ? value : 16;
};

const createOffscreen2DContext = (
  deviceWidth: number,
  deviceHeight: number,
): {
  canvas: OffscreenCanvas | HTMLCanvasElement;
  ctx: AnyCanvas2DContext;
} | null => {
  if (typeof OffscreenCanvas !== 'undefined') {
    const canvas = new OffscreenCanvas(deviceWidth, deviceHeight);
    const ctx = canvas.getContext('2d', READBACK_CONTEXT_OPTIONS);
    if (!ctx) {
      return null;
    }
    return { canvas, ctx };
  }

  if (typeof document === 'undefined') {
    return null;
  }

  const canvas = document.createElement('canvas');
  canvas.width = deviceWidth;
  canvas.height = deviceHeight;
  const ctx = canvas.getContext('2d', READBACK_CONTEXT_OPTIONS);
  if (!ctx) {
    return null;
  }
  return { canvas, ctx };
};

export const rasterizeFontMetrics = (
  options: FontMetricsRasterOptions,
): FontMetricsRasterResult | null => {
  const { cssFont, probeText, dpr, alphaThreshold } = options;
  if (!cssFont || !probeText) {
    return null;
  }

  const safeDpr = Number.isFinite(dpr) && dpr > 0 ? dpr : 1;
  const fontSize = parsePixelFontSize(cssFont);
  const paddingCss = Math.ceil(fontSize * 1.5);

  const baselineYCss = paddingCss + fontSize * 2;
  const approxHeightCss = Math.ceil(fontSize * 6 + paddingCss * 2);

  const tempMeasure = createOffscreen2DContext(1, 1);
  let approxWidthCss = Math.ceil(fontSize * 20 + paddingCss * 2);
  if (tempMeasure) {
    tempMeasure.ctx.font = cssFont;
    const measured = tempMeasure.ctx.measureText(probeText);
    approxWidthCss = Math.ceil(measured.width + paddingCss * 2);
  }

  const deviceWidth = Math.max(1, Math.ceil(approxWidthCss * safeDpr));
  const deviceHeight = Math.max(1, Math.ceil(approxHeightCss * safeDpr));

  const result = createOffscreen2DContext(deviceWidth, deviceHeight);
  if (!result) {
    return null;
  }

  const { ctx } = result;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, deviceWidth, deviceHeight);

  ctx.setTransform(safeDpr, 0, 0, safeDpr, 0, 0);
  ctx.font = cssFont;
  ctx.textBaseline = 'alphabetic';
  ctx.fillStyle = '#000';

  const baselineYDevice = Math.round(baselineYCss * safeDpr);
  const baselineYAlignedCss = baselineYDevice / safeDpr;
  ctx.fillText(probeText, paddingCss, baselineYAlignedCss);

  let imageData: ImageData;
  try {
    imageData = ctx.getImageData(0, 0, deviceWidth, deviceHeight);
  } catch {
    return null;
  }

  const bounds = findVerticalInkBounds({
    data: imageData.data,
    width: imageData.width,
    height: imageData.height,
    alphaThreshold,
  });
  if (!bounds) {
    return null;
  }

  const ascentDevice = Math.max(0, baselineYDevice - bounds.top);
  const descentDevice = Math.max(0, bounds.bottom - baselineYDevice + 1);

  const ascentCss = ascentDevice / safeDpr;
  const descentCss = descentDevice / safeDpr;
  const topLeading = Math.min(2, Math.max(1, Math.round(fontSize * 0.06)));
  const baseline = Math.max(
    Math.ceil(ascentCss + topLeading),
    Math.round(fontSize),
  );
  const minDescent = Math.round(fontSize / 3);
  const descent = Math.max(Math.ceil(descentCss), minDescent);
  const height = baseline + descent;

  if (
    !Number.isFinite(height) ||
    height <= 0 ||
    !Number.isFinite(baseline) ||
    baseline <= 0
  ) {
    return null;
  }

  return { height, baseline };
};
