import m3gWebgpuWireSchema from '@jsjvm/j2me-core/m3g-wire';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { J2MEWeb } from '../adapter';
import { M3gRenderer } from '../m3g-renderer';
import { MascotRenderer } from '../mascot-renderer';
import { WebGPUGraphicsRenderer } from '../webgpu-graphics-renderer';

class FakeElement {
  style: Record<string, string> = {};
  dataset: Record<string, string> = {};
  children: FakeElement[] = [];
  parentElement: FakeElement | null = null;
  innerHTML = '';

  appendChild<T extends FakeElement>(child: T): T {
    child.parentElement = this;
    this.children.push(child);
    return child;
  }

  contains(child: FakeElement): boolean {
    return this.children.includes(child);
  }

  replaceWith(node: FakeElement): void {
    if (!this.parentElement) return;
    const index = this.parentElement.children.indexOf(this);
    if (index >= 0) {
      this.parentElement.children[index] = node;
      node.parentElement = this.parentElement;
      this.parentElement = null;
    }
  }
}

class FakeCanvasRenderingContext2D {
  canvas: FakeCanvasElement;
  imageSmoothingEnabled = false;
  imageSmoothingQuality: ImageSmoothingQuality = 'low';

  constructor(canvas: FakeCanvasElement) {
    this.canvas = canvas;
  }

  scale(): void {}
  save(): void {}
  restore(): void {}
  setTransform(): void {}
  clearRect(): void {}
  fillRect(): void {}
  fillText(): void {}
  drawImage(): void {}
  getImageData(
    _x: number,
    _y: number,
    width: number,
    height: number,
  ): ImageData {
    return {
      data: new Uint8ClampedArray(width * height * 4),
      width,
      height,
    } as ImageData;
  }
  measureText(text: string): TextMetrics {
    return { width: text.length * 8 } as TextMetrics;
  }
  getTransform(): DOMMatrixReadOnly {
    return { a: 1, d: 1 } as DOMMatrixReadOnly;
  }
}

class FakeCanvasElement extends FakeElement {
  width = 240;
  height = 320;
  contexts: string[] = [];
  private context2d = new FakeCanvasRenderingContext2D(this);

  getContext(type: string): unknown {
    this.contexts.push(type);
    if (type === '2d') return this.context2d;
    if (type === 'webgpu') return fakeWebGPUContext;
    return null;
  }

  addEventListener(): void {}
}

const fakeTexture = {
  createView: () => ({}),
  destroy: () => {},
};

const fakePass = {
  setPipeline: () => {},
  setBindGroup: () => {},
  setScissorRect: () => {},
  setVertexBuffer: () => {},
  draw: () => {},
  end: () => {},
};

const fakeWebGPUContext = {
  configure: () => {},
  getCurrentTexture: () => fakeTexture,
};

const fakeDevice = {
  queue: {
    copyExternalImageToTexture: () => {},
    onSubmittedWorkDone: () => Promise.resolve(),
    writeBuffer: () => {},
    writeTexture: () => {},
    submit: () => {},
  },
  createBuffer: () => ({
    destroy: () => {},
  }),
  createBindGroup: () => ({}),
  createBindGroupLayout: () => ({}),
  createCommandEncoder: () => ({
    beginRenderPass: () => fakePass,
    copyTextureToTexture: () => {},
    finish: () => ({}),
  }),
  createPipelineLayout: () => ({}),
  createRenderPipeline: () => ({}),
  createSampler: () => ({}),
  createShaderModule: () => ({}),
  createTexture: () => fakeTexture,
};

describe('J2MEWeb WebGPU renderer', () => {
  beforeEach(() => {
    vi.restoreAllMocks();

    const body = new FakeElement();
    const documentStub = {
      body,
      createElement: (tag: string) => {
        if (tag === 'canvas') return new FakeCanvasElement();
        return new FakeElement();
      },
    };
    const navigatorStub = {
      gpu: {
        requestAdapter: () =>
          Promise.resolve({
            requestDevice: () => Promise.resolve(fakeDevice),
          }),
        getPreferredCanvasFormat: () => 'bgra8unorm',
      },
    };

    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      value: documentStub,
    });
    Object.defineProperty(globalThis, 'HTMLCanvasElement', {
      configurable: true,
      value: FakeCanvasElement,
    });
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: {
        devicePixelRatio: 2,
        addEventListener: () => {},
        removeEventListener: () => {},
      },
    });
    Object.defineProperty(globalThis, 'navigator', {
      configurable: true,
      value: navigatorStub,
    });
    Object.defineProperty(globalThis, 'GPUTextureUsage', {
      configurable: true,
      value: {
        COPY_SRC: 8,
        COPY_DST: 1,
        RENDER_ATTACHMENT: 2,
        TEXTURE_BINDING: 4,
      },
    });
    Object.defineProperty(globalThis, 'GPUBufferUsage', {
      configurable: true,
      value: {
        COPY_DST: 1,
        VERTEX: 2,
      },
    });
    Object.defineProperty(globalThis, 'GPUShaderStage', {
      configurable: true,
      value: {
        FRAGMENT: 1,
      },
    });
    Object.defineProperty(globalThis, 'AudioContext', {
      configurable: true,
      value: class {
        destination = {};
        createGain() {
          return {
            gain: { value: 1 },
            connect: () => {},
          };
        }
        createMediaElementSource() {
          return {
            connect: () => {},
          };
        }
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses a WebGPU context for the main canvas', async () => {
    const canvas = document.createElement('canvas') as HTMLCanvasElement;
    const web = new J2MEWeb({
      canvas,
      jar: [],
    });

    await (web as unknown as { initPromise: Promise<void> }).initPromise;

    expect((canvas as unknown as FakeCanvasElement).contexts).not.toContain(
      '2d',
    );
    expect((canvas as unknown as FakeCanvasElement).contexts).toContain(
      'webgpu',
    );
  });

  it('skips WebGPU draws with an empty MIDP clip', () => {
    const draw = vi.spyOn(fakePass, 'draw');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    draw.mockClear();

    renderer.drawRGBA(new Uint8Array([0, 255, 0, 255]), 0, 0, 1, 1, {
      surfaceId: 0,
      color: 0,
      clipX: 134,
      clipY: 57,
      clipWidth: 0,
      clipHeight: 32,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    });

    expect(draw).not.toHaveBeenCalled();
  });

  it('only updates the WebGPU scissor when the clip changes', () => {
    const setScissorRect = vi.spyOn(fakePass, 'setScissorRect');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    setScissorRect.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.fillRect(0, 0, 10, 10, state);
    renderer.fillRect(10, 10, 10, 10, state);
    renderer.fillRect(20, 20, 10, 10, {
      ...state,
      clipX: 5,
      clipWidth: 100,
    });
    renderer.flush(0);

    expect(setScissorRect).toHaveBeenCalledTimes(2);
    expect(setScissorRect).toHaveBeenNthCalledWith(1, 0, 0, 120, 160);
    expect(setScissorRect).toHaveBeenNthCalledWith(2, 5, 0, 100, 160);
  });

  it('reuses paged vertex buffers across draws and batches', () => {
    const createBuffer = vi.spyOn(fakeDevice, 'createBuffer');
    const setVertexBuffer = vi.spyOn(fakePass, 'setVertexBuffer');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    createBuffer.mockClear();
    setVertexBuffer.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.fillRect(0, 0, 10, 10, state);
    renderer.fillRect(10, 10, 10, 10, state);
    renderer.flush(0);

    expect(createBuffer).toHaveBeenCalledTimes(1);
    expect(setVertexBuffer.mock.calls[0]?.[2]).toBe(0);
    expect(setVertexBuffer).toHaveBeenCalledTimes(1);

    renderer.fillRect(20, 20, 10, 10, state);
    renderer.flush(0);

    expect(createBuffer).toHaveBeenCalledTimes(1);
    expect(setVertexBuffer.mock.calls[1]?.[2]).toBe(0);
  });

  it('combines supported heterogeneous shapes into one draw', () => {
    const draw = vi.spyOn(fakePass, 'draw');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    draw.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.fillRect(0, 0, 10, 10, state);
    renderer.drawShapeBatch(2, 14, new Float32Array(6 * 14), state);
    renderer.drawShapeBatch(5, 18, new Float32Array(6 * 18), state);
    renderer.drawShapeBatch(8, 20, new Float32Array(6 * 20), state);
    renderer.flush(0);

    expect(draw).toHaveBeenCalledTimes(1);
    expect(draw).toHaveBeenCalledWith(24);
  });

  it('reuses texture bind groups for persistent image surfaces', () => {
    const createBindGroup = vi.fn(() => ({}));
    const createTexture = vi.fn(() => ({
      createView: () => ({}),
      destroy: () => {},
    }));
    const renderer = new WebGPUGraphicsRenderer(
      {
        ...fakeDevice,
        createBindGroup,
        createTexture,
      } as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    const source = renderer.createSurface(1, 16, 16);
    createBindGroup.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawImageBatch(source, new Int32Array([10, 10, 20]), state);
    renderer.drawImageBatch(source, new Int32Array([30, 30, 20]), state);
    renderer.flush(0);
    renderer.drawImageBatch(source, new Int32Array([50, 50, 20]), state);
    renderer.flush(0);

    expect(createBindGroup).toHaveBeenCalledTimes(1);
  });

  it('combines consecutive quads that use the same persistent texture', () => {
    const draw = vi.spyOn(fakePass, 'draw');
    const createTexture = vi.fn(() => ({
      createView: () => ({}),
      destroy: () => {},
    }));
    const renderer = new WebGPUGraphicsRenderer(
      { ...fakeDevice, createTexture } as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    const source = renderer.createSurface(1, 16, 16);
    draw.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawImageBatch(source, new Int32Array([10, 10, 20]), state);
    renderer.drawRegion(source, 0, 0, 8, 8, 0, 30, 30, 20, state);
    renderer.drawImageBatch(source, new Int32Array([50, 50, 20]), state);
    renderer.flush(0);

    expect(draw).toHaveBeenCalledTimes(1);
    expect(draw).toHaveBeenCalledWith(18);
  });

  it('keeps texture and shape batches in command order', () => {
    const setPipeline = vi.spyOn(fakePass, 'setPipeline');
    const draw = vi.spyOn(fakePass, 'draw');
    const createTexture = vi.fn(() => ({
      createView: () => ({}),
      destroy: () => {},
    }));
    const renderer = new WebGPUGraphicsRenderer(
      { ...fakeDevice, createTexture } as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    const source = renderer.createSurface(1, 16, 16);
    setPipeline.mockClear();
    draw.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawImageBatch(source, new Int32Array([0, 0, 20]), state);
    renderer.fillRect(20, 20, 10, 10, state);
    renderer.drawImageBatch(source, new Int32Array([40, 40, 20]), state);
    renderer.flush(0);

    expect(draw.mock.calls.map(([count]) => count)).toEqual([6, 6, 6]);
    expect(setPipeline).toHaveBeenCalledTimes(3);
  });

  it('reuses one dynamic drawRGBA atlas across submitted batches', () => {
    const createTexture = vi.fn(() => ({
      createView: () => ({}),
      destroy: () => {},
    }));
    const renderer = new WebGPUGraphicsRenderer(
      {
        ...fakeDevice,
        createTexture,
      } as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    createTexture.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };
    const pixels = new Uint8Array([0, 255, 0, 255]);

    renderer.drawRGBA(pixels, 0, 0, 1, 1, state);
    renderer.drawRGBA(pixels, 1, 1, 1, 1, state);
    renderer.flush(0);
    renderer.drawRGBA(pixels, 2, 2, 1, 1, state);
    renderer.flush(0);

    expect(createTexture).toHaveBeenCalledTimes(1);
  });

  it('batches a decoded image stream into one texture draw', () => {
    const draw = vi.spyOn(fakePass, 'draw');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    const source = renderer.createSurface(1, 16, 16);
    draw.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawImageBatch(
      source,
      new Int32Array([0, 0, 20, 20, 20, 20, 40, 40, 20]),
      state,
    );
    renderer.flush(0);

    expect(draw).toHaveBeenCalledTimes(1);
    expect(draw).toHaveBeenCalledWith(18);
  });

  it('snapshots self-referencing image streams instead of batching them', () => {
    const copyTextureToTexture = vi.fn();
    const submit = vi.fn();
    const createTexture = vi.fn(() => ({
      createView: () => ({}),
      destroy: () => {},
    }));
    const renderer = new WebGPUGraphicsRenderer(
      {
        ...fakeDevice,
        queue: { ...fakeDevice.queue, submit },
        createTexture,
        createCommandEncoder: () => ({
          beginRenderPass: () => fakePass,
          copyTextureToTexture,
          finish: () => ({}),
        }),
      } as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    const source = renderer.createSurface(0, 120, 160);
    copyTextureToTexture.mockClear();
    submit.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawImageBatch(
      source,
      new Int32Array([0, 0, 20, 20, 20, 20]),
      state,
    );
    renderer.flush(0);

    expect(copyTextureToTexture).toHaveBeenCalledTimes(2);
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('keeps copyArea copy and draw in one submission and reuses scratch texture', () => {
    const submit = vi.spyOn(fakeDevice.queue, 'submit');
    const createTexture = vi.fn(() => ({
      createView: () => ({}),
      destroy: () => {},
    }));
    const renderer = new WebGPUGraphicsRenderer(
      { ...fakeDevice, createTexture } as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    createTexture.mockClear();
    submit.mockClear();
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.copyArea(0, 0, 8, 8, 10, 10, 0, state);
    renderer.flush(0);
    renderer.copyArea(0, 0, 8, 8, 20, 20, 0, state);
    renderer.flush(0);

    expect(createTexture).toHaveBeenCalledTimes(1);
    expect(submit).toHaveBeenCalledTimes(2);
  });

  it('passes the renderer command encoder into the presenter', () => {
    const present = vi.fn();
    const submit = vi.spyOn(fakeDevice.queue, 'submit');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      present,
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    submit.mockClear();

    renderer.flush(0);

    expect(present).toHaveBeenCalledTimes(1);
    expect(present.mock.calls[0]?.[3]).toBeDefined();
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('uploads Rust-produced RGBA bytes without converting the pixel array', () => {
    const writeTexture = vi.fn();
    const renderer = new WebGPUGraphicsRenderer(
      {
        ...fakeDevice,
        queue: { ...fakeDevice.queue, writeTexture },
      } as unknown as GPUDevice,
      () => {},
      {} as never,
    );
    renderer.createSurface(0, 120, 160);
    const rgba = new Uint8Array([0x12, 0x34, 0xab, 0x7f]);
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawRGBA(rgba, 0, 0, 1, 1, state);
    renderer.flush(0);

    expect(writeTexture).toHaveBeenCalledTimes(1);
    expect(writeTexture.mock.calls[0]?.[1]).toBe(rgba);
  });

  it('downscales safe glyphs with Canvas instead of reading pixels into JavaScript', () => {
    const getImageData = vi.spyOn(
      FakeCanvasRenderingContext2D.prototype,
      'getImageData',
    );
    const drawImage = vi.spyOn(
      FakeCanvasRenderingContext2D.prototype,
      'drawImage',
    );
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => '12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: (text: string) => text.length * 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);

    renderer.drawString('dynamic-1', 0, 0, 20, {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    });

    expect(drawImage).toHaveBeenCalledTimes(9);
    expect(getImageData).not.toHaveBeenCalled();
  });

  it('uploads an explicit transparent gutter around glyph atlas entries', () => {
    const drawImage = vi.spyOn(
      FakeCanvasRenderingContext2D.prototype,
      'drawImage',
    );
    const copyExternalImageToTexture = vi.spyOn(
      fakeDevice.queue,
      'copyExternalImageToTexture',
    );
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => '12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: (text: string) => text.length * 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);

    renderer.drawString('01', 0, 0, 20, {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    });

    expect(drawImage).toHaveBeenCalledTimes(2);
    expect(copyExternalImageToTexture).toHaveBeenCalledTimes(2);
    for (let i = 0; i < 2; i++) {
      const downscale = drawImage.mock.calls[i];
      const upload = copyExternalImageToTexture.mock.calls[i];
      expect(downscale?.[5]).toBe(5);
      expect(downscale?.[6]).toBe(5);
      expect(upload?.[1]?.origin).toMatchObject({ y: 0 });
      expect(upload?.[2]).toEqual([
        Number(downscale?.[7]) + 10,
        Number(downscale?.[8]) + 10,
      ]);
    }
  });

  it('retains glyph masks at 1.5x the surface resolution', () => {
    const drawImage = vi.spyOn(
      FakeCanvasRenderingContext2D.prototype,
      'drawImage',
    );
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => '12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: () => 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);

    renderer.drawString('A', 0, 0, 20, {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    });

    const downscale = drawImage.mock.calls[0];
    expect(downscale?.[7]).toBe(Math.ceil((Number(downscale?.[3]) * 1.5) / 4));
    expect(downscale?.[8]).toBe(Math.ceil((Number(downscale?.[4]) * 1.5) / 4));
  });

  it('preserves italic glyph overhang outside the advance width', () => {
    const measureText = vi
      .spyOn(FakeCanvasRenderingContext2D.prototype, 'measureText')
      .mockReturnValue({
        width: 32,
        actualBoundingBoxLeft: 1,
        actualBoundingBoxRight: 40,
      } as TextMetrics);
    const fillText = vi.spyOn(
      FakeCanvasRenderingContext2D.prototype,
      'fillText',
    );
    const drawImage = vi.spyOn(
      FakeCanvasRenderingContext2D.prototype,
      'drawImage',
    );
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => 'italic 12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: () => 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);

    renderer.drawString('中', 0, 0, 20, {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 2,
      fontSize: 0,
    });

    expect(measureText).toHaveBeenCalledWith('中');
    expect(fillText).toHaveBeenCalledWith('中', 9, 44);
    expect(drawImage.mock.calls[0]?.[3]).toBe(57);
  });

  it('reuses Latin, numeric, and CJK glyphs across changing strings', () => {
    const copyExternalImageToTexture = vi.spyOn(
      fakeDevice.queue,
      'copyExternalImageToTexture',
    );
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => '12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: (text: string) => text.length * 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawString('Score: 100 汉字', 0, 0, 20, state);
    const firstUploads = copyExternalImageToTexture.mock.calls.length;
    renderer.drawString('Score: 101 汉语', 0, 20, 20, state);

    expect(firstUploads).toBe(11);
    expect(copyExternalImageToTexture).toHaveBeenCalledTimes(12);
  });

  it('keeps unsupported scripts and complex graphemes on the full-string text path', () => {
    const copyExternalImageToTexture = vi.spyOn(
      fakeDevice.queue,
      'copyExternalImageToTexture',
    );
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => '12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: (text: string) => text.length * 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    const fallbackStrings = ['Ж', 'e\u0301', '👨‍👩‍👧‍👦'];
    for (const text of fallbackStrings) {
      renderer.drawString(text, 0, 0, 20, state);
      renderer.drawString(text, 0, 20, 20, state);
    }

    expect(copyExternalImageToTexture).toHaveBeenCalledTimes(3);
  });

  it('keeps scratch text canvases at their grown capacity', () => {
    const createElement = vi.spyOn(document, 'createElement');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => '12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: (text: string) => text.length * 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);
    const state = {
      surfaceId: 0,
      color: 0xffffff,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawString('x'.repeat(200), 0, 0, 20, state);
    const canvases = createElement.mock.results
      .map((result) => result.value)
      .filter(
        (element): element is FakeCanvasElement =>
          element instanceof FakeCanvasElement,
      );
    const [textCanvas, uploadCanvas] = canvases.slice(-2);
    const grownSizes = [
      textCanvas.width,
      textCanvas.height,
      uploadCanvas.width,
      uploadCanvas.height,
    ];

    renderer.drawString('short-unique', 0, 0, 20, state);

    expect([
      textCanvas.width,
      textCanvas.height,
      uploadCanvas.width,
      uploadCanvas.height,
    ]).toEqual(grownSizes);
  });

  it('stores text color in vertices without per-draw GPU resources', () => {
    const createBuffer = vi.spyOn(fakeDevice, 'createBuffer');
    const createBindGroup = vi.spyOn(fakeDevice, 'createBindGroup');
    const renderer = new WebGPUGraphicsRenderer(
      fakeDevice as unknown as GPUDevice,
      () => {},
      {
        getCSSFont: () => '12px sans-serif',
        getHeight: () => 14,
        getBaselinePosition: () => 11,
        stringWidth: (text: string) => text.length * 8,
      } as never,
    );
    renderer.createSurface(0, 120, 160);
    createBuffer.mockClear();
    createBindGroup.mockClear();
    const state = {
      surfaceId: 0,
      color: 0x12abef,
      clipX: 0,
      clipY: 0,
      clipWidth: 120,
      clipHeight: 160,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };

    renderer.drawString('cached', 0, 0, 20, state);
    renderer.drawString('cached', 20, 20, 20, state);

    expect(createBuffer).toHaveBeenCalledTimes(1);
    expect(createBindGroup).toHaveBeenCalledTimes(1);
  });

  it('preserves Mascot draw commands across a depth-only clear', () => {
    const pass = {
      ...fakePass,
      end: vi.fn(),
    };
    const encoder = {
      beginRenderPass: vi.fn(() => pass),
      finish: vi.fn(() => ({})),
    };
    const submit = vi.fn();
    const createCommandEncoder = vi.fn(() => encoder);
    const renderer = new MascotRenderer(
      {
        ...fakeDevice,
        queue: { ...fakeDevice.queue, submit },
        createCommandEncoder,
      } as unknown as GPUDevice,
      () => fakeTexture as unknown as GPUTexture,
      1,
    );
    const internals = renderer as unknown as {
      target: {
        texture: GPUTexture;
        depthTexture: GPUTexture;
        width: number;
        height: number;
      } | null;
      clearDepth(): void;
      currentPass(): void;
      flush(): void;
    };
    internals.target = {
      texture: fakeTexture as unknown as GPUTexture,
      depthTexture: fakeTexture as unknown as GPUTexture,
      width: 240,
      height: 320,
    };

    internals.currentPass();
    internals.clearDepth();
    internals.currentPass();
    internals.flush();

    expect(createCommandEncoder).toHaveBeenCalledTimes(1);
    expect(encoder.finish).toHaveBeenCalledTimes(1);
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('defers Mascot flush submission until the target is released', () => {
    const pass = {
      ...fakePass,
      end: vi.fn(),
    };
    const encoder = {
      beginRenderPass: vi.fn(() => pass),
      finish: vi.fn(() => ({})),
    };
    const submit = vi.fn();
    const renderer = new MascotRenderer(
      {
        ...fakeDevice,
        queue: { ...fakeDevice.queue, submit },
        createCommandEncoder: vi.fn(() => encoder),
      } as unknown as GPUDevice,
      () => fakeTexture as unknown as GPUTexture,
      1,
    );
    const internals = renderer as unknown as {
      target: {
        texture: GPUTexture;
        depthTexture: GPUTexture;
        width: number;
        height: number;
      } | null;
      currentPass(): void;
      handleCommand(commandId: number, view: DataView, offset: number): number;
    };
    internals.target = {
      texture: fakeTexture as unknown as GPUTexture,
      depthTexture: fakeTexture as unknown as GPUTexture,
      width: 240,
      height: 320,
    };

    internals.currentPass();
    internals.handleCommand(193, new DataView(new ArrayBuffer(0)), 0);

    expect(submit).not.toHaveBeenCalled();

    internals.handleCommand(191, new DataView(new ArrayBuffer(0)), 0);

    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('reuses the Mascot depth texture for repeated target binds', () => {
    const depthTexture = { createView: () => ({}), destroy: vi.fn() };
    const resizedDepthTexture = { createView: () => ({}), destroy: vi.fn() };
    const createTexture = vi
      .fn()
      .mockReturnValueOnce(fakeTexture)
      .mockReturnValueOnce(depthTexture)
      .mockReturnValueOnce(resizedDepthTexture);
    const renderer = new MascotRenderer(
      { ...fakeDevice, createTexture } as unknown as GPUDevice,
      () => fakeTexture as unknown as GPUTexture,
      1,
    );
    const bind = (width: number, height: number) => {
      const payload = new Uint8Array(28);
      const view = new DataView(payload.buffer);
      view.setInt32(0, 7, true);
      view.setInt32(4, width, true);
      view.setInt32(8, height, true);
      renderer.handleCommand(190, view, 0);
      renderer.handleCommand(191, new DataView(new ArrayBuffer(0)), 0);
    };

    bind(240, 320);
    bind(240, 320);

    expect(createTexture).toHaveBeenCalledTimes(2);
    expect(depthTexture.destroy).not.toHaveBeenCalled();

    bind(320, 240);

    expect(createTexture).toHaveBeenCalledTimes(3);
    expect(depthTexture.destroy).toHaveBeenCalledTimes(1);
    expect(resizedDepthTexture.destroy).not.toHaveBeenCalled();
  });

  it('applies a draw packet color write mask to its WebGPU pipeline', () => {
    const createRenderPipeline = vi.fn(() => ({}));
    const renderer = new M3gRenderer(
      {
        ...fakeDevice,
        createRenderPipeline,
      } as unknown as GPUDevice,
      fakeWebGPUContext as unknown as GPUCanvasContext,
    );
    const pipelineKey = 0x7 << m3gWebgpuWireSchema.pipelineKey.writeMaskShift;
    (
      renderer as unknown as {
        getDrawPacketPipeline: (
          topology: GPUPrimitiveTopology,
          pipelineKey: number,
          polygonOffsetFactor: number,
          polygonOffsetUnits: number,
        ) => GPURenderPipeline;
      }
    ).getDrawPacketPipeline('triangle-list', pipelineKey, 0, 0);

    const descriptor = createRenderPipeline.mock
      .calls[0][0] as GPURenderPipelineDescriptor;
    const target = descriptor.fragment?.targets?.[0] as GPUColorTargetState;
    expect(target.writeMask).toBe(0x8);
  });

  it('keeps depth writes enabled for alpha-blended draw packets by default', () => {
    const createRenderPipeline = vi.fn(() => ({}));
    const renderer = new M3gRenderer(
      {
        ...fakeDevice,
        createRenderPipeline,
      } as unknown as GPUDevice,
      fakeWebGPUContext as unknown as GPUCanvasContext,
    );
    const alphaPipelineKey = 1 << m3gWebgpuWireSchema.pipelineKey.blendShift;
    const alphaNoDepthWritePipelineKey =
      alphaPipelineKey | m3gWebgpuWireSchema.pipelineKey.depthWriteOff;
    const getDrawPacketPipeline = (
      renderer as unknown as {
        getDrawPacketPipeline: (
          topology: GPUPrimitiveTopology,
          pipelineKey: number,
          polygonOffsetFactor: number,
          polygonOffsetUnits: number,
        ) => GPURenderPipeline;
      }
    ).getDrawPacketPipeline.bind(renderer);

    getDrawPacketPipeline('triangle-list', alphaPipelineKey, 0, 0);
    getDrawPacketPipeline('triangle-list', alphaNoDepthWritePipelineKey, 0, 0);

    const alphaDescriptor = createRenderPipeline.mock
      .calls[0][0] as GPURenderPipelineDescriptor;
    const noDepthWriteDescriptor = createRenderPipeline.mock
      .calls[1][0] as GPURenderPipelineDescriptor;
    expect(alphaDescriptor.depthStencil?.depthWriteEnabled).toBe(true);
    expect(noDepthWriteDescriptor.depthStencil?.depthWriteEnabled).toBe(false);
  });

  it('does not write depth while drawing a color clear', () => {
    const createRenderPipeline = vi.fn(() => ({}));
    const renderer = new M3gRenderer(
      {
        ...fakeDevice,
        createRenderPipeline,
      } as unknown as GPUDevice,
      fakeWebGPUContext as unknown as GPUCanvasContext,
    );

    (
      renderer as unknown as {
        ensureClearPipeline: () => void;
      }
    ).ensureClearPipeline();

    const descriptor = createRenderPipeline.mock
      .calls[0][0] as GPURenderPipelineDescriptor;
    expect(descriptor.depthStencil?.depthWriteEnabled).toBe(false);
  });

  it('keeps a clear command viewport instead of reading a later draw viewport', () => {
    const renderer = new M3gRenderer(
      fakeDevice as unknown as GPUDevice,
      fakeWebGPUContext as unknown as GPUCanvasContext,
    );
    const raw = m3gWebgpuWireSchema.clear;
    const headerBytes = raw.header.reduce(
      (total, [, bytes]) => total + bytes,
      0,
    );
    const payload = new Uint8Array(headerBytes);
    const view = new DataView(payload.buffer);

    view.setUint32(0, 1, true);
    view.setUint32(4, 0, true);
    view.setInt32(8, 0, true);
    view.setInt32(12, 0, true);
    view.setInt32(16, 640, true);
    view.setInt32(20, 480, true);
    view.setInt32(24, 0, true);
    view.setInt32(28, 0, true);
    view.setInt32(32, 640, true);
    view.setInt32(36, 480, true);

    const next = (
      renderer as unknown as {
        handleCommand: (
          commandId: number,
          view: DataView,
          offset: number,
        ) => number;
        pendingClearViewport: { x: number; y: number; w: number; h: number };
      }
    ).handleCommand(raw.commandId, view, 0);

    expect(next).toBe(40);
    expect(
      (
        renderer as unknown as {
          pendingClearViewport: { x: number; y: number; w: number; h: number };
        }
      ).pendingClearViewport,
    ).toEqual({ x: 0, y: 0, w: 640, h: 480 });
  });

  it('retires resized textures after GPU completion and keeps image invalidation precise', async () => {
    let resolveCompletion: (() => void) | undefined;
    const completion = new Promise<void>((resolve) => {
      resolveCompletion = resolve;
    });
    const firstTexture = {
      createView: () => ({}),
      destroy: vi.fn(),
    };
    const secondTexture = {
      createView: () => ({}),
      destroy: vi.fn(),
    };
    const createTexture = vi
      .fn()
      .mockReturnValueOnce(firstTexture)
      .mockReturnValueOnce(secondTexture);
    const renderer = new M3gRenderer(
      {
        ...fakeDevice,
        createTexture,
        queue: {
          ...fakeDevice.queue,
          onSubmittedWorkDone: () => completion,
        },
      } as unknown as GPUDevice,
      fakeWebGPUContext as unknown as GPUCanvasContext,
    );
    const updateTexture = (
      imageHandle: number,
      version: number,
      width: number,
      height: number,
    ) => {
      const pixelBytes = width * height * 4;
      const payload = new Uint8Array(24 + pixelBytes);
      const view = new DataView(payload.buffer);
      view.setUint32(0, 10, true);
      view.setUint32(4, imageHandle, true);
      view.setUint32(8, version, true);
      view.setUint32(12, width, true);
      view.setUint32(16, height, true);
      view.setUint32(20, pixelBytes, true);
      (
        renderer as unknown as {
          handleCommand: (
            commandId: number,
            view: DataView,
            offset: number,
          ) => number;
        }
      ).handleCommand(147, view, 0);
    };

    updateTexture(7, 1, 1, 1);
    updateTexture(8, 2, 1, 1);
    updateTexture(8, 3, 2, 1);

    const registry = renderer as unknown as {
      imageToGlTextures: Map<number, Set<number>>;
    };
    expect(registry.imageToGlTextures.has(7)).toBe(false);
    expect(registry.imageToGlTextures.get(8)).toEqual(new Set([10]));
    expect(firstTexture.destroy).not.toHaveBeenCalled();

    resolveCompletion!();
    await completion;
    await Promise.resolve();
    expect(firstTexture.destroy).toHaveBeenCalledTimes(1);
    expect(secondTexture.destroy).not.toHaveBeenCalled();
  });

  it('retires resized image targets after GPU completion', async () => {
    let resolveCompletion: (() => void) | undefined;
    const completion = new Promise<void>((resolve) => {
      resolveCompletion = resolve;
    });
    const firstTarget = {
      width: 8,
      height: 8,
      createView: () => ({}),
      destroy: vi.fn(),
    };
    const secondTarget = {
      width: 16,
      height: 8,
      createView: () => ({}),
      destroy: vi.fn(),
    };
    const renderer = new M3gRenderer(
      {
        ...fakeDevice,
        createTexture: vi
          .fn()
          .mockReturnValueOnce(firstTarget)
          .mockReturnValueOnce(secondTarget),
        queue: {
          ...fakeDevice.queue,
          onSubmittedWorkDone: () => completion,
        },
      } as unknown as GPUDevice,
      fakeWebGPUContext as unknown as GPUCanvasContext,
    );
    const bindImageTarget = (width: number, height: number) => {
      const payload = new Uint8Array(12);
      const view = new DataView(payload.buffer);
      view.setUint32(0, 77, true);
      view.setUint32(4, width, true);
      view.setUint32(8, height, true);
      (
        renderer as unknown as {
          handleCommand: (
            commandId: number,
            view: DataView,
            offset: number,
          ) => number;
        }
      ).handleCommand(137, view, 0);
    };

    bindImageTarget(8, 8);
    (
      renderer as unknown as {
        recordGpuSubmission: () => Promise<void>;
      }
    ).recordGpuSubmission();
    bindImageTarget(16, 8);

    expect(firstTarget.destroy).not.toHaveBeenCalled();
    resolveCompletion!();
    await completion;
    await Promise.resolve();
    expect(firstTarget.destroy).toHaveBeenCalledTimes(1);
    expect(secondTarget.destroy).not.toHaveBeenCalled();

    const deletePayload = new Uint8Array(8);
    const deleteView = new DataView(deletePayload.buffer);
    deleteView.setUint32(0, 3, true);
    deleteView.setUint32(4, 77, true);
    (
      renderer as unknown as {
        handleCommand: (
          commandId: number,
          view: DataView,
          offset: number,
        ) => number;
        renderTarget: unknown;
      }
    ).handleCommand(148, deleteView, 0);
    expect(
      (renderer as unknown as { renderTarget: unknown }).renderTarget,
    ).toBeNull();
    expect(secondTarget.destroy).toHaveBeenCalledTimes(1);
  });
});
