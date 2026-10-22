import type { J2MEPerformanceCollector } from '@jsjvm/j2me-simulator';
import type { J2MEOnlyPlatformBridge } from '@jsjvm/j2me-simulator/env';
import { READBACK_CONTEXT_OPTIONS } from './canvas-context-options';
import type { FontManager } from './font-manager';
import {
  type GlyphAtlasPage,
  type PositionedGlyph,
  WebGPUGlyphAtlas,
} from './webgpu-glyph-atlas';
import {
  ARC_DOTTED_STROKE_AA_FRAGMENT_SHADER,
  ARC_FILL_AA_FRAGMENT_SHADER,
  ARC_FILL_AA_VERTEX_SHADER,
  ARC_STROKE_AA_FRAGMENT_SHADER,
  ARC_STROKE_AA_VERTEX_SHADER,
  COLOR_AA_FRAGMENT_SHADER,
  COLOR_AA_VERTEX_SHADER,
  COLOR_FRAGMENT_SHADER,
  COLOR_VERTEX_SHADER,
  ELLIPSE_AA_FRAGMENT_SHADER,
  ELLIPSE_AA_VERTEX_SHADER,
  LINE_AA_FRAGMENT_SHADER,
  LINE_AA_VERTEX_SHADER,
  ROUND_RECT_AA_FRAGMENT_SHADER,
  ROUND_RECT_AA_VERTEX_SHADER,
  ROUND_RECT_DOTTED_STROKE_AA_FRAGMENT_SHADER,
  ROUND_RECT_STROKE_AA_FRAGMENT_SHADER,
  ROUND_RECT_STROKE_AA_VERTEX_SHADER,
  SHAPE_BATCH_FRAGMENT_SHADER,
  SHAPE_BATCH_VERTEX_SHADER,
  STRAIGHT_TEXTURE_FRAGMENT_SHADER,
  TEXT_ALPHA_FRAGMENT_SHADER,
  TEXT_ALPHA_VERTEX_SHADER,
  TEXTURE_FRAGMENT_SHADER,
  TEXTURE_VERTEX_SHADER,
} from './webgpu-shaders';

interface GraphicsRenderState {
  surfaceId: number;
  color: number;
  clipX: number;
  clipY: number;
  clipWidth: number;
  clipHeight: number;
  translateX: number;
  translateY: number;
  strokeStyle: number;
  fontHandle: number;
  fontFace: number;
  fontStyle: number;
  fontSize: number;
}
interface GpuSurface {
  id: number;
  width: number;
  height: number;
  physicalWidth: number;
  physicalHeight: number;
  scale: number;
  texture: GPUTexture;
}

interface TextureSource {
  texture: GPUTexture;
  width: number;
  height: number;
  surfaceId?: number;
  textureWidth?: number;
  textureHeight?: number;
  owned: boolean;
  premultiplied?: boolean;
}

interface EncodedTextureDraw {
  buffer: GPUBuffer;
  ownedTexture?: GPUTexture;
  snapshotTexture?: GPUTexture;
}

interface BatchVertexPage {
  buffer: GPUBuffer;
  capacity: number;
  offset: number;
}

interface CachedTextureBindGroups {
  nearest?: GPUBindGroup;
  linear?: GPUBindGroup;
}

interface TransientTexturePool {
  textures: GPUTexture[];
  index: number;
}

interface DynamicRgbaAtlasPage {
  texture: GPUTexture;
  cursorX: number;
  cursorY: number;
  rowHeight: number;
}

interface PendingTextureBatch {
  texture: GPUTexture;
  linear: boolean;
  premultiplied: boolean;
  state: GraphicsRenderState;
}

interface TextureRenderTarget {
  texture: GPUTexture;
  width: number;
  height: number;
  clipScale: number;
}

const ANCHOR = {
  HCENTER: 1,
  VCENTER: 2,
  LEFT: 4,
  RIGHT: 8,
  TOP: 16,
  BOTTOM: 32,
  BASELINE: 64,
} as const;

const BATCH_VERTEX_PAGE_BYTES = 1024 * 1024;
const DYNAMIC_RGBA_ATLAS_SIZE = 1024;
const DYNAMIC_RGBA_ATLAS_PADDING = 1;
const DYNAMIC_RGBA_ATLAS_MAX_PAGES = 4;
const UNIFIED_SHAPE_STRIDE = 21;
const UNIFIED_SHAPE_PIPELINES = new Set([0, 2, 5, 8]);
const QUAD_X_INDEX = [0, 1, 0, 0, 1, 1] as const;
const QUAD_Y_INDEX = [0, 0, 1, 1, 0, 1] as const;

interface CachedTextEntry {
  texture: GPUTexture;
  width: number;
  height: number;
  logicalWidth: number;
  logicalHeight: number;
  baseline: number;
  lastUsedFrame: number;
}

type GraphicsHooks = NonNullable<
  NonNullable<J2MEOnlyPlatformBridge<HTMLCanvasElement>['j2me']>['graphics']
>;

export class WebGPUGraphicsRenderer implements GraphicsHooks {
  private surfaces = new Map<number, GpuSurface>();
  private surfaceHandles = new Map<number, HTMLCanvasElement>();
  private handleToSurfaceId = new WeakMap<HTMLCanvasElement, number>();
  private colorPipeline: GPURenderPipeline;
  private colorAAPipeline: GPURenderPipeline;
  private lineAAPipeline: GPURenderPipeline;
  private ellipseAAPipeline: GPURenderPipeline;
  private roundRectAAPipeline: GPURenderPipeline;
  private roundRectStrokeAAPipeline: GPURenderPipeline;
  private roundRectDottedStrokeAAPipeline: GPURenderPipeline;
  private arcFillAAPipeline: GPURenderPipeline;
  private arcStrokeAAPipeline: GPURenderPipeline;
  private arcDottedStrokeAAPipeline: GPURenderPipeline;
  private shapeBatchPipeline: GPURenderPipeline;
  private texturePipeline: GPURenderPipeline;
  private straightTexturePipeline: GPURenderPipeline;
  private textAlphaPipeline: GPURenderPipeline;
  private textureBindGroupLayout: GPUBindGroupLayout;
  private nearestSampler: GPUSampler;
  private linearSampler: GPUSampler;
  private textCanvas: HTMLCanvasElement | null = null;
  private textContext: CanvasRenderingContext2D | null = null;
  private textUploadCanvas: HTMLCanvasElement | null = null;
  private textUploadContext: CanvasRenderingContext2D | null = null;
  private textCache = new Map<string, CachedTextEntry>();
  private textCacheFrameCounter = 0;
  private glyphAtlas: WebGPUGlyphAtlas;
  private static TEXT_CACHE_MAX_ENTRIES = 256;

  // Batch state
  private batchEncoder: GPUCommandEncoder | null = null;
  private batchPass: GPURenderPassEncoder | null = null;
  private batchSurfaceId: number = -1;
  private batchScissorRect: [number, number, number, number] | null = null;
  private batchBuffers: GPUBuffer[] = [];
  private batchOwnedTextures: GPUTexture[] = [];
  private batchWrittenSurfaceIds = new Set<number>();
  private batchVertexPages: BatchVertexPage[] = [];
  private batchVertexPageIndex = 0;
  private pendingShapeVertices = new Float32Array(
    UNIFIED_SHAPE_STRIDE * 6 * 128,
  );
  private pendingShapeFloatCount = 0;
  private pendingShapeState: GraphicsRenderState | null = null;
  private flushingShapeBatch = false;
  private pendingTextureVertices = new Float32Array(6 * 4 * 128);
  private pendingTextureFloatCount = 0;
  private pendingTextureBatch: PendingTextureBatch | null = null;
  private flushingTextureBatch = false;
  private textureBindGroupCache = new WeakMap<
    GPUTexture,
    CachedTextureBindGroups
  >();
  private transientTexturePools = new Map<string, TransientTexturePool>();
  private scratchTextures = new Map<string, GPUTexture>();
  private dynamicRgbaAtlasPages: DynamicRgbaAtlasPage[] = [];
  private lastPresentedSurfaceId: number | null = null;

  constructor(
    private device: GPUDevice,
    private presentTexture: (
      texture: GPUTexture,
      width: number,
      height: number,
      encoder?: GPUCommandEncoder,
    ) => void,
    private fontManager: FontManager,
    private surfaceScale = 1,
    private performanceCollector: J2MEPerformanceCollector | null = null,
    private canMergePresent: () => boolean = () => true,
  ) {
    this.glyphAtlas = new WebGPUGlyphAtlas(
      device,
      fontManager,
      performanceCollector,
    );
    this.nearestSampler = device.createSampler({
      magFilter: 'nearest',
      minFilter: 'nearest',
    });
    this.linearSampler = device.createSampler({
      magFilter: 'linear',
      minFilter: 'linear',
    });

    this.textureBindGroupLayout = device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.FRAGMENT,
          texture: {},
        },
        {
          binding: 1,
          visibility: GPUShaderStage.FRAGMENT,
          sampler: {},
        },
      ],
    });

    this.colorPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({ code: COLOR_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 24,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({ code: COLOR_FRAGMENT_SHADER }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.colorAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({ code: COLOR_AA_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 36,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x3' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({ code: COLOR_AA_FRAGMENT_SHADER }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.lineAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({ code: LINE_AA_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 56,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x2' },
              { shaderLocation: 4, offset: 40, format: 'float32x2' },
              { shaderLocation: 5, offset: 48, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({ code: LINE_AA_FRAGMENT_SHADER }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.ellipseAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({ code: ELLIPSE_AA_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 48,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x2' },
              { shaderLocation: 4, offset: 40, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({ code: ELLIPSE_AA_FRAGMENT_SHADER }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.roundRectAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({
          code: ROUND_RECT_AA_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 64,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x4' },
              { shaderLocation: 4, offset: 48, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: ROUND_RECT_AA_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.roundRectStrokeAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({
          code: ROUND_RECT_STROKE_AA_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 72,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x4' },
              { shaderLocation: 4, offset: 48, format: 'float32x2' },
              { shaderLocation: 5, offset: 56, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: ROUND_RECT_STROKE_AA_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.roundRectDottedStrokeAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({
          code: ROUND_RECT_STROKE_AA_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 72,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x4' },
              { shaderLocation: 4, offset: 48, format: 'float32x2' },
              { shaderLocation: 5, offset: 56, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: ROUND_RECT_DOTTED_STROKE_AA_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.arcFillAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({ code: ARC_FILL_AA_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 76,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x2' },
              { shaderLocation: 4, offset: 40, format: 'float32x2' },
              { shaderLocation: 5, offset: 48, format: 'float32x3' },
              { shaderLocation: 6, offset: 60, format: 'float32x2' },
              { shaderLocation: 7, offset: 68, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: ARC_FILL_AA_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.arcStrokeAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({
          code: ARC_STROKE_AA_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 80,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x2' },
              { shaderLocation: 4, offset: 40, format: 'float32x2' },
              { shaderLocation: 5, offset: 48, format: 'float32x4' },
              { shaderLocation: 6, offset: 64, format: 'float32x2' },
              { shaderLocation: 7, offset: 72, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: ARC_STROKE_AA_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.arcDottedStrokeAAPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({
          code: ARC_STROKE_AA_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 80,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x2' },
              { shaderLocation: 4, offset: 40, format: 'float32x2' },
              { shaderLocation: 5, offset: 48, format: 'float32x4' },
              { shaderLocation: 6, offset: 64, format: 'float32x2' },
              { shaderLocation: 7, offset: 72, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: ARC_DOTTED_STROKE_AA_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.shapeBatchPipeline = device.createRenderPipeline({
      layout: 'auto' as unknown as GPUPipelineLayout,
      vertex: {
        module: device.createShaderModule({ code: SHAPE_BATCH_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: UNIFIED_SHAPE_STRIDE * 4,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x4' },
              { shaderLocation: 2, offset: 24, format: 'float32x2' },
              { shaderLocation: 3, offset: 32, format: 'float32x4' },
              { shaderLocation: 4, offset: 48, format: 'float32x4' },
              { shaderLocation: 5, offset: 64, format: 'float32x4' },
              { shaderLocation: 6, offset: 80, format: 'float32' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: SHAPE_BATCH_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.texturePipeline = device.createRenderPipeline({
      layout: device.createPipelineLayout({
        bindGroupLayouts: [this.textureBindGroupLayout],
      }),
      vertex: {
        module: device.createShaderModule({ code: TEXTURE_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 16,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({ code: TEXTURE_FRAGMENT_SHADER }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.straightTexturePipeline = device.createRenderPipeline({
      layout: device.createPipelineLayout({
        bindGroupLayouts: [this.textureBindGroupLayout],
      }),
      vertex: {
        module: device.createShaderModule({ code: TEXTURE_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 16,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x2' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({
          code: STRAIGHT_TEXTURE_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });

    this.textAlphaPipeline = device.createRenderPipeline({
      layout: device.createPipelineLayout({
        bindGroupLayouts: [this.textureBindGroupLayout],
      }),
      vertex: {
        module: device.createShaderModule({ code: TEXT_ALPHA_VERTEX_SHADER }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 20,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x2' },
              { shaderLocation: 1, offset: 8, format: 'float32x2' },
              { shaderLocation: 2, offset: 16, format: 'unorm8x4' },
            ],
          },
        ],
      } as unknown as GPUVertexState,
      fragment: {
        module: device.createShaderModule({ code: TEXT_ALPHA_FRAGMENT_SHADER }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: {
              color: {
                srcFactor: 'src-alpha',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
              alpha: {
                srcFactor: 'one',
                dstFactor: 'one-minus-src-alpha',
                operation: 'add',
              },
            },
          },
        ],
      } as unknown as GPUFragmentState,
      primitive: { topology: 'triangle-list' },
    });
  }

  createSurface(
    surfaceId: number,
    width: number,
    height: number,
  ): HTMLCanvasElement {
    this.flushBatch();
    const old = this.surfaces.get(surfaceId);
    if (old) {
      this.performanceCollector?.addEstimatedGpuBytes(
        -(old.physicalWidth * old.physicalHeight * 4),
      );
    }
    old?.texture.destroy();

    const surfaceScale = Math.max(1, this.surfaceScale);
    const physicalWidth = Math.max(1, Math.round(width * surfaceScale));
    const physicalHeight = Math.max(1, Math.round(height * surfaceScale));

    const texture = this.device.createTexture({
      size: [physicalWidth, physicalHeight],
      format: 'rgba8unorm',
      usage:
        GPUTextureUsage.RENDER_ATTACHMENT |
        GPUTextureUsage.TEXTURE_BINDING |
        GPUTextureUsage.COPY_DST |
        GPUTextureUsage.COPY_SRC,
    });
    const textureBytes = physicalWidth * physicalHeight * 4;
    this.performanceCollector?.addGpuCounter('texturesCreated');
    this.performanceCollector?.addGpuCounter(
      'textureBytesAllocated',
      textureBytes,
    );
    this.performanceCollector?.addEstimatedGpuBytes(textureBytes);
    const surface = {
      id: surfaceId,
      width,
      height,
      physicalWidth,
      physicalHeight,
      scale: surfaceScale,
      texture,
    };
    this.surfaces.set(surfaceId, surface);

    const handle = document.createElement('canvas');
    handle.width = physicalWidth;
    handle.height = physicalHeight;
    handle.style.width = `${width}px`;
    handle.style.height = `${height}px`;
    this.surfaceHandles.set(surfaceId, handle);
    this.handleToSurfaceId.set(handle, surfaceId);
    this.clearSurface(surface, [0, 0, 0, 0]);
    return handle;
  }

  destroySurface(surfaceId: number): void {
    this.flushBatch();
    const surface = this.surfaces.get(surfaceId);
    if (surface) {
      this.performanceCollector?.addEstimatedGpuBytes(
        -(surface.physicalWidth * surface.physicalHeight * 4),
      );
    }
    surface?.texture.destroy();
    this.surfaces.delete(surfaceId);
    this.surfaceHandles.delete(surfaceId);
  }

  getSurfaceCanvas(surfaceId: number): HTMLCanvasElement | undefined {
    return this.surfaceHandles.get(surfaceId);
  }

  getSurfaceScale(surfaceId = 0): number {
    return this.surfaces.get(surfaceId)?.scale ?? this.surfaceScale;
  }

  present(surfaceId: number): void {
    const surface = this.surfaces.get(surfaceId);
    if (!surface) return;
    if (this.canMergePresent()) {
      this.flushPendingShapeBatch();
      this.flushPendingTextureBatch();
      this.endBatchPass();
      const encoder = this.getBatchEncoder();
      this.presentTexture(
        surface.texture,
        surface.physicalWidth,
        surface.physicalHeight,
        encoder,
      );
      this.submitBatch();
    } else {
      this.flushBatch();
      this.presentTexture(
        surface.texture,
        surface.physicalWidth,
        surface.physicalHeight,
      );
    }
    this.lastPresentedSurfaceId = surfaceId;
    this.textCacheFrameCounter++;
    this.performanceCollector?.recordPresent();
  }

  async screenshot(
    surfaceId: number | null = this.lastPresentedSurfaceId,
    fullResolution = false,
  ): Promise<ImageData | null> {
    if (surfaceId == null) return null;
    this.flushBatch();
    const surface = this.surfaces.get(surfaceId);
    if (!surface) return null;

    const width = fullResolution ? surface.physicalWidth : surface.width;
    const height = fullResolution ? surface.physicalHeight : surface.height;
    const bytesPerPixel = 4;
    const unpaddedBytesPerRow = width * bytesPerPixel;
    const bytesPerRow = Math.ceil(unpaddedBytesPerRow / 256) * 256;
    const bufferSize = bytesPerRow * height;
    const buffer = this.device.createBuffer({
      size: bufferSize,
      usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ,
    });

    const encoder = this.device.createCommandEncoder();
    if (fullResolution) {
      encoder.copyTextureToBuffer(
        { texture: surface.texture },
        { buffer, bytesPerRow, rowsPerImage: height },
        [width, height],
      );
    } else {
      const readback = this.createReadbackTexture(
        encoder,
        surface,
        0,
        0,
        width,
        height,
      );
      encoder.copyTextureToBuffer(
        { texture: readback.texture },
        { buffer, bytesPerRow, rowsPerImage: height },
        [width, height],
      );
      this.device.queue.submit([encoder.finish()]);

      try {
        await buffer.mapAsync(GPUMapMode.READ);
        const data = new Uint8ClampedArray(width * height * bytesPerPixel);
        const mapped = new Uint8Array(buffer.getMappedRange());
        for (let row = 0; row < height; row++) {
          const srcStart = row * bytesPerRow;
          const dstStart = row * unpaddedBytesPerRow;
          data.set(
            mapped.subarray(srcStart, srcStart + unpaddedBytesPerRow),
            dstStart,
          );
        }
        return new ImageData(data, width, height);
      } finally {
        buffer.unmap();
        buffer.destroy();
        readback.resources?.buffer.destroy();
        readback.resources?.ownedTexture?.destroy();
        readback.resources?.snapshotTexture?.destroy();
        readback.texture.destroy();
      }
    }
    this.device.queue.submit([encoder.finish()]);

    try {
      await buffer.mapAsync(GPUMapMode.READ);
      const data = new Uint8ClampedArray(width * height * bytesPerPixel);
      const mapped = new Uint8Array(buffer.getMappedRange());
      for (let row = 0; row < height; row++) {
        const srcStart = row * bytesPerRow;
        const dstStart = row * unpaddedBytesPerRow;
        data.set(
          mapped.subarray(srcStart, srcStart + unpaddedBytesPerRow),
          dstStart,
        );
      }
      return new ImageData(data, width, height);
    } finally {
      buffer.unmap();
      buffer.destroy();
    }
  }

  async surfaceSnapshot(surfaceId: number): Promise<ImageData | null> {
    this.flushBatch();
    const surface = this.surfaces.get(surfaceId);
    if (!surface) return null;

    const width = surface.physicalWidth;
    const height = surface.physicalHeight;
    const bytesPerPixel = 4;
    const unpaddedBytesPerRow = width * bytesPerPixel;
    const bytesPerRow = Math.ceil(unpaddedBytesPerRow / 256) * 256;
    const bufferSize = bytesPerRow * height;
    const buffer = this.device.createBuffer({
      size: bufferSize,
      usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ,
    });

    const encoder = this.device.createCommandEncoder();
    encoder.copyTextureToBuffer(
      { texture: surface.texture },
      { buffer, bytesPerRow, rowsPerImage: height },
      [width, height],
    );
    this.device.queue.submit([encoder.finish()]);

    try {
      await buffer.mapAsync(GPUMapMode.READ);
      const data = new Uint8ClampedArray(width * height * bytesPerPixel);
      const mapped = new Uint8Array(buffer.getMappedRange());
      for (let row = 0; row < height; row++) {
        const srcStart = row * bytesPerRow;
        const dstStart = row * unpaddedBytesPerRow;
        data.set(
          mapped.subarray(srcStart, srcStart + unpaddedBytesPerRow),
          dstStart,
        );
      }
      return new ImageData(data, width, height);
    } finally {
      buffer.unmap();
      buffer.destroy();
    }
  }

  async surfaceSnapshotForHandle(
    handle: HTMLCanvasElement,
  ): Promise<ImageData | null> {
    const surfaceId = this.handleToSurfaceId.get(handle);
    return surfaceId === undefined ? null : this.surfaceSnapshot(surfaceId);
  }

  flushPendingDraws(): void {
    this.flushBatch();
  }

  flush(surfaceId: number): void {
    this.present(surfaceId);
  }

  scheduleRepaint(callback: () => void): void {
    if (typeof requestAnimationFrame !== 'undefined') {
      requestAnimationFrame(callback);
    } else {
      setTimeout(callback, 0);
    }
  }

  getSurfaceTexture(surfaceId: number): GPUTexture | null {
    const surface = this.surfaces.get(surfaceId);
    return surface?.texture ?? null;
  }

  private flushBatch(): void {
    this.flushPendingShapeBatch();
    this.flushPendingTextureBatch();
    if (!this.batchEncoder) return;

    this.endBatchPass();
    this.submitBatch();
  }

  private submitBatch(): void {
    if (!this.batchEncoder) return;
    this.device.queue.submit([this.batchEncoder.finish()]);
    this.performanceCollector?.addGpuCounter('submits');
    this.batchEncoder = null;
    this.batchSurfaceId = -1;
    this.batchWrittenSurfaceIds.clear();
    this.batchVertexPageIndex = 0;
    for (const page of this.batchVertexPages) {
      page.offset = 0;
    }
    for (const pool of this.transientTexturePools.values()) {
      pool.index = 0;
    }
    for (const page of this.dynamicRgbaAtlasPages) {
      page.cursorX = 0;
      page.cursorY = 0;
      page.rowHeight = 0;
    }

    for (const buf of this.batchBuffers) {
      buf.destroy();
    }
    this.batchBuffers = [];

    for (const tex of this.batchOwnedTextures) {
      tex.destroy();
    }
    this.batchOwnedTextures = [];
  }

  private endBatchPass(): void {
    if (!this.batchPass) return;
    this.batchPass.end();
    this.batchPass = null;
    this.batchSurfaceId = -1;
    this.batchScissorRect = null;
  }

  private getBatchEncoder(): GPUCommandEncoder {
    if (!this.batchEncoder) {
      this.batchEncoder = this.device.createCommandEncoder();
    }
    return this.batchEncoder;
  }

  private getBatchPass(
    surface: GpuSurface,
    state: GraphicsRenderState,
  ): GPURenderPassEncoder {
    if (!this.flushingShapeBatch) {
      this.flushPendingShapeBatch();
    }
    if (!this.flushingTextureBatch) {
      this.flushPendingTextureBatch();
    }
    this.getBatchEncoder();

    if (this.batchPass && this.batchSurfaceId !== surface.id) {
      this.batchPass.end();
      this.batchPass = null;
      this.batchScissorRect = null;
    }

    if (!this.batchPass) {
      this.batchPass = this.batchEncoder!.beginRenderPass({
        colorAttachments: [
          {
            view: surface.texture.createView(),
            loadOp: 'load',
            storeOp: 'store',
          },
        ],
      } as GPURenderPassDescriptor);
      this.performanceCollector?.addGpuCounter('renderPasses');
      this.batchSurfaceId = surface.id;
      this.batchScissorRect = null;
    }
    this.batchWrittenSurfaceIds.add(surface.id);

    this.applyScissorRect(
      this.batchPass,
      surface.physicalWidth,
      surface.physicalHeight,
      surface.scale,
      state,
    );

    return this.batchPass;
  }

  private canAppendPendingShape(state: GraphicsRenderState): boolean {
    const pending = this.pendingShapeState;
    return (
      !pending ||
      (pending.surfaceId === state.surfaceId &&
        pending.clipX === state.clipX &&
        pending.clipY === state.clipY &&
        pending.clipWidth === state.clipWidth &&
        pending.clipHeight === state.clipHeight)
    );
  }

  private ensurePendingShapeCapacity(additionalFloats: number): void {
    const required = this.pendingShapeFloatCount + additionalFloats;
    if (required <= this.pendingShapeVertices.length) return;
    let capacity = this.pendingShapeVertices.length;
    while (capacity < required) capacity *= 2;
    const grown = new Float32Array(capacity);
    grown.set(
      this.pendingShapeVertices.subarray(0, this.pendingShapeFloatCount),
    );
    this.pendingShapeVertices = grown;
  }

  private canAppendPendingTexture(
    texture: GPUTexture,
    linear: boolean,
    premultiplied: boolean,
    state: GraphicsRenderState,
  ): boolean {
    const pending = this.pendingTextureBatch;
    return (
      !pending ||
      (pending.texture === texture &&
        pending.linear === linear &&
        pending.premultiplied === premultiplied &&
        pending.state.surfaceId === state.surfaceId &&
        pending.state.clipX === state.clipX &&
        pending.state.clipY === state.clipY &&
        pending.state.clipWidth === state.clipWidth &&
        pending.state.clipHeight === state.clipHeight)
    );
  }

  private ensurePendingTextureCapacity(additionalFloats: number): void {
    const required = this.pendingTextureFloatCount + additionalFloats;
    if (required <= this.pendingTextureVertices.length) return;
    let capacity = this.pendingTextureVertices.length;
    while (capacity < required) capacity *= 2;
    const grown = new Float32Array(capacity);
    grown.set(
      this.pendingTextureVertices.subarray(0, this.pendingTextureFloatCount),
    );
    this.pendingTextureVertices = grown;
  }

  private appendPendingTextureVertices(
    texture: GPUTexture,
    linear: boolean,
    premultiplied: boolean,
    state: GraphicsRenderState,
    vertices: Float32Array,
  ): void {
    this.flushPendingShapeBatch();
    if (!this.canAppendPendingTexture(texture, linear, premultiplied, state)) {
      this.flushPendingTextureBatch();
    }
    if (!this.pendingTextureBatch) {
      this.pendingTextureBatch = {
        texture,
        linear,
        premultiplied,
        state: { ...state },
      };
    }
    this.ensurePendingTextureCapacity(vertices.length);
    this.pendingTextureVertices.set(vertices, this.pendingTextureFloatCount);
    this.pendingTextureFloatCount += vertices.length;
  }

  private flushPendingTextureBatch(): void {
    const batch = this.pendingTextureBatch;
    if (!batch || this.pendingTextureFloatCount === 0) return;
    const surface = this.surfaces.get(batch.state.surfaceId);
    if (!surface) {
      this.pendingTextureFloatCount = 0;
      this.pendingTextureBatch = null;
      return;
    }

    const vertices = this.pendingTextureVertices.subarray(
      0,
      this.pendingTextureFloatCount,
    );
    const vertexData = this.writeBatchVertexData(vertices);
    const vertexCount = this.pendingTextureFloatCount / 4;
    this.pendingTextureFloatCount = 0;
    this.pendingTextureBatch = null;
    this.flushingTextureBatch = true;
    try {
      const pass = this.getBatchPass(surface, batch.state);
      pass.setPipeline(
        batch.premultiplied
          ? this.texturePipeline
          : this.straightTexturePipeline,
      );
      pass.setBindGroup(
        0,
        this.getCachedTextureBindGroup(batch.texture, batch.linear),
      );
      pass.setVertexBuffer(
        0,
        vertexData.buffer,
        vertexData.offset,
        vertices.byteLength,
      );
      pass.draw(vertexCount);
      this.performanceCollector?.addGpuCounter('drawCalls');
    } finally {
      this.flushingTextureBatch = false;
    }
  }

  private appendPendingShapeVertices(
    pipelineKind: number,
    stride: number,
    vertices: Float32Array,
    state: GraphicsRenderState,
  ): void {
    this.flushPendingTextureBatch();
    if (!this.canAppendPendingShape(state)) {
      this.flushPendingShapeBatch();
    }
    if (!this.pendingShapeState) {
      this.pendingShapeState = { ...state };
    }

    const vertexCount = vertices.length / stride;
    this.ensurePendingShapeCapacity(vertexCount * UNIFIED_SHAPE_STRIDE);
    const target = this.pendingShapeVertices;
    let output = this.pendingShapeFloatCount;
    for (let vertex = 0; vertex < vertexCount; vertex++) {
      const input = vertex * stride;
      target.fill(0, output, output + UNIFIED_SHAPE_STRIDE);
      const commonFloats = pipelineKind === 0 ? 6 : 8;
      for (let i = 0; i < commonFloats; i++) {
        target[output + i] = vertices[input + i];
      }
      for (let i = 8; i < stride; i++) {
        target[output + i] = vertices[input + i];
      }
      target[output + 20] = pipelineKind;
      output += UNIFIED_SHAPE_STRIDE;
    }
    this.pendingShapeFloatCount = output;
  }

  private appendPendingFillRect(
    x: number,
    y: number,
    width: number,
    height: number,
    state: GraphicsRenderState,
    surface: GpuSurface,
  ): void {
    this.flushPendingTextureBatch();
    if (!this.canAppendPendingShape(state)) {
      this.flushPendingShapeBatch();
    }
    if (!this.pendingShapeState) {
      this.pendingShapeState = { ...state };
    }
    this.ensurePendingShapeCapacity(6 * UNIFIED_SHAPE_STRIDE);

    const tx = x + state.translateX;
    const ty = y + state.translateY;
    const nx0 = (tx / surface.width) * 2 - 1;
    const nx1 = ((tx + width) / surface.width) * 2 - 1;
    const ny0 = 1 - (ty / surface.height) * 2;
    const ny1 = 1 - ((ty + height) / surface.height) * 2;
    const red = ((state.color >> 16) & 0xff) / 255;
    const green = ((state.color >> 8) & 0xff) / 255;
    const blue = (state.color & 0xff) / 255;
    let output = this.pendingShapeFloatCount;
    for (let vertex = 0; vertex < 6; vertex++) {
      this.pendingShapeVertices.fill(0, output, output + UNIFIED_SHAPE_STRIDE);
      this.pendingShapeVertices[output] =
        QUAD_X_INDEX[vertex] === 0 ? nx0 : nx1;
      this.pendingShapeVertices[output + 1] =
        QUAD_Y_INDEX[vertex] === 0 ? ny0 : ny1;
      this.pendingShapeVertices[output + 2] = red;
      this.pendingShapeVertices[output + 3] = green;
      this.pendingShapeVertices[output + 4] = blue;
      this.pendingShapeVertices[output + 5] = 1;
      output += UNIFIED_SHAPE_STRIDE;
    }
    this.pendingShapeFloatCount = output;
  }

  private flushPendingShapeBatch(): void {
    const state = this.pendingShapeState;
    if (!state || this.pendingShapeFloatCount === 0) return;
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface) {
      this.pendingShapeFloatCount = 0;
      this.pendingShapeState = null;
      return;
    }

    const vertices = this.pendingShapeVertices.subarray(
      0,
      this.pendingShapeFloatCount,
    );
    const vertexData = this.writeBatchVertexData(vertices);
    const vertexCount = this.pendingShapeFloatCount / UNIFIED_SHAPE_STRIDE;
    this.pendingShapeFloatCount = 0;
    this.pendingShapeState = null;
    this.flushingShapeBatch = true;
    try {
      const pass = this.getBatchPass(surface, state);
      pass.setPipeline(this.shapeBatchPipeline);
      pass.setVertexBuffer(
        0,
        vertexData.buffer,
        vertexData.offset,
        vertices.byteLength,
      );
      pass.draw(vertexCount);
      this.performanceCollector?.addGpuCounter('drawCalls');
    } finally {
      this.flushingShapeBatch = false;
    }
  }

  private applyScissorRect(
    pass: GPURenderPassEncoder,
    targetWidth: number,
    targetHeight: number,
    scale: number,
    state: GraphicsRenderState,
  ): void {
    let x = 0;
    let y = 0;
    let width = targetWidth;
    let height = targetHeight;
    if (state.clipWidth > 0 && state.clipHeight > 0) {
      const x1 = Math.min(
        targetWidth,
        Math.max(0, Math.round((state.clipX + state.clipWidth) * scale)),
      );
      const y1 = Math.min(
        targetHeight,
        Math.max(0, Math.round((state.clipY + state.clipHeight) * scale)),
      );
      x = Math.min(targetWidth, Math.max(0, Math.round(state.clipX * scale)));
      y = Math.min(targetHeight, Math.max(0, Math.round(state.clipY * scale)));
      width = Math.max(0, x1 - x);
      height = Math.max(0, y1 - y);
    }

    const previous = this.batchScissorRect;
    if (
      previous &&
      previous[0] === x &&
      previous[1] === y &&
      previous[2] === width &&
      previous[3] === height
    ) {
      return;
    }
    (pass as any).setScissorRect(x, y, width, height);
    this.batchScissorRect = [x, y, width, height];
  }

  drawShapeBatch(
    pipelineKind: number,
    stride: number,
    vertices: Float32Array,
    state: GraphicsRenderState,
  ): void {
    if (!this.hasVisibleClip(state) || vertices.length === 0) return;
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface || stride <= 0 || vertices.length % stride !== 0) return;
    const pipelines = [
      this.colorPipeline,
      this.colorAAPipeline,
      this.lineAAPipeline,
      this.ellipseAAPipeline,
      this.roundRectAAPipeline,
      this.roundRectStrokeAAPipeline,
      this.roundRectDottedStrokeAAPipeline,
      this.arcFillAAPipeline,
      this.arcStrokeAAPipeline,
      this.arcDottedStrokeAAPipeline,
    ];
    const expectedStrides = [6, 9, 14, 12, 16, 18, 18, 19, 20, 20];
    const pipeline = pipelines[pipelineKind];
    if (!pipeline || stride !== expectedStrides[pipelineKind]) return;
    if (UNIFIED_SHAPE_PIPELINES.has(pipelineKind)) {
      this.appendPendingShapeVertices(pipelineKind, stride, vertices, state);
      return;
    }
    const vertexData = this.writeBatchVertexData(vertices);
    const pass = this.getBatchPass(surface, state);
    pass.setPipeline(pipeline);
    pass.setVertexBuffer(
      0,
      vertexData.buffer,
      vertexData.offset,
      vertices.byteLength,
    );
    pass.draw(vertices.length / stride);
    this.performanceCollector?.addGpuCounter('drawCalls');
  }

  fillRect(
    x: number,
    y: number,
    width: number,
    height: number,
    state: GraphicsRenderState,
  ): void {
    if (width <= 0 || height <= 0) return;
    if (!this.hasVisibleClip(state)) return;
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface) return;
    this.appendPendingFillRect(x, y, width, height, state, surface);
  }

  drawRGBA(
    rgbaData: Uint8Array,
    x: number,
    y: number,
    width: number,
    height: number,
    state: GraphicsRenderState,
  ): void {
    if (
      width <= 0 ||
      height <= 0 ||
      rgbaData.byteLength < width * height * 4 ||
      !this.hasVisibleClip(state) ||
      !this.surfaces.has(state.surfaceId)
    )
      return;
    const atlasRegion = this.uploadDynamicRgba(rgbaData, width, height);
    const texture =
      atlasRegion?.page.texture ??
      this.acquireTransientTexture(rgbaData, width, height);
    const u0 = atlasRegion ? atlasRegion.x / DYNAMIC_RGBA_ATLAS_SIZE : 0;
    const v0 = atlasRegion ? atlasRegion.y / DYNAMIC_RGBA_ATLAS_SIZE : 0;
    const u1 = atlasRegion
      ? (atlasRegion.x + width) / DYNAMIC_RGBA_ATLAS_SIZE
      : 1;
    const v1 = atlasRegion
      ? (atlasRegion.y + height) / DYNAMIC_RGBA_ATLAS_SIZE
      : 1;
    this.drawTexture(
      state,
      { texture, width, height, owned: false, premultiplied: false },
      [
        x + state.translateX,
        y + state.translateY,
        x + state.translateX + width,
        y + state.translateY,
        x + state.translateX,
        y + state.translateY + height,
        x + state.translateX,
        y + state.translateY + height,
        x + state.translateX + width,
        y + state.translateY,
        x + state.translateX + width,
        y + state.translateY + height,
      ],
      [u0, v0, u1, v0, u0, v1, u0, v1, u1, v0, u1, v1],
    );
  }

  drawPixels(
    pixels: number[] | Int16Array | Uint8Array,
    transparency: boolean,
    offset: number,
    scanlength: number,
    x: number,
    y: number,
    width: number,
    height: number,
    state: GraphicsRenderState,
    format?: number,
  ): void {
    if (
      width <= 0 ||
      height <= 0 ||
      !this.hasVisibleClip(state) ||
      !this.surfaces.has(state.surfaceId)
    )
      return;
    const rgba = new Uint8Array(width * height * 4);
    for (let row = 0; row < height; row++) {
      for (let col = 0; col < width; col++) {
        const src = offset + row * scanlength + col;
        const dst = (row * width + col) * 4;
        if (pixels instanceof Int16Array) {
          const pixel = pixels[src] & 0xffff;
          if (format === 4444) {
            rgba[dst + 3] = transparency ? ((pixel >> 12) & 0xf) * 17 : 255;
            rgba[dst] = ((pixel >> 8) & 0xf) * 17;
            rgba[dst + 1] = ((pixel >> 4) & 0xf) * 17;
            rgba[dst + 2] = (pixel & 0xf) * 17;
          } else if (format === 1555) {
            rgba[dst + 3] = transparency && ((pixel >> 15) & 1) === 0 ? 0 : 255;
            rgba[dst] = Math.round((((pixel >> 10) & 0x1f) * 255) / 31);
            rgba[dst + 1] = Math.round((((pixel >> 5) & 0x1f) * 255) / 31);
            rgba[dst + 2] = Math.round(((pixel & 0x1f) * 255) / 31);
          } else {
            rgba[dst + 3] = 255;
            rgba[dst] = Math.round((((pixel >> 11) & 0x1f) * 255) / 31);
            rgba[dst + 1] = Math.round((((pixel >> 5) & 0x3f) * 255) / 63);
            rgba[dst + 2] = Math.round(((pixel & 0x1f) * 255) / 31);
          }
        } else if (pixels instanceof Uint8Array) {
          const gray = pixels[src] & 0xff;
          rgba[dst] = gray;
          rgba[dst + 1] = gray;
          rgba[dst + 2] = gray;
          rgba[dst + 3] = 255;
        } else {
          const argb = pixels[src] | 0;
          rgba[dst] = (argb >> 16) & 0xff;
          rgba[dst + 1] = (argb >> 8) & 0xff;
          rgba[dst + 2] = argb & 0xff;
          rgba[dst + 3] = transparency ? (argb >>> 24) & 0xff : 255;
        }
      }
    }
    const texture = this.acquireTransientTexture(rgba, width, height);
    this.drawTexture(
      state,
      { texture, width, height, owned: false },
      [
        x + state.translateX,
        y + state.translateY,
        x + state.translateX + width,
        y + state.translateY,
        x + state.translateX,
        y + state.translateY + height,
        x + state.translateX,
        y + state.translateY + height,
        x + state.translateX + width,
        y + state.translateY,
        x + state.translateX + width,
        y + state.translateY + height,
      ],
      [0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1],
    );
  }

  async getPixels(
    pixels: number[] | Int16Array | Uint8Array,
    offset: number,
    scanlength: number,
    x: number,
    y: number,
    width: number,
    height: number,
    state: GraphicsRenderState,
  ): Promise<void> {
    const readbackStartedAt = this.performanceCollector?.now() ?? 0;
    this.flushBatch();
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface || width <= 0 || height <= 0) return;

    const logicalSrcX = Math.max(0, x + state.translateX);
    const logicalSrcY = Math.max(0, y + state.translateY);
    const readWidth = Math.max(0, Math.min(width, surface.width - logicalSrcX));
    const readHeight = Math.max(
      0,
      Math.min(height, surface.height - logicalSrcY),
    );
    if (readWidth <= 0 || readHeight <= 0) return;

    const bytesPerPixel = 4;
    const unpaddedBytesPerRow = readWidth * bytesPerPixel;
    const bytesPerRow = Math.ceil(unpaddedBytesPerRow / 256) * 256;
    const bufferSize = bytesPerRow * readHeight;
    const buffer = this.device.createBuffer({
      size: bufferSize,
      usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ,
    });
    this.performanceCollector?.addGpuCounter('buffersCreated');
    this.performanceCollector?.addGpuCounter(
      'bufferBytesAllocated',
      bufferSize,
    );
    this.performanceCollector?.addGpuCounter('readbacks');
    this.performanceCollector?.addGpuCounter('readbackBytes', bufferSize);

    const encoder = this.device.createCommandEncoder();
    const readback = this.createReadbackTexture(
      encoder,
      surface,
      logicalSrcX,
      logicalSrcY,
      readWidth,
      readHeight,
    );
    encoder.copyTextureToBuffer(
      { texture: readback.texture },
      { buffer, bytesPerRow, rowsPerImage: readHeight },
      [readWidth, readHeight],
    );
    this.device.queue.submit([encoder.finish()]);
    this.performanceCollector?.addGpuCounter('submits');

    try {
      await buffer.mapAsync(GPUMapMode.READ);
      const data = new Uint8Array(buffer.getMappedRange());
      for (let row = 0; row < readHeight; row++) {
        const rowStart = row * bytesPerRow;
        for (let col = 0; col < readWidth; col++) {
          const src = rowStart + col * bytesPerPixel;
          const dst = offset + row * scanlength + col;
          const a = data[src + 3];
          let r = data[src];
          let g = data[src + 1];
          let b = data[src + 2];
          if (a > 0 && a < 255) {
            r = Math.min(255, Math.round((r * 255) / a));
            g = Math.min(255, Math.round((g * 255) / a));
            b = Math.min(255, Math.round((b * 255) / a));
          }

          if (pixels instanceof Int16Array) {
            const r5 = Math.floor((r * 31) / 255);
            const g6 = Math.floor((g * 63) / 255);
            const b5 = Math.floor((b * 31) / 255);
            pixels[dst] = (r5 << 11) | (g6 << 5) | b5;
          } else if (pixels instanceof Uint8Array) {
            pixels[dst] = Math.floor((r + g + b) / 3);
          } else {
            pixels[dst] = (a << 24) | (r << 16) | (g << 8) | b;
          }
        }
      }
    } finally {
      buffer.unmap();
      buffer.destroy();
      readback.resources?.buffer.destroy();
      readback.resources?.ownedTexture?.destroy();
      readback.resources?.snapshotTexture?.destroy();
      readback.texture.destroy();
      this.performanceCollector?.addTiming('readbackWait', readbackStartedAt);
    }
  }

  importImageToSurface(surfaceId: number, source: CanvasImageSource): void {
    const surface = this.surfaces.get(surfaceId);
    if (!surface) return;
    const width = Math.max(1, (source as any).width ?? surface.width);
    const height = Math.max(1, (source as any).height ?? surface.height);
    const texture = this.createExternalTexture(
      source as HTMLCanvasElement | HTMLImageElement | ImageBitmap,
      width,
      height,
    );
    this.clearSurface(surface, [0, 0, 0, 0]);
    this.drawTexture(
      {
        surfaceId,
        color: 0,
        clipX: 0,
        clipY: 0,
        clipWidth: surface.width,
        clipHeight: surface.height,
        translateX: 0,
        translateY: 0,
        strokeStyle: 0,
        fontHandle: 0,
        fontFace: 0,
        fontStyle: 0,
        fontSize: 0,
      },
      { texture, width, height, owned: true },
      [
        0,
        0,
        surface.width,
        0,
        0,
        surface.height,
        0,
        surface.height,
        surface.width,
        0,
        surface.width,
        surface.height,
      ],
      [0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1],
    );
  }

  drawImageBatch(
    source: HTMLImageElement | HTMLCanvasElement | ImageBitmap,
    draws: Int32Array,
    state: GraphicsRenderState,
  ): void {
    if (draws.length === 0 || !this.hasVisibleClip(state)) return;
    const surface = this.surfaces.get(state.surfaceId);
    const textureSource = this.getTextureSource(source);
    if (!surface || !textureSource) return;

    // A mutable image can be drawn through the same Graphics that owns it.
    // WebGPU forbids sampling a texture while it is also the current render
    // attachment, so keep self-referencing draws on the snapshot path. Process
    // them one at a time because a later draw may depend on an earlier one.
    if (textureSource.surfaceId === surface.id) {
      const { width, height } = textureSource;
      const snapshotTexture = this.getScratchTexture(
        surface.physicalWidth,
        surface.physicalHeight,
      );
      for (let i = 0; i + 2 < draws.length; i += 3) {
        // Close the preceding render pass before copying its result. Keeping
        // the copy and following pass in the same encoder preserves command
        // order without introducing a queue submission per image.
        this.flushPendingShapeBatch();
        this.flushPendingTextureBatch();
        this.endBatchPass();
        const encoder = this.getBatchEncoder();
        encoder.copyTextureToTexture(
          { texture: surface.texture },
          { texture: snapshotTexture },
          [surface.physicalWidth, surface.physicalHeight],
        );

        const x = draws[i];
        const y = draws[i + 1];
        const anchor = draws[i + 2];
        let dx = x;
        let dy = y;
        if (anchor & ANCHOR.RIGHT) dx -= width;
        else if (anchor & ANCHOR.HCENTER) dx -= width / 2;
        if (anchor & ANCHOR.BOTTOM) dy -= height;
        else if (anchor & ANCHOR.VCENTER) dy -= height / 2;
        dx += state.translateX;
        dy += state.translateY;
        this.drawTexture(
          state,
          {
            ...textureSource,
            texture: snapshotTexture,
            surfaceId: undefined,
          },
          [
            dx,
            dy,
            dx + width,
            dy,
            dx,
            dy + height,
            dx,
            dy + height,
            dx + width,
            dy,
            dx + width,
            dy + height,
          ],
          [0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1],
        );
        // Encode the sampling pass before the next iteration overwrites the
        // shared snapshot texture.
        this.flushPendingTextureBatch();
      }
      return;
    }

    if (textureSource.owned)
      this.batchOwnedTextures.push(textureSource.texture);

    const { width, height } = textureSource;
    const vertices = new Float32Array((draws.length / 3) * 6 * 4);
    let output = 0;
    for (let i = 0; i + 2 < draws.length; i += 3) {
      const x = draws[i];
      const y = draws[i + 1];
      const anchor = draws[i + 2];
      let dx = x;
      let dy = y;
      if (anchor & ANCHOR.RIGHT) dx -= width;
      else if (anchor & ANCHOR.HCENTER) dx -= width / 2;
      if (anchor & ANCHOR.BOTTOM) dy -= height;
      else if (anchor & ANCHOR.VCENTER) dy -= height / 2;
      dx += state.translateX;
      dy += state.translateY;
      const positions = [
        dx,
        dy,
        dx + width,
        dy,
        dx,
        dy + height,
        dx,
        dy + height,
        dx + width,
        dy,
        dx + width,
        dy + height,
      ];
      for (let vertex = 0; vertex < 6; vertex++) {
        const [nx, ny] = this.logicalToNdc(
          positions[vertex * 2],
          positions[vertex * 2 + 1],
          surface,
        );
        vertices[output++] = nx;
        vertices[output++] = ny;
        vertices[output++] = QUAD_X_INDEX[vertex];
        vertices[output++] = QUAD_Y_INDEX[vertex];
      }
    }
    this.appendPendingTextureVertices(
      textureSource.texture,
      false,
      textureSource.premultiplied === true,
      state,
      vertices,
    );
  }

  drawRegion(
    source: HTMLImageElement | HTMLCanvasElement | ImageBitmap,
    sx: number,
    sy: number,
    width: number,
    height: number,
    transform: number,
    dx: number,
    dy: number,
    anchor: number,
    state: GraphicsRenderState,
  ): void {
    const textureSource = this.getTextureSource(source);
    if (!textureSource) return;
    const mapped = this.mapRegionQuad(width, height, transform);
    const bounds = this.bounds(mapped);
    let adx = dx;
    let ady = dy;
    if (anchor & ANCHOR.RIGHT) adx = dx - (bounds.maxX - bounds.minX);
    else if (anchor & ANCHOR.HCENTER)
      adx = dx - (bounds.maxX - bounds.minX) / 2;
    if (anchor & ANCHOR.BOTTOM) ady = dy - (bounds.maxY - bounds.minY);
    else if (anchor & ANCHOR.VCENTER)
      ady = dy - (bounds.maxY - bounds.minY) / 2;
    const tx = adx - bounds.minX + state.translateX;
    const ty = ady - bounds.minY + state.translateY;
    const positions = mapped.map(
      (value, index) => value + (index % 2 === 0 ? tx : ty),
    );
    const textureWidth = textureSource.textureWidth ?? textureSource.width;
    const textureHeight = textureSource.textureHeight ?? textureSource.height;
    const sourceScaleX = textureWidth / textureSource.width;
    const sourceScaleY = textureHeight / textureSource.height;
    const u0 = (sx * sourceScaleX) / textureWidth;
    const v0 = (sy * sourceScaleY) / textureHeight;
    const u1 = ((sx + width) * sourceScaleX) / textureWidth;
    const v1 = ((sy + height) * sourceScaleY) / textureHeight;
    this.drawTexture(state, textureSource, positions, [
      u0,
      v0,
      u1,
      v0,
      u0,
      v1,
      u0,
      v1,
      u1,
      v0,
      u1,
      v1,
    ]);
  }

  copyArea(
    x: number,
    y: number,
    width: number,
    height: number,
    dx: number,
    dy: number,
    _anchor: number,
    state: GraphicsRenderState,
  ): void {
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface || width <= 0 || height <= 0) return;
    this.flushPendingShapeBatch();
    this.flushPendingTextureBatch();
    this.endBatchPass();
    const physicalWidth = Math.max(1, Math.round(width * surface.scale));
    const physicalHeight = Math.max(1, Math.round(height * surface.scale));
    const tempTexture = this.getScratchTexture(physicalWidth, physicalHeight);
    const encoder = this.getBatchEncoder();
    (encoder as any).copyTextureToTexture(
      {
        texture: surface.texture,
        origin: {
          x: Math.max(0, Math.round((x + state.translateX) * surface.scale)),
          y: Math.max(0, Math.round((y + state.translateY) * surface.scale)),
        },
      },
      { texture: tempTexture },
      [physicalWidth, physicalHeight],
    );
    // copyArea is not affected by the current clip per MIDP spec
    this.drawTexture(
      {
        ...state,
        translateX: 0,
        translateY: 0,
        clipX: 0,
        clipY: 0,
        clipWidth: surface.width,
        clipHeight: surface.height,
      },
      {
        texture: tempTexture,
        width,
        height,
        textureWidth: physicalWidth,
        textureHeight: physicalHeight,
        owned: false,
        premultiplied: true,
      },
      [
        dx + state.translateX,
        dy + state.translateY,
        dx + state.translateX + width,
        dy + state.translateY,
        dx + state.translateX,
        dy + state.translateY + height,
        dx + state.translateX,
        dy + state.translateY + height,
        dx + state.translateX + width,
        dy + state.translateY,
        dx + state.translateX + width,
        dy + state.translateY + height,
      ],
      [0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1],
    );
    // Encode the sampling pass before this scratch texture can be overwritten
    // by a later copyArea in the same command encoder.
    this.flushPendingTextureBatch();
  }

  drawString(
    text: string,
    x: number,
    y: number,
    anchor: number,
    state: GraphicsRenderState,
  ): void {
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface) return;

    if (this.drawGlyphAtlasText(text, x, y, anchor, state, surface)) {
      return;
    }

    const entry = this.rasterizeTextHighRes(text, state, surface);
    if (!entry) return;

    const fontHeight = this.fontManager.getHeight(
      state.fontFace,
      state.fontStyle,
      state.fontSize,
    );
    const fontBaseline = this.fontManager.getBaselinePosition(
      state.fontFace,
      state.fontStyle,
      state.fontSize,
    );
    let dx = x;
    let baselineY = y;
    if (anchor & ANCHOR.RIGHT) dx = x - entry.logicalWidth;
    else if (anchor & ANCHOR.HCENTER) dx = x - entry.logicalWidth / 2;
    if (anchor & ANCHOR.TOP) baselineY = y + fontBaseline;
    else if (anchor & ANCHOR.BOTTOM)
      baselineY = y - (fontHeight - fontBaseline);
    else if (anchor & ANCHOR.VCENTER)
      baselineY = y + fontBaseline - fontHeight / 2;
    else if (anchor & ANCHOR.BASELINE) baselineY = y;
    else baselineY = y + fontBaseline;
    const dy = baselineY - entry.baseline;

    this.drawTextAlpha(state, surface, entry, dx, dy);
  }

  private drawTexture(
    state: GraphicsRenderState,
    source: TextureSource,
    logicalPositions: number[],
    uvs: number[],
    linear = false,
  ): void {
    if (!this.hasVisibleClip(state)) {
      if (source.owned) source.texture.destroy();
      return;
    }
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface) {
      if (source.owned) source.texture.destroy();
      return;
    }

    if (
      source.surfaceId !== undefined &&
      source.surfaceId !== surface.id &&
      this.batchWrittenSurfaceIds.has(source.surfaceId)
    ) {
      this.flushBatch();
    }

    const isSelfRef = source.texture === surface.texture;
    if (isSelfRef) {
      // Self-referencing: flush current batch, then use independent encoder for snapshot copy + draw
      this.flushBatch();
      const renderTarget = {
        texture: surface.texture,
        width: surface.physicalWidth,
        height: surface.physicalHeight,
        clipScale: surface.scale,
      };
      const vertices = new Float32Array((logicalPositions.length / 2) * 4);
      for (let i = 0, v = 0; i < logicalPositions.length; i += 2, v += 4) {
        const [nx, ny] = this.logicalToNdc(
          logicalPositions[i],
          logicalPositions[i + 1],
          surface,
        );
        vertices[v] = nx;
        vertices[v + 1] = ny;
        vertices[v + 2] = uvs[i];
        vertices[v + 3] = uvs[i + 1];
      }
      const buffer = this.createVertexBuffer(vertices);
      const encoder = this.device.createCommandEncoder();
      const preparedSource = this.prepareTextureSourceForTarget(
        encoder,
        source,
        renderTarget,
      );
      const bindGroup = this.createTextureBindGroup(
        preparedSource.source.texture,
        linear,
      );
      this.encodeTextureDraw(
        encoder,
        renderTarget,
        state,
        bindGroup,
        buffer,
        vertices.length / 4,
        preparedSource.source.premultiplied
          ? this.texturePipeline
          : this.straightTexturePipeline,
      );
      this.device.queue.submit([encoder.finish()]);
      this.performanceCollector?.addGpuCounter('submits');
      buffer.destroy();
      preparedSource.snapshotTexture?.destroy();
      if (source.owned) source.texture.destroy();
      return;
    }

    // Non-self-referencing: use batch pass
    const vertices = new Float32Array((logicalPositions.length / 2) * 4);
    for (let i = 0, v = 0; i < logicalPositions.length; i += 2, v += 4) {
      const [nx, ny] = this.logicalToNdc(
        logicalPositions[i],
        logicalPositions[i + 1],
        surface,
      );
      vertices[v] = nx;
      vertices[v + 1] = ny;
      vertices[v + 2] = uvs[i];
      vertices[v + 3] = uvs[i + 1];
    }
    if (!source.owned) {
      this.appendPendingTextureVertices(
        source.texture,
        linear,
        source.premultiplied === true,
        state,
        vertices,
      );
      return;
    }

    // Transient textures cannot share a bind group, so draw them immediately.
    const vertexData = this.writeBatchVertexData(vertices);
    this.batchOwnedTextures.push(source.texture);

    const bindGroup = this.createTextureBindGroup(source.texture, linear);

    const pass = this.getBatchPass(surface, state);
    pass.setPipeline(
      source.premultiplied
        ? this.texturePipeline
        : this.straightTexturePipeline,
    );
    pass.setBindGroup(0, bindGroup);
    (pass as any).setVertexBuffer(
      0,
      vertexData.buffer,
      vertexData.offset,
      vertices.byteLength,
    );
    pass.draw(vertices.length / 4);
    this.performanceCollector?.addGpuCounter('drawCalls');
  }

  private drawTextureWithEncoder(
    encoder: GPUCommandEncoder,
    state: GraphicsRenderState,
    source: TextureSource,
    logicalPositions: number[],
    uvs: number[],
    linear = false,
    pipeline = this.texturePipeline,
    target?: TextureRenderTarget,
  ): EncodedTextureDraw | null {
    const surface = this.surfaces.get(state.surfaceId);
    if (!surface) {
      if (source.owned) source.texture.destroy();
      return null;
    }
    const renderTarget = target ?? {
      texture: surface.texture,
      width: surface.physicalWidth,
      height: surface.physicalHeight,
      clipScale: surface.scale,
    };
    const vertices = new Float32Array((logicalPositions.length / 2) * 4);
    for (let i = 0, v = 0; i < logicalPositions.length; i += 2, v += 4) {
      const [nx, ny] = target
        ? this.toNdc(logicalPositions[i], logicalPositions[i + 1], renderTarget)
        : this.logicalToNdc(
            logicalPositions[i],
            logicalPositions[i + 1],
            surface,
          );
      vertices[v] = nx;
      vertices[v + 1] = ny;
      vertices[v + 2] = uvs[i];
      vertices[v + 3] = uvs[i + 1];
    }
    const buffer = this.createVertexBuffer(vertices);
    const preparedSource = this.prepareTextureSourceForTarget(
      encoder,
      source,
      renderTarget,
    );
    const drawPipeline =
      pipeline === this.texturePipeline && !preparedSource.source.premultiplied
        ? this.straightTexturePipeline
        : pipeline;
    const bindGroup = this.device.createBindGroup({
      layout: this.textureBindGroupLayout,
      entries: [
        { binding: 0, resource: preparedSource.source.texture.createView() },
        {
          binding: 1,
          resource: linear ? this.linearSampler : this.nearestSampler,
        },
      ],
    });
    this.performanceCollector?.addGpuCounter('bindGroupsCreated');
    this.encodeTextureDraw(
      encoder,
      renderTarget,
      state,
      bindGroup,
      buffer,
      vertices.length / 4,
      drawPipeline,
    );
    return {
      buffer,
      ownedTexture: source.owned ? source.texture : undefined,
      snapshotTexture: preparedSource.snapshotTexture,
    };
  }

  private prepareTextureSourceForTarget(
    encoder: GPUCommandEncoder,
    source: TextureSource,
    target: TextureRenderTarget,
  ): { source: TextureSource; snapshotTexture?: GPUTexture } {
    if (source.texture !== target.texture) {
      return { source };
    }

    const textureWidth = source.textureWidth ?? source.width;
    const textureHeight = source.textureHeight ?? source.height;
    const snapshotTexture = this.device.createTexture({
      size: [Math.max(1, textureWidth), Math.max(1, textureHeight)],
      format: 'rgba8unorm',
      usage: GPUTextureUsage.COPY_DST | GPUTextureUsage.TEXTURE_BINDING,
    });
    encoder.copyTextureToTexture(
      { texture: source.texture },
      { texture: snapshotTexture },
      [Math.max(1, textureWidth), Math.max(1, textureHeight)],
    );
    return {
      source: {
        texture: snapshotTexture,
        width: source.width,
        height: source.height,
        surfaceId: source.surfaceId,
        textureWidth,
        textureHeight,
        owned: false,
        premultiplied: source.premultiplied,
      },
      snapshotTexture,
    };
  }

  private encodeTextureDraw(
    encoder: GPUCommandEncoder,
    target: TextureRenderTarget,
    state: GraphicsRenderState,
    bindGroup: GPUBindGroup,
    buffer: GPUBuffer,
    vertexCount: number,
    pipeline = this.texturePipeline,
  ): void {
    const pass = this.beginTexturePass(encoder, target, state);
    pass.setPipeline(pipeline);
    pass.setBindGroup(0, bindGroup);
    (pass as any).setVertexBuffer(0, buffer);
    pass.draw(vertexCount);
    this.performanceCollector?.addGpuCounter('drawCalls');
    pass.end();
  }

  private beginTexturePass(
    encoder: GPUCommandEncoder,
    target: TextureRenderTarget,
    state: GraphicsRenderState,
  ): GPURenderPassEncoder {
    const pass = encoder.beginRenderPass({
      colorAttachments: [
        {
          view: target.texture.createView(),
          loadOp: 'load',
          storeOp: 'store',
        },
      ],
    } as GPURenderPassDescriptor);
    this.applyScissorRect(
      pass,
      target.width,
      target.height,
      target.clipScale,
      state,
    );
    return pass;
  }

  private clearSurface(
    surface: GpuSurface,
    color: [number, number, number, number],
  ): void {
    this.flushBatch();
    const encoder = this.device.createCommandEncoder();
    const pass = encoder.beginRenderPass({
      colorAttachments: [
        {
          view: surface.texture.createView(),
          clearValue: { r: color[0], g: color[1], b: color[2], a: color[3] },
          loadOp: 'clear',
          storeOp: 'store',
        },
      ],
    });
    pass.end();
    this.device.queue.submit([encoder.finish()]);
  }

  private createReadbackTexture(
    encoder: GPUCommandEncoder,
    surface: GpuSurface,
    x: number,
    y: number,
    width: number,
    height: number,
  ): { texture: GPUTexture; resources: EncodedTextureDraw | null } {
    const texture = this.device.createTexture({
      size: [Math.max(1, width), Math.max(1, height)],
      format: 'rgba8unorm',
      usage:
        GPUTextureUsage.RENDER_ATTACHMENT |
        GPUTextureUsage.TEXTURE_BINDING |
        GPUTextureUsage.COPY_SRC,
    });
    // The readback texture is a temporary render target.  When the source
    // contains translucent pixels (sprite background, save/load snapshots),
    // blending into an undefined fresh texture can perturb alpha/RGB and make
    // restored sprites look too transparent.  Start from transparent black so
    // drawing a premultiplied surface preserves the exact source pixel.
    const clearPass = encoder.beginRenderPass({
      colorAttachments: [
        {
          view: texture.createView(),
          clearValue: { r: 0, g: 0, b: 0, a: 0 },
          loadOp: 'clear',
          storeOp: 'store',
        },
      ],
    } as GPURenderPassDescriptor);
    clearPass.end();
    const state: GraphicsRenderState = {
      surfaceId: surface.id,
      color: 0,
      clipX: 0,
      clipY: 0,
      clipWidth: width,
      clipHeight: height,
      translateX: 0,
      translateY: 0,
      strokeStyle: 0,
      fontHandle: 0,
      fontFace: 0,
      fontStyle: 0,
      fontSize: 0,
    };
    const resources = this.drawTextureWithEncoder(
      encoder,
      state,
      {
        texture: surface.texture,
        width: surface.physicalWidth,
        height: surface.physicalHeight,
        owned: false,
        premultiplied: true,
      },
      [0, 0, width, 0, 0, height, 0, height, width, 0, width, height],
      [
        (x * surface.scale) / surface.physicalWidth,
        (y * surface.scale) / surface.physicalHeight,
        ((x + width) * surface.scale) / surface.physicalWidth,
        (y * surface.scale) / surface.physicalHeight,
        (x * surface.scale) / surface.physicalWidth,
        ((y + height) * surface.scale) / surface.physicalHeight,
        (x * surface.scale) / surface.physicalWidth,
        ((y + height) * surface.scale) / surface.physicalHeight,
        ((x + width) * surface.scale) / surface.physicalWidth,
        (y * surface.scale) / surface.physicalHeight,
        ((x + width) * surface.scale) / surface.physicalWidth,
        ((y + height) * surface.scale) / surface.physicalHeight,
      ],
      true,
      this.texturePipeline,
      {
        texture,
        width,
        height,
        clipScale: 1,
      },
    );
    return { texture, resources };
  }

  private createVertexBuffer(data: Float32Array): GPUBuffer {
    const buffer = (this.device as any).createBuffer({
      size: data.byteLength,
      usage:
        (globalThis as any).GPUBufferUsage.VERTEX |
        (globalThis as any).GPUBufferUsage.COPY_DST,
    });
    this.performanceCollector?.addGpuCounter('buffersCreated');
    this.performanceCollector?.addGpuCounter(
      'bufferBytesAllocated',
      data.byteLength,
    );
    this.device.queue.writeBuffer(buffer, 0, data);
    this.performanceCollector?.addGpuCounter(
      'writeBufferBytes',
      data.byteLength,
    );
    return buffer;
  }

  private createTextureBindGroup(
    texture: GPUTexture,
    linear: boolean,
  ): GPUBindGroup {
    const bindGroup = this.device.createBindGroup({
      layout: this.textureBindGroupLayout,
      entries: [
        { binding: 0, resource: texture.createView() },
        {
          binding: 1,
          resource: linear ? this.linearSampler : this.nearestSampler,
        },
      ],
    });
    this.performanceCollector?.addGpuCounter('bindGroupsCreated');
    return bindGroup;
  }

  private getCachedTextureBindGroup(
    texture: GPUTexture,
    linear: boolean,
  ): GPUBindGroup {
    let cached = this.textureBindGroupCache.get(texture);
    if (!cached) {
      cached = {};
      this.textureBindGroupCache.set(texture, cached);
    }
    const key = linear ? 'linear' : 'nearest';
    let bindGroup = cached[key];
    if (!bindGroup) {
      bindGroup = this.createTextureBindGroup(texture, linear);
      cached[key] = bindGroup;
    }
    return bindGroup;
  }

  private writeBatchVertexData(data: Float32Array | Uint8Array): {
    buffer: GPUBuffer;
    offset: number;
  } {
    const byteLength = data.byteLength;
    while (true) {
      let page = this.batchVertexPages[this.batchVertexPageIndex];
      if (!page) {
        const capacity = Math.max(
          BATCH_VERTEX_PAGE_BYTES,
          2 ** Math.ceil(Math.log2(Math.max(4, byteLength))),
        );
        const buffer = (this.device as any).createBuffer({
          size: capacity,
          usage:
            (globalThis as any).GPUBufferUsage.VERTEX |
            (globalThis as any).GPUBufferUsage.COPY_DST,
        });
        this.performanceCollector?.addGpuCounter('buffersCreated');
        this.performanceCollector?.addGpuCounter(
          'bufferBytesAllocated',
          capacity,
        );
        page = { buffer, capacity, offset: 0 };
        this.batchVertexPages.push(page);
      }

      const offset = (page.offset + 3) & ~3;
      if (offset + byteLength <= page.capacity) {
        this.device.queue.writeBuffer(page.buffer, offset, data);
        this.performanceCollector?.addGpuCounter(
          'writeBufferBytes',
          byteLength,
        );
        page.offset = offset + byteLength;
        return { buffer: page.buffer, offset };
      }
      this.batchVertexPageIndex++;
    }
  }

  private acquireTransientTexture(
    rgba: Uint8Array,
    width: number,
    height: number,
  ): GPUTexture {
    const key = `${width}x${height}`;
    let pool = this.transientTexturePools.get(key);
    if (!pool) {
      pool = { textures: [], index: 0 };
      this.transientTexturePools.set(key, pool);
    }

    let texture = pool.textures[pool.index];
    if (!texture) {
      texture = this.device.createTexture({
        size: [width, height],
        format: 'rgba8unorm',
        usage:
          GPUTextureUsage.TEXTURE_BINDING |
          GPUTextureUsage.COPY_DST |
          GPUTextureUsage.COPY_SRC |
          GPUTextureUsage.RENDER_ATTACHMENT,
      });
      pool.textures.push(texture);
      const textureBytes = width * height * 4;
      this.performanceCollector?.addGpuCounter('texturesCreated');
      this.performanceCollector?.addGpuCounter(
        'textureBytesAllocated',
        textureBytes,
      );
      this.performanceCollector?.addEstimatedGpuBytes(textureBytes);
    }
    pool.index++;

    this.device.queue.writeTexture(
      { texture },
      rgba,
      { bytesPerRow: width * 4, rowsPerImage: height },
      [width, height],
    );
    this.performanceCollector?.addGpuCounter(
      'writeTextureBytes',
      rgba.byteLength,
    );
    return texture;
  }

  private uploadDynamicRgba(
    rgba: Uint8Array,
    width: number,
    height: number,
  ): { page: DynamicRgbaAtlasPage; x: number; y: number } | null {
    const slotWidth = width + DYNAMIC_RGBA_ATLAS_PADDING * 2;
    const slotHeight = height + DYNAMIC_RGBA_ATLAS_PADDING * 2;
    if (
      slotWidth > DYNAMIC_RGBA_ATLAS_SIZE ||
      slotHeight > DYNAMIC_RGBA_ATLAS_SIZE
    ) {
      return null;
    }

    let placement: { page: DynamicRgbaAtlasPage; x: number; y: number } | null =
      null;
    for (const page of this.dynamicRgbaAtlasPages) {
      placement = this.allocateDynamicRgbaRegion(page, slotWidth, slotHeight);
      if (placement) break;
    }
    if (
      !placement &&
      this.dynamicRgbaAtlasPages.length < DYNAMIC_RGBA_ATLAS_MAX_PAGES
    ) {
      const texture = this.device.createTexture({
        size: [DYNAMIC_RGBA_ATLAS_SIZE, DYNAMIC_RGBA_ATLAS_SIZE],
        format: 'rgba8unorm',
        usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST,
      });
      const page = { texture, cursorX: 0, cursorY: 0, rowHeight: 0 };
      this.dynamicRgbaAtlasPages.push(page);
      const bytes = DYNAMIC_RGBA_ATLAS_SIZE * DYNAMIC_RGBA_ATLAS_SIZE * 4;
      this.performanceCollector?.addGpuCounter('texturesCreated');
      this.performanceCollector?.addGpuCounter('textureBytesAllocated', bytes);
      this.performanceCollector?.addEstimatedGpuBytes(bytes);
      placement = this.allocateDynamicRgbaRegion(page, slotWidth, slotHeight);
    }
    if (!placement) return null;

    this.device.queue.writeTexture(
      {
        texture: placement.page.texture,
        origin: { x: placement.x, y: placement.y },
      },
      rgba,
      { bytesPerRow: width * 4, rowsPerImage: height },
      [width, height],
    );
    this.performanceCollector?.addGpuCounter(
      'writeTextureBytes',
      width * height * 4,
    );
    return placement;
  }

  private allocateDynamicRgbaRegion(
    page: DynamicRgbaAtlasPage,
    slotWidth: number,
    slotHeight: number,
  ): { page: DynamicRgbaAtlasPage; x: number; y: number } | null {
    if (page.cursorX + slotWidth > DYNAMIC_RGBA_ATLAS_SIZE) {
      page.cursorX = 0;
      page.cursorY += page.rowHeight;
      page.rowHeight = 0;
    }
    if (page.cursorY + slotHeight > DYNAMIC_RGBA_ATLAS_SIZE) return null;
    const x = page.cursorX + DYNAMIC_RGBA_ATLAS_PADDING;
    const y = page.cursorY + DYNAMIC_RGBA_ATLAS_PADDING;
    page.cursorX += slotWidth;
    page.rowHeight = Math.max(page.rowHeight, slotHeight);
    return { page, x, y };
  }

  private getScratchTexture(width: number, height: number): GPUTexture {
    const key = `${width}x${height}`;
    const cached = this.scratchTextures.get(key);
    if (cached) return cached;
    const texture = this.device.createTexture({
      size: [width, height],
      format: 'rgba8unorm',
      usage:
        GPUTextureUsage.COPY_DST |
        GPUTextureUsage.TEXTURE_BINDING |
        GPUTextureUsage.RENDER_ATTACHMENT,
    });
    const bytes = width * height * 4;
    this.scratchTextures.set(key, texture);
    this.performanceCollector?.addGpuCounter('texturesCreated');
    this.performanceCollector?.addGpuCounter('textureBytesAllocated', bytes);
    this.performanceCollector?.addEstimatedGpuBytes(bytes);
    return texture;
  }

  private createExternalTexture(
    source: HTMLCanvasElement | HTMLImageElement | ImageBitmap,
    width: number,
    height: number,
  ): GPUTexture {
    const texture = this.device.createTexture({
      size: [Math.max(1, width), Math.max(1, height)],
      format: 'rgba8unorm',
      usage:
        GPUTextureUsage.TEXTURE_BINDING |
        GPUTextureUsage.COPY_DST |
        GPUTextureUsage.COPY_SRC |
        GPUTextureUsage.RENDER_ATTACHMENT,
    });
    this.device.queue.copyExternalImageToTexture({ source }, { texture }, [
      Math.max(1, width),
      Math.max(1, height),
    ]);
    return texture;
  }

  private getTextureSource(
    source: HTMLImageElement | HTMLCanvasElement | ImageBitmap,
  ): TextureSource | null {
    if (source instanceof HTMLCanvasElement) {
      const surfaceId = this.handleToSurfaceId.get(source);
      if (surfaceId !== undefined) {
        const surface = this.surfaces.get(surfaceId);
        if (!surface) return null;
        return {
          texture: surface.texture,
          width: surface.width,
          height: surface.height,
          surfaceId,
          textureWidth: surface.physicalWidth,
          textureHeight: surface.physicalHeight,
          owned: false,
          premultiplied: true,
        };
      }
    }

    const width = (source as any).width ?? 0;
    const height = (source as any).height ?? 0;
    if (width <= 0 || height <= 0) return null;
    return {
      texture: this.createExternalTexture(source, width, height),
      width,
      height,
      owned: true,
    };
  }

  private hasVisibleClip(state: GraphicsRenderState): boolean {
    return state.clipWidth > 0 && state.clipHeight > 0;
  }

  private drawGlyphAtlasText(
    text: string,
    x: number,
    y: number,
    anchor: number,
    state: GraphicsRenderState,
    surface: GpuSurface,
  ): boolean {
    const layout = this.glyphAtlas.layout(text, state, surface.scale);
    if (!layout) return false;
    if (layout.glyphs.length === 0) return true;

    let dx = x;
    let baselineY = y;
    if (anchor & ANCHOR.RIGHT) dx = x - layout.logicalWidth;
    else if (anchor & ANCHOR.HCENTER) dx = x - layout.logicalWidth / 2;
    if (anchor & ANCHOR.TOP) baselineY = y + layout.fontBaseline;
    else if (anchor & ANCHOR.BOTTOM)
      baselineY = y - (layout.fontHeight - layout.fontBaseline);
    else if (anchor & ANCHOR.VCENTER)
      baselineY = y + layout.fontBaseline - layout.fontHeight / 2;
    else if (anchor & ANCHOR.BASELINE) baselineY = y;
    else baselineY = y + layout.fontBaseline;
    let runStart = 0;
    while (runStart < layout.glyphs.length) {
      const page = layout.glyphs[runStart].entry.page;
      let runEnd = runStart + 1;
      while (
        runEnd < layout.glyphs.length &&
        layout.glyphs[runEnd].entry.page === page
      ) {
        runEnd++;
      }
      this.drawGlyphRun(
        state,
        surface,
        layout.glyphs.slice(runStart, runEnd),
        page,
        dx,
        baselineY,
      );
      runStart = runEnd;
    }
    return true;
  }

  private drawGlyphRun(
    state: GraphicsRenderState,
    surface: GpuSurface,
    glyphs: PositionedGlyph[],
    page: GlyphAtlasPage,
    originX: number,
    baselineY: number,
  ): void {
    if (glyphs.length === 0) return;
    const vertices = new Uint8Array(glyphs.length * 6 * 20);
    const vertexFloats = new Float32Array(vertices.buffer);
    const red = (state.color >> 16) & 0xff;
    const green = (state.color >> 8) & 0xff;
    const blue = state.color & 0xff;
    const alpha = state.fontStyle & 1 ? Math.round(0.95 * 255) : 255;
    let vertex = 0;
    for (const positioned of glyphs) {
      const entry = positioned.entry;
      const x0 = originX + positioned.x + entry.offsetX + state.translateX;
      const y0 = baselineY - entry.baseline + entry.offsetY + state.translateY;
      const x1 = x0 + entry.logicalWidth;
      const y1 = y0 + entry.logicalHeight;
      const positions = [x0, y0, x1, y0, x0, y1, x0, y1, x1, y0, x1, y1];
      const uvs = [
        entry.u0,
        entry.v0,
        entry.u1,
        entry.v0,
        entry.u0,
        entry.v1,
        entry.u0,
        entry.v1,
        entry.u1,
        entry.v0,
        entry.u1,
        entry.v1,
      ];
      for (let i = 0; i < positions.length; i += 2, vertex++) {
        const [nx, ny] = this.logicalToNdc(
          positions[i],
          positions[i + 1],
          surface,
        );
        const floatOffset = vertex * 5;
        vertexFloats[floatOffset] = nx;
        vertexFloats[floatOffset + 1] = ny;
        vertexFloats[floatOffset + 2] = uvs[i];
        vertexFloats[floatOffset + 3] = uvs[i + 1];
        const colorOffset = vertex * 20 + 16;
        vertices[colorOffset] = red;
        vertices[colorOffset + 1] = green;
        vertices[colorOffset + 2] = blue;
        vertices[colorOffset + 3] = alpha;
      }
    }

    const vertexData = this.writeBatchVertexData(vertices);
    const pass = this.getBatchPass(surface, state);
    pass.setPipeline(this.textAlphaPipeline);
    pass.setBindGroup(0, this.getCachedTextureBindGroup(page.texture, true));
    pass.setVertexBuffer(
      0,
      vertexData.buffer,
      vertexData.offset,
      vertices.byteLength,
    );
    pass.draw(glyphs.length * 6);
    this.performanceCollector?.addGpuCounter('drawCalls');
  }

  private rasterizeTextHighRes(
    text: string,
    state: GraphicsRenderState,
    surface: GpuSurface,
  ): CachedTextEntry | null {
    const scale = surface.scale;
    const cacheKey = `${text}\x00${state.fontFace}\x00${state.fontStyle}\x00${state.fontSize}\x00${scale}`;

    const cached = this.textCache.get(cacheKey);
    if (cached) {
      cached.lastUsedFrame = this.textCacheFrameCounter;
      return cached;
    }
    const rasterStartedAt = this.performanceCollector?.now() ?? 0;

    const cssFont = this.fontManager.getCSSFont(
      state.fontFace,
      state.fontStyle,
      state.fontSize,
    );
    if (!this.textCanvas) {
      this.textCanvas = document.createElement('canvas');
      this.textContext = this.textCanvas.getContext(
        '2d',
        READBACK_CONTEXT_OPTIONS,
      );
      this.textUploadCanvas = document.createElement('canvas');
      this.textUploadContext = this.textUploadCanvas.getContext('2d');
    }
    const ctx = this.textContext;
    const uploadCtx = this.textUploadContext;
    if (!this.textCanvas || !ctx || !this.textUploadCanvas || !uploadCtx)
      return null;

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

    // Super-sampling: bold uses 2x for natural edge softness, others 4x
    const ssScale = state.fontStyle & 1 ? 2 : 4;
    const rasterScale = scale * ssScale;
    const rasterFont = cssFont.replace(
      /(\d+)px/,
      (_, sz) => `${Number(sz) * rasterScale}px`,
    );
    ctx.font = rasterFont;
    const metrics = ctx.measureText(text);
    const logicalTotalHeight = logicalHeight + 4;

    const rasterWidth = Math.max(
      1,
      Math.ceil(
        Math.max(
          metrics.width,
          (metrics.actualBoundingBoxLeft || 0) +
            (metrics.actualBoundingBoxRight || metrics.width),
        ) + 2,
      ),
    );
    const rasterHeight = Math.max(
      1,
      Math.ceil(logicalTotalHeight * rasterScale),
    );

    this.ensureCanvasCapacity(this.textCanvas, rasterWidth, rasterHeight);

    ctx.font = rasterFont;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';
    ctx.clearRect(0, 0, rasterWidth, rasterHeight);
    ctx.fillStyle = 'white';
    ctx.fillText(text, 0, logicalBaseline * rasterScale);
    if (state.fontStyle & 4) {
      const ulY =
        Math.min(logicalTotalHeight - 1, logicalBaseline + 2) * rasterScale;
      ctx.fillRect(0, ulY, rasterWidth, rasterScale);
    }

    // Let Canvas perform the supersampled downscale. This avoids the
    // synchronous getImageData readback and the per-pixel JavaScript loop.
    const physWidth = Math.max(1, Math.ceil(rasterWidth / ssScale));
    const physHeight = Math.max(1, Math.ceil(rasterHeight / ssScale));
    this.ensureCanvasCapacity(this.textUploadCanvas, physWidth, physHeight);
    uploadCtx.clearRect(0, 0, physWidth, physHeight);
    uploadCtx.imageSmoothingEnabled = true;
    uploadCtx.imageSmoothingQuality = 'high';
    uploadCtx.drawImage(
      this.textCanvas,
      0,
      0,
      rasterWidth,
      rasterHeight,
      0,
      0,
      physWidth,
      physHeight,
    );

    const texture = this.createTextureFromCanvas(
      this.textUploadCanvas,
      physWidth,
      physHeight,
    );
    const logicalWidth = physWidth / scale;

    const entry: CachedTextEntry = {
      texture,
      width: physWidth,
      height: physHeight,
      logicalWidth,
      logicalHeight: physHeight / scale,
      baseline: logicalBaseline,
      lastUsedFrame: this.textCacheFrameCounter,
    };

    this.textCache.set(cacheKey, entry);
    this.evictTextCacheIfNeeded();
    this.performanceCollector?.addTiming('textRaster', rasterStartedAt);
    return entry;
  }

  private createTextureFromCanvas(
    canvas: HTMLCanvasElement,
    width: number,
    height: number,
  ): GPUTexture {
    const texture = this.device.createTexture({
      size: [width, height],
      format: 'rgba8unorm',
      usage:
        GPUTextureUsage.TEXTURE_BINDING |
        GPUTextureUsage.COPY_DST |
        GPUTextureUsage.COPY_SRC |
        GPUTextureUsage.RENDER_ATTACHMENT,
    });
    this.device.queue.copyExternalImageToTexture(
      { source: canvas },
      { texture },
      [width, height],
    );
    const bytes = width * height * 4;
    this.performanceCollector?.addGpuCounter('texturesCreated');
    this.performanceCollector?.addGpuCounter('textureBytesAllocated', bytes);
    this.performanceCollector?.addGpuCounter('writeTextureBytes', bytes);
    return texture;
  }

  private ensureCanvasCapacity(
    canvas: HTMLCanvasElement,
    requiredWidth: number,
    requiredHeight: number,
  ): void {
    if (canvas.width >= requiredWidth && canvas.height >= requiredHeight) {
      return;
    }

    const width = Math.max(canvas.width, Math.ceil(requiredWidth / 64) * 64);
    const height = Math.max(canvas.height, Math.ceil(requiredHeight / 64) * 64);
    canvas.width = width;
    canvas.height = height;
  }

  private drawTextAlpha(
    state: GraphicsRenderState,
    surface: GpuSurface,
    entry: CachedTextEntry,
    logicalX: number,
    logicalY: number,
  ): void {
    const x0 = logicalX + state.translateX;
    const y0 = logicalY + state.translateY;
    const x1 = x0 + entry.logicalWidth;
    const y1 = y0 + entry.logicalHeight;

    const positions = [x0, y0, x1, y0, x0, y1, x0, y1, x1, y0, x1, y1];
    const uvs = [0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1];

    const vertices = new Uint8Array(6 * 20);
    const vertexFloats = new Float32Array(vertices.buffer);
    const red = (state.color >> 16) & 0xff;
    const green = (state.color >> 8) & 0xff;
    const blue = state.color & 0xff;
    // Bold text is slightly less opaque to match Canvas 2D native appearance.
    const alpha = state.fontStyle & 1 ? Math.round(0.95 * 255) : 255;
    for (let i = 0, vertex = 0; i < positions.length; i += 2, vertex++) {
      const [nx, ny] = this.logicalToNdc(
        positions[i],
        positions[i + 1],
        surface,
      );
      const floatOffset = vertex * 5;
      vertexFloats[floatOffset] = nx;
      vertexFloats[floatOffset + 1] = ny;
      vertexFloats[floatOffset + 2] = uvs[i];
      vertexFloats[floatOffset + 3] = uvs[i + 1];
      const colorOffset = vertex * 20 + 16;
      vertices[colorOffset] = red;
      vertices[colorOffset + 1] = green;
      vertices[colorOffset + 2] = blue;
      vertices[colorOffset + 3] = alpha;
    }

    const vertexData = this.writeBatchVertexData(vertices);

    const textureBindGroup = this.getCachedTextureBindGroup(
      entry.texture,
      true,
    );

    const pass = this.getBatchPass(surface, state);
    pass.setPipeline(this.textAlphaPipeline);
    pass.setBindGroup(0, textureBindGroup);
    (pass as any).setVertexBuffer(
      0,
      vertexData.buffer,
      vertexData.offset,
      vertices.byteLength,
    );
    pass.draw(6);
    this.performanceCollector?.addGpuCounter('drawCalls');
  }

  private evictTextCacheIfNeeded(): void {
    if (this.textCache.size <= WebGPUGraphicsRenderer.TEXT_CACHE_MAX_ENTRIES)
      return;
    let oldestKey: string | null = null;
    let oldestFrame = Infinity;
    for (const [key, entry] of this.textCache) {
      if (entry.lastUsedFrame < oldestFrame) {
        oldestFrame = entry.lastUsedFrame;
        oldestKey = key;
      }
    }
    if (oldestKey) {
      const entry = this.textCache.get(oldestKey)!;
      entry.texture.destroy();
      this.textCache.delete(oldestKey);
    }
  }

  private mapRegionQuad(
    width: number,
    height: number,
    transform: number,
  ): number[] {
    const corners = [
      [0, 0],
      [width, 0],
      [0, height],
      [0, height],
      [width, 0],
      [width, height],
    ];
    const mapped: number[] = [];
    for (const [x, y] of corners) {
      mapped.push(...this.mapPoint(x, y, width, height, transform));
    }
    return mapped;
  }

  private mapPoint(
    x: number,
    y: number,
    width: number,
    height: number,
    transform: number,
  ): [number, number] {
    void width;
    void height;
    switch (transform) {
      case 1:
        return [x, -y];
      case 2:
        return [-x, y];
      case 3:
        return [-x, -y];
      case 4:
        return [y, x];
      case 5:
        return [-y, x];
      case 6:
        return [y, -x];
      case 7:
        return [-y, -x];
      default:
        return [x, y];
    }
  }

  private bounds(points: number[]) {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (let i = 0; i < points.length; i += 2) {
      minX = Math.min(minX, points[i]);
      minY = Math.min(minY, points[i + 1]);
      maxX = Math.max(maxX, points[i]);
      maxY = Math.max(maxY, points[i + 1]);
    }
    return { minX, minY, maxX, maxY };
  }

  private logicalToNdc(
    x: number,
    y: number,
    surface: GpuSurface,
  ): [number, number] {
    return [(x / surface.width) * 2 - 1, 1 - (y / surface.height) * 2];
  }

  private toNdc(
    x: number,
    y: number,
    surface: Pick<GpuSurface, 'width' | 'height'>,
  ): [number, number] {
    return [(x / surface.width) * 2 - 1, 1 - (y / surface.height) * 2];
  }
}
