import m3gWebgpuWireSchema from '@jsjvm/j2me-core/m3g-wire';
import { resolveSizedWireLayout } from '@jsjvm/j2me-simulator';
import drawPacketFragmentShader from './shaders/m3g-draw-packet.frag.wgsl';
import drawPacketVertexShader from './shaders/m3g-draw-packet.vert.wgsl';
import lightingFragmentShader from './shaders/m3g-lighting.frag.wgsl';
import lightingVertexShader from './shaders/m3g-lighting.vert.wgsl';
import textureFragmentShader from './shaders/m3g-texture.frag.wgsl';
import textureVertexShader from './shaders/m3g-texture.vert.wgsl';

const rawM3gDrawPacket = m3gWebgpuWireSchema.drawPacket;
const M3G_DRAW_PACKET_HEADER = resolveSizedWireLayout(
  rawM3gDrawPacket.header,
  'M3G draw packet header',
);
const M3G_DRAW_PACKET_INSTANCE = resolveSizedWireLayout(
  rawM3gDrawPacket.instance,
  'M3G draw packet instance',
);
const M3G_DRAW_PACKET = {
  name: rawM3gDrawPacket.name,
  commandId: rawM3gDrawPacket.commandId,
  headerBytes: M3G_DRAW_PACKET_HEADER.bytes,
  instanceBytes: M3G_DRAW_PACKET_INSTANCE.bytes,
  uniformBytes: M3G_DRAW_PACKET_HEADER.size.uniform,
  offset: M3G_DRAW_PACKET_HEADER.offset,
};
const rawM3gDerivedIndexBuffer = m3gWebgpuWireSchema.derivedIndexBuffer;
const M3G_DERIVED_INDEX_BUFFER_HEADER = resolveSizedWireLayout(
  rawM3gDerivedIndexBuffer.header,
  'M3G derived index buffer header',
);
const M3G_DERIVED_INDEX_BUFFER = {
  commandId: rawM3gDerivedIndexBuffer.commandId,
  headerBytes: M3G_DERIVED_INDEX_BUFFER_HEADER.bytes,
  offset: M3G_DERIVED_INDEX_BUFFER_HEADER.offset,
};
const rawM3gClear = m3gWebgpuWireSchema.clear;
const M3G_CLEAR_HEADER = resolveSizedWireLayout(
  rawM3gClear.header,
  'M3G clear header',
);
const M3G_CLEAR = {
  commandId: rawM3gClear.commandId,
  headerBytes: M3G_CLEAR_HEADER.bytes,
  offset: M3G_CLEAR_HEADER.offset,
};
const M3G_PIPELINE_KEY = m3gWebgpuWireSchema.pipelineKey;

const CMD_M3G_CLEAR = M3G_CLEAR.commandId;
const CMD_M3G_BIND_TARGET = 135;
const CMD_M3G_RELEASE_TARGET = 136;
const CMD_M3G_BIND_IMAGE_TARGET = 137;
const CMD_M3G_CREATE_VERTEX_ARRAY = 140;
const CMD_M3G_UPDATE_TEXTURE = 147;
const CMD_M3G_DELETE_RESOURCE = 148;
const CMD_M3G_INVALIDATE_IMAGE_TEXTURES = 149;
const CMD_M3G_CREATE_INDEX_BUFFER = 150;
const CMD_M3G_BEGIN_WGPU_BATCH = 164;
const CMD_M3G_END_WGPU_BATCH = 165;
const CMD_M3G_DRAW_PACKET = M3G_DRAW_PACKET.commandId;
const CMD_M3G_CREATE_BACKGROUND = 175;
const CMD_M3G_BG_SET_COLOR = 176;
const CMD_M3G_BG_SET_ENABLE = 178;
const CMD_M3G_CREATE_DERIVED_INDEX_BUFFER = M3G_DERIVED_INDEX_BUFFER.commandId;

const WGPU_DRAW_HAS_VERTEX_COLOR = M3G_PIPELINE_KEY.hasVertexColor;
const WGPU_DRAW_HAS_TEXTURE = M3G_PIPELINE_KEY.hasTexture;
const WGPU_DRAW_HAS_LIGHTING = M3G_PIPELINE_KEY.hasLighting;
const WGPU_DRAW_HAS_FOG = M3G_PIPELINE_KEY.hasFog;
const WGPU_DRAW_HAS_ALPHA_TEST = M3G_PIPELINE_KEY.hasAlphaTest;
const WGPU_DRAW_FLAT_SHADING = M3G_PIPELINE_KEY.flatShading;
const WGPU_DRAW_PERSPECTIVE_CORRECTION = M3G_PIPELINE_KEY.perspectiveCorrection;
const WGPU_DRAW_BLEND_SHIFT = M3G_PIPELINE_KEY.blendShift;
const WGPU_DRAW_BLEND_MASK =
  M3G_PIPELINE_KEY.blendMask << WGPU_DRAW_BLEND_SHIFT;
const WGPU_DRAW_WRITE_MASK_SHIFT = M3G_PIPELINE_KEY.writeMaskShift;
const WGPU_DRAW_COLOR_MASK_RED_OFF = 1 << (WGPU_DRAW_WRITE_MASK_SHIFT + 0);
const WGPU_DRAW_COLOR_MASK_GREEN_OFF = 1 << (WGPU_DRAW_WRITE_MASK_SHIFT + 1);
const WGPU_DRAW_COLOR_MASK_BLUE_OFF = 1 << (WGPU_DRAW_WRITE_MASK_SHIFT + 2);
const WGPU_DRAW_COLOR_MASK_ALPHA_OFF = 1 << (WGPU_DRAW_WRITE_MASK_SHIFT + 3);
const M3G_DRAW_PACKET_FRAME_INDEX = 0x01;
const M3G_DRAW_PACKET_FRAME_POSITION = 0x02;
const M3G_DRAW_PACKET_FRAME_COLOR = 0x04;
const M3G_DRAW_PACKET_FRAME_NORMAL = 0x08;
const M3G_DRAW_PACKET_FRAME_TEXCOORD0 = 0x10;
const M3G_DRAW_PACKET_FRAME_TEXCOORD1 = 0x20;
const WGPU_DRAW_CULL_FACE = M3G_PIPELINE_KEY.cullFace;
const WGPU_DRAW_CULL_FRONT = M3G_PIPELINE_KEY.cullFront;
const WGPU_DRAW_FRONT_CW = M3G_PIPELINE_KEY.frontCW;
const WGPU_DRAW_DEPTH_TEST_ALWAYS = M3G_PIPELINE_KEY.depthTestAlways;
const WGPU_DRAW_DEPTH_WRITE_OFF = M3G_PIPELINE_KEY.depthWriteOff;
const WGPU_TOPOLOGY_TRIANGLE_STRIP = 1;
const M3G_RESOURCE_VERTEX_ARRAY = 1;
const M3G_RESOURCE_GL_TEXTURE = 2;
const M3G_RESOURCE_IMAGE_TARGET = 3;
const M3G_RESOURCE_INDEX_BUFFER = 4;

const GL_REPEAT = 0x2901;
const GL_NEAREST = 0x2600;
const GL_LINEAR = 0x2601;
const GL_NEAREST_MIPMAP_NEAREST = 0x2700;
const GL_NEAREST_MIPMAP_LINEAR = 0x2702;
const GL_LINEAR_MIPMAP_LINEAR = 0x2703;
const RAW_UNIFORM_SIZE = 1376;
const RAW_LIGHTS_OFFSET = 480;
const MAX_WGPU_DRAWS_PER_BATCH = 1024;
const MAX_WGPU_BATCHES_IN_FLIGHT = 2;

const STANDARD_ALPHA_BLEND: GPUBlendState = {
  color: {
    srcFactor: 'src-alpha' as GPUBlendFactor,
    dstFactor: 'one-minus-src-alpha' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
  alpha: {
    srcFactor: 'one' as GPUBlendFactor,
    dstFactor: 'one-minus-src-alpha' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
};

const ALPHA_ADD_BLEND: GPUBlendState = {
  color: {
    srcFactor: 'src-alpha' as GPUBlendFactor,
    dstFactor: 'one' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
  alpha: {
    srcFactor: 'one' as GPUBlendFactor,
    dstFactor: 'one' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
};

const MODULATE_BLEND: GPUBlendState = {
  color: {
    srcFactor: 'zero' as GPUBlendFactor,
    dstFactor: 'src' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
  alpha: {
    srcFactor: 'zero' as GPUBlendFactor,
    dstFactor: 'one' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
};

const MODULATE_X2_BLEND: GPUBlendState = {
  color: {
    srcFactor: 'dst' as GPUBlendFactor,
    dstFactor: 'src' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
  alpha: {
    srcFactor: 'dst' as GPUBlendFactor,
    dstFactor: 'src' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
};

const ZERO_ONE_MINUS_SRC_BLEND: GPUBlendState = {
  color: {
    srcFactor: 'zero' as GPUBlendFactor,
    dstFactor: 'one-minus-src' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
  alpha: {
    srcFactor: 'zero' as GPUBlendFactor,
    dstFactor: 'one-minus-src' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
};

const ADDITIVE_BLEND: GPUBlendState = {
  color: {
    srcFactor: 'one' as GPUBlendFactor,
    dstFactor: 'one' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
  alpha: {
    srcFactor: 'one' as GPUBlendFactor,
    dstFactor: 'one' as GPUBlendFactor,
    operation: 'add' as GPUBlendOperation,
  },
};

const WRITE_MASK_ALL = 0xf;

function colorWriteMaskForPipelineKey(pipelineKey: number): GPUColorWriteFlags {
  let mask = WRITE_MASK_ALL;
  if ((pipelineKey & WGPU_DRAW_COLOR_MASK_RED_OFF) !== 0) {
    mask &= ~0x1;
  }
  if ((pipelineKey & WGPU_DRAW_COLOR_MASK_GREEN_OFF) !== 0) {
    mask &= ~0x2;
  }
  if ((pipelineKey & WGPU_DRAW_COLOR_MASK_BLUE_OFF) !== 0) {
    mask &= ~0x4;
  }
  if ((pipelineKey & WGPU_DRAW_COLOR_MASK_ALPHA_OFF) !== 0) {
    mask &= ~0x8;
  }
  return mask as GPUColorWriteFlags;
}

interface VertexArrayEntry {
  version: number;
  numVertices: number;
  gpuBufferVec4: GPUBuffer;
  gpuBufferNormalizedVec4?: GPUBuffer;
}

interface IndexBufferEntry {
  version: number;
  gpuIndexBuffer?: GPUBuffer;
  indexCount: number;
}

interface GlTextureEntry {
  texture: GPUTexture;
  view: GPUTextureView;
  width: number;
  height: number;
  version: number;
  sourceImageHandle: number;
}

interface BackgroundEntry {
  color: number; // ARGB
  colorClearEnabled: boolean;
  depthClearEnabled: boolean;
}

interface RenderTargetEntry {
  texture: GPUTexture;
  view: GPUTextureView;
  cacheName: number;
  version: number;
  scale: number;
  logicalHeight: number;
}

interface DrawPacketFrameBlob {
  vertexCount: number;
  indices: Uint32Array;
  positions: Float32Array;
  colors?: Float32Array;
  normals?: Float32Array;
  texcoord0?: Float32Array;
  texcoord1?: Float32Array;
}

interface DynamicGpuBuffer {
  buffer: GPUBuffer;
  size: number;
  offset: number;
}

interface WgpuDrawBatch {
  encoder: GPUCommandEncoder;
  pass: GPURenderPassEncoder;
  resources: GPUBuffer[];
  vertexArena: DynamicGpuBuffer | null;
  indexArena: DynamicGpuBuffer | null;
  uniformArena: DynamicGpuBuffer | null;
  texturedUniformArena: DynamicGpuBuffer | null;
  uniformBindGroups: Map<GPUBuffer, GPUBindGroup>;
  texturedUniformBindGroups: Map<GPUBuffer, GPUBindGroup>;
  instanceArena: DynamicGpuBuffer | null;
  drawCount: number;
  sourceDrawCount: number;
  transientBytes: number;
}

interface FogState {
  enabled: boolean;
  mode: number;
  density: number;
  start: number;
  end: number;
  color: Float32Array;
}

interface PolygonOffsetState {
  enabled: boolean;
  factor: number;
  units: number;
}

interface LightState {
  enabled: boolean;
  spotExponent: number;
  spotCutoff: number;
  constantAttenuation: number;
  ambient: Float32Array;
  diffuse: Float32Array;
  specular: Float32Array;
  position: Float32Array;
  spotDirection: Float32Array;
  linearAttenuation: number;
  quadraticAttenuation: number;
}

interface LightingState {
  enabled: boolean;
  normalize: boolean;
  colorMaterial: boolean;
  twoSided: boolean;
  lightModelAmbient: Float32Array;
  materialAmbient: Float32Array;
  materialDiffuse: Float32Array;
  materialEmission: Float32Array;
  materialSpecular: Float32Array;
  materialShininess: number;
  lights: LightState[];
}

function createDefaultLightingState(): LightingState {
  const lights: LightState[] = [];
  for (let i = 0; i < 8; i++) {
    lights.push({
      enabled: false,
      spotExponent: 0,
      spotCutoff: 180,
      constantAttenuation: 1,
      ambient: new Float32Array([0, 0, 0, 1]),
      diffuse: new Float32Array([0, 0, 0, 1]),
      specular: new Float32Array([0, 0, 0, 1]),
      position: new Float32Array([0, 0, 1, 0]),
      spotDirection: new Float32Array([0, 0, -1, 0]),
      linearAttenuation: 0,
      quadraticAttenuation: 0,
    });
  }
  return {
    enabled: false,
    normalize: false,
    colorMaterial: false,
    twoSided: false,
    lightModelAmbient: new Float32Array([0, 0, 0, 1]),
    materialAmbient: new Float32Array([0.2, 0.2, 0.2, 1]),
    materialDiffuse: new Float32Array([0.8, 0.8, 0.8, 1]),
    materialEmission: new Float32Array([0, 0, 0, 1]),
    materialSpecular: new Float32Array([0, 0, 0, 1]),
    materialShininess: 0,
    lights,
  };
}

const SHADER_CODE = `${lightingVertexShader}\n${lightingFragmentShader}`;

/** Flat-shading variant: color uses @interpolate(flat) so all fragments in a
 * triangle receive the provoking (last) vertex's color. */
const FLAT_COLOR = '@location(0) @interpolate(flat) color: vec4<f32>,';
const flatColorShader = (vertex: string, fragment: string): string =>
  `${vertex.replace('@location(0) color: vec4<f32>,', FLAT_COLOR)}\n${fragment.replace('@location(0) color: vec4<f32>,', FLAT_COLOR)}`;

const SHADER_CODE_FLAT = flatColorShader(
  lightingVertexShader,
  lightingFragmentShader,
);

const DRAW_PACKET_SHADER_CODE = `${drawPacketVertexShader}\n${drawPacketFragmentShader}`;

const DRAW_PACKET_SHADER_CODE_FLAT = flatColorShader(
  drawPacketVertexShader,
  drawPacketFragmentShader,
);

const TEXTURED_SHADER_CODE = `${textureVertexShader}\n${textureFragmentShader}`;

const TEXTURED_SHADER_CODE_FLAT = flatColorShader(
  textureVertexShader,
  textureFragmentShader,
);

export class M3gRenderer {
  private device: GPUDevice;
  private context: GPUCanvasContext;
  private clearPipeline: GPURenderPipeline | null = null;
  private rawPipeline: GPURenderPipeline | null = null;
  private rawTexturePipeline: GPURenderPipeline | null = null;
  private rawPipelineBlend: GPURenderPipeline | null = null;
  private rawTexturePipelineBlend: GPURenderPipeline | null = null;
  private drawPacketPipelines = new Map<string, GPURenderPipeline>();
  /** Explicit uniform bind group layout shared by all raw (non-textured) pipelines. */
  private rawUniformBindGroupLayout: GPUBindGroupLayout | null = null;
  /** Explicit uniform bind group layout shared by all raw textured pipelines (binding 0 of group 0). */
  private rawTextureUniformBindGroupLayout: GPUBindGroupLayout | null = null;
  /**
   * Cache of raw draw pipelines keyed by (textured, blend, cullMode,
   * frontFace). Populated lazily by `getRawPipeline`. Keeps the four
   * eagerly-initialised pipelines above for bindGroupLayout / uniform buffer
   * reuse.
   */
  private rawPipelineVariants = new Map<string, GPURenderPipeline>();
  private rawTextureBindGroupLayout: GPUBindGroupLayout | null = null;
  private depthTexture: GPUTexture | null = null;
  private depthTextureView: GPUTextureView | null = null;
  private retiredStableResources: Array<GPUBuffer | GPUTexture> = [];
  private wgpuDrawBatch: WgpuDrawBatch | null = null;
  private wgpuDrawBatchOpen = false;
  private batchProfileInFlight = 0;
  private lastWgpuBatchCompletion: Promise<void> | null = null;
  private lastGpuCompletion: Promise<void> | null = null;

  private vertexArrays = new Map<number, VertexArrayEntry>();
  private indexBuffers = new Map<number, IndexBufferEntry>();
  private derivedIndexBuffersBySource = new Map<string, Set<number>>();
  private derivedIndexBufferSources = new Map<number, string>();
  private backgrounds = new Map<number, BackgroundEntry>();
  private glTextures = new Map<number, GlTextureEntry>();
  private imageToGlTextures = new Map<number, Set<number>>();
  private textureSamplers = new Map<string, GPUSampler>();
  private textureBindGroups = new Map<string, GPUBindGroup>();
  private fallbackTextureView: GPUTextureView | null = null;
  private fallbackTexture: GPUTexture | null = null;
  private readonly identityMat4 = new Float32Array([
    1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1,
  ]);

  private clearColor = { r: 0, g: 0, b: 0, a: 1 };
  private needsClear = false;
  private needsDepthClear = false;
  private pendingClearScissor: {
    x: number;
    y: number;
    w: number;
    h: number;
  } | null = null;
  private pendingClearViewport: {
    x: number;
    y: number;
    w: number;
    h: number;
  } | null = null;
  private format: GPUTextureFormat;

  private renderTarget: RenderTargetEntry | null = null;
  private imageTargets = new Map<number, RenderTargetEntry>();
  private surfaceTextureResolver:
    | ((surfaceId: number) => GPUTexture | null)
    | null = null;
  private surfaceScale = 1;

  constructor(device: GPUDevice, context: GPUCanvasContext) {
    this.device = device;
    this.context = context;
    this.format = 'rgba8unorm';
    const diagnosticsDevice = this.device as GPUDevice & {
      addEventListener?: (
        type: 'uncapturederror',
        listener: (event: GPUUncapturedErrorEvent) => void,
      ) => void;
      lost?: Promise<GPUDeviceLostInfo>;
    };
    diagnosticsDevice.addEventListener?.('uncapturederror', (event) => {
      console.error('[M3G batch] WebGPU validation error:', event.error);
    });
    void diagnosticsDevice.lost?.then((info) => {
      this.clearPipelineCaches();
      console.error(
        `[M3G batch] device lost reason=${info.reason} message=${info.message}`,
      );
    });
  }

  setSurfaceTextureResolver(
    resolver: (surfaceId: number) => GPUTexture | null,
    scale = 1,
  ): void {
    this.surfaceTextureResolver = resolver;
    this.surfaceScale = Math.max(1, scale);
  }

  /** Flush all pending M3G draws. Call after 2D present. */
  flushFrame(): void {
    this.flushWgpuDrawBatch();
  }

  hasPendingFrame(): boolean {
    return this.wgpuDrawBatch !== null;
  }

  waitForGpu(): Promise<void> | null {
    if (this.batchProfileInFlight < MAX_WGPU_BATCHES_IN_FLIGHT) {
      return null;
    }
    return this.lastWgpuBatchCompletion;
  }

  handleCommand(cmdId: number, view: DataView, offset: number): number {
    switch (cmdId) {
      case CMD_M3G_BIND_TARGET:
        return this.handleBindTarget(view, offset);
      case CMD_M3G_BIND_IMAGE_TARGET:
        return this.handleBindImageTarget(view, offset);
      case CMD_M3G_RELEASE_TARGET:
        return this.handleReleaseTarget(view, offset);
      case CMD_M3G_CLEAR:
        return this.handleClear(view, offset);
      case CMD_M3G_CREATE_VERTEX_ARRAY:
        return this.handleCreateVertexArray(view, offset);
      case CMD_M3G_UPDATE_TEXTURE:
        return this.handleUpdateTexture(view, offset);
      case CMD_M3G_DELETE_RESOURCE:
        return this.handleDeleteResource(view, offset);
      case CMD_M3G_INVALIDATE_IMAGE_TEXTURES:
        return this.handleInvalidateImageTextures(view, offset);
      case CMD_M3G_CREATE_INDEX_BUFFER:
        return this.handleCreateIndexBuffer(view, offset);
      case CMD_M3G_CREATE_DERIVED_INDEX_BUFFER:
        return this.handleCreateDerivedIndexBuffer(view, offset);
      case CMD_M3G_BEGIN_WGPU_BATCH:
        return this.handleBeginWgpuBatch(offset);
      case CMD_M3G_END_WGPU_BATCH:
        return this.handleEndWgpuBatch(offset);
      case CMD_M3G_DRAW_PACKET:
        return this.handleDrawPacket(view, offset);
      case CMD_M3G_CREATE_BACKGROUND:
        return this.handleCreateBackground(view, offset);
      case CMD_M3G_BG_SET_COLOR:
        return this.handleBgSetColor(view, offset);
      case CMD_M3G_BG_SET_ENABLE:
        return this.handleBgSetEnable(view, offset);
      default:
        console.warn('[M3gRenderer] unknown cmdId:', cmdId);
        return offset;
    }
  }

  private handleBindTarget(view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    const surfaceId = view.getUint32(offset, true);
    if (this.surfaceTextureResolver) {
      const texture = this.surfaceTextureResolver(surfaceId);
      this.renderTarget = texture
        ? {
            texture,
            view: texture.createView(),
            cacheName: 0,
            version: 0,
            scale: this.surfaceScale,
            logicalHeight: texture.height / this.surfaceScale,
          }
        : null;
    }
    return offset + 4;
  }

  private handleBindImageTarget(view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    const imageHandle = view.getUint32(offset, true);
    const width = view.getUint32(offset + 4, true);
    const height = view.getUint32(offset + 8, true);
    const scale = this.surfaceScale;
    const targetWidth = Math.max(1, Math.round(width * scale));
    const targetHeight = Math.max(1, Math.round(height * scale));
    let target = this.imageTargets.get(imageHandle);
    const recreated =
      !target ||
      target.texture.width !== targetWidth ||
      target.texture.height !== targetHeight;
    if (recreated) {
      if (target) {
        this.retireStableResource(target.texture);
      }
      this.invalidateTextureBindGroups();
      const texture = this.device.createTexture({
        size: { width: targetWidth, height: targetHeight },
        format: 'rgba8unorm',
        usage:
          GPUTextureUsage.RENDER_ATTACHMENT |
          GPUTextureUsage.TEXTURE_BINDING |
          GPUTextureUsage.COPY_SRC,
      });
      target = {
        texture,
        view: texture.createView(),
        cacheName: imageHandle,
        version: (target?.version ?? 0) + 1,
        scale,
        logicalHeight: height,
      };
      this.imageTargets.set(imageHandle, target);
    }
    if (!target) {
      throw new Error('M3G image target creation failed');
    }
    this.renderTarget = target;
    return offset + 12;
  }

  private handleReleaseTarget(_view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    this.renderTarget = null;
    return offset;
  }

  async readImageTargetPixels(
    imageHandle: number,
    width: number,
    height: number,
  ): Promise<Uint8Array> {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    const target = this.imageTargets.get(imageHandle);
    if (!target || width <= 0 || height <= 0) {
      return new Uint8Array();
    }
    const bytesPerRow = Math.ceil((width * 4) / 256) * 256;
    const buffer = this.device.createBuffer({
      size: bytesPerRow * height,
      usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ,
    });
    const encoder = this.device.createCommandEncoder();
    encoder.copyTextureToBuffer(
      { texture: target.texture },
      { buffer, bytesPerRow, rowsPerImage: height },
      { width, height, depthOrArrayLayers: 1 },
    );
    this.device.queue.submit([encoder.finish()]);
    await buffer.mapAsync(GPUMapMode.READ);
    const source = new Uint8Array(buffer.getMappedRange());
    const pixels = new Uint8Array(width * height * 4);
    for (let row = 0; row < height; row++) {
      pixels.set(
        source.subarray(row * bytesPerRow, row * bytesPerRow + width * 4),
        row * width * 4,
      );
    }
    buffer.unmap();
    buffer.destroy();
    return pixels;
  }

  private handleClear(view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    const schema = M3G_CLEAR;
    if (offset + schema.headerBytes > view.byteLength) {
      console.warn('[M3G clear] truncated command');
      return view.byteLength;
    }
    const commandStart = offset;
    view.getUint32(commandStart + schema.offset.contextHandle, true); // ctx handle (unused)
    const bgHandle = view.getUint32(
      commandStart + schema.offset.backgroundHandle,
      true,
    );
    const scX = view.getInt32(commandStart + schema.offset.scissorX, true);
    const scY = view.getInt32(commandStart + schema.offset.scissorY, true);
    const scW = view.getInt32(commandStart + schema.offset.scissorWidth, true);
    const scH = view.getInt32(commandStart + schema.offset.scissorHeight, true);
    const vpX = view.getInt32(commandStart + schema.offset.viewportX, true);
    const vpY = view.getInt32(commandStart + schema.offset.viewportY, true);
    const vpW = view.getInt32(commandStart + schema.offset.viewportWidth, true);
    const vpH = view.getInt32(
      commandStart + schema.offset.viewportHeight,
      true,
    );
    this.pendingClearScissor = { x: scX, y: scY, w: scW, h: scH };
    this.pendingClearViewport = { x: vpX, y: vpY, w: vpW, h: vpH };
    const bg =
      bgHandle === 0
        ? {
            color: 0x00000000,
            colorClearEnabled: true,
            depthClearEnabled: true,
          }
        : this.backgrounds.get(bgHandle);
    if (bg) {
      if (bg.colorClearEnabled) {
        const argb = bg.color;
        this.clearColor = {
          r: ((argb >> 16) & 0xff) / 255.0,
          g: ((argb >> 8) & 0xff) / 255.0,
          b: (argb & 0xff) / 255.0,
          a: ((argb >> 24) & 0xff) / 255.0,
        };
        this.needsClear = true;
      }
      if (bg.depthClearEnabled) {
        this.needsDepthClear = true;
      }
    }
    return commandStart + schema.headerBytes;
  }

  private handleBeginWgpuBatch(offset: number): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    this.wgpuDrawBatchOpen = true;
    return offset;
  }

  private handleEndWgpuBatch(offset: number): number {
    this.flushWgpuDrawBatch();
    this.wgpuDrawBatchOpen = false;
    return offset;
  }

  private readDrawPacketFrameBlob(
    view: DataView,
    offset: number,
    byteLength: number,
    sourceFlags: number,
    _vertexStart: number,
    vertexCount: number,
    indexCount: number,
  ): DrawPacketFrameBlob | null {
    const knownFlags =
      M3G_DRAW_PACKET_FRAME_INDEX |
      M3G_DRAW_PACKET_FRAME_POSITION |
      M3G_DRAW_PACKET_FRAME_COLOR |
      M3G_DRAW_PACKET_FRAME_NORMAL |
      M3G_DRAW_PACKET_FRAME_TEXCOORD0 |
      M3G_DRAW_PACKET_FRAME_TEXCOORD1;
    if (
      sourceFlags === 0 ||
      (sourceFlags & ~knownFlags) !== 0 ||
      (sourceFlags &
        (M3G_DRAW_PACKET_FRAME_INDEX | M3G_DRAW_PACKET_FRAME_POSITION)) !==
        (M3G_DRAW_PACKET_FRAME_INDEX | M3G_DRAW_PACKET_FRAME_POSITION)
    ) {
      return null;
    }

    const end = offset + byteLength;
    const readFloatArray = (length: number): Float32Array | null => {
      const bytes = length * 4;
      if (offset + bytes > end) return null;
      const output = new Float32Array(length);
      for (let index = 0; index < length; index++) {
        output[index] = view.getFloat32(offset + index * 4, true);
      }
      offset += bytes;
      return output;
    };
    const readUintArray = (length: number): Uint32Array | null => {
      const bytes = length * 4;
      if (offset + bytes > end) return null;
      const output = new Uint32Array(length);
      for (let index = 0; index < length; index++) {
        output[index] = view.getUint32(offset + index * 4, true);
      }
      offset += bytes;
      return output;
    };

    const indices = readUintArray(indexCount);
    const positions = readFloatArray(vertexCount * 4);
    if (!indices || !positions) return null;
    const colors =
      sourceFlags & M3G_DRAW_PACKET_FRAME_COLOR
        ? (readFloatArray(vertexCount * 4) ?? undefined)
        : undefined;
    const normals =
      sourceFlags & M3G_DRAW_PACKET_FRAME_NORMAL
        ? (readFloatArray(vertexCount * 4) ?? undefined)
        : undefined;
    const texcoord0 =
      sourceFlags & M3G_DRAW_PACKET_FRAME_TEXCOORD0
        ? (readFloatArray(vertexCount * 4) ?? undefined)
        : undefined;
    const texcoord1 =
      sourceFlags & M3G_DRAW_PACKET_FRAME_TEXCOORD1
        ? (readFloatArray(vertexCount * 4) ?? undefined)
        : undefined;
    if (
      (sourceFlags & M3G_DRAW_PACKET_FRAME_COLOR && !colors) ||
      (sourceFlags & M3G_DRAW_PACKET_FRAME_NORMAL && !normals) ||
      (sourceFlags & M3G_DRAW_PACKET_FRAME_TEXCOORD0 && !texcoord0) ||
      (sourceFlags & M3G_DRAW_PACKET_FRAME_TEXCOORD1 && !texcoord1) ||
      offset !== end
    ) {
      return null;
    }

    return {
      vertexCount,
      indices,
      positions,
      colors,
      normals,
      texcoord0,
      texcoord1,
    };
  }

  private handleDrawPacket(view: DataView, offset: number): number {
    const schema = M3G_DRAW_PACKET;
    const packetStart = offset;
    if (view.byteLength - packetStart < schema.headerBytes) {
      console.warn('[M3G draw packet] truncated header');
      return view.byteLength;
    }
    const topology = view.getUint32(packetStart + schema.offset.topology, true);
    const pipelineKey = view.getUint32(
      packetStart + schema.offset.pipelineKey,
      true,
    );
    const positionHandle = view.getUint32(
      packetStart + schema.offset.positionHandle,
      true,
    );
    const positionVersion = view.getUint32(
      packetStart + schema.offset.positionVersion,
      true,
    );
    const colorHandle = view.getUint32(
      packetStart + schema.offset.colorHandle,
      true,
    );
    const colorVersion = view.getUint32(
      packetStart + schema.offset.colorVersion,
      true,
    );
    const normalHandle = view.getUint32(
      packetStart + schema.offset.normalHandle,
      true,
    );
    const normalVersion = view.getUint32(
      packetStart + schema.offset.normalVersion,
      true,
    );
    const texcoord0Handle = view.getUint32(
      packetStart + schema.offset.texcoord0Handle,
      true,
    );
    const texcoord0Version = view.getUint32(
      packetStart + schema.offset.texcoord0Version,
      true,
    );
    const texcoord1Handle = view.getUint32(
      packetStart + schema.offset.texcoord1Handle,
      true,
    );
    const texcoord1Version = view.getUint32(
      packetStart + schema.offset.texcoord1Version,
      true,
    );
    const indexHandle = view.getUint32(
      packetStart + schema.offset.indexHandle,
      true,
    );
    const indexVersion = view.getUint32(
      packetStart + schema.offset.indexVersion,
      true,
    );
    const texture0Handle = view.getUint32(
      packetStart + schema.offset.texture0Handle,
      true,
    );
    const texture0Version = view.getUint32(
      packetStart + schema.offset.texture0Version,
      true,
    );
    const texture0SourceImageHandle = view.getUint32(
      packetStart + schema.offset.texture0SourceImageHandle,
      true,
    );
    const texture1Handle = view.getUint32(
      packetStart + schema.offset.texture1Handle,
      true,
    );
    const texture1Version = view.getUint32(
      packetStart + schema.offset.texture1Version,
      true,
    );
    const texture1SourceImageHandle = view.getUint32(
      packetStart + schema.offset.texture1SourceImageHandle,
      true,
    );
    const indexCount = view.getUint32(
      packetStart + schema.offset.indexCount,
      true,
    );
    const instanceCount = view.getUint32(
      packetStart + schema.offset.instanceCount,
      true,
    );
    const frameVertexStart = view.getUint32(
      packetStart + schema.offset.frameVertexStart,
      true,
    );
    const frameVertexCount = view.getUint32(
      packetStart + schema.offset.frameVertexCount,
      true,
    );
    const frameSourceFlags = view.getUint32(
      packetStart + schema.offset.frameSourceFlags,
      true,
    );
    const frameBytes = view.getUint32(
      packetStart + schema.offset.frameBytes,
      true,
    );
    const texture0WrapS = view.getUint32(
      packetStart + schema.offset.texture0WrapS,
      true,
    );
    const texture0WrapT = view.getUint32(
      packetStart + schema.offset.texture0WrapT,
      true,
    );
    const texture0MinFilter = view.getUint32(
      packetStart + schema.offset.texture0MinFilter,
      true,
    );
    const texture0MagFilter = view.getUint32(
      packetStart + schema.offset.texture0MagFilter,
      true,
    );
    const texture1WrapS = view.getUint32(
      packetStart + schema.offset.texture1WrapS,
      true,
    );
    const texture1WrapT = view.getUint32(
      packetStart + schema.offset.texture1WrapT,
      true,
    );
    const texture1MinFilter = view.getUint32(
      packetStart + schema.offset.texture1MinFilter,
      true,
    );
    const texture1MagFilter = view.getUint32(
      packetStart + schema.offset.texture1MagFilter,
      true,
    );
    const polygonOffsetFactor = view.getFloat32(
      packetStart + schema.offset.polygonOffsetFactor,
      true,
    );
    const polygonOffsetUnits = view.getFloat32(
      packetStart + schema.offset.polygonOffsetUnits,
      true,
    );
    const packetBytes =
      schema.headerBytes + frameBytes + instanceCount * schema.instanceBytes;
    const glViewport = {
      x: view.getInt32(packetStart + schema.offset.viewportX, true),
      y: view.getInt32(packetStart + schema.offset.viewportY, true),
      w: view.getInt32(packetStart + schema.offset.viewportWidth, true),
      h: view.getInt32(packetStart + schema.offset.viewportHeight, true),
    };
    const glScissor = {
      x: view.getInt32(packetStart + schema.offset.scissorX, true),
      y: view.getInt32(packetStart + schema.offset.scissorY, true),
      w: view.getInt32(packetStart + schema.offset.scissorWidth, true),
      h: view.getInt32(packetStart + schema.offset.scissorHeight, true),
    };
    if (
      indexCount === 0 ||
      instanceCount === 0 ||
      packetBytes > view.byteLength - packetStart ||
      (frameBytes === 0 && frameSourceFlags !== 0) ||
      (frameBytes !== 0 && frameVertexCount === 0) ||
      glViewport.w <= 0 ||
      glViewport.h <= 0 ||
      glScissor.w <= 0 ||
      glScissor.h <= 0
    ) {
      console.warn('[M3G draw packet] invalid draw range or viewport');
      return Math.min(view.byteLength, packetStart + schema.headerBytes);
    }
    const frame =
      frameBytes === 0
        ? null
        : this.readDrawPacketFrameBlob(
            view,
            packetStart + schema.headerBytes,
            frameBytes,
            frameSourceFlags,
            frameVertexStart,
            frameVertexCount,
            indexCount,
          );
    if (frameBytes !== 0 && !frame) {
      console.warn('[M3G draw packet] invalid frame blob');
      return packetStart + packetBytes;
    }
    const position = this.vertexArrays.get(positionHandle);
    const color = this.vertexArrays.get(colorHandle);
    const normal = this.vertexArrays.get(normalHandle);
    const texcoord0 = this.vertexArrays.get(texcoord0Handle);
    const texcoord1 = this.vertexArrays.get(texcoord1Handle);
    const index = this.indexBuffers.get(indexHandle);
    const colorBuffer = color?.gpuBufferNormalizedVec4 ?? color?.gpuBufferVec4;
    const hasVertexColor = (pipelineKey & WGPU_DRAW_HAS_VERTEX_COLOR) !== 0;
    const hasTexture = (pipelineKey & WGPU_DRAW_HAS_TEXTURE) !== 0;
    const imageTarget = hasTexture
      ? this.imageTargets.get(texture0SourceImageHandle)
      : undefined;
    const texture0 = hasTexture
      ? this.glTextures.get(texture0Handle)
      : undefined;
    const hasTexture1 = texture1Handle !== 0;
    const imageTarget1 =
      hasTexture && texture1Handle !== 0
        ? this.imageTargets.get(texture1SourceImageHandle)
        : undefined;
    const texture1 =
      hasTexture && texture1Handle !== 0
        ? this.glTextures.get(texture1Handle)
        : undefined;
    if (hasTexture && texture0) {
      this.setTextureImageSource(texture0Handle, texture0SourceImageHandle);
    }
    if (hasTexture1 && texture1) {
      this.setTextureImageSource(texture1Handle, texture1SourceImageHandle);
    }
    const hasLighting = (pipelineKey & WGPU_DRAW_HAS_LIGHTING) !== 0;
    const hasFog = (pipelineKey & WGPU_DRAW_HAS_FOG) !== 0;
    const hasAlphaTest = (pipelineKey & WGPU_DRAW_HAS_ALPHA_TEST) !== 0;
    const stableResourceMismatch =
      !frame &&
      (!position?.gpuBufferVec4 ||
        !index?.gpuIndexBuffer ||
        position.version !== positionVersion ||
        index.version !== indexVersion ||
        indexCount > index.indexCount ||
        (colorHandle !== 0 &&
          (!colorBuffer || color?.version !== colorVersion)) ||
        (colorHandle === 0 && colorVersion !== 0) ||
        (hasLighting && (!normal || normal.version !== normalVersion)) ||
        (!hasLighting && (normalHandle !== 0 || normalVersion !== 0)) ||
        (hasTexture &&
          (!texcoord0?.gpuBufferVec4 ||
            texcoord0.version !== texcoord0Version)) ||
        (hasTexture1 &&
          (!texcoord1?.gpuBufferVec4 ||
            texcoord1.version !== texcoord1Version)) ||
        (!hasTexture &&
          (texcoord0Handle !== 0 ||
            texture0Handle !== 0 ||
            texture0Version !== 0 ||
            texcoord1Handle !== 0 ||
            texture1Handle !== 0 ||
            texture1Version !== 0)) ||
        (!hasTexture1 &&
          (texcoord1Handle !== 0 ||
            texcoord1Version !== 0 ||
            texture1Version !== 0)));
    const frameMismatch =
      !!frame &&
      (!frame.positions ||
        !frame.indices ||
        ((pipelineKey & WGPU_DRAW_HAS_VERTEX_COLOR) !== 0 && !frame.colors) ||
        (hasLighting && !frame.normals) ||
        (hasTexture && !frame.texcoord0) ||
        (hasTexture1 && !frame.texcoord1));
    if (
      stableResourceMismatch ||
      frameMismatch ||
      (hasTexture && (!texture0 || texture0.version !== texture0Version)) ||
      (hasTexture1 && (!texture1 || texture1.version !== texture1Version))
    ) {
      console.warn(
        `[M3G draw packet] resource mismatch pos=${positionHandle}@${positionVersion} color=${colorHandle}@${colorVersion} normal=${normalHandle}@${normalVersion} uv0=${texcoord0Handle}@${texcoord0Version} texture0=${texture0Handle}@${texture0Version} image0=${texture0SourceImageHandle} uv1=${texcoord1Handle}@${texcoord1Version} texture1=${texture1Handle}@${texture1Version} image1=${texture1SourceImageHandle} index=${indexHandle}@${indexVersion} count=${indexCount}`,
      );
      return packetStart + packetBytes;
    }
    this.ensureDepthTexture();
    this.flushPendingClear();
    const batch = this.currentWgpuDrawBatch();
    if (!batch) {
      console.warn('[M3G draw packet] command outside WebGPU batch');
      return packetStart + packetBytes;
    }
    const drawVertexCount = frame?.vertexCount ?? position!.numVertices;
    const defaultColor = new Float32Array(4);
    for (let component = 0; component < 4; component++) {
      defaultColor[component] = view.getFloat32(
        packetStart + schema.offset.defaultColor + component * 4,
        true,
      );
    }
    const useFixedFunctionPipeline =
      hasTexture || hasLighting || hasFog || hasAlphaTest;
    const primitiveTopology =
      topology === WGPU_TOPOLOGY_TRIANGLE_STRIP
        ? 'triangle-strip'
        : 'triangle-list';
    const blendMode =
      (pipelineKey & WGPU_DRAW_BLEND_MASK) >> WGPU_DRAW_BLEND_SHIFT;
    const writeMask = colorWriteMaskForPipelineKey(pipelineKey);
    const useRawLightingPipeline = hasLighting && !hasTexture;
    const depthBias = this.depthBiasFor(
      polygonOffsetFactor === 0 && polygonOffsetUnits === 0
        ? undefined
        : {
            enabled: true,
            factor: polygonOffsetFactor,
            units: polygonOffsetUnits,
          },
    );
    const useTexturedPipeline =
      useFixedFunctionPipeline && !useRawLightingPipeline;
    const pipeline = useRawLightingPipeline
      ? this.getRawPipeline({
          textured: false,
          blend: blendMode === 1,
          cullFace: (pipelineKey & WGPU_DRAW_CULL_FACE) !== 0,
          cullFront: (pipelineKey & WGPU_DRAW_CULL_FRONT) !== 0,
          frontCW: (pipelineKey & WGPU_DRAW_FRONT_CW) !== 0,
          depthTestAlways: (pipelineKey & WGPU_DRAW_DEPTH_TEST_ALWAYS) !== 0,
          depthWriteOff: (pipelineKey & WGPU_DRAW_DEPTH_WRITE_OFF) !== 0,
          blendMode,
          writeMask,
          alphaTest: hasAlphaTest,
          flatShading: (pipelineKey & WGPU_DRAW_FLAT_SHADING) !== 0,
          depthBias,
          primitiveTopology,
          colorFormat: 'float32x4',
          positionFormat: 'float32x4',
          constantColor: !hasVertexColor,
        })
      : this.getDrawPacketPipeline(
          primitiveTopology,
          pipelineKey,
          polygonOffsetFactor,
          polygonOffsetUnits,
        );
    const scale = this.renderTarget?.scale ?? this.surfaceScale;
    const vpX = glViewport.x * scale;
    const vpY = (this.canvasHeight() - (glViewport.y + glViewport.h)) * scale;
    const vpW = glViewport.w * scale;
    const vpH = glViewport.h * scale;
    const scX = glScissor.x * scale;
    const scY = (this.canvasHeight() - (glScissor.y + glScissor.h)) * scale;
    const scW = glScissor.w * scale;
    const scH = glScissor.h * scale;
    const instanceBytes = new Uint8Array(
      view.buffer,
      view.byteOffset + packetStart + schema.headerBytes + frameBytes,
      instanceCount * schema.instanceBytes,
    );
    if (useTexturedPipeline) {
      this.ensureRawTexturePipeline();
    }
    if (useRawLightingPipeline) {
      this.ensureRawPipeline();
    }
    const instanceAllocation = useFixedFunctionPipeline
      ? null
      : this.allocateBatchBuffer(
          batch,
          'instance',
          instanceBytes,
          GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
        );
    const uniformBytes = new Uint8Array(
      view.buffer,
      view.byteOffset + packetStart + schema.offset.uniform,
      schema.uniformBytes,
    );
    const uniformAllocation = useFixedFunctionPipeline
      ? this.allocateBatchBuffer(
          batch,
          useRawLightingPipeline ? 'uniform' : 'textured-uniform',
          uniformBytes,
          GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
        )
      : null;
    let uniformBindGroup: GPUBindGroup | null = null;
    if (uniformAllocation) {
      const uniformBindGroups = useRawLightingPipeline
        ? batch.uniformBindGroups
        : batch.texturedUniformBindGroups;
      uniformBindGroup =
        uniformBindGroups.get(uniformAllocation.buffer) ?? null;
      if (!uniformBindGroup) {
        uniformBindGroup = this.device.createBindGroup({
          layout: pipeline.getBindGroupLayout(0),
          entries: [
            {
              binding: 0,
              resource: {
                buffer: uniformAllocation.buffer,
                size: schema.uniformBytes,
              },
            },
          ],
        });
        uniformBindGroups.set(uniformAllocation.buffer, uniformBindGroup);
      }
    }
    const textureView =
      imageTarget?.view ?? texture0?.view ?? this.getFallbackTextureView();
    const samplerKey = this.textureSamplerKey(
      texture0WrapS,
      texture0WrapT,
      texture0MinFilter,
      texture0MagFilter,
    );
    const sampler = this.getTextureSampler(
      samplerKey,
      texture0WrapS,
      texture0WrapT,
      texture0MinFilter,
      texture0MagFilter,
    );
    const textureCacheName = imageTarget?.cacheName ?? texture0Handle;
    const textureCacheKind = imageTarget ? 'img' : 'gl';
    const textureResourceVersion = imageTarget?.version ?? texture0Version;
    const textureView1 =
      imageTarget1?.view ?? texture1?.view ?? this.getFallbackTextureView();
    const samplerKey1 = this.textureSamplerKey(
      texture1WrapS,
      texture1WrapT,
      texture1MinFilter,
      texture1MagFilter,
    );
    const sampler1 = this.getTextureSampler(
      samplerKey1,
      texture1WrapS,
      texture1WrapT,
      texture1MinFilter,
      texture1MagFilter,
    );
    const textureCacheName1 = imageTarget1?.cacheName ?? texture1Handle;
    const textureCacheKind1 = imageTarget1 ? 'img' : 'gl';
    const textureResourceVersion1 = imageTarget1?.version ?? texture1Version;
    const textureBindGroup = useTexturedPipeline
      ? this.getTextureBindGroup(
          textureCacheName,
          textureCacheKind,
          textureResourceVersion,
          samplerKey,
          sampler,
          textureView,
          hasTexture1 ? textureCacheName1 : textureCacheName,
          hasTexture1 ? textureCacheKind1 : 'fallback',
          hasTexture1 ? textureResourceVersion1 : 0,
          hasTexture1 ? samplerKey1 : samplerKey,
          hasTexture1 ? sampler1 : sampler,
          hasTexture1 ? textureView1 : this.getFallbackTextureView(),
        )
      : null;

    batch.pass.setPipeline(pipeline);
    if (uniformBindGroup && uniformAllocation) {
      batch.pass.setBindGroup(0, uniformBindGroup, [uniformAllocation.offset]);
    }
    if (useTexturedPipeline) {
      batch.pass.setBindGroup(1, textureBindGroup!);
    }
    batch.pass.setViewport(vpX, vpY, vpW, vpH, 0, 1);
    batch.pass.setScissorRect(
      Math.max(0, Math.round(scX)),
      Math.max(0, Math.round(scY)),
      Math.max(0, Math.round(scW)),
      Math.max(0, Math.round(scH)),
    );
    const allocateFrameVertex = (data: Float32Array) =>
      this.allocateBatchBuffer(
        batch,
        'vertex',
        new Uint8Array(
          data.buffer as ArrayBuffer,
          data.byteOffset,
          data.byteLength,
        ),
        GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
      );
    const framePosition = frame ? allocateFrameVertex(frame.positions) : null;
    batch.pass.setVertexBuffer(
      0,
      framePosition?.buffer ?? position!.gpuBufferVec4,
      framePosition?.offset,
    );
    const frameColor = frame?.colors ? allocateFrameVertex(frame.colors) : null;
    if (frameColor) {
      batch.pass.setVertexBuffer(1, frameColor.buffer, frameColor.offset);
    } else if (pipelineKey & WGPU_DRAW_HAS_VERTEX_COLOR) {
      batch.pass.setVertexBuffer(1, colorBuffer!);
    } else {
      const colorAllocation = this.allocateBatchBuffer(
        batch,
        'vertex',
        new Uint8Array(
          defaultColor.buffer as ArrayBuffer,
          defaultColor.byteOffset,
          defaultColor.byteLength,
        ),
        GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
      );
      batch.pass.setVertexBuffer(
        1,
        colorAllocation.buffer,
        colorAllocation.offset,
      );
    }
    if (useTexturedPipeline) {
      const texcoordAllocation = frame?.texcoord0
        ? allocateFrameVertex(frame.texcoord0)
        : hasTexture
          ? null
          : this.allocateBatchBuffer(
              batch,
              'vertex',
              new Uint8Array(new Float32Array(drawVertexCount * 4).buffer),
              GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
            );
      batch.pass.setVertexBuffer(
        2,
        texcoordAllocation?.buffer ?? texcoord0!.gpuBufferVec4!,
        texcoordAllocation?.offset,
      );
      const texcoord1Allocation = frame?.texcoord1
        ? allocateFrameVertex(frame.texcoord1)
        : null;
      batch.pass.setVertexBuffer(
        3,
        texcoord1Allocation
          ? texcoord1Allocation.buffer
          : hasTexture1
            ? texcoord1!.gpuBufferVec4!
            : (texcoordAllocation?.buffer ?? texcoord0!.gpuBufferVec4!),
        texcoord1Allocation
          ? texcoord1Allocation.offset
          : texcoordAllocation?.offset,
      );
      if (frame?.normals) {
        const frameNormal = allocateFrameVertex(frame.normals);
        batch.pass.setVertexBuffer(4, frameNormal.buffer, frameNormal.offset);
      } else if (hasLighting) {
        batch.pass.setVertexBuffer(4, normal!.gpuBufferVec4);
      } else {
        const normalAllocation = this.allocateBatchBuffer(
          batch,
          'vertex',
          new Uint8Array(new Float32Array([0, 0, 0, 0]).buffer as ArrayBuffer),
          GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
        );
        batch.pass.setVertexBuffer(
          4,
          normalAllocation.buffer,
          normalAllocation.offset,
        );
      }
    } else if (useRawLightingPipeline) {
      if (frame?.normals) {
        const frameNormal = allocateFrameVertex(frame.normals);
        batch.pass.setVertexBuffer(2, frameNormal.buffer, frameNormal.offset);
      } else {
        batch.pass.setVertexBuffer(2, normal!.gpuBufferVec4);
      }
    } else {
      batch.pass.setVertexBuffer(
        2,
        instanceAllocation!.buffer,
        instanceAllocation!.offset,
      );
    }
    const frameIndex = frame
      ? this.allocateBatchBuffer(
          batch,
          'index',
          new Uint8Array(
            frame.indices.buffer as ArrayBuffer,
            frame.indices.byteOffset,
            frame.indices.byteLength,
          ),
          GPUBufferUsage.INDEX | GPUBufferUsage.COPY_DST,
        )
      : null;
    batch.pass.setIndexBuffer(
      frameIndex?.buffer ?? index!.gpuIndexBuffer!,
      'uint32',
      frameIndex?.offset,
    );
    batch.pass.drawIndexed(
      indexCount,
      useFixedFunctionPipeline ? 1 : instanceCount,
    );
    batch.drawCount++;
    batch.sourceDrawCount += useFixedFunctionPipeline ? 1 : instanceCount;
    if (batch.drawCount >= MAX_WGPU_DRAWS_PER_BATCH) {
      this.flushWgpuDrawBatch();
    }
    return packetStart + packetBytes;
  }

  private flushWgpuDrawBatch(): void {
    const batch = this.wgpuDrawBatch;
    if (!batch) {
      return;
    }
    batch.pass.end();
    this.device.queue.submit([batch.encoder.finish()]);
    this.markRenderTargetWritten();
    this.wgpuDrawBatch = null;
    const resources = batch.resources;
    this.batchProfileInFlight++;
    const completion = this.recordGpuSubmission();
    this.lastWgpuBatchCompletion = completion;
    void completion.then(() => {
      for (const resource of resources) {
        resource.destroy();
      }
      this.batchProfileInFlight--;
      if (this.lastWgpuBatchCompletion === completion) {
        this.lastWgpuBatchCompletion = null;
      }
    });
  }

  private recordGpuSubmission(): Promise<void> {
    const completion = this.device.queue.onSubmittedWorkDone();
    this.lastGpuCompletion = completion;
    void completion.then(() => {
      if (this.lastGpuCompletion === completion) {
        this.lastGpuCompletion = null;
      }
    });
    return completion;
  }

  private beginWgpuDrawBatch(): WgpuDrawBatch {
    const encoder = this.device.createCommandEncoder();
    const pass = encoder.beginRenderPass({
      colorAttachments: [
        {
          view: this.getRenderTargetView(),
          loadOp: 'load' as GPULoadOp,
          storeOp: 'store' as GPUStoreOp,
        },
      ],
      depthStencilAttachment: {
        view: this.depthTextureView!,
        depthClearValue: 1.0,
        depthLoadOp: 'load' as GPULoadOp,
        depthStoreOp: 'store' as GPUStoreOp,
      },
    });
    const batch: WgpuDrawBatch = {
      encoder,
      pass,
      resources: [],
      vertexArena: null,
      indexArena: null,
      uniformArena: null,
      texturedUniformArena: null,
      uniformBindGroups: new Map(),
      texturedUniformBindGroups: new Map(),
      instanceArena: null,
      drawCount: 0,
      sourceDrawCount: 0,
      transientBytes: 0,
    };
    this.wgpuDrawBatch = batch;
    return batch;
  }

  private currentWgpuDrawBatch(): WgpuDrawBatch | null {
    if (!this.wgpuDrawBatchOpen) {
      return null;
    }
    return this.wgpuDrawBatch ?? this.beginWgpuDrawBatch();
  }

  private handleCreateVertexArray(view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    const headerBytes = 24;
    if (offset + headerBytes > view.byteLength) {
      console.warn('[M3G vertex array] truncated header');
      return view.byteLength;
    }
    const handle = view.getUint32(offset, true);
    const version = view.getUint32(offset + 4, true);
    const numVertices = view.getUint32(offset + 8, true);
    const flags = view.getUint32(offset + 12, true);
    const vectorBytes = view.getUint32(offset + 16, true);
    const colorBytes = view.getUint32(offset + 20, true);
    offset += headerBytes;
    const commandEnd = offset + vectorBytes + colorBytes;
    if (
      vectorBytes !== numVertices * 16 ||
      colorBytes !== ((flags & 1) !== 0 ? numVertices * 16 : 0) ||
      commandEnd > view.byteLength
    ) {
      console.warn(
        `[M3G vertex array] invalid GPU-ready payload handle=${handle}`,
      );
      return Math.min(view.byteLength, commandEnd);
    }
    this.releaseDerivedIndexBuffers(M3G_RESOURCE_VERTEX_ARRAY, handle);
    const previous = this.vertexArrays.get(handle);
    if (previous) {
      this.retireVertexArrayBuffers(previous);
    }

    const gpuBufferPositionVec4 = this.device.createBuffer({
      size: Math.max(vectorBytes, 4),
      usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
      mappedAtCreation: true,
    });
    new Uint8Array(gpuBufferPositionVec4.getMappedRange()).set(
      new Uint8Array(view.buffer, view.byteOffset + offset, vectorBytes),
    );
    gpuBufferPositionVec4.unmap();
    let gpuBufferNormalizedVec4: GPUBuffer | undefined;
    if (colorBytes > 0) {
      gpuBufferNormalizedVec4 = this.device.createBuffer({
        size: colorBytes,
        usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
        mappedAtCreation: true,
      });
      new Uint8Array(gpuBufferNormalizedVec4.getMappedRange()).set(
        new Uint8Array(
          view.buffer,
          view.byteOffset + offset + vectorBytes,
          colorBytes,
        ),
      );
      gpuBufferNormalizedVec4.unmap();
    }

    this.vertexArrays.set(handle, {
      version,
      numVertices,
      gpuBufferVec4: gpuBufferPositionVec4,
      gpuBufferNormalizedVec4,
    });

    return commandEnd;
  }

  private handleUpdateTexture(view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    const textureName = view.getUint32(offset, true);
    const imageHandle = view.getUint32(offset + 4, true);
    const uploadVersion = view.getUint32(offset + 8, true);
    const width = view.getUint32(offset + 12, true);
    const height = view.getUint32(offset + 16, true);
    const pixelBytes = view.getUint32(offset + 20, true);
    offset += 24;

    const pixels = new Uint8Array(new ArrayBuffer(pixelBytes));
    pixels.set(
      new Uint8Array(view.buffer, view.byteOffset + offset, pixelBytes),
    );
    offset += pixelBytes;

    let entry = this.glTextures.get(textureName);
    if (
      entry &&
      entry.version === uploadVersion &&
      entry.width === width &&
      entry.height === height &&
      entry.sourceImageHandle === imageHandle
    ) {
      return offset;
    }
    if (!entry || entry.width !== width || entry.height !== height) {
      if (entry) {
        this.unlinkTextureFromImage(textureName, entry.sourceImageHandle);
        this.retireStableResource(entry.texture);
      }
      const mipLevelCount = this.textureMipLevelCount(width, height);
      const gpuTexture = this.device.createTexture({
        size: { width: Math.max(1, width), height: Math.max(1, height) },
        mipLevelCount,
        format: 'rgba8unorm',
        usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST,
      });
      entry = {
        texture: gpuTexture,
        view: gpuTexture.createView(),
        width,
        height,
        version: uploadVersion,
        sourceImageHandle: imageHandle,
      };
      this.glTextures.set(textureName, entry);
    } else {
      entry.version = uploadVersion;
    }
    this.invalidateTextureBindGroups();
    this.setTextureImageSource(textureName, imageHandle);
    let mipPixels = pixels;
    let mipWidth = width;
    let mipHeight = height;
    const mipLevelCount = this.textureMipLevelCount(width, height);
    for (let mipLevel = 0; mipLevel < mipLevelCount; mipLevel++) {
      this.device.queue.writeTexture(
        { texture: entry.texture, mipLevel },
        mipPixels,
        { bytesPerRow: mipWidth * 4, rowsPerImage: mipHeight },
        { width: mipWidth, height: mipHeight },
      );
      if (mipLevel + 1 < mipLevelCount) {
        mipPixels = this.downsampleRgba8(mipPixels, mipWidth, mipHeight);
        mipWidth = Math.max(1, mipWidth >> 1);
        mipHeight = Math.max(1, mipHeight >> 1);
      }
    }
    this.recordGpuSubmission();
    return offset;
  }

  private handleDeleteResource(view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    const kind = view.getUint32(offset, true);
    const handle = view.getUint32(offset + 4, true);
    this.releaseDerivedIndexBuffers(kind, handle);
    if (kind === M3G_RESOURCE_VERTEX_ARRAY) {
      const va = this.vertexArrays.get(handle);
      if (va) {
        this.retireVertexArrayBuffers(va);
        this.vertexArrays.delete(handle);
      }
    } else if (kind === M3G_RESOURCE_GL_TEXTURE) {
      const entry = this.glTextures.get(handle);
      if (entry) {
        this.unlinkTextureFromImage(handle, entry.sourceImageHandle);
        this.retireStableResource(entry.texture);
        this.glTextures.delete(handle);
        this.invalidateTextureBindGroups();
      }
    } else if (kind === M3G_RESOURCE_IMAGE_TARGET) {
      const target = this.imageTargets.get(handle);
      if (target) {
        this.retireStableResource(target.texture);
        this.imageTargets.delete(handle);
        if (this.renderTarget === target) {
          this.renderTarget = null;
        }
      }
      this.invalidateImageTextures(handle);
    } else if (kind === M3G_RESOURCE_INDEX_BUFFER) {
      const indexBuffer = this.indexBuffers.get(handle);
      if (indexBuffer?.gpuIndexBuffer) {
        this.retireStableResource(indexBuffer.gpuIndexBuffer);
      }
      this.indexBuffers.delete(handle);
      this.unregisterDerivedIndexBuffer(handle);
    }
    return offset + 8;
  }

  private handleInvalidateImageTextures(
    view: DataView,
    offset: number,
  ): number {
    this.flushWgpuDrawBatch();
    this.flushPendingClear();
    const imageHandle = view.getUint32(offset, true);
    this.invalidateImageTextures(imageHandle);
    return offset + 4;
  }

  private invalidateImageTextures(imageHandle: number): void {
    const textureNames = this.imageToGlTextures.get(imageHandle);
    let invalidated = false;
    if (textureNames) {
      for (const textureName of textureNames) {
        const entry = this.glTextures.get(textureName);
        if (entry) {
          this.retireStableResource(entry.texture);
          this.glTextures.delete(textureName);
          invalidated = true;
        }
      }
      this.imageToGlTextures.delete(imageHandle);
    }
    const target = this.imageTargets.get(imageHandle);
    if (target) {
      this.retireStableResource(target.texture);
      this.imageTargets.delete(imageHandle);
      if (this.renderTarget === target) {
        this.renderTarget = null;
      }
      invalidated = true;
    }
    if (invalidated) {
      this.invalidateTextureBindGroups();
    }
  }

  private invalidateTextureBindGroups(): void {
    this.textureBindGroups.clear();
  }

  private linkTextureToImage(textureName: number, imageHandle: number): void {
    if (imageHandle === 0) {
      return;
    }
    let textures = this.imageToGlTextures.get(imageHandle);
    if (!textures) {
      textures = new Set<number>();
      this.imageToGlTextures.set(imageHandle, textures);
    }
    textures.add(textureName);
  }

  private setTextureImageSource(
    textureName: number,
    imageHandle: number,
  ): void {
    const entry = this.glTextures.get(textureName);
    if (!entry) {
      return;
    }
    if (entry.sourceImageHandle === imageHandle) {
      this.linkTextureToImage(textureName, imageHandle);
      return;
    }
    this.unlinkTextureFromImage(textureName, entry.sourceImageHandle);
    entry.sourceImageHandle = imageHandle;
    this.linkTextureToImage(textureName, imageHandle);
  }

  private unlinkTextureFromImage(
    textureName: number,
    imageHandle: number,
  ): void {
    if (imageHandle === 0) {
      return;
    }
    const textures = this.imageToGlTextures.get(imageHandle);
    if (!textures) {
      return;
    }
    textures.delete(textureName);
    if (textures.size === 0) {
      this.imageToGlTextures.delete(imageHandle);
    }
  }

  private markRenderTargetWritten(): void {
    if (this.renderTarget?.cacheName) {
      this.renderTarget.version++;
      this.invalidateTextureBindGroups();
    }
  }

  private handleCreateIndexBuffer(view: DataView, offset: number): number {
    this.flushWgpuDrawBatch();
    if (offset + 12 > view.byteLength) return view.byteLength;
    const handle = view.getUint32(offset, true);
    const version = view.getUint32(offset + 4, true);
    const indexCount = view.getUint32(offset + 8, true);
    offset += 12;
    const byteLength = indexCount * 4;
    if (offset + byteLength > view.byteLength) return view.byteLength;
    this.releaseDerivedIndexBuffers(M3G_RESOURCE_INDEX_BUFFER, handle);
    const previous = this.indexBuffers.get(handle);
    if (previous?.gpuIndexBuffer) {
      this.retireStableResource(previous.gpuIndexBuffer);
    }
    const gpuIndexBuffer = this.device.createBuffer({
      size: Math.max(byteLength, 4),
      usage: GPUBufferUsage.INDEX | GPUBufferUsage.COPY_DST,
      mappedAtCreation: true,
    });
    new Uint8Array(gpuIndexBuffer.getMappedRange()).set(
      new Uint8Array(view.buffer, view.byteOffset + offset, byteLength),
    );
    gpuIndexBuffer.unmap();

    this.indexBuffers.set(handle, {
      version,
      gpuIndexBuffer,
      indexCount,
    });

    return offset + byteLength;
  }

  private handleCreateDerivedIndexBuffer(
    view: DataView,
    offset: number,
  ): number {
    this.flushWgpuDrawBatch();
    const schema = M3G_DERIVED_INDEX_BUFFER;
    if (offset + schema.headerBytes > view.byteLength) {
      console.warn('[M3G derived index] truncated header');
      return view.byteLength;
    }
    const headerStart = offset;
    const handle = view.getUint32(headerStart + schema.offset.handle, true);
    const version = view.getUint32(headerStart + schema.offset.version, true);
    const sourceKind = view.getUint32(
      headerStart + schema.offset.sourceKind,
      true,
    );
    const sourceHandle = view.getUint32(
      headerStart + schema.offset.sourceHandle,
      true,
    );
    view.getUint32(headerStart + schema.offset.sourceVersion, true); // encoded in the C cache key
    const indexCount = view.getUint32(
      headerStart + schema.offset.indexCount,
      true,
    );
    offset += schema.headerBytes;
    const byteLength = indexCount * 4;
    if (offset + byteLength > view.byteLength) {
      console.warn('[M3G derived index] truncated triangle fan index payload');
      return view.byteLength;
    }

    const previous = this.indexBuffers.get(handle);
    if (previous?.gpuIndexBuffer) {
      this.retireStableResource(previous.gpuIndexBuffer);
    }
    this.unregisterDerivedIndexBuffer(handle);

    const indices = new Uint32Array(indexCount);
    for (let index = 0; index < indexCount; index++) {
      indices[index] = view.getUint32(offset + index * 4, true);
    }
    const gpuIndexBuffer = this.device.createBuffer({
      size: Math.max((indices.byteLength + 3) & ~3, 4),
      usage: GPUBufferUsage.INDEX | GPUBufferUsage.COPY_DST,
      mappedAtCreation: true,
    });
    new Uint32Array(gpuIndexBuffer.getMappedRange()).set(indices);
    gpuIndexBuffer.unmap();

    this.indexBuffers.set(handle, {
      version,
      gpuIndexBuffer,
      indexCount,
    });
    this.registerDerivedIndexBuffer(sourceKind, sourceHandle, handle);
    return offset + byteLength;
  }

  private derivedIndexSourceKey(kind: number, handle: number): string {
    return `${kind}:${handle}`;
  }

  private registerDerivedIndexBuffer(
    sourceKind: number,
    sourceHandle: number,
    handle: number,
  ): void {
    const sourceKey = this.derivedIndexSourceKey(sourceKind, sourceHandle);
    const derived =
      this.derivedIndexBuffersBySource.get(sourceKey) ?? new Set<number>();
    derived.add(handle);
    this.derivedIndexBuffersBySource.set(sourceKey, derived);
    this.derivedIndexBufferSources.set(handle, sourceKey);
  }

  private unregisterDerivedIndexBuffer(handle: number): void {
    const sourceKey = this.derivedIndexBufferSources.get(handle);
    if (!sourceKey) return;
    const derived = this.derivedIndexBuffersBySource.get(sourceKey);
    derived?.delete(handle);
    if (derived?.size === 0) {
      this.derivedIndexBuffersBySource.delete(sourceKey);
    }
    this.derivedIndexBufferSources.delete(handle);
  }

  private releaseDerivedIndexBuffers(kind: number, sourceHandle: number): void {
    const sourceKey = this.derivedIndexSourceKey(kind, sourceHandle);
    const derived = this.derivedIndexBuffersBySource.get(sourceKey);
    if (!derived) return;
    for (const handle of derived) {
      const indexBuffer = this.indexBuffers.get(handle);
      if (indexBuffer?.gpuIndexBuffer) {
        this.retireStableResource(indexBuffer.gpuIndexBuffer);
      }
      this.indexBuffers.delete(handle);
      this.derivedIndexBufferSources.delete(handle);
    }
    this.derivedIndexBuffersBySource.delete(sourceKey);
  }

  private handleCreateBackground(view: DataView, offset: number): number {
    const handle = view.getUint32(offset, true);
    this.backgrounds.set(handle, {
      color: 0xff000000,
      colorClearEnabled: true,
      depthClearEnabled: true,
    });
    return offset + 4;
  }

  private handleBgSetColor(view: DataView, offset: number): number {
    const handle = view.getUint32(offset, true);
    const argb = view.getUint32(offset + 4, true);
    const bg = this.backgrounds.get(handle);
    if (bg) bg.color = argb;
    return offset + 8;
  }

  private handleBgSetEnable(view: DataView, offset: number): number {
    const handle = view.getUint32(offset, true);
    const which = view.getUint32(offset + 4, true);
    const enable = view.getUint32(offset + 8, true) !== 0;
    const bg = this.backgrounds.get(handle);
    if (bg) {
      if (which === 0) bg.colorClearEnabled = enable;
      else if (which === 1) bg.depthClearEnabled = enable;
    }
    return offset + 12;
  }

  private flushPendingClear(): void {
    if (!this.needsClear && !this.needsDepthClear) return;

    this.ensureClearPipeline();
    this.ensureDepthTexture();

    const s = this.renderTarget?.scale ?? this.surfaceScale;
    const clearViewport = this.pendingClearViewport;
    if (!clearViewport) return;
    const vpX = clearViewport.x * s;
    const vpY = clearViewport.y * s;
    const vpW = clearViewport.w * s;
    const vpH = clearViewport.h * s;
    const scissor = this.pendingClearScissor ?? clearViewport;
    const scX = scissor.x * s;
    const scY = (this.canvasHeight() - (scissor.y + scissor.h)) * s;
    const scW = scissor.w * s;
    const scH = scissor.h * s;
    const textureView = this.getRenderTargetView();
    const clearEncoder = this.device.createCommandEncoder();
    const clearResources: GPUBuffer[] = [];
    const clearPass = clearEncoder.beginRenderPass({
      colorAttachments: [
        {
          view: textureView,
          loadOp: 'load' as GPULoadOp,
          storeOp: 'store' as GPUStoreOp,
        },
      ],
      depthStencilAttachment: {
        view: this.depthTextureView!,
        depthClearValue: 1.0,
        depthLoadOp: 'clear' as GPULoadOp,
        depthStoreOp: 'store' as GPUStoreOp,
      },
    });
    clearPass.setViewport(vpX, vpY, vpW, vpH, 0, 1);
    clearPass.setScissorRect(
      Math.max(0, Math.round(scX)),
      Math.max(0, Math.round(scY)),
      Math.max(0, Math.round(scW)),
      Math.max(0, Math.round(scH)),
    );

    if (this.needsClear) {
      clearPass.setPipeline(this.clearPipeline!);
      const clearUniformBuf = this.device.createBuffer({
        size: RAW_UNIFORM_SIZE,
        usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
      });
      const clearUniform = new ArrayBuffer(RAW_UNIFORM_SIZE);
      new Float32Array(clearUniform, 0, 16).set(this.identityMatrix());
      new Float32Array(clearUniform, 64, 16).set(this.identityMatrix());
      new Float32Array(clearUniform, 128, 16).set(this.identityMatrix());
      new Float32Array(clearUniform, 192, 16).set(this.identityMatrix());
      new Float32Array(clearUniform, 256, 4).set([vpX, vpY, vpW, vpH]);
      this.writeFogUniform(clearUniform, 324, 328, 336, 352);
      this.writeLightingUniform(clearUniform, 332, 368, RAW_LIGHTS_OFFSET);
      this.device.queue.writeBuffer(clearUniformBuf, 0, clearUniform);
      const clearBindGroup = this.device.createBindGroup({
        layout: this.clearPipeline!.getBindGroupLayout(0),
        entries: [{ binding: 0, resource: { buffer: clearUniformBuf } }],
      });
      clearPass.setBindGroup(0, clearBindGroup);
      const clearVerts = new Float32Array([
        -4, -4, 0.999, 1, 4, -4, 0.999, 1, 0, 4, 0.999, 1,
      ]);
      const clearPosBuf = this.device.createBuffer({
        size: clearVerts.byteLength,
        usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
        mappedAtCreation: true,
      });
      new Float32Array(clearPosBuf.getMappedRange()).set(clearVerts);
      clearPosBuf.unmap();
      const { r, g, b, a } = this.clearColor;
      const clearColors = new Float32Array([
        r,
        g,
        b,
        a,
        r,
        g,
        b,
        a,
        r,
        g,
        b,
        a,
      ]);
      const clearColBuf = this.device.createBuffer({
        size: clearColors.byteLength,
        usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
        mappedAtCreation: true,
      });
      new Float32Array(clearColBuf.getMappedRange()).set(clearColors);
      clearColBuf.unmap();
      const clearNormals = new Float32Array([
        0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0,
      ]);
      const clearNormalBuf = this.device.createBuffer({
        size: clearNormals.byteLength,
        usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
        mappedAtCreation: true,
      });
      new Float32Array(clearNormalBuf.getMappedRange()).set(clearNormals);
      clearNormalBuf.unmap();
      clearPass.setVertexBuffer(0, clearPosBuf);
      clearPass.setVertexBuffer(1, clearColBuf);
      clearPass.setVertexBuffer(2, clearNormalBuf);
      clearPass.draw(3);
      clearResources.push(
        clearUniformBuf,
        clearPosBuf,
        clearColBuf,
        clearNormalBuf,
      );
    }

    clearPass.end();
    this.device.queue.submit([clearEncoder.finish()]);
    const completion = this.recordGpuSubmission();
    for (const resource of clearResources) {
      this.retireResourceAfterCompletion(resource, completion);
    }
    this.markRenderTargetWritten();
    this.needsClear = false;
    this.needsDepthClear = false;
    this.pendingClearScissor = null;
    this.pendingClearViewport = null;
  }

  private identityMatrix(): Float32Array {
    return this.identityMat4;
  }

  private writeFogUniform(
    uniform: ArrayBuffer,
    modeOffset: number,
    hasFogOffset: number,
    paramsOffset: number,
    colorOffset: number,
    fog?: FogState,
  ): void {
    new Uint32Array(uniform, modeOffset, 1)[0] = fog?.mode ?? 0;
    new Uint32Array(uniform, hasFogOffset, 1)[0] = fog?.enabled ? 1 : 0;
    new Float32Array(uniform, paramsOffset, 4).set([
      fog?.density ?? 1,
      fog?.start ?? 0,
      fog?.end ?? 1,
      0,
    ]);
    new Float32Array(uniform, colorOffset, 4).set(fog?.color ?? [0, 0, 0, 1]);
  }

  private writeLightingUniform(
    uniform: ArrayBuffer,
    hasLightingOffset: number,
    baseOffset: number,
    lightsOffset: number,
    lighting?: LightingState,
  ): void {
    const state = lighting ?? createDefaultLightingState();
    new Uint32Array(uniform, hasLightingOffset, 1)[0] = state.enabled ? 1 : 0;
    new Uint32Array(uniform, baseOffset, 4).set([
      state.enabled ? 1 : 0,
      state.normalize ? 1 : 0,
      state.colorMaterial ? 1 : 0,
      state.twoSided ? 1 : 0,
    ]);
    new Float32Array(uniform, baseOffset + 16, 4).set(state.lightModelAmbient);
    new Float32Array(uniform, baseOffset + 32, 4).set(state.materialAmbient);
    new Float32Array(uniform, baseOffset + 48, 4).set(state.materialDiffuse);
    new Float32Array(uniform, baseOffset + 64, 4).set(state.materialEmission);
    new Float32Array(uniform, baseOffset + 80, 4).set(state.materialSpecular);
    new Float32Array(uniform, baseOffset + 96, 4).set([
      state.materialShininess,
      0,
      0,
      0,
    ]);
    for (let i = 0; i < 8; i++) {
      const light = state.lights[i] ?? createDefaultLightingState().lights[i];
      const offset = lightsOffset + i * 112;
      new Float32Array(uniform, offset, 4).set([
        light.enabled ? 1 : 0,
        light.spotExponent,
        light.spotCutoff,
        light.constantAttenuation,
      ]);
      new Float32Array(uniform, offset + 16, 4).set(light.ambient);
      new Float32Array(uniform, offset + 32, 4).set(light.diffuse);
      new Float32Array(uniform, offset + 48, 4).set(light.specular);
      new Float32Array(uniform, offset + 64, 4).set(light.position);
      new Float32Array(uniform, offset + 80, 4).set(light.spotDirection);
      new Float32Array(uniform, offset + 96, 4).set([
        light.linearAttenuation,
        light.quadraticAttenuation,
        0,
        0,
      ]);
    }
  }

  private depthBiasFor(offset?: PolygonOffsetState): {
    constant: number;
    slopeScale: number;
    clamp: number;
  } {
    if (!offset?.enabled) {
      return { constant: 0, slopeScale: 0, clamp: 0 };
    }
    return {
      constant: Math.round(offset.units * 0x10000),
      slopeScale: offset.factor,
      clamp: 0,
    };
  }

  private ensureClearPipeline(): void {
    if (this.clearPipeline) return;

    const shaderModule = this.device.createShaderModule({ code: SHADER_CODE });

    const bufferLayouts: GPUVertexBufferLayout[] = [
      {
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 0,
            offset: 0,
            format: 'float32x4' as GPUVertexFormat,
          },
        ],
      },
      {
        arrayStride: 4 * 4, // float32 x 4
        attributes: [
          {
            shaderLocation: 1,
            offset: 0,
            format: 'float32x4' as GPUVertexFormat,
          },
        ],
      },
      {
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 4,
            offset: 0,
            format: 'float32x3' as GPUVertexFormat,
          },
        ],
      },
    ];

    this.clearPipeline = this.device.createRenderPipeline({
      layout: 'auto',
      vertex: {
        module: shaderModule,
        entryPoint: 'vs_main',
        buffers: bufferLayouts,
      },
      fragment: {
        module: shaderModule,
        entryPoint: 'fs_main',
        targets: [{ format: this.format }],
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: false,
        depthCompare: 'less-equal',
      },
      primitive: {
        topology: 'triangle-strip',
        stripIndexFormat: 'uint16',
      },
    });
  }

  private ensureRawPipeline(): void {
    if (this.rawPipeline) return;
    const shaderModule = this.device.createShaderModule({ code: SHADER_CODE });
    const bufferLayouts: GPUVertexBufferLayout[] = [
      {
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 0,
            offset: 0,
            format: 'float32x4' as GPUVertexFormat,
          },
        ],
      },
      {
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 1,
            offset: 0,
            format: 'float32x4' as GPUVertexFormat,
          },
        ],
      },
      {
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 4,
            offset: 0,
            format: 'float32x3' as GPUVertexFormat,
          },
        ],
      },
    ];
    this.rawUniformBindGroupLayout = this.device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT,
          buffer: { type: 'uniform', hasDynamicOffset: true },
        },
      ],
    });
    this.rawPipeline = this.device.createRenderPipeline({
      layout: this.device.createPipelineLayout({
        bindGroupLayouts: [this.rawUniformBindGroupLayout],
      }),
      vertex: {
        module: shaderModule,
        entryPoint: 'vs_main',
        buffers: bufferLayouts,
      },
      fragment: {
        module: shaderModule,
        entryPoint: 'fs_main',
        targets: [{ format: this.format }],
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: true,
        depthCompare: 'less-equal',
      },
      primitive: {
        topology: 'triangle-list',
      },
    });
  }

  private ensureRawPipelineBlend(): void {
    if (this.rawPipelineBlend) return;
    this.ensureRawPipeline();
    const shaderModule = this.device.createShaderModule({ code: SHADER_CODE });
    this.rawPipelineBlend = this.device.createRenderPipeline({
      layout: this.device.createPipelineLayout({
        bindGroupLayouts: [this.rawUniformBindGroupLayout!],
      }),
      vertex: {
        module: shaderModule,
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 0,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 1,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 4,
                offset: 0,
                format: 'float32x3' as GPUVertexFormat,
              },
            ],
          },
        ],
      },
      fragment: {
        module: shaderModule,
        entryPoint: 'fs_main',
        targets: [
          {
            format: this.format,
            blend: STANDARD_ALPHA_BLEND,
          },
        ],
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: true,
        depthCompare: 'less-equal',
      },
      primitive: {
        topology: 'triangle-list',
      },
    });
  }

  private ensureRawTexturePipeline(): void {
    if (this.rawTexturePipeline) return;
    const shaderModule = this.device.createShaderModule({
      code: TEXTURED_SHADER_CODE,
    });
    this.rawTextureUniformBindGroupLayout = this.device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT,
          buffer: { type: 'uniform', hasDynamicOffset: true },
        },
      ],
    });
    this.rawTextureBindGroupLayout = this.device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.FRAGMENT,
          sampler: {},
        },
        {
          binding: 1,
          visibility: GPUShaderStage.FRAGMENT,
          texture: {},
        },
        {
          binding: 2,
          visibility: GPUShaderStage.FRAGMENT,
          sampler: {},
        },
        {
          binding: 3,
          visibility: GPUShaderStage.FRAGMENT,
          texture: {},
        },
      ],
    });
    this.rawTexturePipeline = this.device.createRenderPipeline({
      layout: this.device.createPipelineLayout({
        bindGroupLayouts: [
          this.rawTextureUniformBindGroupLayout,
          this.rawTextureBindGroupLayout,
        ],
      }),
      vertex: {
        module: shaderModule,
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 0,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 1,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 2,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 3,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 4,
                offset: 0,
                format: 'float32x3' as GPUVertexFormat,
              },
            ],
          },
        ],
      },
      fragment: {
        module: shaderModule,
        entryPoint: 'fs_main',
        targets: [{ format: this.format }],
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: true,
        depthCompare: 'less-equal',
      },
      primitive: {
        topology: 'triangle-list',
      },
    });
  }

  private ensureRawTexturePipelineBlend(): void {
    if (this.rawTexturePipelineBlend) return;
    this.ensureRawTexturePipeline();
    const shaderModule = this.device.createShaderModule({
      code: TEXTURED_SHADER_CODE,
    });
    this.rawTexturePipelineBlend = this.device.createRenderPipeline({
      layout: this.device.createPipelineLayout({
        bindGroupLayouts: [
          this.rawTextureUniformBindGroupLayout!,
          this.rawTextureBindGroupLayout!,
        ],
      }),
      vertex: {
        module: shaderModule,
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 0,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 1,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 2,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 3,
                offset: 0,
                format: 'float32x4' as GPUVertexFormat,
              },
            ],
          },
          {
            arrayStride: 4 * 4,
            attributes: [
              {
                shaderLocation: 4,
                offset: 0,
                format: 'float32x3' as GPUVertexFormat,
              },
            ],
          },
        ],
      },
      fragment: {
        module: shaderModule,
        entryPoint: 'fs_main',
        targets: [
          {
            format: this.format,
            blend: STANDARD_ALPHA_BLEND,
          },
        ],
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: true,
        depthCompare: 'less-equal',
      },
      primitive: {
        topology: 'triangle-list',
      },
    });
  }

  private getRawPipeline(opts: {
    textured: boolean;
    blend: boolean;
    cullFace: boolean;
    cullFront: boolean;
    frontCW: boolean;
    depthTestAlways: boolean;
    depthWriteOff: boolean;
    blendMode: number;
    writeMask: GPUColorWriteFlags;
    alphaTest: boolean;
    flatShading: boolean;
    depthBias: {
      constant: number;
      slopeScale: number;
      clamp: number;
    };
    primitiveTopology: GPUPrimitiveTopology;
    colorFormat: GPUVertexFormat;
    positionFormat: GPUVertexFormat;
    constantColor: boolean;
  }): GPURenderPipeline {
    const topologyKey = opts.primitiveTopology === 'triangle-strip' ? 1 : 0;
    const colorFormatKey = opts.colorFormat === 'unorm8x4' ? 1 : 0;
    const positionFormatKey = opts.positionFormat === 'sint16x4' ? 1 : 0;
    const baseKey =
      (opts.textured ? 1 : 0) |
      (opts.blend ? 2 : 0) |
      (opts.cullFace ? 4 : 0) |
      (opts.cullFront ? 8 : 0) |
      (opts.frontCW ? 16 : 0) |
      (opts.depthTestAlways ? 32 : 0) |
      (opts.depthWriteOff ? 64 : 0) |
      ((opts.blendMode & 0xf) << 7) |
      (opts.alphaTest ? 2048 : 0) |
      (opts.flatShading ? 4096 : 0) |
      (topologyKey << 13) |
      (colorFormatKey << 14) |
      (positionFormatKey << 15) |
      (opts.writeMask << 16) |
      (opts.constantColor ? 1 << 20 : 0);
    const key = [
      baseKey,
      opts.depthBias.constant,
      opts.depthBias.slopeScale,
      opts.depthBias.clamp,
    ].join(':');
    const cached = this.rawPipelineVariants.get(key);
    if (cached) return cached;

    // Base pipelines set up the shared uniform buffer + bind group layouts;
    // ensure they exist first regardless of variant.
    if (opts.textured) {
      if (opts.blend) this.ensureRawTexturePipelineBlend();
      else this.ensureRawTexturePipeline();
    } else {
      if (opts.blend) this.ensureRawPipelineBlend();
      else this.ensureRawPipeline();
    }
    // The four base pipelines cover the common case (default cull, depth,
    // blend, smooth shading, no alpha discard). Fall through to build a
    // variant only when any of those defaults changes.
    const hasDepthBias =
      opts.depthBias.constant !== 0 ||
      opts.depthBias.slopeScale !== 0 ||
      opts.depthBias.clamp !== 0;
    const isVariant =
      opts.cullFace ||
      opts.depthTestAlways ||
      opts.depthWriteOff ||
      opts.blendMode > 1 ||
      opts.flatShading ||
      hasDepthBias ||
      opts.writeMask !== WRITE_MASK_ALL ||
      opts.constantColor ||
      opts.primitiveTopology !== 'triangle-list' ||
      opts.colorFormat !== 'float32x4' ||
      opts.positionFormat !== 'float32x4';
    if (!isVariant) {
      const base = opts.textured
        ? opts.blend
          ? this.rawTexturePipelineBlend!
          : this.rawTexturePipeline!
        : opts.blend
          ? this.rawPipelineBlend!
          : this.rawPipeline!;
      this.rawPipelineVariants.set(key, base);
      return base;
    }

    const shaderCode = opts.textured
      ? TEXTURED_SHADER_CODE
      : opts.flatShading
        ? SHADER_CODE_FLAT
        : SHADER_CODE;
    const shaderModule = this.device.createShaderModule({ code: shaderCode });
    const buffers: GPUVertexBufferLayout[] = [
      {
        arrayStride: opts.positionFormat === 'sint16x4' ? 4 * 2 : 4 * 4,
        attributes: [
          { shaderLocation: 0, offset: 0, format: opts.positionFormat },
        ],
      },
      {
        arrayStride: opts.colorFormat === 'unorm8x4' ? 4 : 4 * 4,
        stepMode: opts.constantColor ? 'instance' : 'vertex',
        attributes: [
          { shaderLocation: 1, offset: 0, format: opts.colorFormat },
        ],
      },
      {
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 4,
            offset: 0,
            format: 'float32x3' as GPUVertexFormat,
          },
        ],
      },
    ];
    if (opts.textured) {
      const normalBuffer = buffers.pop()!;
      buffers.push({
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 2,
            offset: 0,
            format: 'float32x4' as GPUVertexFormat,
          },
        ],
      });
      buffers.push({
        arrayStride: 4 * 4,
        attributes: [
          {
            shaderLocation: 3,
            offset: 0,
            format: 'float32x4' as GPUVertexFormat,
          },
        ],
      });
      buffers.push(normalBuffer);
    }
    let layout: GPUPipelineLayout | 'auto' = 'auto';
    if (opts.textured) {
      layout = this.device.createPipelineLayout({
        bindGroupLayouts: [
          this.rawTextureUniformBindGroupLayout!,
          this.rawTextureBindGroupLayout!,
        ],
      });
    } else {
      layout = this.device.createPipelineLayout({
        bindGroupLayouts: [this.rawUniformBindGroupLayout!],
      });
    }
    let blendState: GPUBlendState | undefined;
    if (opts.blendMode === 1) blendState = STANDARD_ALPHA_BLEND;
    else if (opts.blendMode === 2) blendState = ALPHA_ADD_BLEND;
    else if (opts.blendMode === 3) blendState = MODULATE_BLEND;
    else if (opts.blendMode === 4) blendState = MODULATE_X2_BLEND;
    else if (opts.blendMode === 5) blendState = ZERO_ONE_MINUS_SRC_BLEND;
    else if (opts.blendMode === 6) blendState = ADDITIVE_BLEND;
    else if (opts.blend) blendState = STANDARD_ALPHA_BLEND;
    const pipeline = this.device.createRenderPipeline({
      layout,
      vertex: {
        module: shaderModule,
        entryPoint: 'vs_main',
        buffers,
      },
      fragment: {
        module: shaderModule,
        entryPoint: 'fs_main',
        targets: [
          {
            format: this.format,
            blend: blendState,
            writeMask: opts.writeMask,
          },
        ],
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: !opts.depthWriteOff,
        depthCompare: opts.depthTestAlways ? 'always' : 'less-equal',
        depthBias: opts.depthBias.constant,
        depthBiasSlopeScale: opts.depthBias.slopeScale,
        depthBiasClamp: opts.depthBias.clamp,
      },
      primitive: {
        topology: opts.primitiveTopology,
        stripIndexFormat:
          opts.primitiveTopology === 'triangle-strip' ? 'uint32' : undefined,
        cullMode: opts.cullFace ? (opts.cullFront ? 'front' : 'back') : 'none',
        frontFace: opts.frontCW ? 'cw' : 'ccw',
      },
    });
    this.rawPipelineVariants.set(key, pipeline);
    return pipeline;
  }

  private getDrawPacketPipeline(
    primitiveTopology: GPUPrimitiveTopology,
    pipelineKey: number,
    polygonOffsetFactor: number,
    polygonOffsetUnits: number,
  ): GPURenderPipeline {
    const cullFace = (pipelineKey & WGPU_DRAW_CULL_FACE) !== 0;
    const cullFront = (pipelineKey & WGPU_DRAW_CULL_FRONT) !== 0;
    const frontCW = (pipelineKey & WGPU_DRAW_FRONT_CW) !== 0;
    const depthTestAlways = (pipelineKey & WGPU_DRAW_DEPTH_TEST_ALWAYS) !== 0;
    const depthWriteOff = (pipelineKey & WGPU_DRAW_DEPTH_WRITE_OFF) !== 0;
    const blendMode =
      (pipelineKey & WGPU_DRAW_BLEND_MASK) >> WGPU_DRAW_BLEND_SHIFT;
    const writeMask = colorWriteMaskForPipelineKey(pipelineKey);
    const useTexturedPipeline =
      (pipelineKey &
        (WGPU_DRAW_HAS_TEXTURE |
          WGPU_DRAW_HAS_LIGHTING |
          WGPU_DRAW_HAS_FOG |
          WGPU_DRAW_HAS_ALPHA_TEST)) !==
      0;
    const flatShading = (pipelineKey & WGPU_DRAW_FLAT_SHADING) !== 0;
    const perspectiveCorrection =
      (pipelineKey & WGPU_DRAW_PERSPECTIVE_CORRECTION) !== 0;
    const hasVertexColor = (pipelineKey & WGPU_DRAW_HAS_VERTEX_COLOR) !== 0;
    const hasLighting = (pipelineKey & WGPU_DRAW_HAS_LIGHTING) !== 0;
    const depthBias = this.depthBiasFor(
      polygonOffsetFactor === 0 && polygonOffsetUnits === 0
        ? undefined
        : {
            enabled: true,
            factor: polygonOffsetFactor,
            units: polygonOffsetUnits,
          },
    );
    const key = [
      primitiveTopology,
      cullFace ? 1 : 0,
      cullFront ? 1 : 0,
      frontCW ? 1 : 0,
      depthTestAlways ? 1 : 0,
      depthWriteOff ? 1 : 0,
      blendMode,
      writeMask,
      useTexturedPipeline ? 1 : 0,
      hasVertexColor ? 1 : 0,
      hasLighting ? 1 : 0,
      flatShading ? 1 : 0,
      perspectiveCorrection ? 1 : 0,
      depthBias.constant,
      depthBias.slopeScale,
    ].join(':');
    const cached = this.drawPacketPipelines.get(key);
    if (cached) return cached;
    const shaderModule = this.device.createShaderModule({
      code: useTexturedPipeline
        ? flatShading
          ? TEXTURED_SHADER_CODE_FLAT
          : TEXTURED_SHADER_CODE
        : flatShading
          ? DRAW_PACKET_SHADER_CODE_FLAT
          : DRAW_PACKET_SHADER_CODE,
    });
    if (useTexturedPipeline) {
      this.ensureRawTexturePipeline();
    }
    const pipeline = this.device.createRenderPipeline({
      layout: useTexturedPipeline
        ? this.device.createPipelineLayout({
            bindGroupLayouts: [
              this.rawTextureUniformBindGroupLayout!,
              this.rawTextureBindGroupLayout!,
            ],
          })
        : 'auto',
      vertex: {
        module: shaderModule,
        entryPoint: 'vs_main',
        buffers: useTexturedPipeline
          ? [
              {
                arrayStride: 4 * 4,
                attributes: [
                  {
                    shaderLocation: 0,
                    offset: 0,
                    format: 'float32x4',
                  },
                ],
              },
              {
                arrayStride: 4 * 4,
                stepMode:
                  (pipelineKey & WGPU_DRAW_HAS_VERTEX_COLOR) !== 0
                    ? 'vertex'
                    : 'instance',
                attributes: [
                  {
                    shaderLocation: 1,
                    offset: 0,
                    format: 'float32x4',
                  },
                ],
              },
              {
                arrayStride: 4 * 4,
                attributes: [
                  {
                    shaderLocation: 2,
                    offset: 0,
                    format: 'float32x4',
                  },
                ],
              },
              {
                arrayStride: 4 * 4,
                attributes: [
                  {
                    shaderLocation: 3,
                    offset: 0,
                    format: 'float32x4',
                  },
                ],
              },
              {
                arrayStride: 4 * 4,
                stepMode:
                  (pipelineKey & WGPU_DRAW_HAS_LIGHTING) !== 0
                    ? 'vertex'
                    : 'instance',
                attributes: [
                  {
                    shaderLocation: 4,
                    offset: 0,
                    format: 'float32x3',
                  },
                ],
              },
            ]
          : [
              {
                arrayStride: 4 * 4,
                attributes: [
                  {
                    shaderLocation: 0,
                    offset: 0,
                    format: 'float32x4',
                  },
                ],
              },
              {
                arrayStride: 4 * 4,
                stepMode: hasVertexColor ? 'vertex' : 'instance',
                attributes: [
                  {
                    shaderLocation: 1,
                    offset: 0,
                    format: 'float32x4',
                  },
                ],
              },
              {
                arrayStride: M3G_DRAW_PACKET.instanceBytes,
                stepMode: 'instance',
                attributes: [
                  { shaderLocation: 5, offset: 0, format: 'float32x4' },
                  { shaderLocation: 6, offset: 16, format: 'float32x4' },
                  { shaderLocation: 7, offset: 32, format: 'float32x4' },
                  { shaderLocation: 8, offset: 48, format: 'float32x4' },
                ],
              },
            ],
      },
      fragment: {
        module: shaderModule,
        entryPoint: 'fs_main',
        targets: [
          {
            format: this.format,
            blend:
              blendMode === 1
                ? STANDARD_ALPHA_BLEND
                : blendMode === 2
                  ? ALPHA_ADD_BLEND
                  : blendMode === 3
                    ? MODULATE_BLEND
                    : blendMode === 4
                      ? MODULATE_X2_BLEND
                      : blendMode === 5
                        ? ZERO_ONE_MINUS_SRC_BLEND
                        : blendMode === 6
                          ? ADDITIVE_BLEND
                          : undefined,
            writeMask,
          },
        ],
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: !depthWriteOff,
        depthCompare: depthTestAlways ? 'always' : 'less-equal',
        depthBias: depthBias.constant,
        depthBiasSlopeScale: depthBias.slopeScale,
        depthBiasClamp: depthBias.clamp,
      },
      primitive: {
        topology: primitiveTopology,
        stripIndexFormat:
          primitiveTopology === 'triangle-strip' ? 'uint32' : undefined,
        cullMode: cullFace ? (cullFront ? 'front' : 'back') : 'none',
        frontFace: frontCW ? 'cw' : 'ccw',
      },
    });
    this.drawPacketPipelines.set(key, pipeline);
    return pipeline;
  }

  private canvasHeight(): number {
    if (this.renderTarget) {
      return this.renderTarget.logicalHeight;
    }
    return this.context.canvas.height / this.surfaceScale;
  }

  private getRenderTargetView(): GPUTextureView {
    if (this.renderTarget) {
      return this.renderTarget.view;
    }
    return this.context.getCurrentTexture().createView();
  }

  private getRenderTargetSize(): { w: number; h: number } {
    if (this.renderTarget) {
      return {
        w: this.renderTarget.texture.width,
        h: this.renderTarget.texture.height,
      };
    }
    const canvas = this.context.canvas;
    return { w: canvas.width, h: canvas.height };
  }

  private ensureDepthTexture(): void {
    const { w, h } = this.getRenderTargetSize();

    if (
      this.depthTexture &&
      this.depthTexture.width === w &&
      this.depthTexture.height === h
    ) {
      return;
    }

    this.depthTexture?.destroy();
    this.depthTexture = this.device.createTexture({
      size: { width: w, height: h },
      format: 'depth24plus',
      usage: GPUTextureUsage.RENDER_ATTACHMENT,
    });
    this.depthTextureView = this.depthTexture.createView();
  }

  private getFallbackTexture(): GPUTexture {
    if (!this.fallbackTexture) {
      this.fallbackTexture = this.device.createTexture({
        size: { width: 1, height: 1 },
        format: 'rgba8unorm',
        usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST,
      });
      this.device.queue.writeTexture(
        { texture: this.fallbackTexture },
        new Uint8Array([255, 255, 255, 255]),
        { bytesPerRow: 4, rowsPerImage: 1 },
        { width: 1, height: 1 },
      );
    }
    return this.fallbackTexture;
  }

  private getFallbackTextureView(): GPUTextureView {
    if (!this.fallbackTextureView) {
      this.fallbackTextureView = this.getFallbackTexture().createView();
    }
    return this.fallbackTextureView;
  }

  private textureMipLevelCount(width: number, height: number): number {
    return Math.floor(Math.log2(Math.max(1, width, height))) + 1;
  }

  private downsampleRgba8(
    source: Uint8Array,
    sourceWidth: number,
    sourceHeight: number,
  ): Uint8Array<ArrayBuffer> {
    const targetWidth = Math.max(1, sourceWidth >> 1);
    const targetHeight = Math.max(1, sourceHeight >> 1);
    const target = new Uint8Array(
      new ArrayBuffer(targetWidth * targetHeight * 4),
    );
    for (let y = 0; y < targetHeight; y++) {
      const sourceY = y << 1;
      const sampleBottom = Math.min(sourceY + 1, sourceHeight - 1);
      for (let x = 0; x < targetWidth; x++) {
        const sourceX = x << 1;
        const sampleRight = Math.min(sourceX + 1, sourceWidth - 1);
        const outputOffset = (y * targetWidth + x) * 4;
        const topLeft = (sourceY * sourceWidth + sourceX) * 4;
        const topRight = (sourceY * sourceWidth + sampleRight) * 4;
        const bottomLeft = (sampleBottom * sourceWidth + sourceX) * 4;
        const bottomRight = (sampleBottom * sourceWidth + sampleRight) * 4;
        for (let channel = 0; channel < 4; channel++) {
          target[outputOffset + channel] =
            (source[topLeft + channel] +
              source[topRight + channel] +
              source[bottomLeft + channel] +
              source[bottomRight + channel]) >>
            2;
        }
      }
    }
    return target;
  }

  private textureSamplerKey(
    wrapS: number,
    wrapT: number,
    minFilter: number,
    magFilter: number,
  ): string {
    return [
      wrapS === GL_REPEAT ? 'r' : 'c',
      wrapT === GL_REPEAT ? 'r' : 'c',
      minFilter,
      magFilter,
    ].join(':');
  }

  private getTextureSampler(
    key: string,
    wrapS: number,
    wrapT: number,
    minFilter: number,
    magFilter: number,
  ): GPUSampler {
    let sampler = this.textureSamplers.get(key);
    if (!sampler) {
      const nearestMinification =
        minFilter === GL_NEAREST ||
        minFilter === GL_NEAREST_MIPMAP_NEAREST ||
        minFilter === GL_NEAREST_MIPMAP_LINEAR;
      sampler = this.device.createSampler({
        addressModeU: wrapS === GL_REPEAT ? 'repeat' : 'clamp-to-edge',
        addressModeV: wrapT === GL_REPEAT ? 'repeat' : 'clamp-to-edge',
        magFilter: magFilter === GL_LINEAR ? 'linear' : 'nearest',
        minFilter: nearestMinification ? 'nearest' : 'linear',
        mipmapFilter:
          minFilter === GL_NEAREST_MIPMAP_LINEAR ||
          minFilter === GL_LINEAR_MIPMAP_LINEAR
            ? 'linear'
            : 'nearest',
      });
      this.textureSamplers.set(key, sampler);
    }
    return sampler;
  }

  private getTextureBindGroup(
    textureName0: number,
    textureKind0: string,
    version0: number,
    samplerKey0: string,
    sampler0: GPUSampler,
    view0: GPUTextureView,
    textureName1: number,
    textureKind1: string,
    version1: number,
    samplerKey1: string,
    sampler1: GPUSampler,
    view1: GPUTextureView,
  ): GPUBindGroup {
    const cacheable = textureName0 !== 0 && textureName1 !== 0;
    const key = cacheable
      ? `${textureKind0}:${textureName0}:${version0}:${samplerKey0}|${textureKind1}:${textureName1}:${version1}:${samplerKey1}`
      : '';
    if (cacheable) {
      const cached = this.textureBindGroups.get(key);
      if (cached) return cached;
    }
    const bindGroup = this.device.createBindGroup({
      layout: this.rawTextureBindGroupLayout!,
      entries: [
        { binding: 0, resource: sampler0 },
        { binding: 1, resource: view0 },
        { binding: 2, resource: sampler1 },
        { binding: 3, resource: view1 },
      ],
    });
    if (cacheable) {
      this.textureBindGroups.set(key, bindGroup);
    }
    return bindGroup;
  }

  private retireVertexArrayBuffers(entry: VertexArrayEntry): void {
    this.retireStableResource(entry.gpuBufferVec4);
    if (entry.gpuBufferNormalizedVec4) {
      this.retireStableResource(entry.gpuBufferNormalizedVec4);
    }
  }

  private retireStableResource(resource: GPUBuffer | GPUTexture): void {
    const completion = this.lastGpuCompletion;
    if (!completion) {
      resource.destroy();
      return;
    }
    this.retireResourceAfterCompletion(resource, completion);
  }

  private retireResourceAfterCompletion(
    resource: GPUBuffer | GPUTexture,
    completion: Promise<void>,
  ): void {
    this.retiredStableResources.push(resource);
    void completion.then(() => {
      const index = this.retiredStableResources.indexOf(resource);
      if (index >= 0) {
        this.retiredStableResources.splice(index, 1);
        resource.destroy();
      }
    });
  }

  private allocateBatchBuffer(
    batch: WgpuDrawBatch,
    kind: 'vertex' | 'index' | 'uniform' | 'textured-uniform' | 'instance',
    data: Uint8Array,
    usage: GPUBufferUsageFlags,
  ): { buffer: GPUBuffer; offset: number } {
    const alignment =
      kind === 'uniform' || kind === 'textured-uniform' ? 256 : 4;
    const size = Math.max((data.byteLength + 3) & ~3, 4);
    const field =
      kind === 'vertex'
        ? 'vertexArena'
        : kind === 'index'
          ? 'indexArena'
          : kind === 'uniform'
            ? 'uniformArena'
            : kind === 'textured-uniform'
              ? 'texturedUniformArena'
              : 'instanceArena';
    let arena = batch[field];
    const offset = arena ? Math.ceil(arena.offset / alignment) * alignment : 0;
    if (!arena || offset + size > arena.size) {
      const arenaSize = Math.max(arena ? arena.size * 2 : 256 * 1024, size);
      const buffer = this.device.createBuffer({ size: arenaSize, usage });
      batch.resources.push(buffer);
      batch.transientBytes += arenaSize;
      arena = { buffer, size: arenaSize, offset: 0 };
      batch[field] = arena;
    }
    const writeOffset = Math.ceil(arena.offset / alignment) * alignment;
    this.device.queue.writeBuffer(
      arena.buffer,
      writeOffset,
      data.buffer,
      data.byteOffset,
      data.byteLength,
    );
    arena.offset = writeOffset + size;
    return { buffer: arena.buffer, offset: writeOffset };
  }

  private clearPipelineCaches(): void {
    this.rawPipelineVariants.clear();
    this.drawPacketPipelines.clear();
  }

  destroy(): void {
    this.clearPipelineCaches();
    for (const va of this.vertexArrays.values()) {
      va.gpuBufferVec4.destroy();
      va.gpuBufferNormalizedVec4?.destroy();
    }
    for (const entry of this.glTextures.values()) {
      entry.texture.destroy();
    }
    for (const target of this.imageTargets.values()) {
      target.texture.destroy();
    }
    this.vertexArrays.clear();
    this.glTextures.clear();
    this.imageTargets.clear();
    this.imageToGlTextures.clear();
    this.textureBindGroups.clear();
    this.textureSamplers.clear();
    this.indexBuffers.clear();
    this.depthTexture?.destroy();
    this.depthTextureView = null;
    for (const buffer of this.retiredStableResources) {
      buffer.destroy();
    }
    this.retiredStableResources = [];
    this.fallbackTexture?.destroy();
    this.fallbackTextureView = null;
  }
}
