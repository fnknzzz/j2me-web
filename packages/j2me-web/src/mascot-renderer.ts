import {
  MASCOT_COLOR_MATERIAL_FRAGMENT_SHADER,
  MASCOT_COLOR_MATERIAL_VERTEX_SHADER,
  MASCOT_PRIMITIVE_FRAGMENT_SHADER,
  MASCOT_PRIMITIVE_VERTEX_SHADER,
  MASCOT_TEXTURE_FRAGMENT_SHADER,
  MASCOT_TEXTURE_VERTEX_SHADER,
} from './webgpu-shaders';

const CMD_MASCOT_BIND_TARGET = 190;
const CMD_MASCOT_RELEASE_TARGET = 191;
const CMD_MASCOT_DRAW_PRIMITIVES = 192;
const CMD_MASCOT_FLUSH = 193;
const CMD_MASCOT_UPLOAD_TEXTURE = 194;
const CMD_MASCOT_DRAW_TEXTURED = 195;
const CMD_MASCOT_DELETE_TEXTURE = 196;
const CMD_MASCOT_RESET_TEXTURES = 197;
const CMD_MASCOT_DRAW_COLOR_MATERIAL = 198;
const CMD_MASCOT_CLEAR_DEPTH = 199;

const BLEND_NORMAL = 0;
const BLEND_HALF = 1;
const BLEND_ADD = 2;
const BLEND_SUB = 3;

type RenderTarget = {
  texture: GPUTexture;
  depthTexture: GPUTexture;
  width: number;
  height: number;
};

export class MascotRenderer {
  private target: RenderTarget | null = null;
  private cachedDepthTexture: {
    surfaceId: number;
    texture: GPUTexture;
    width: number;
    height: number;
  } | null = null;
  private encoder: GPUCommandEncoder | null = null;
  private pass: GPURenderPassEncoder | null = null;
  private resources: GPUBuffer[] = [];
  private readonly pipelineLayout: GPUPipelineLayout;
  private readonly colorBindGroupLayout: GPUBindGroupLayout;
  private readonly pipelines: Record<number, Record<number, GPURenderPipeline>>;
  private readonly texturePipelines: Record<
    number,
    Record<number, GPURenderPipeline>
  >;
  private readonly textureBindGroupLayout: GPUBindGroupLayout;
  private readonly colorMaterialBindGroupLayout: GPUBindGroupLayout;
  private readonly lightingBindGroupLayout: GPUBindGroupLayout;
  private readonly colorMaterialPipelines: Record<number, GPURenderPipeline>;
  private readonly neutralTexture: GPUTexture;
  private readonly textures = new Map<number, GPUTexture>();

  constructor(
    private readonly device: GPUDevice,
    private readonly resolveSurfaceTexture: (
      surfaceId: number,
    ) => GPUTexture | null,
    private readonly surfaceScale: number,
  ) {
    this.colorBindGroupLayout = device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.FRAGMENT,
          buffer: { type: 'uniform' },
        },
      ],
    });
    this.pipelineLayout = device.createPipelineLayout({
      bindGroupLayouts: [this.colorBindGroupLayout],
    });
    this.textureBindGroupLayout = device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.FRAGMENT,
          texture: { sampleType: 'float' },
        },
        {
          binding: 1,
          visibility: GPUShaderStage.FRAGMENT,
          texture: { sampleType: 'float' },
        },
      ],
    });
    this.colorMaterialBindGroupLayout = device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.FRAGMENT,
          texture: { sampleType: 'float' },
        },
      ],
    });
    this.lightingBindGroupLayout = device.createBindGroupLayout({
      entries: [
        {
          binding: 0,
          visibility: GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT,
          buffer: { type: 'uniform' },
        },
      ],
    });
    this.pipelines = {
      1: this.createPipelines('point-list'),
      2: this.createPipelines('line-list'),
      3: this.createPipelines('triangle-list'),
    };
    this.texturePipelines = {
      0: this.createTexturePipelines('none'),
      1: this.createTexturePipelines('back'),
    };
    this.colorMaterialPipelines = this.createColorMaterialPipelines();
    this.neutralTexture = device.createTexture({
      size: { width: 1, height: 1 },
      format: 'rgba8unorm',
      usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST,
    });
    device.queue.writeTexture(
      { texture: this.neutralTexture },
      new Uint8Array([0, 0, 0, 0]),
      { bytesPerRow: 4, rowsPerImage: 1 },
      { width: 1, height: 1 },
    );
  }

  handleCommand(cmdId: number, view: DataView, offset: number): number {
    switch (cmdId) {
      case CMD_MASCOT_BIND_TARGET:
        return this.handleBind(view, offset);
      case CMD_MASCOT_RELEASE_TARGET:
        this.flush();
        this.target = null;
        return offset;
      case CMD_MASCOT_DRAW_PRIMITIVES:
        return this.handleDraw(view, offset);
      case CMD_MASCOT_UPLOAD_TEXTURE:
        return this.handleTextureUpload(view, offset);
      case CMD_MASCOT_DRAW_TEXTURED:
        return this.handleTexturedDraw(view, offset);
      case CMD_MASCOT_DRAW_COLOR_MATERIAL:
        return this.handleColorMaterialDraw(view, offset);
      case CMD_MASCOT_CLEAR_DEPTH:
        this.clearDepth();
        return offset;
      case CMD_MASCOT_DELETE_TEXTURE:
        return this.handleTextureDelete(view, offset);
      case CMD_MASCOT_RESET_TEXTURES:
        this.resetTextures();
        return offset;
      case CMD_MASCOT_FLUSH:
        this.clearDepth();
        return offset;
      default:
        return offset;
    }
  }

  flush(clearDepth = false): void {
    if (!this.encoder) {
      return;
    }
    this.pass?.end();
    this.pass = null;
    if (clearDepth && this.target) {
      this.appendDepthClearPass(this.encoder);
    }
    this.device.queue.submit([this.encoder.finish()]);
    this.encoder = null;
    for (const resource of this.resources) {
      resource.destroy();
    }
    this.resources = [];
  }

  waitForGpu(): Promise<void> | null {
    if (!this.encoder) {
      return null;
    }
    this.flush();
    return this.device.queue.onSubmittedWorkDone();
  }

  private blendState(blendMode: number): GPUBlendState {
    switch (blendMode) {
      case BLEND_HALF:
        return {
          color: {
            srcFactor: 'constant',
            dstFactor: 'constant',
            operation: 'add',
          },
          alpha: {
            srcFactor: 'one',
            dstFactor: 'one-minus-src-alpha',
            operation: 'add',
          },
        };
      case BLEND_ADD:
        return {
          color: { srcFactor: 'one', dstFactor: 'one', operation: 'add' },
          alpha: {
            srcFactor: 'one',
            dstFactor: 'one-minus-src-alpha',
            operation: 'add',
          },
        };
      case BLEND_SUB:
        return {
          color: {
            srcFactor: 'one',
            dstFactor: 'one',
            operation: 'reverse-subtract',
          },
          alpha: {
            srcFactor: 'one',
            dstFactor: 'one-minus-src-alpha',
            operation: 'add',
          },
        };
      default:
        return {
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
        };
    }
  }

  private createPipelines(
    topology: GPUPrimitiveTopology,
  ): Record<number, GPURenderPipeline> {
    return {
      [BLEND_NORMAL]: this.createPipeline(topology, BLEND_NORMAL),
      [BLEND_HALF]: this.createPipeline(topology, BLEND_HALF),
      [BLEND_ADD]: this.createPipeline(topology, BLEND_ADD),
      [BLEND_SUB]: this.createPipeline(topology, BLEND_SUB),
    };
  }

  private createPipeline(
    topology: GPUPrimitiveTopology,
    blendMode: number,
  ): GPURenderPipeline {
    return this.device.createRenderPipeline({
      layout: this.pipelineLayout,
      vertex: {
        module: this.device.createShaderModule({
          code: MASCOT_PRIMITIVE_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 16,
            attributes: [{ shaderLocation: 0, offset: 0, format: 'float32x4' }],
          },
        ],
      },
      fragment: {
        module: this.device.createShaderModule({
          code: MASCOT_PRIMITIVE_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: this.blendState(blendMode),
          },
        ],
      },
      primitive: { topology },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: blendMode === BLEND_NORMAL,
        depthCompare: 'less-equal',
      },
    });
  }

  private createTexturePipelines(
    cullMode: GPUCullMode,
  ): Record<number, GPURenderPipeline> {
    return {
      [BLEND_NORMAL]: this.createTexturePipeline(cullMode, BLEND_NORMAL),
      [BLEND_HALF]: this.createTexturePipeline(cullMode, BLEND_HALF),
      [BLEND_ADD]: this.createTexturePipeline(cullMode, BLEND_ADD),
      [BLEND_SUB]: this.createTexturePipeline(cullMode, BLEND_SUB),
    };
  }

  private createTexturePipeline(
    cullMode: GPUCullMode,
    blendMode: number,
  ): GPURenderPipeline {
    return this.device.createRenderPipeline({
      layout: this.device.createPipelineLayout({
        bindGroupLayouts: [
          this.textureBindGroupLayout,
          this.lightingBindGroupLayout,
        ],
      }),
      vertex: {
        module: this.device.createShaderModule({
          code: MASCOT_TEXTURE_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 44,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x4' },
              { shaderLocation: 1, offset: 16, format: 'float32x2' },
              { shaderLocation: 2, offset: 24, format: 'float32x3' },
              { shaderLocation: 3, offset: 36, format: 'float32' },
              { shaderLocation: 4, offset: 40, format: 'float32' },
            ],
          },
        ],
      },
      fragment: {
        module: this.device.createShaderModule({
          code: MASCOT_TEXTURE_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: this.blendState(blendMode),
          },
        ],
      },
      primitive: {
        topology: 'triangle-list',
        cullMode,
        frontFace: cullMode === 'back' ? 'cw' : 'ccw',
      },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: blendMode === BLEND_NORMAL,
        depthCompare: 'less-equal',
      },
    });
  }

  private createColorMaterialPipelines(): Record<number, GPURenderPipeline> {
    return {
      [BLEND_NORMAL]: this.createColorMaterialPipeline(BLEND_NORMAL),
      [BLEND_HALF]: this.createColorMaterialPipeline(BLEND_HALF),
      [BLEND_ADD]: this.createColorMaterialPipeline(BLEND_ADD),
      [BLEND_SUB]: this.createColorMaterialPipeline(BLEND_SUB),
    };
  }

  private createColorMaterialPipeline(blendMode: number): GPURenderPipeline {
    return this.device.createRenderPipeline({
      layout: this.device.createPipelineLayout({
        bindGroupLayouts: [
          this.colorMaterialBindGroupLayout,
          this.lightingBindGroupLayout,
        ],
      }),
      vertex: {
        module: this.device.createShaderModule({
          code: MASCOT_COLOR_MATERIAL_VERTEX_SHADER,
        }),
        entryPoint: 'vs_main',
        buffers: [
          {
            arrayStride: 48,
            attributes: [
              { shaderLocation: 0, offset: 0, format: 'float32x4' },
              { shaderLocation: 1, offset: 16, format: 'float32x3' },
              { shaderLocation: 2, offset: 28, format: 'float32x3' },
              { shaderLocation: 3, offset: 40, format: 'float32' },
              { shaderLocation: 4, offset: 44, format: 'float32' },
            ],
          },
        ],
      },
      fragment: {
        module: this.device.createShaderModule({
          code: MASCOT_COLOR_MATERIAL_FRAGMENT_SHADER,
        }),
        entryPoint: 'fs_main',
        targets: [
          {
            format: 'rgba8unorm',
            blend: this.blendState(blendMode),
          },
        ],
      },
      primitive: { topology: 'triangle-list' },
      depthStencil: {
        format: 'depth24plus',
        depthWriteEnabled: blendMode === BLEND_NORMAL,
        depthCompare: 'less-equal',
      },
    });
  }

  private handleBind(view: DataView, offset: number): number {
    this.flush();
    this.target = null;
    if (offset + 28 > view.byteLength) {
      return view.byteLength;
    }
    const surfaceId = view.getInt32(offset, true);
    const width = view.getInt32(offset + 4, true);
    const height = view.getInt32(offset + 8, true);
    const texture = this.resolveSurfaceTexture(surfaceId);
    if (texture && width > 0 && height > 0) {
      const scale = this.surfaceScale;
      const targetWidth = Math.max(1, Math.round(width * scale));
      const targetHeight = Math.max(1, Math.round(height * scale));
      let depth = this.cachedDepthTexture;
      if (
        !depth ||
        depth.surfaceId !== surfaceId ||
        depth.width !== targetWidth ||
        depth.height !== targetHeight
      ) {
        depth?.texture.destroy();
        depth = {
          surfaceId,
          texture: this.device.createTexture({
            size: { width: targetWidth, height: targetHeight },
            format: 'depth24plus',
            usage: GPUTextureUsage.RENDER_ATTACHMENT,
          }),
          width: targetWidth,
          height: targetHeight,
        };
        this.cachedDepthTexture = depth;
      }
      this.target = {
        texture,
        depthTexture: depth.texture,
        width,
        height,
      };
    }
    return offset + 28;
  }

  private handleDraw(view: DataView, offset: number): number {
    const headerBytes = 40;
    if (offset + headerBytes > view.byteLength) {
      return Math.min(view.byteLength, offset + headerBytes);
    }
    const packedTopology = view.getUint32(offset, true);
    const topology = packedTopology & 0xff;
    const blendMode = (packedTopology >>> 8) & 0xff;
    const vertexCount = view.getUint32(offset + 4, true);
    const clipX = view.getInt32(offset + 8, true);
    const clipY = view.getInt32(offset + 12, true);
    const clipWidth = view.getInt32(offset + 16, true);
    const clipHeight = view.getInt32(offset + 20, true);
    const packetBytes = headerBytes + vertexCount * 16;
    const pipeline =
      this.pipelines[topology]?.[blendMode] ??
      this.pipelines[topology]?.[BLEND_NORMAL];
    if (
      !this.target ||
      !pipeline ||
      vertexCount === 0 ||
      clipWidth <= 0 ||
      clipHeight <= 0 ||
      offset + packetBytes > view.byteLength
    ) {
      return Math.min(view.byteLength, offset + packetBytes);
    }

    const vertices = new Uint8Array(
      view.buffer,
      view.byteOffset + offset + headerBytes,
      vertexCount * 16,
    );
    const vertexBuffer = this.device.createBuffer({
      size: vertices.byteLength,
      usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
    });
    this.device.queue.writeBuffer(vertexBuffer, 0, vertices);

    const colorBuffer = this.device.createBuffer({
      size: 16,
      usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
    });
    this.resources.push(vertexBuffer, colorBuffer);
    const color = new Float32Array([
      view.getFloat32(offset + 24, true),
      view.getFloat32(offset + 28, true),
      view.getFloat32(offset + 32, true),
      view.getFloat32(offset + 36, true),
    ]);
    this.device.queue.writeBuffer(colorBuffer, 0, color);

    const pass = this.currentPass();
    const scale = this.surfaceScale;
    const targetWidth = Math.max(1, Math.round(this.target.width * scale));
    const targetHeight = Math.max(1, Math.round(this.target.height * scale));
    const x0 = Math.min(targetWidth, Math.max(0, Math.round(clipX * scale)));
    const y0 = Math.min(targetHeight, Math.max(0, Math.round(clipY * scale)));
    const x1 = Math.min(
      targetWidth,
      Math.max(0, Math.round((clipX + clipWidth) * scale)),
    );
    const y1 = Math.min(
      targetHeight,
      Math.max(0, Math.round((clipY + clipHeight) * scale)),
    );
    pass.setScissorRect(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    pass.setPipeline(pipeline);
    if (blendMode === BLEND_HALF) {
      pass.setBlendConstant({ r: 0.5, g: 0.5, b: 0.5, a: 1 });
    }
    pass.setBindGroup(
      0,
      this.device.createBindGroup({
        layout: this.colorBindGroupLayout,
        entries: [{ binding: 0, resource: { buffer: colorBuffer } }],
      }),
    );
    pass.setVertexBuffer(0, vertexBuffer);
    pass.draw(vertexCount);
    return offset + packetBytes;
  }

  private handleTextureUpload(view: DataView, offset: number): number {
    const headerBytes = 16;
    if (offset + headerBytes > view.byteLength) {
      return Math.min(view.byteLength, offset + headerBytes);
    }
    const textureId = view.getInt32(offset, true);
    const width = view.getUint32(offset + 4, true);
    const height = view.getUint32(offset + 8, true);
    const dataLength = view.getUint32(offset + 12, true);
    const packetBytes = headerBytes + dataLength;
    if (
      textureId <= 0 ||
      width === 0 ||
      height === 0 ||
      dataLength !== width * height * 4 ||
      offset + packetBytes > view.byteLength
    ) {
      return Math.min(view.byteLength, offset + packetBytes);
    }
    this.textures.get(textureId)?.destroy();
    const texture = this.device.createTexture({
      size: { width, height },
      format: 'rgba8unorm',
      usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST,
    });
    this.device.queue.writeTexture(
      { texture },
      new Uint8Array(
        view.buffer,
        view.byteOffset + offset + headerBytes,
        dataLength,
      ),
      { bytesPerRow: width * 4, rowsPerImage: height },
      { width, height },
    );
    this.textures.set(textureId, texture);
    return offset + packetBytes;
  }

  private handleTexturedDraw(view: DataView, offset: number): number {
    const headerBytes = 128;
    if (offset + headerBytes > view.byteLength) {
      return Math.min(view.byteLength, offset + headerBytes);
    }
    const vertexCount = view.getUint32(offset, true);
    const textureId = view.getInt32(offset + 4, true);
    const packedMaterial = view.getUint32(offset + 8, true);
    const cullBackFaces = packedMaterial & 0xff;
    const blendMode = (packedMaterial >>> 8) & 0xff;
    const clipX = view.getInt32(offset + 12, true);
    const clipY = view.getInt32(offset + 16, true);
    const clipWidth = view.getInt32(offset + 20, true);
    const clipHeight = view.getInt32(offset + 24, true);
    const packetBytes = headerBytes + vertexCount * 44;
    const texture = this.textures.get(textureId);
    const texturePipelines = this.texturePipelines[cullBackFaces];
    const pipeline =
      texturePipelines?.[blendMode] ?? texturePipelines?.[BLEND_NORMAL];
    if (
      !this.target ||
      !texture ||
      !pipeline ||
      vertexCount === 0 ||
      clipWidth <= 0 ||
      clipHeight <= 0 ||
      offset + packetBytes > view.byteLength
    ) {
      return Math.min(view.byteLength, offset + packetBytes);
    }
    const sphereTextureId = view.getInt32(offset + 124, true);
    const sphereTexture = this.textures.get(sphereTextureId) ?? texture;
    const vertices = new Uint8Array(
      view.buffer,
      view.byteOffset + offset + headerBytes,
      vertexCount * 44,
    );
    const vertexBuffer = this.device.createBuffer({
      size: vertices.byteLength,
      usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
    });
    this.resources.push(vertexBuffer);
    this.device.queue.writeBuffer(vertexBuffer, 0, vertices);
    const lightingBuffer = this.device.createBuffer({
      size: 96,
      usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
    });
    this.resources.push(lightingBuffer);
    this.device.queue.writeBuffer(
      lightingBuffer,
      0,
      new Uint8Array(view.buffer, view.byteOffset + offset + 28, 96),
    );

    const pass = this.currentPass();
    const scale = this.surfaceScale;
    const targetWidth = Math.max(1, Math.round(this.target.width * scale));
    const targetHeight = Math.max(1, Math.round(this.target.height * scale));
    const x0 = Math.min(targetWidth, Math.max(0, Math.round(clipX * scale)));
    const y0 = Math.min(targetHeight, Math.max(0, Math.round(clipY * scale)));
    const x1 = Math.min(
      targetWidth,
      Math.max(0, Math.round((clipX + clipWidth) * scale)),
    );
    const y1 = Math.min(
      targetHeight,
      Math.max(0, Math.round((clipY + clipHeight) * scale)),
    );
    pass.setScissorRect(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    pass.setPipeline(pipeline);
    if (blendMode === BLEND_HALF) {
      pass.setBlendConstant({ r: 0.5, g: 0.5, b: 0.5, a: 1 });
    }
    pass.setBindGroup(
      0,
      this.device.createBindGroup({
        layout: this.textureBindGroupLayout,
        entries: [
          { binding: 0, resource: texture.createView() },
          { binding: 1, resource: sphereTexture.createView() },
        ],
      }),
    );
    pass.setBindGroup(
      1,
      this.device.createBindGroup({
        layout: this.lightingBindGroupLayout,
        entries: [{ binding: 0, resource: { buffer: lightingBuffer } }],
      }),
    );
    pass.setVertexBuffer(0, vertexBuffer);
    pass.draw(vertexCount);
    return offset + packetBytes;
  }

  private handleColorMaterialDraw(view: DataView, offset: number): number {
    const headerBytes = 124;
    if (offset + headerBytes > view.byteLength) {
      return Math.min(view.byteLength, offset + headerBytes);
    }
    const vertexCount = view.getUint32(offset, true);
    const blendMode = view.getUint32(offset + 4, true);
    const clipX = view.getInt32(offset + 8, true);
    const clipY = view.getInt32(offset + 12, true);
    const clipWidth = view.getInt32(offset + 16, true);
    const clipHeight = view.getInt32(offset + 20, true);
    const packetBytes = headerBytes + vertexCount * 48;
    const pipeline =
      this.colorMaterialPipelines[blendMode] ??
      this.colorMaterialPipelines[BLEND_NORMAL];
    if (
      !this.target ||
      !pipeline ||
      vertexCount === 0 ||
      clipWidth <= 0 ||
      clipHeight <= 0 ||
      offset + packetBytes > view.byteLength
    ) {
      return Math.min(view.byteLength, offset + packetBytes);
    }
    const sphereTextureId = view.getInt32(offset + 120, true);
    const vertices = new Uint8Array(
      view.buffer,
      view.byteOffset + offset + headerBytes,
      vertexCount * 48,
    );
    const vertexBuffer = this.device.createBuffer({
      size: vertices.byteLength,
      usage: GPUBufferUsage.VERTEX | GPUBufferUsage.COPY_DST,
    });
    const lightingBuffer = this.device.createBuffer({
      size: 96,
      usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
    });
    this.resources.push(vertexBuffer, lightingBuffer);
    this.device.queue.writeBuffer(vertexBuffer, 0, vertices);
    this.device.queue.writeBuffer(
      lightingBuffer,
      0,
      new Uint8Array(view.buffer, view.byteOffset + offset + 24, 96),
    );

    const pass = this.currentPass();
    const scale = this.surfaceScale;
    const targetWidth = Math.max(1, Math.round(this.target.width * scale));
    const targetHeight = Math.max(1, Math.round(this.target.height * scale));
    const x0 = Math.min(targetWidth, Math.max(0, Math.round(clipX * scale)));
    const y0 = Math.min(targetHeight, Math.max(0, Math.round(clipY * scale)));
    const x1 = Math.min(
      targetWidth,
      Math.max(0, Math.round((clipX + clipWidth) * scale)),
    );
    const y1 = Math.min(
      targetHeight,
      Math.max(0, Math.round((clipY + clipHeight) * scale)),
    );
    pass.setScissorRect(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    pass.setPipeline(pipeline);
    if (blendMode === BLEND_HALF) {
      pass.setBlendConstant({ r: 0.5, g: 0.5, b: 0.5, a: 1 });
    }
    pass.setBindGroup(
      0,
      this.device.createBindGroup({
        layout: this.colorMaterialBindGroupLayout,
        entries: [
          {
            binding: 0,
            resource: (
              this.textures.get(sphereTextureId) ?? this.neutralTexture
            ).createView(),
          },
        ],
      }),
    );
    pass.setBindGroup(
      1,
      this.device.createBindGroup({
        layout: this.lightingBindGroupLayout,
        entries: [{ binding: 0, resource: { buffer: lightingBuffer } }],
      }),
    );
    pass.setVertexBuffer(0, vertexBuffer);
    pass.draw(vertexCount);
    return offset + packetBytes;
  }

  private handleTextureDelete(view: DataView, offset: number): number {
    if (offset + 4 > view.byteLength) {
      return Math.min(view.byteLength, offset + 4);
    }
    const textureId = view.getInt32(offset, true);
    this.textures.get(textureId)?.destroy();
    this.textures.delete(textureId);
    return offset + 4;
  }

  private resetTextures(): void {
    for (const texture of this.textures.values()) {
      texture.destroy();
    }
    this.textures.clear();
  }

  private clearDepth(): void {
    if (!this.target || !this.encoder) {
      return;
    }
    this.pass?.end();
    this.pass = null;
    this.appendDepthClearPass(this.encoder);
  }

  private appendDepthClearPass(encoder: GPUCommandEncoder): void {
    if (!this.target) {
      return;
    }
    const pass = encoder.beginRenderPass({
      colorAttachments: [
        {
          view: this.target.texture.createView(),
          loadOp: 'load',
          storeOp: 'store',
        },
      ],
      depthStencilAttachment: {
        view: this.target.depthTexture.createView(),
        depthClearValue: 1,
        depthLoadOp: 'clear',
        depthStoreOp: 'store',
      },
    });
    pass.end();
  }

  private currentPass(): GPURenderPassEncoder {
    if (this.pass) {
      return this.pass;
    }
    if (!this.target) {
      throw new Error('Mascot draw issued without an active render target');
    }
    this.encoder ??= this.device.createCommandEncoder();
    this.pass = this.encoder.beginRenderPass({
      colorAttachments: [
        {
          view: this.target.texture.createView(),
          loadOp: 'load',
          storeOp: 'store',
        },
      ],
      depthStencilAttachment: {
        view: this.target.depthTexture.createView(),
        depthClearValue: 1,
        depthLoadOp: 'clear',
        depthStoreOp: 'store',
      },
    });
    return this.pass;
  }
}
