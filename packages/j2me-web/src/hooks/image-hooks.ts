import type { ImageManager } from '../image-manager';
import type { WebGPUGraphicsRenderer } from '../webgpu-graphics-renderer';

export const createImageHooks = (
  imageManager: ImageManager,
  webgpuRenderer: WebGPUGraphicsRenderer | null,
) => ({
  image: {
    platformCreateCanvas: (width: number, height: number) => {
      return imageManager.createCanvas(width, height);
    },

    platformLoadImage: async (url: string) => {
      return imageManager.loadImage(url);
    },

    platformDecodeImageData: async (data: Uint8Array, mimeType: string) => {
      return imageManager.decodeImageData(data, mimeType);
    },

    platformImageToPng: async (
      source: HTMLImageElement | HTMLCanvasElement | ImageBitmap,
    ) => {
      if (source instanceof HTMLCanvasElement && webgpuRenderer) {
        const imageData = await webgpuRenderer.surfaceSnapshotForHandle(source);
        if (imageData) {
          for (let offset = 0; offset < imageData.data.length; offset += 4) {
            const a = imageData.data[offset + 3];
            if (a > 0 && a < 255) {
              imageData.data[offset] = Math.min(
                255,
                Math.round((imageData.data[offset] * 255) / a),
              );
              imageData.data[offset + 1] = Math.min(
                255,
                Math.round((imageData.data[offset + 1] * 255) / a),
              );
              imageData.data[offset + 2] = Math.min(
                255,
                Math.round((imageData.data[offset + 2] * 255) / a),
              );
            }
          }
          return imageManager.imageDataToPng(imageData);
        }
      }
      return imageManager.imageToPng(source);
    },

    platformCreateImageFromRGB: (
      width: number,
      height: number,
      rgbData: Int32Array,
      processAlpha: boolean,
    ) => {
      return imageManager.createImageFromRGB(
        width,
        height,
        rgbData,
        processAlpha,
      );
    },

    platformGetImageRGB: (
      source: HTMLImageElement | HTMLCanvasElement,
      x: number,
      y: number,
      width: number,
      height: number,
    ) => {
      return imageManager.getImageRGB(source, x, y, width, height);
    },

    createImageFromResource: (resourceName: string) => {
      console.log(`[createImageFromResource] ${resourceName} - not found`);
      return undefined;
    },
  },
});
