import type { J2MEPerformanceCollector } from '@jsjvm/j2me-simulator';

const VERTEX_SHADER = `
struct VertexOut {
  @builtin(position) position: vec4f,
  @location(0) uv: vec2f,
};

@vertex
fn vs_main(@builtin(vertex_index) vertexIndex: u32) -> VertexOut {
  var positions = array<vec2f, 6>(
    vec2f(-1.0, -1.0),
    vec2f(1.0, -1.0),
    vec2f(-1.0, 1.0),
    vec2f(-1.0, 1.0),
    vec2f(1.0, -1.0),
    vec2f(1.0, 1.0),
  );
  var uvs = array<vec2f, 6>(
    vec2f(0.0, 1.0),
    vec2f(1.0, 1.0),
    vec2f(0.0, 0.0),
    vec2f(0.0, 0.0),
    vec2f(1.0, 1.0),
    vec2f(1.0, 0.0),
  );

  var out: VertexOut;
  out.position = vec4f(positions[vertexIndex], 0.0, 1.0);
  out.uv = uvs[vertexIndex];
  return out;
}
`;

const FRAGMENT_SHADER = `
@group(0) @binding(0) var sourceTexture: texture_2d<f32>;
@group(0) @binding(1) var sourceSampler: sampler;

@fragment
fn fs_main(@location(0) uv: vec2f) -> @location(0) vec4f {
  return textureSample(sourceTexture, sourceSampler, uv);
}
`;

export class WebGPUCanvasPresenter {
  private device: GPUDevice | null = null;
  private context: GPUCanvasContext | null = null;
  private format: GPUTextureFormat | null = null;
  private sampler: GPUSampler | null = null;
  private pipeline: GPURenderPipeline | null = null;
  private bindGroupLayout: GPUBindGroupLayout | null = null;
  private bindGroupCache = new WeakMap<GPUTexture, GPUBindGroup>();
  private logicalWidth: number;
  private logicalHeight: number;

  constructor(
    private canvas: HTMLCanvasElement,
    private performanceCollector: J2MEPerformanceCollector | null = null,
  ) {
    this.logicalWidth = canvas.width;
    this.logicalHeight = canvas.height;
  }

  async initialize(): Promise<{
    device: GPUDevice;
    context: GPUCanvasContext;
  }> {
    const gpu = navigator.gpu;
    if (!gpu) {
      throw new Error('WebGPU is not supported');
    }

    const adapter = await gpu.requestAdapter();
    if (!adapter) {
      throw new Error('No WebGPU adapter is available');
    }

    const device = await adapter.requestDevice();
    const context = this.canvas.getContext('webgpu');
    if (!context) {
      throw new Error('Failed to create WebGPU canvas context');
    }

    this.device = device;
    this.context = context;
    this.format = gpu.getPreferredCanvasFormat();
    this.configureCanvas();
    this.createPipeline();
    this.clearToWhite();
    return { device, context };
  }

  presentTexture(
    texture: GPUTexture,
    _width: number,
    _height: number,
    encoder?: GPUCommandEncoder,
  ): void {
    if (!this.device || !this.context || !this.pipeline || !this.sampler) {
      return;
    }

    this.resizeIfNeeded();

    const startedAt = this.performanceCollector?.now() ?? 0;
    let bindGroup = this.bindGroupCache.get(texture);
    if (!bindGroup) {
      bindGroup = this.device.createBindGroup({
        layout: this.bindGroupLayout!,
        entries: [
          { binding: 0, resource: texture.createView() },
          { binding: 1, resource: this.sampler },
        ],
      });
      this.bindGroupCache.set(texture, bindGroup);
      this.performanceCollector?.addGpuCounter('bindGroupsCreated');
    }

    const ownsEncoder = !encoder;
    const targetEncoder = encoder ?? this.device.createCommandEncoder();
    const pass = targetEncoder.beginRenderPass({
      colorAttachments: [
        {
          view: this.context.getCurrentTexture().createView(),
          clearValue: { r: 1, g: 1, b: 1, a: 1 },
          loadOp: 'clear',
          storeOp: 'store',
        },
      ],
    });
    this.performanceCollector?.addGpuCounter('renderPasses');
    pass.setPipeline(this.pipeline);
    pass.setBindGroup(0, bindGroup);
    pass.draw(6);
    this.performanceCollector?.addGpuCounter('drawCalls');
    pass.end();

    if (ownsEncoder) {
      this.device.queue.submit([targetEncoder.finish()]);
      this.performanceCollector?.addGpuCounter('submits');
    }
    this.performanceCollector?.addTiming('present', startedAt);
  }

  getLogicalWidth(): number {
    return this.logicalWidth;
  }

  getLogicalHeight(): number {
    return this.logicalHeight;
  }

  getRenderScale(): number {
    return this.getRenderScaleForWindow();
  }

  private configureCanvas(): void {
    if (!this.device || !this.context || !this.format) {
      return;
    }

    const scale = this.getRenderScale();
    this.canvas.width = Math.max(1, Math.round(this.logicalWidth * scale));
    this.canvas.height = Math.max(1, Math.round(this.logicalHeight * scale));
    this.canvas.style.width = `${this.logicalWidth}px`;
    this.canvas.style.height = `${this.logicalHeight}px`;

    this.context.configure({
      device: this.device,
      format: this.format,
      alphaMode: 'opaque',
    });
  }

  private createPipeline(): void {
    if (!this.device || !this.format) {
      return;
    }

    this.sampler = this.device.createSampler({
      magFilter: 'nearest',
      minFilter: 'nearest',
    });

    this.bindGroupLayout = this.device.createBindGroupLayout({
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

    const pipelineLayout = this.device.createPipelineLayout({
      bindGroupLayouts: [this.bindGroupLayout],
    });

    this.pipeline = this.device.createRenderPipeline({
      layout: pipelineLayout,
      vertex: {
        module: this.device.createShaderModule({ code: VERTEX_SHADER }),
        entryPoint: 'vs_main',
      },
      fragment: {
        module: this.device.createShaderModule({ code: FRAGMENT_SHADER }),
        entryPoint: 'fs_main',
        targets: [{ format: this.format }],
      },
      primitive: {
        topology: 'triangle-list',
      },
    });
  }

  private clearToWhite(): void {
    if (!this.device || !this.context) {
      return;
    }

    const encoder = this.device.createCommandEncoder();
    const pass = encoder.beginRenderPass({
      colorAttachments: [
        {
          view: this.context.getCurrentTexture().createView(),
          clearValue: { r: 1, g: 1, b: 1, a: 1 },
          loadOp: 'clear',
          storeOp: 'store',
        },
      ],
    });
    pass.end();
    this.device.queue.submit([encoder.finish()]);
  }

  private resizeIfNeeded(): void {
    const scale = this.getRenderScale();
    const width = Math.max(1, Math.round(this.logicalWidth * scale));
    const height = Math.max(1, Math.round(this.logicalHeight * scale));
    if (this.canvas.width !== width || this.canvas.height !== height) {
      this.configureCanvas();
    }
  }

  private getRenderScaleForWindow(): number {
    const dpr =
      typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
    return Math.min(4, Math.max(2, Math.ceil(dpr)));
  }
}
