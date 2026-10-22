import type { PlatformBridge } from './platform';
import type { J2MEBluetoothHost } from './bluetooth';
import type { LcduiEvent, LcduiViewSnapshot } from './lcdui-types';

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

export interface PlatformImageSource {
  width: number;
  height: number;
}

// Network types
export interface NetworkHttpRequest {
  url: string;
  method: 'GET' | 'POST' | 'HEAD' | 'PUT' | 'DELETE';
  headers?: Record<string, string>;
  body?: Uint8Array;
  timeout?: number;
}

export interface NetworkHttpResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: Uint8Array;
}

export interface NetworkSocket {
  send: (data: Uint8Array) => Promise<void>;
  receive: (maxLength: number) => Promise<Uint8Array>;
  close: () => void;
  onData?: (data: Uint8Array) => void;
  onClose?: () => void;
  onError?: (error: Error) => void;
}

export type MediaPlayerState =
  | 'UNREALIZED'
  | 'REALIZED'
  | 'PREFETCHED'
  | 'STARTED'
  | 'CLOSED';

export interface MediaPlayerRequest {
  locator?: string;
  data?: Uint8Array;
  contentType?: string;
}

export interface MediaPlayerHandle {
  play: () => Promise<void>;
  pause: () => void;
  stop: () => void;
  close: () => void;
  getDuration: () => number;
  getCurrentTime: () => number;
  setCurrentTime: (time: number) => void;
  getVolume: () => number;
  setVolume: (volume: number) => void;
  isMuted: () => boolean;
  setMuted: (muted: boolean) => void;
  setLoopCount: (count: number) => void;
  getContentType: () => string;
  onStateChange?: (state: MediaPlayerState) => void;
  onEnd?: () => void;
  onError?: (error: Error) => void;
}

export interface J2METextInputRequest {
  show: boolean;
  textFieldHandle?: number;
  constraints?: number;
  maxSize?: number;
  text?: string;
}

export interface J2METextInputResult {
  text: string;
}

export interface J2MEDateInputRequest {
  show: boolean;
  dateFieldHandle?: number;
  mode?: number;
  initialized?: boolean;
  valueMillis?: number;
}

export interface J2MEDateInputResult {
  initialized: boolean;
  valueMillis: number;
}

export interface J2MELcdUiHost {
  setVisible?: (visible: boolean) => void;
  render: (snapshot: LcduiViewSnapshot) => void;
  onEvent: (listener: (event: LcduiEvent) => void) => () => void;
  updateSoftKeyLabels?: (labels: { left?: string; right?: string }) => void;
  textInput?: (
    request: J2METextInputRequest,
  ) => Promise<J2METextInputResult | null>;
  dateInput?: (
    request: J2MEDateInputRequest,
  ) => Promise<J2MEDateInputResult | null>;
}

export interface J2MEOnlyPlatformBridge<
  TImageSource extends PlatformImageSource = PlatformImageSource,
> {
  j2me?: {
    graphics?: {
      createSurface?: (
        surfaceId: number,
        width: number,
        height: number,
      ) => void;
      destroySurface?: (surfaceId: number) => void;
      getSurfaceCanvas?: (surfaceId: number) => TImageSource | undefined;
      importImageToSurface?: (
        surfaceId: number,
        source: TImageSource,
      ) => void | Promise<void>;
      flush?: (surfaceId: number) => void;
      drawShapeBatch?: (
        pipeline: number,
        stride: number,
        vertices: Float32Array,
        state: GraphicsRenderState,
      ) => void;
      scheduleRepaint?: (callback: () => void) => void;
      drawString?: (
        text: string,
        x: number,
        y: number,
        anchor: number,
        state: GraphicsRenderState,
      ) => void;
      drawLine?: (
        x1: number,
        y1: number,
        x2: number,
        y2: number,
        state: GraphicsRenderState,
      ) => void;
      drawRect?: (
        x: number,
        y: number,
        width: number,
        height: number,
        state: GraphicsRenderState,
      ) => void;
      fillRect?: (
        x: number,
        y: number,
        width: number,
        height: number,
        state: GraphicsRenderState,
      ) => void | Promise<void>;
      copyArea?: (
        x: number,
        y: number,
        width: number,
        height: number,
        dx: number,
        dy: number,
        anchor: number,
        state: GraphicsRenderState,
      ) => void;
      drawRGBA?: (
        rgbaData: Uint8Array,
        x: number,
        y: number,
        width: number,
        height: number,
        state: GraphicsRenderState,
      ) => void;
      fillTriangle?: (
        x1: number,
        y1: number,
        x2: number,
        y2: number,
        x3: number,
        y3: number,
        state: GraphicsRenderState,
      ) => void;
      drawRoundRect?: (
        x: number,
        y: number,
        width: number,
        height: number,
        arcWidth: number,
        arcHeight: number,
        state: GraphicsRenderState,
      ) => void;
      fillRoundRect?: (
        x: number,
        y: number,
        width: number,
        height: number,
        arcWidth: number,
        arcHeight: number,
        state: GraphicsRenderState,
      ) => void;
      drawArc?: (
        x: number,
        y: number,
        width: number,
        height: number,
        startAngle: number,
        arcAngle: number,
        state: GraphicsRenderState,
      ) => void;
      fillArc?: (
        x: number,
        y: number,
        width: number,
        height: number,
        startAngle: number,
        arcAngle: number,
        state: GraphicsRenderState,
      ) => void;
      drawRegion?: (
        source: TImageSource,
        sx: number,
        sy: number,
        width: number,
        height: number,
        transform: number,
        dx: number,
        dy: number,
        anchor: number,
        state: GraphicsRenderState,
      ) => void;
      drawImageBatch?: (
        source: TImageSource,
        draws: Int32Array,
        state: GraphicsRenderState,
      ) => void;
      drawPolygon?: (
        xPoints: number[],
        xOffset: number,
        yPoints: number[],
        yOffset: number,
        nPoints: number,
        state: GraphicsRenderState,
      ) => void;
      fillPolygon?: (
        xPoints: number[],
        xOffset: number,
        yPoints: number[],
        yOffset: number,
        nPoints: number,
        state: GraphicsRenderState,
      ) => void;
      drawPixels?: (
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
      ) => void;
      getPixels?: (
        pixels: number[] | Int16Array | Uint8Array,
        offset: number,
        scanlength: number,
        x: number,
        y: number,
        width: number,
        height: number,
        state: GraphicsRenderState,
      ) => void;
    };
    font?: {
      stringWidth?: (
        text: string,
        face: number,
        style: number,
        size: number,
      ) => number;
      charWidth?: (
        charCode: number,
        face: number,
        style: number,
        size: number,
      ) => number;
      charsWidth?: (
        charCodes: number[],
        face: number,
        style: number,
        size: number,
      ) => number;
      getHeight?: (face: number, style: number, size: number) => number;
      getBaselinePosition?: (
        face: number,
        style: number,
        size: number,
      ) => number;
    };
    image?: {
      createImageFromResource?: (
        resourceName: string,
      ) => Promise<PlatformImageSource> | null;
      platformCreateCanvas?: (
        width: number,
        height: number,
      ) => PlatformImageSource;
      platformLoadImage?: (url: string) => Promise<PlatformImageSource>;
      platformDecodeImageData?: (
        data: Uint8Array,
        mimeType: string,
      ) => Promise<PlatformImageSource>;
      platformImageToPng?: (
        source: TImageSource,
      ) => Promise<
        { width: number; height: number; data: Uint8Array } | undefined
      >;
      platformCreateImageFromRGB?: (
        width: number,
        height: number,
        rgbData: Int32Array,
        processAlpha: boolean,
      ) => PlatformImageSource;
      platformGetImageRGB?: (
        source: PlatformImageSource,
        x: number,
        y: number,
        width: number,
        height: number,
      ) => Int32Array;
    };

    input?: {
      keyPressed?: (keyCode: number) => void;
      keyReleased?: (keyCode: number) => void;
      keyRepeated?: (keyCode: number) => void;
      pointerPressed?: (x: number, y: number) => void;
      pointerDragged?: (x: number, y: number) => void;
      pointerReleased?: (x: number, y: number) => void;
      setFullScreenMode?: (fullScreen: boolean) => void;
    };
    canvas?: {
      getGameAction?: (keyCode: number) => number;
      getKeyCode?: (gameAction: number) => number;
      getKeyName?: (keyCode: number) => string;
      hasPointerEvents?: () => boolean;
      hasPointerMotionEvents?: () => boolean;
      hasRepeatEvents?: () => boolean;
    };
    gameCanvas?: {
      flushGraphics?: (surfaceId: number) => void;
      flushGraphicsRegion?: (
        surfaceId: number,
        x: number,
        y: number,
        width: number,
        height: number,
      ) => void;
      getKeyStates?: () => number;
    };
    midlet?: {
      onStateChanged?: (state: 'active' | 'paused' | 'destroyed') => void;
      onResumeRequested?: () => void;
      platformRequest?: (url: string) => boolean;
    };
    media?: {
      createPlayer?: (
        request: MediaPlayerRequest,
      ) => Promise<MediaPlayerHandle>;
      loadMediaData?: (locator: string) => Promise<Uint8Array>;
      getSupportedContentTypes?: (protocol: string) => string[];
      getSupportedProtocols?: (contentType: string) => string[];
      playTone?: (note: number, duration: number, volume: number) => void;
      onPlayerStateChange?: (
        playerHandle: number,
        state: MediaPlayerState,
      ) => void;
      onPlayerEvent?: (
        playerHandle: number,
        event: string,
        eventData?: unknown,
      ) => void;
    };
    rms?: {
      load?: (key: string) => ArrayBuffer | null;
      save?: (key: string, value: ArrayBuffer) => void;
      remove?: (key: string) => void;
      keys?: () => string[];
    };
    network?: {
      httpRequest?: (
        request: NetworkHttpRequest,
      ) => Promise<NetworkHttpResponse>;
      openSocket?: (host: string, port: number) => Promise<NetworkSocket>;
      isNetworkAvailable?: () => boolean;
    };
    pki?: {
      getIssuer?: () => string;
      getNotAfter?: () => number;
      getNotBefore?: () => number;
      getSerialNumber?: () => string;
      getSigAlgName?: () => string;
      getSubject?: () => string;
      getType?: () => string;
      getVersion?: () => string;
    };
    display?: {
      getWidth?: () => number;
      getHeight?: () => number;
      sizeChanged?: (width: number, height: number) => void;
      isColor?: () => boolean;
      numColors?: () => number;
      numAlphaLevels?: () => number;
      getBestImageWidth?: (imageType: number) => number;
      getBestImageHeight?: (imageType: number) => number;
      getBorderStyle?: (highlighted: boolean) => number;
      getColor?: (colorSpecifier: number) => number;
      flashBacklight?: (duration: number) => boolean;
      vibrate?: (duration: number) => boolean;
    };
    lcdUi?: J2MELcdUiHost;
    bluetooth?: J2MEBluetoothHost;
    m3g?: {
      handleCommand?: (cmdId: number, view: DataView, offset: number) => number;
      waitForGpu?: () => Promise<void> | null;
      readImageTargetPixels?: (
        imageHandle: number,
        width: number,
        height: number,
      ) => Promise<Uint8Array>;
    };
  };
}

export interface J2MPlatformBridge<
  _TImageSource extends PlatformImageSource = PlatformImageSource,
> extends PlatformBridge,
    J2MEOnlyPlatformBridge {}
