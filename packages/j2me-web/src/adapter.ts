import type {
  J2MEBluetoothHost,
  J2MEPerformanceOptions,
  J2MEPerformanceSnapshot,
  JarEntry,
  JvmRewindSnapshot,
} from '@jsjvm/j2me-simulator';
import {
  J2MESimulator as CoreSimulator,
  J2MEPerformanceCollector,
} from '@jsjvm/j2me-simulator';
import type {
  J2MEDateInputResult,
  J2METextInputResult,
  J2MPlatformBridge,
} from '@jsjvm/j2me-simulator/env';
import { type FontConfig, FontManager } from './font-manager';
import {
  createCanvasHooks,
  setCanvasPointerCapabilities,
} from './hooks/canvas-hooks';
import { createDisplayHooks } from './hooks/display-hooks';
import { createFontHooks } from './hooks/font-hooks';
import { createGameCanvasHooks } from './hooks/game-canvas-hooks';
import { createImageHooks } from './hooks/image-hooks';
import { createInputHooks } from './hooks/input-hooks';
import { createMediaHooks } from './hooks/media-hooks';
import {
  createNetworkHooks,
  type NetworkHooksOptions,
} from './hooks/network-hooks';
import { createRMSHooks } from './hooks/rms-hooks';
import { ImageManager } from './image-manager';
import { M3gRenderer } from './m3g-renderer';
import { MascotRenderer } from './mascot-renderer';
import { WebGPUCanvasPresenter } from './webgpu-canvas-presenter';
import { WebGPUGraphicsRenderer } from './webgpu-graphics-renderer';

export interface J2MEWebLifecycleCallbacks {
  onDestroyed?: () => void;
  onPaused?: () => void;
  onResumed?: () => void;
}

export interface J2MESoftKeyLabels {
  left?: string;
  right?: string;
}

interface J2METextInputRequest {
  show: boolean;
  textFieldHandle?: number;
  constraints?: number;
  maxSize?: number;
  text?: string;
}

interface J2MEDateInputRequest {
  show: boolean;
  dateFieldHandle?: number;
  mode?: number;
  initialized?: boolean;
  valueMillis?: number;
}

const DATEFIELD_DATE = 1;
const DATEFIELD_TIME = 2;
const DATEFIELD_DATE_TIME = 3;

const TEXTFIELD_NUMERIC = 2;
const TEXTFIELD_PHONENUMBER = 3;
const TEXTFIELD_DECIMAL = 5;
const TEXTFIELD_CONSTRAINT_MASK = 0xffff;

export interface J2MEWebOptions {
  canvas?: HTMLCanvasElement;
  container?: HTMLElement;
  jar: ArrayBuffer | JarEntry[];
  autoStart?: boolean;
  midletId?: string;
  lifecycleCallbacks?: J2MEWebLifecycleCallbacks;
  midletNumber?: number;
  network?: NetworkHooksOptions;
  bluetooth?: J2MEBluetoothHost;
  font?: FontConfig;
  performanceMonitor?: J2MEPerformanceOptions;
  onSoftKeyLabelsChanged?: (labels: J2MESoftKeyLabels) => void;
}

export class J2MEWeb {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private simulator: CoreSimulator | null = null;
  private fontManager: FontManager;
  private imageManager: ImageManager;
  private hooks!: J2MPlatformBridge;
  private gameCanvasHooksDestroy?: () => void;
  private midletId?: string;
  private mediaHooks!: ReturnType<typeof createMediaHooks>;
  private initPromise: Promise<void>;
  private webgpuGraphicsRenderer!: WebGPUGraphicsRenderer;
  private m3gRenderer!: M3gRenderer;
  private mascotRenderer!: MascotRenderer;
  private speedMultiplier = 1;
  private musicFollowSpeed = false;
  private softKeyLabels: J2MESoftKeyLabels = {};
  private softKeyLabelsHandler?: (labels: J2MESoftKeyLabels) => void;
  private nativeTextInputOverlay: HTMLDivElement | null = null;
  private nativeTextInput: HTMLTextAreaElement | null = null;
  private nativeTextInputResolve:
    | ((value: J2METextInputResult | null) => void)
    | null = null;
  private nativeDateInputOverlay: HTMLDivElement | null = null;
  private nativeDateInput: HTMLInputElement | null = null;
  private nativeDateInputResolve:
    | ((value: J2MEDateInputResult | null) => void)
    | null = null;
  private readonly performanceCollector: J2MEPerformanceCollector | null;

  constructor(private options: J2MEWebOptions) {
    this.performanceCollector = options.performanceMonitor?.enabled
      ? new J2MEPerformanceCollector(options.performanceMonitor)
      : null;
    this.midletId = options.midletId;
    this.canvas = this.resolveCanvas(options);
    this.ctx = this.createMetricsContext(this.canvas);
    this.fontManager = new FontManager(this.ctx, options.font);
    void this.fontManager.prewarmMetricsOnce();
    this.imageManager = new ImageManager(
      () => this.webgpuGraphicsRenderer?.getSurfaceScale() ?? 2,
    );
    this.initPromise = this.initializeRenderer();

    if (options.autoStart) {
      this.start();
    }
  }

  private createMetricsContext(
    canvas: HTMLCanvasElement,
  ): CanvasRenderingContext2D {
    const metricsCanvas = document.createElement('canvas');
    metricsCanvas.width = canvas.width;
    metricsCanvas.height = canvas.height;
    const ctx = metricsCanvas.getContext('2d');
    if (!ctx) {
      throw new Error('Failed to create 2D context for font metrics');
    }
    return ctx;
  }

  private resolveCanvas(options: J2MEWebOptions): HTMLCanvasElement {
    if (options.canvas) {
      return options.canvas;
    }
    if (!options.container) {
      throw new Error('Either container or canvas must be provided');
    }
    const canvas = document.createElement('canvas');
    canvas.style.display = 'block';
    canvas.width = 240;
    canvas.height = 320;
    options.container.appendChild(canvas);
    return canvas;
  }

  private async initializeRenderer(): Promise<void> {
    const presenter = new WebGPUCanvasPresenter(
      this.canvas,
      this.performanceCollector,
    );
    const { device, context } = await presenter.initialize();
    const scale = presenter.getRenderScale();
    this.webgpuGraphicsRenderer = new WebGPUGraphicsRenderer(
      device,
      (texture, width, height, encoder) => {
        this.m3gRenderer?.flushFrame();
        presenter.presentTexture(texture, width, height, encoder);
      },
      this.fontManager,
      scale,
      this.performanceCollector,
      () => !this.m3gRenderer?.hasPendingFrame(),
    );
    this.performanceCollector?.configureEnvironment({
      backend: 'rust',
      logicalWidth: presenter.getLogicalWidth(),
      logicalHeight: presenter.getLogicalHeight(),
      physicalWidth: this.canvas.width,
      physicalHeight: this.canvas.height,
      renderScale: scale,
      gpuTimestampSupported: device.features.has('timestamp-query'),
    });
    this.mascotRenderer = new MascotRenderer(
      device,
      (surfaceId: number) =>
        this.webgpuGraphicsRenderer.getSurfaceTexture(surfaceId),
      scale,
    );
    this.m3gRenderer = new M3gRenderer(device, context);
    this.m3gRenderer.setSurfaceTextureResolver((surfaceId: number) => {
      return this.webgpuGraphicsRenderer.getSurfaceTexture(surfaceId);
    }, scale);
    this.hooks = this.createHooks(
      presenter.getLogicalWidth(),
      presenter.getLogicalHeight(),
    );
  }

  private createHooks(width: number, height: number): J2MPlatformBridge {
    const webgpuRenderer = this.webgpuGraphicsRenderer;
    const displayHooks = createDisplayHooks(this.canvas, width, height);
    const graphicsHooks = { graphics: webgpuRenderer };
    const fontHooks = createFontHooks(this.fontManager);
    const imageHooks = createImageHooks(this.imageManager, webgpuRenderer);
    const inputHooks = createInputHooks();
    const canvasHooks = createCanvasHooks();
    const gameCanvasHooksResult = createGameCanvasHooks(
      webgpuRenderer.present.bind(webgpuRenderer),
    );
    this.mediaHooks = createMediaHooks({
      getSpeed: () => this.getMediaSpeed(),
    });
    const mediaHooks = this.mediaHooks;
    const networkHooks = createNetworkHooks(this.options.network);
    const rmsHooks = createRMSHooks(this.midletId);
    this.gameCanvasHooksDestroy = gameCanvasHooksResult.destroy;

    const hooks = {
      print: (text: string) => console.log('[J2ME]', text),
      println: (text: string) => console.log('[J2ME]', text),
      j2me: {
        bluetooth: this.options.bluetooth,
        ...displayHooks,
        ...graphicsHooks,
        ...fontHooks,
        ...imageHooks,
        ...inputHooks,
        ...canvasHooks,
        ...gameCanvasHooksResult,
        ...mediaHooks,
        ...networkHooks,
        ...rmsHooks,
        m3g: {
          handleCommand: (cmdId: number, view: DataView, offset: number) => {
            // Any pending LCDUI draws must be submitted before the next
            // M3G op paints on the same canvas, so the time-ordering of
            // the underlying command stream is preserved on the GPU.
            this.webgpuGraphicsRenderer.flushPendingDraws();
            if (cmdId >= 190 && cmdId <= 199) {
              return this.mascotRenderer.handleCommand(cmdId, view, offset);
            }
            return this.m3gRenderer.handleCommand(cmdId, view, offset);
          },
          waitForGpu: () =>
            this.mascotRenderer.waitForGpu() ?? this.m3gRenderer.waitForGpu(),
          readImageTargetPixels: (
            imageHandle: number,
            width: number,
            height: number,
          ) =>
            this.m3gRenderer.readImageTargetPixels(imageHandle, width, height),
        },
        lcdUi: {
          updateSoftKeyLabels: (labels: J2MESoftKeyLabels) => {
            this.setSoftKeyLabels(labels);
          },
          textInput: (request: J2METextInputRequest) => {
            return this.updateNativeTextInput(request);
          },
          dateInput: (request: J2MEDateInputRequest) => {
            return this.updateNativeDateInput(request);
          },
        },
        midlet: {
          onStateChanged: (state: 'active' | 'paused' | 'destroyed') => {
            this.handleMIDletStateChanged(state);
          },
          onResumeRequested: () => {
            this.handleResumeRequest();
          },
        },
      },
    } as any;

    return hooks;
  }

  async start(): Promise<void> {
    await this.initPromise;
    if (this.simulator) {
      return;
    }

    this.simulator = new CoreSimulator({
      jar: Array.isArray(this.options.jar)
        ? this.options.jar
        : new Uint8Array(this.options.jar),
      hooks: this.hooks,
      performanceCollector: this.performanceCollector ?? undefined,
      midletNumber: this.options.midletNumber,
    });

    this.setupInputHandlers();
    setCanvasPointerCapabilities({
      events: 'ontouchstart' in window || 'onmousedown' in window,
      motion: 'ontouchmove' in window || 'onmousemove' in window,
      repeat: true,
    });

    await this.simulator.initialize();
    return this.simulator.start();
  }

  private setupInputHandlers(): void {
    const inputHooks = (this.hooks as any).j2me.input as {
      onKeyPressed: (handler: (keyCode: number) => void) => void;
      onKeyReleased: (handler: (keyCode: number) => void) => void;
      onKeyRepeated: (handler: (keyCode: number) => void) => void;
      onPointerPressed: (handler: (x: number, y: number) => void) => void;
      onPointerReleased: (handler: (x: number, y: number) => void) => void;
      onPointerDragged: (handler: (x: number, y: number) => void) => void;
    };

    inputHooks.onKeyPressed((keyCode: number) => {
      this.simulator?.keyPressed(keyCode);
    });

    inputHooks.onKeyReleased((keyCode: number) => {
      this.simulator?.keyReleased(keyCode);
    });

    inputHooks.onKeyRepeated((keyCode: number) => {
      this.simulator?.keyRepeated(keyCode);
    });

    inputHooks.onPointerPressed((x: number, y: number) => {
      this.simulator?.pointerPressed(x, y);
    });

    inputHooks.onPointerReleased((x: number, y: number) => {
      this.simulator?.pointerReleased(x, y);
    });

    inputHooks.onPointerDragged((x: number, y: number) => {
      this.simulator?.pointerDragged(x, y);
    });

    setCanvasPointerCapabilities({ events: true, motion: true });

    const trigger = (this.hooks as any).j2me.input as {
      triggerKeyPressed: (keyCode: number) => void;
      triggerKeyReleased: (keyCode: number) => void;
      triggerPointerPressed: (x: number, y: number) => void;
      triggerPointerReleased: (x: number, y: number) => void;
      triggerPointerDragged: (x: number, y: number) => void;
    };

    let pressed = false;
    const toLogical = (clientX: number, clientY: number) => {
      const rect = this.canvas.getBoundingClientRect();
      const x = Math.floor(clientX - rect.left);
      const y = Math.floor(clientY - rect.top);
      return { x, y };
    };

    this.canvas.addEventListener('mousedown', (e) => {
      pressed = true;
      const { x, y } = toLogical(e.clientX, e.clientY);
      trigger.triggerPointerPressed(x, y);
      e.preventDefault();
    });

    this.canvas.addEventListener('mousemove', (e) => {
      if (!pressed) return;
      const { x, y } = toLogical(e.clientX, e.clientY);
      const now = Date.now();
      (this as any)._dragLast = (this as any)._dragLast ?? 0;
      const interval = (this as any)._dragInterval ?? 30;
      if (now - (this as any)._dragLast >= interval) {
        (this as any)._dragLast = now;
        trigger.triggerPointerDragged(x, y);
      }
      e.preventDefault();
    });

    this.canvas.addEventListener('mouseup', (e) => {
      if (!pressed) return;
      pressed = false;
      const { x, y } = toLogical(e.clientX, e.clientY);
      trigger.triggerPointerReleased(x, y);
      e.preventDefault();
    });

    this.canvas.addEventListener('mouseleave', (e) => {
      if (!pressed) return;
      pressed = false;
      const { x, y } = toLogical(
        (e as MouseEvent).clientX,
        (e as MouseEvent).clientY,
      );
      trigger.triggerPointerReleased(x, y);
      e.preventDefault();
    });

    this.canvas.addEventListener(
      'touchstart',
      (e) => {
        pressed = true;
        const t = e.touches[0];
        const { x, y } = toLogical(t.clientX, t.clientY);
        trigger.triggerPointerPressed(x, y);
        e.preventDefault();
      },
      { passive: false },
    );

    this.canvas.addEventListener(
      'touchmove',
      (e) => {
        if (!pressed) return;
        const t = e.touches[0];
        const { x, y } = toLogical(t.clientX, t.clientY);
        const now = Date.now();
        (this as any)._dragLast = (this as any)._dragLast ?? 0;
        const interval = (this as any)._dragInterval ?? 30;
        if (now - (this as any)._dragLast >= interval) {
          (this as any)._dragLast = now;
          trigger.triggerPointerDragged(x, y);
        }
        e.preventDefault();
      },
      { passive: false },
    );

    this.canvas.addEventListener(
      'touchend',
      (e) => {
        if (!pressed) return;
        pressed = false;
        const t = e.changedTouches[0];
        const { x, y } = toLogical(t.clientX, t.clientY);
        trigger.triggerPointerReleased(x, y);
        e.preventDefault();
      },
      { passive: false },
    );
  }

  private handleMIDletStateChanged(
    state: 'active' | 'paused' | 'destroyed',
  ): void {
    if (state === 'destroyed') {
      this.options.lifecycleCallbacks?.onDestroyed?.();
      if (this.gameCanvasHooksDestroy) {
        this.gameCanvasHooksDestroy();
        this.gameCanvasHooksDestroy = undefined;
      }
      this.simulator = null;
    } else if (state === 'paused') {
      this.options.lifecycleCallbacks?.onPaused?.();
    } else if (state === 'active') {
      this.options.lifecycleCallbacks?.onResumed?.();
    }
  }

  private handleResumeRequest(): void {
    if (this.simulator) {
      this.simulator.start().catch((err) => {
        console.error('[J2MEWeb] Failed to resume MIDlet:', err);
      });
    }
  }

  async pause(): Promise<void> {
    this.mediaHooks?.pauseAll();
    if (this.simulator) {
      await this.simulator.pause();
    }
  }

  async resume(): Promise<void> {
    if (this.simulator) {
      await this.simulator.start();
    }
    this.mediaHooks?.resumeAll();
  }

  async suspendExecution(): Promise<void> {
    this.mediaHooks?.pauseAll();
    try {
      await this.simulator?.suspendExecution();
    } catch (error) {
      this.mediaHooks?.resumeAll();
      throw error;
    }
  }

  async resumeExecution(): Promise<void> {
    await this.simulator?.resumeExecution();
    this.mediaHooks?.resumeAll();
  }

  async stop(): Promise<void> {
    if (this.simulator) {
      await this.simulator.destroy();
      this.simulator = null;
    }
    if (this.gameCanvasHooksDestroy) {
      this.gameCanvasHooksDestroy();
    }
    this.hideNativeTextInput();
  }

  getSimulator(): CoreSimulator | null {
    return this.simulator;
  }

  getPerformanceSnapshot(): J2MEPerformanceSnapshot | null {
    return this.performanceCollector?.snapshot() ?? null;
  }

  resetPerformanceCounters(): void {
    this.performanceCollector?.reset();
  }

  get input() {
    return this.hooks.j2me!.input as {
      triggerKeyPressed: (keyCode: number) => void;
      triggerKeyReleased: (keyCode: number) => void;
      triggerPointerPressed: (x: number, y: number) => void;
      triggerPointerReleased: (x: number, y: number) => void;
      triggerPointerDragged: (x: number, y: number) => void;
    };
  }

  screenshot(): string {
    return this.canvas.toDataURL('image/png');
  }

  downloadScreenshot(filename?: string): void {
    const link = document.createElement('a');
    link.download = filename || `j2me-screenshot-${Date.now()}.png`;
    link.href = this.screenshot();
    link.click();
  }

  getCanvas(): HTMLCanvasElement {
    return this.canvas;
  }

  private updateNativeTextInput(
    request: J2METextInputRequest,
  ): Promise<J2METextInputResult | null> {
    if (!request.show) {
      this.hideNativeTextInput();
      return Promise.resolve(null);
    }

    const handle = request.textFieldHandle;
    if (typeof handle !== 'number') {
      return Promise.resolve(null);
    }
    this.nativeTextInputResolve?.(null);
    this.nativeTextInputResolve = null;

    const input = this.nativeTextInput ?? document.createElement('textarea');
    if (!this.nativeTextInputOverlay) {
      this.nativeTextInput = input;
      const overlay = document.createElement('div');
      this.nativeTextInputOverlay = overlay;
      overlay.dataset.jsjvm = 'native-text-input-overlay';
      overlay.style.position = 'absolute';
      overlay.style.inset = '0';
      overlay.style.zIndex = '10000';
      overlay.style.display = 'none';
      overlay.style.alignItems = 'center';
      overlay.style.justifyContent = 'center';
      overlay.style.padding = '16px';
      overlay.style.boxSizing = 'border-box';
      overlay.style.background = 'rgba(0,0,0,0.32)';
      overlay.style.pointerEvents = 'auto';

      const panel = document.createElement('div');
      panel.style.width = 'min(86%, 320px)';
      panel.style.boxSizing = 'border-box';
      panel.style.padding = '12px';
      panel.style.borderRadius = '8px';
      panel.style.background = '#f7f7f7';
      panel.style.boxShadow = '0 8px 24px rgba(0,0,0,0.22)';
      this.configureNativeTextInputElement(input);
      panel.appendChild(input);

      overlay.appendChild(panel);
      const container = this.canvas.parentElement || this.canvas;
      container.appendChild(overlay);
    }

    input.dataset.textFieldHandle = String(handle);
    input.dataset.constraints = String(request.constraints ?? 0);
    input.dataset.initialValue = request.text ?? '';
    input.value = this.filterNativeTextInputValue(
      request.text ?? '',
      request.constraints ?? 0,
    );
    const maxSize = Math.max(0, request.maxSize ?? 0);
    input.maxLength = maxSize > 0 ? maxSize : 524288;
    this.applyNativeTextInputType(input, request.constraints ?? 0);
    input.rows = 6;
    input.style.minHeight = '132px';
    input.style.resize = 'vertical';
    input.style.lineHeight = '18px';

    if (this.nativeTextInputOverlay) {
      this.nativeTextInputOverlay.style.display = 'flex';
    }
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);
    return new Promise((resolve) => {
      this.nativeTextInputResolve = resolve;
    });
  }

  private configureNativeTextInputElement(input: HTMLTextAreaElement): void {
    input.dataset.jsjvm = 'native-text-input';
    input.style.boxSizing = 'border-box';
    input.style.margin = '0';
    input.style.width = '100%';
    input.style.padding = '8px 10px';
    input.style.border = '1px solid #4f8fd8';
    input.style.outline = '1px solid #d7ecff';
    input.style.borderRadius = '6px';
    input.style.background = '#ffffff';
    input.style.color = '#202020';
    input.style.font = '14px sans-serif';
    input.style.pointerEvents = 'auto';
    input.addEventListener('input', () => {
      const constraints = Number(input.dataset.constraints || '0');
      const filtered = this.filterNativeTextInputValue(
        input.value,
        constraints,
      );
      if (filtered !== input.value) {
        const selectionStart = input.selectionStart ?? input.value.length;
        const removedBeforeCursor =
          input.value.slice(0, selectionStart).length -
          this.filterNativeTextInputValue(
            input.value.slice(0, selectionStart),
            constraints,
          ).length;
        input.value = filtered;
        const nextCursor = Math.max(0, selectionStart - removedBeforeCursor);
        input.setSelectionRange(nextCursor, nextCursor);
      }
    });
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        this.hideNativeTextInput(false);
        event.preventDefault();
      }
    });
    input.addEventListener('blur', () => {
      if (this.nativeTextInputOverlay?.style.display === 'flex') {
        this.hideNativeTextInput(true);
      }
    });
  }

  private hideNativeTextInput(commit = false): void {
    if (!this.nativeTextInputOverlay || !this.nativeTextInput) {
      return;
    }
    const input = this.nativeTextInput;
    const handle = Number(input.dataset.textFieldHandle || '0');
    const initialValue = input.dataset.initialValue ?? '';
    const value = input.value;
    input.dataset.textFieldHandle = '';
    input.dataset.constraints = '';
    input.dataset.initialValue = '';
    this.nativeTextInputOverlay.style.display = 'none';
    input.blur();
    const resolve = this.nativeTextInputResolve;
    this.nativeTextInputResolve = null;
    if (commit && handle && value !== initialValue) {
      resolve?.({ text: value });
    } else {
      resolve?.(null);
    }
  }

  private updateNativeDateInput(
    request: J2MEDateInputRequest,
  ): Promise<J2MEDateInputResult | null> {
    if (!request.show) {
      this.hideNativeDateInput(false);
      return Promise.resolve(null);
    }

    const handle = request.dateFieldHandle;
    const mode = request.mode ?? DATEFIELD_DATE;
    if (typeof handle !== 'number') {
      return Promise.resolve(null);
    }
    this.nativeDateInputResolve?.(null);
    this.nativeDateInputResolve = null;

    const input = this.nativeDateInput ?? document.createElement('input');
    if (!this.nativeDateInputOverlay) {
      this.nativeDateInput = input;
      const overlay = document.createElement('div');
      this.nativeDateInputOverlay = overlay;
      overlay.dataset.jsjvm = 'native-date-input-overlay';
      overlay.style.position = 'absolute';
      overlay.style.inset = '0';
      overlay.style.zIndex = '10000';
      overlay.style.display = 'none';
      overlay.style.alignItems = 'center';
      overlay.style.justifyContent = 'center';
      overlay.style.padding = '16px';
      overlay.style.boxSizing = 'border-box';
      overlay.style.background = 'rgba(0,0,0,0.32)';
      overlay.style.pointerEvents = 'auto';

      const panel = document.createElement('div');
      panel.style.width = 'min(86%, 320px)';
      panel.style.boxSizing = 'border-box';
      panel.style.padding = '12px';
      panel.style.borderRadius = '8px';
      panel.style.background = '#f7f7f7';
      panel.style.boxShadow = '0 8px 24px rgba(0,0,0,0.22)';
      this.configureNativeDateInputElement(input);
      panel.appendChild(input);

      overlay.appendChild(panel);
      const container = this.canvas.parentElement || this.canvas;
      container.appendChild(overlay);
    }

    input.dataset.dateFieldHandle = String(handle);
    input.dataset.mode = String(mode);
    input.dataset.initialized = request.initialized ? '1' : '0';
    input.dataset.initialValueMillis = String(request.valueMillis ?? 0);
    input.type = this.getNativeDateInputType(mode);
    input.value = request.initialized
      ? this.formatNativeDateInputValue(mode, request.valueMillis ?? 0)
      : '';

    if (this.nativeDateInputOverlay) {
      this.nativeDateInputOverlay.style.display = 'flex';
    }
    input.focus();
    if (typeof input.showPicker === 'function') {
      try {
        input.showPicker();
      } catch {
        // showPicker is user-activation/browser-policy dependent.
      }
    }
    return new Promise((resolve) => {
      this.nativeDateInputResolve = resolve;
    });
  }

  private configureNativeDateInputElement(input: HTMLInputElement): void {
    input.dataset.jsjvm = 'native-date-input';
    input.style.boxSizing = 'border-box';
    input.style.margin = '0';
    input.style.width = '100%';
    input.style.padding = '8px 10px';
    input.style.border = '1px solid #4f8fd8';
    input.style.outline = '1px solid #d7ecff';
    input.style.borderRadius = '6px';
    input.style.background = '#ffffff';
    input.style.color = '#202020';
    input.style.font = '14px sans-serif';
    input.style.pointerEvents = 'auto';
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        this.hideNativeDateInput(false);
        event.preventDefault();
      } else if (event.key === 'Enter') {
        this.hideNativeDateInput(true);
        event.preventDefault();
      }
    });
    input.addEventListener('change', () => {
      if (this.nativeDateInputOverlay?.style.display === 'flex') {
        this.hideNativeDateInput(true);
      }
    });
    input.addEventListener('blur', () => {
      if (this.nativeDateInputOverlay?.style.display === 'flex') {
        this.hideNativeDateInput(true);
      }
    });
  }

  private hideNativeDateInput(commit = false): void {
    if (!this.nativeDateInputOverlay || !this.nativeDateInput) {
      return;
    }
    const input = this.nativeDateInput;
    const handle = Number(input.dataset.dateFieldHandle || '0');
    const mode = Number(input.dataset.mode || String(DATEFIELD_DATE));
    const wasInitialized = input.dataset.initialized === '1';
    const initialValueMillis = Number(input.dataset.initialValueMillis || '0');
    const parsedValue = this.parseNativeDateInputValue(mode, input.value);
    const initialized = parsedValue !== null;
    const valueMillis = parsedValue ?? 0;
    input.dataset.dateFieldHandle = '';
    input.dataset.mode = '';
    input.dataset.initialized = '';
    input.dataset.initialValueMillis = '';
    this.nativeDateInputOverlay.style.display = 'none';
    input.blur();
    this.canvas.focus();
    const resolve = this.nativeDateInputResolve;
    this.nativeDateInputResolve = null;

    const changed =
      initialized !== wasInitialized ||
      (initialized && valueMillis !== initialValueMillis);
    if (commit && handle && changed) {
      resolve?.({ initialized, valueMillis });
    } else {
      resolve?.(null);
    }
  }

  private getNativeDateInputType(mode: number): string {
    if (mode === DATEFIELD_TIME) {
      return 'time';
    }
    if (mode === DATEFIELD_DATE_TIME) {
      return 'datetime-local';
    }
    return 'date';
  }

  private formatNativeDateInputValue(
    mode: number,
    valueMillis: number,
  ): string {
    const date = new Date(valueMillis);
    const yyyy = this.padDatePart(date.getFullYear(), 4);
    const mm = this.padDatePart(date.getMonth() + 1, 2);
    const dd = this.padDatePart(date.getDate(), 2);
    const hh = this.padDatePart(date.getHours(), 2);
    const mi = this.padDatePart(date.getMinutes(), 2);
    if (mode === DATEFIELD_TIME) {
      return `${hh}:${mi}`;
    }
    if (mode === DATEFIELD_DATE_TIME) {
      return `${yyyy}-${mm}-${dd}T${hh}:${mi}`;
    }
    return `${yyyy}-${mm}-${dd}`;
  }

  private parseNativeDateInputValue(
    mode: number,
    value: string,
  ): number | null {
    if (!value) {
      return null;
    }
    if (mode === DATEFIELD_TIME) {
      const parts = value.split(':');
      const hours = Number(parts[0]);
      const minutes = Number(parts[1]);
      if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
        return null;
      }
      return new Date(1970, 0, 1, hours, minutes, 0, 0).getTime();
    }
    if (mode === DATEFIELD_DATE_TIME) {
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? null : date.getTime();
    }
    const parts = value.split('-');
    if (parts.length !== 3) {
      return null;
    }
    const year = Number(parts[0]);
    const month = Number(parts[1]);
    const day = Number(parts[2]);
    if (
      !Number.isFinite(year) ||
      !Number.isFinite(month) ||
      !Number.isFinite(day)
    ) {
      return null;
    }
    return new Date(year, month - 1, day, 0, 0, 0, 0).getTime();
  }

  private padDatePart(value: number, length: number): string {
    let text = String(value);
    while (text.length < length) {
      text = `0${text}`;
    }
    return text;
  }

  private applyNativeTextInputType(
    input: HTMLTextAreaElement,
    constraints: number,
  ): void {
    const base = constraints & 0xffff;
    const password = (constraints & 0x10000) !== 0;
    input.inputMode = 'text';
    if (password) {
      return;
    }
    if (base === 1) {
      input.inputMode = 'email';
    } else if (base === 2) {
      input.inputMode = 'numeric';
    } else if (base === 3) {
      input.inputMode = 'tel';
    } else if (base === 4) {
      input.inputMode = 'url';
    } else if (base === 5) {
      input.inputMode = 'decimal';
    }
  }

  private filterNativeTextInputValue(
    value: string,
    constraints: number,
  ): string {
    const base = constraints & TEXTFIELD_CONSTRAINT_MASK;
    if (base === TEXTFIELD_NUMERIC) {
      return this.filterNumericTextInputValue(value);
    }
    if (base === TEXTFIELD_DECIMAL) {
      return this.filterDecimalTextInputValue(value);
    }
    if (base === TEXTFIELD_PHONENUMBER) {
      return value.replace(/[^0-9#*+ \-()]/g, '');
    }
    return value;
  }

  private filterNumericTextInputValue(value: string): string {
    let result = '';
    for (let i = 0; i < value.length; i++) {
      const ch = value[i];
      if (ch >= '0' && ch <= '9') {
        result += ch;
      } else if (ch === '-' && result.length === 0) {
        result += ch;
      }
    }
    return result;
  }

  private filterDecimalTextInputValue(value: string): string {
    let result = '';
    let hasSeparator = false;
    for (let i = 0; i < value.length; i++) {
      const ch = value[i];
      if (ch >= '0' && ch <= '9') {
        result += ch;
      } else if (ch === '-' && result.length === 0) {
        result += ch;
      } else if (ch === '.' && !hasSeparator) {
        result += ch;
        hasSeparator = true;
      }
    }
    return result;
  }

  private fullscreenStyleElement: HTMLStyleElement | null = null;

  async toggleFullscreen(): Promise<void> {
    const container = this.canvas.parentElement || this.canvas;
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else {
      this.applyFullscreenStyles(container as HTMLElement);
      await container.requestFullscreen();
    }
  }

  private applyFullscreenStyles(_container: HTMLElement): void {
    if (this.fullscreenStyleElement) return;

    const canvasWidth = this.canvas.width;
    const canvasHeight = this.canvas.height;

    const styleId = 'j2me-fullscreen-style';
    this.fullscreenStyleElement = document.createElement('style');
    this.fullscreenStyleElement.id = styleId;
    this.fullscreenStyleElement.textContent = `
      *:fullscreen {
        background: #000 !important;
        display: flex !important;
        align-items: center !important;
        justify-content: center !important;
        overflow: hidden !important;
      }
      *:fullscreen canvas {
        width: min(100vw, 100vh * ${canvasWidth / canvasHeight}) !important;
        height: min(100vh, 100vw * ${canvasHeight / canvasWidth}) !important;
        image-rendering: pixelated !important;
        image-rendering: crisp-edges !important;
      }
    `;
    document.head.appendChild(this.fullscreenStyleElement);

    const handleFullscreenChange = () => {
      if (!document.fullscreenElement && this.fullscreenStyleElement) {
        this.fullscreenStyleElement.remove();
        this.fullscreenStyleElement = null;
        document.removeEventListener(
          'fullscreenchange',
          handleFullscreenChange,
        );
      }
    };
    document.addEventListener('fullscreenchange', handleFullscreenChange);
  }

  isFullscreen(): boolean {
    return document.fullscreenElement !== null;
  }

  setSpeed(multiplier: number): void {
    this.speedMultiplier = multiplier;
    this.simulator?.setSpeed(multiplier);
    this.mediaHooks?.setSpeed(this.getMediaSpeed());
  }

  getSpeed(): number {
    return this.simulator?.getSpeed() ?? this.speedMultiplier;
  }

  setMusicFollowSpeed(enabled: boolean): void {
    this.musicFollowSpeed = enabled;
    this.mediaHooks?.setSpeed(this.getMediaSpeed());
  }

  getMusicFollowSpeed(): boolean {
    return this.musicFollowSpeed;
  }

  getSoftKeyLabels(): J2MESoftKeyLabels {
    return { ...this.softKeyLabels };
  }

  onSoftKeyLabelsChanged(
    handler: (labels: J2MESoftKeyLabels) => void,
  ): () => void {
    this.softKeyLabelsHandler = handler;
    handler(this.getSoftKeyLabels());
    return () => {
      if (this.softKeyLabelsHandler === handler) {
        this.softKeyLabelsHandler = undefined;
      }
    };
  }

  private getMediaSpeed(): number {
    return this.musicFollowSpeed ? this.getSpeed() : 1;
  }

  private setSoftKeyLabels(labels: J2MESoftKeyLabels): void {
    this.softKeyLabels = labels;
    const snapshot = this.getSoftKeyLabels();
    this.options.onSoftKeyLabelsChanged?.(snapshot);
    this.softKeyLabelsHandler?.(snapshot);
  }

  dumpThreads(): string {
    return this.simulator?.dumpThreads?.() ?? '';
  }

  async saveState(): Promise<Uint8Array | null> {
    return (await this.simulator?.saveState?.()) ?? null;
  }

  async saveStateFast(): Promise<Uint8Array | null> {
    return (await this.simulator?.saveStateFast()) ?? null;
  }

  async captureRewindSnapshot(): Promise<JvmRewindSnapshot | null> {
    return (await this.simulator?.captureRewindSnapshot()) ?? null;
  }

  loadState(data: Uint8Array): void {
    this.simulator?.loadState?.(data);
  }

  restoreRewindSnapshot(snapshot: JvmRewindSnapshot): Promise<void> {
    if (!this.simulator) {
      snapshot.dispose();
      return Promise.resolve();
    }
    return this.simulator.restoreRewindSnapshot(snapshot);
  }

  getJvm() {
    return this.simulator?.getJvm() ?? null;
  }
}
