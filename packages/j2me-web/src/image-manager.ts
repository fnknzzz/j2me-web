import { READBACK_CONTEXT_OPTIONS } from './canvas-context-options';

export class ImageManager {
  private getDPIScale: () => number;

  constructor(getDPIScale: () => number) {
    this.getDPIScale = getDPIScale;
  }

  createCanvas(width: number, height: number): HTMLCanvasElement {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    return canvas;
  }

  async loadImage(url: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error(`Failed to load image: ${url}`));
      img.src = url;
    });
  }

  async decodeImageData(
    data: Uint8Array,
    mimeType: string,
  ): Promise<HTMLImageElement> {
    const blob = new Blob([data as BlobPart], { type: mimeType });
    const url = URL.createObjectURL(blob);

    try {
      return await this.loadImage(url);
    } finally {
      URL.revokeObjectURL(url);
    }
  }

  async imageToPng(
    source: HTMLImageElement | HTMLCanvasElement | ImageBitmap,
  ): Promise<{ width: number; height: number; data: Uint8Array } | undefined> {
    const width = source.width;
    const height = source.height;
    if (width <= 0 || height <= 0) return undefined;
    const canvas =
      source instanceof HTMLCanvasElement
        ? source
        : this.createCanvas(width, height);
    if (!(source instanceof HTMLCanvasElement)) {
      const ctx = canvas.getContext('2d', READBACK_CONTEXT_OPTIONS);
      if (!ctx) return undefined;
      ctx.drawImage(source, 0, 0);
    }
    const blob: Blob | null = await new Promise((resolve) =>
      canvas.toBlob(resolve, 'image/png'),
    );
    if (!blob) return undefined;
    return { width, height, data: new Uint8Array(await blob.arrayBuffer()) };
  }

  async imageDataToPng(
    imageData: ImageData,
  ): Promise<{ width: number; height: number; data: Uint8Array } | undefined> {
    const canvas = this.createCanvas(imageData.width, imageData.height);
    const ctx = canvas.getContext('2d', READBACK_CONTEXT_OPTIONS);
    if (!ctx) return undefined;
    ctx.putImageData(imageData, 0, 0);
    return this.imageToPng(canvas);
  }

  createImageFromRGB(
    width: number,
    height: number,
    rgbData: Int32Array,
    processAlpha: boolean,
  ): HTMLCanvasElement {
    const canvas = this.createCanvas(width, height);
    const ctx = canvas.getContext('2d', READBACK_CONTEXT_OPTIONS);
    if (!ctx) {
      throw new Error('Failed to get 2D context');
    }

    const imageData = ctx.createImageData(width, height);
    const pixels = imageData.data;

    for (let i = 0; i < rgbData.length; i++) {
      const rgb = rgbData[i];
      const pixelIndex = i * 4;

      if (processAlpha) {
        pixels[pixelIndex] = (rgb >> 16) & 0xff;
        pixels[pixelIndex + 1] = (rgb >> 8) & 0xff;
        pixels[pixelIndex + 2] = rgb & 0xff;
        pixels[pixelIndex + 3] = (rgb >> 24) & 0xff;
      } else {
        const r = (rgb >> 16) & 0xff;
        const g = (rgb >> 8) & 0xff;
        const b = rgb & 0xff;
        const isMagenta = r >= 248 && g === 0 && b >= 248;
        pixels[pixelIndex] = r;
        pixels[pixelIndex + 1] = g;
        pixels[pixelIndex + 2] = b;
        pixels[pixelIndex + 3] = isMagenta ? 0 : 255;
      }
    }

    ctx.putImageData(imageData, 0, 0);
    return canvas;
  }

  getImageRGB(
    source: HTMLImageElement | HTMLCanvasElement,
    x: number,
    y: number,
    width: number,
    height: number,
  ): Int32Array {
    const canvas = this.createCanvas(width, height);
    const ctx = canvas.getContext('2d', READBACK_CONTEXT_OPTIONS);
    if (!ctx) {
      throw new Error('Failed to get 2D context');
    }

    let dpiScale = 1;
    if (source instanceof HTMLCanvasElement && source.style.width) {
      const styleWidth = parseInt(source.style.width, 10);
      if (styleWidth > 0 && source.width > styleWidth) {
        dpiScale = source.width / styleWidth;
      }
    } else if (source instanceof HTMLCanvasElement) {
      dpiScale = this.getDPIScale();
    }

    const sx = x * dpiScale;
    const sy = y * dpiScale;
    const sw = width * dpiScale;
    const sh = height * dpiScale;

    ctx.drawImage(source, sx, sy, sw, sh, 0, 0, width, height);
    const imageData = ctx.getImageData(0, 0, width, height);
    const pixels = imageData.data;
    const rgbData = new Int32Array(width * height);

    for (let i = 0; i < rgbData.length; i++) {
      const pixelIndex = i * 4;
      const r = pixels[pixelIndex];
      const g = pixels[pixelIndex + 1];
      const b = pixels[pixelIndex + 2];
      const a = pixels[pixelIndex + 3];
      rgbData[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    return rgbData;
  }
}
