import {
  type FontConfig,
  J2MEKeyCode,
  type J2MESoftKeyLabels,
  J2MEWeb,
} from 'j2me-web';
import {
  type GamepadMapping,
  type GamepadPreset,
  type InputConfig,
  InputConfigManager,
  type KeyboardMapping,
  type KeyboardPreset,
} from './input-config';
import { InputConfigUI } from './input-config-ui';
import './input-config.css';

export interface J2MEPhoneOptions {
  container: HTMLElement;
  jar: ArrayBuffer | any[];
  width?: number;
  height?: number;
  midletNumber?: number;
  midletId?: string;
  font?: FontConfig;
  enableGamepad?: boolean;
  onReady?: (j2me: J2MEWeb) => void;
  onError?: (error: Error) => void;
  showSettingsButton?: boolean;
  showFps?: boolean;
  controlButtonHandlers?: {
    gamepad?: {
      rewind?: (pressed: boolean, gamepad: Gamepad) => void;
      quickSave?: (pressed: boolean, gamepad: Gamepad) => void;
      quickLoad?: (pressed: boolean, gamepad: Gamepad) => void;
      fastForward?: (pressed: boolean, speed: number, gamepad: Gamepad) => void;
    };
  };
}

export class J2MEPhone {
  private container: HTMLElement;
  private j2me: J2MEWeb | null = null;
  private canvas: HTMLCanvasElement | null = null;
  private inputConfigManager: InputConfigManager;
  private inputConfigUI: InputConfigUI | null = null;
  private gamepadIndex: number | null = null;
  private gamepadAnimationId: number | null = null;
  private gamepadButtonStates: Map<number | string, boolean> = new Map();
  private activeGamepadControls = new Map<
    number,
    {
      handler: (pressed: boolean, gamepad: Gamepad) => void;
      gamepad: Gamepad;
    }
  >();
  private keyboardHandler: ((e: KeyboardEvent) => void) | null = null;
  private keyboardUpHandler: ((e: KeyboardEvent) => void) | null = null;
  private settingsButton: HTMLElement | null = null;
  private fpsElement: HTMLElement | null = null;
  private fpsIntervalId: number | null = null;
  private lastFpsSample: { frames: number; durationMs: number } | null = null;
  private leftSoftKeyElement: HTMLElement | null = null;
  private rightSoftKeyElement: HTMLElement | null = null;
  private softKeyLabelsUnsubscribe: (() => void) | null = null;
  private fpsVisible = false;
  private static readonly FPS_STORAGE_KEY = 'j2me-show-fps';

  constructor(private options: J2MEPhoneOptions) {
    this.container = options.container;
    this.inputConfigManager = new InputConfigManager(options.midletId);
    this.render();
    this.init();
    this.setupKeyboardListeners();
    if (options.enableGamepad !== false) {
      this.initGamepad();
    }
  }

  private render() {
    const width = this.options.width ?? 240;
    const height = this.options.height ?? 320;

    this.container.innerHTML = `
      <div class="phone">
        <div class="brand-logo">J2ME Phone</div>
        <div class="screen">
          <canvas id="game-canvas" width="${width}" height="${height}"></canvas>
        </div>
        
        <div class="keypad">
          <div class="dpad-container">
            <div class="left-keys">
              <div class="soft-key" data-key="Backquote" data-soft-key="left"></div>
              <div class="call-btn" data-key="c">📞</div>
            </div>
            
            <div class="dpad-wrapper">
              <div class="dpad">
                <div class="dpad-btn dpad-up" data-key="ArrowUp">▲</div>
                <div class="dpad-btn dpad-down" data-key="ArrowDown">▼</div>
                <div class="dpad-btn dpad-left" data-key="ArrowLeft">◀</div>
                <div class="dpad-btn dpad-right" data-key="ArrowRight">▶</div>
                <div class="dpad-btn dpad-center" data-key="Enter">OK</div>
              </div>
            </div>
            
            <div class="right-keys">
              <div class="soft-key" data-key="Backspace" data-soft-key="right"></div>
              <div class="hangup-btn" data-key="h">📵</div>
            </div>
          </div>

          <div class="number-pad">
            <div class="number-row">
              <div class="num-btn" data-key="Digit1"><span>1</span></div>
              <div class="num-btn" data-key="Digit2"><span>2</span><span class="letters">ABC</span></div>
              <div class="num-btn" data-key="Digit3"><span>3</span><span class="letters">DEF</span></div>
            </div>
            <div class="number-row">
              <div class="num-btn" data-key="Digit4"><span>4</span><span class="letters">GHI</span></div>
              <div class="num-btn" data-key="Digit5"><span>5</span><span class="letters">JKL</span></div>
              <div class="num-btn" data-key="Digit6"><span>6</span><span class="letters">MNO</span></div>
            </div>
            <div class="number-row">
              <div class="num-btn" data-key="Digit7"><span>7</span><span class="letters">PQRS</span></div>
              <div class="num-btn" data-key="Digit8"><span>8</span><span class="letters">TUV</span></div>
              <div class="num-btn" data-key="Digit9"><span>9</span><span class="letters">WXYZ</span></div>
            </div>
            <div class="number-row">
              <div class="num-btn" data-key="Minus"><span>*</span></div>
              <div class="num-btn" data-key="Digit0"><span>0</span><span class="letters">+</span></div>
              <div class="num-btn" data-key="Equal"><span>#</span></div>
            </div>
          </div>
        </div>
      </div>
    `;

    this.canvas = this.container.querySelector('#game-canvas');
    this.leftSoftKeyElement = this.container.querySelector(
      '[data-soft-key="left"]',
    );
    this.rightSoftKeyElement = this.container.querySelector(
      '[data-soft-key="right"]',
    );

    if (this.options.showSettingsButton !== false) {
      this.renderSettingsButton();
    }

    this.renderFpsElement();
    const savedFpsVisible = localStorage.getItem(J2MEPhone.FPS_STORAGE_KEY);
    if (this.options.showFps || savedFpsVisible === 'true') {
      this.showFps();
    }

    this.setupVirtualKeypadListeners();
  }

  private renderSettingsButton() {
    const brandLogo = this.container.querySelector(
      '.brand-logo',
    ) as HTMLElement;
    if (!brandLogo) return;

    brandLogo.style.position = 'relative';

    this.settingsButton = document.createElement('div');
    this.settingsButton.className = 'j2me-settings-btn';
    this.settingsButton.innerHTML = '⚙';
    this.settingsButton.title = '输入设置';
    this.settingsButton.addEventListener('click', () => {
      this.toggleConfigUI();
    });
    brandLogo.appendChild(this.settingsButton);
  }

  private renderFpsElement() {
    const screen = this.container.querySelector('.screen') as HTMLElement;
    if (!screen) return;

    screen.style.position = 'relative';

    this.fpsElement = document.createElement('div');
    this.fpsElement.style.cssText = `
      position: absolute;
      top: 4px;
      left: 4px;
      background: rgba(0, 0, 0, 0.6);
      color: #0f0;
      font-size: 12px;
      padding: 2px 6px;
      border-radius: 3px;
      font-family: monospace;
      z-index: 10;
      pointer-events: none;
      display: none;
    `;
    this.fpsElement.textContent = 'FPS: --';
    screen.appendChild(this.fpsElement);
  }

  private updateFps(fps: number) {
    if (this.fpsElement && this.fpsVisible) {
      this.fpsElement.textContent = `FPS: ${fps.toFixed(1)}`;
    }
  }

  private startFpsMonitoring(): void {
    if (this.fpsIntervalId !== null || !this.j2me) return;
    const initial = this.j2me.getPerformanceSnapshot();
    this.lastFpsSample = initial
      ? {
          frames: initial.experience.presentedFrames,
          durationMs: initial.durationMs,
        }
      : null;
    this.fpsIntervalId = window.setInterval(() => {
      const snapshot = this.j2me?.getPerformanceSnapshot();
      if (!snapshot) return;
      const current = {
        frames: snapshot.experience.presentedFrames,
        durationMs: snapshot.durationMs,
      };
      const previous = this.lastFpsSample;
      if (
        previous &&
        current.frames >= previous.frames &&
        current.durationMs > previous.durationMs
      ) {
        this.updateFps(
          ((current.frames - previous.frames) * 1000) /
            (current.durationMs - previous.durationMs),
        );
      } else {
        this.updateFps(snapshot.experience.presentFps);
      }
      this.lastFpsSample = current;
    }, 1000);
  }

  private stopFpsMonitoring(): void {
    if (this.fpsIntervalId !== null) {
      window.clearInterval(this.fpsIntervalId);
      this.fpsIntervalId = null;
    }
    this.lastFpsSample = null;
  }

  public showFps() {
    this.fpsVisible = true;
    localStorage.setItem(J2MEPhone.FPS_STORAGE_KEY, 'true');
    if (this.fpsElement) {
      this.fpsElement.style.display = 'block';
    }
    this.startFpsMonitoring();
  }

  public hideFps() {
    this.fpsVisible = false;
    this.stopFpsMonitoring();
    localStorage.setItem(J2MEPhone.FPS_STORAGE_KEY, 'false');
    if (this.fpsElement) {
      this.fpsElement.style.display = 'none';
    }
  }

  public toggleFps() {
    if (this.fpsVisible) {
      this.hideFps();
    } else {
      this.showFps();
    }
  }

  public isFpsVisible(): boolean {
    return this.fpsVisible;
  }

  private setupVirtualKeypadListeners() {
    const keypad = this.container.querySelector('.keypad');
    if (!keypad) return;

    const handleButtonPress = (target: Element, pressed: boolean) => {
      const key = target.getAttribute('data-key');
      if (!key || !this.j2me) return;

      const keyCodes = this.inputConfigManager.getKeyboardMapping()[key];
      if (!keyCodes || keyCodes.length === 0) return;

      if (pressed) {
        target.classList.add('pressed');
        for (const keyCode of keyCodes) {
          this.j2me.input.triggerKeyPressed(keyCode);
        }
      } else {
        target.classList.remove('pressed');
        for (const keyCode of keyCodes) {
          this.j2me.input.triggerKeyReleased(keyCode);
        }
      }
    };

    keypad.addEventListener('mousedown', (e) => {
      const target = (e.target as Element).closest('[data-key]');
      if (target) {
        e.preventDefault();
        handleButtonPress(target, true);
      }
    });

    keypad.addEventListener('mouseup', (e) => {
      const target = (e.target as Element).closest('[data-key]');
      if (target) {
        e.preventDefault();
        handleButtonPress(target, false);
      }
    });

    keypad.addEventListener('mouseleave', (e) => {
      const target = (e.target as Element).closest('[data-key]');
      if (target) {
        target.classList.remove('pressed');
      }
    });

    keypad.addEventListener(
      'touchstart',
      (e) => {
        const target = (e.target as Element).closest('[data-key]');
        if (target) {
          e.preventDefault();
          handleButtonPress(target, true);
        }
      },
      { passive: false },
    );

    keypad.addEventListener(
      'touchend',
      (e) => {
        const target = (e.target as Element).closest('[data-key]');
        if (target) {
          e.preventDefault();
          handleButtonPress(target, false);
        }
      },
      { passive: false },
    );
  }

  private static isInputFocused(e: KeyboardEvent): boolean {
    const t = e.target as HTMLElement | null;
    if (!t) return false;
    const tag = t.tagName;
    return (
      tag === 'INPUT' ||
      tag === 'TEXTAREA' ||
      tag === 'SELECT' ||
      t.isContentEditable
    );
  }

  private setupKeyboardListeners() {
    this.keyboardHandler = (e: KeyboardEvent) => {
      if (!this.j2me) return;
      if (J2MEPhone.isInputFocused(e)) return;
      if (this.inputConfigUI && (this.inputConfigUI as any).isVisible) return;

      if (e.code === 'F11') {
        e.preventDefault();
        this.j2me.toggleFullscreen();
        return;
      }

      if (e.code === 'Escape' && this.j2me.isFullscreen()) {
        e.preventDefault();
      }

      const keyCodes = this.inputConfigManager.getKeyboardMapping()[e.code];
      if (keyCodes && keyCodes.length > 0) {
        e.preventDefault();
        for (const keyCode of keyCodes) {
          this.j2me.input.triggerKeyPressed(keyCode);
        }
        this.highlightVirtualKeys(keyCodes, true);
      }
    };

    this.keyboardUpHandler = (e: KeyboardEvent) => {
      if (!this.j2me) return;
      if (J2MEPhone.isInputFocused(e)) return;
      if (this.inputConfigUI && (this.inputConfigUI as any).isVisible) return;

      if (e.code === 'F11') {
        e.preventDefault();
        return;
      }

      if (e.code === 'Escape' && this.j2me.isFullscreen()) {
        e.preventDefault();
        return;
      }

      const keyCodes = this.inputConfigManager.getKeyboardMapping()[e.code];
      if (keyCodes && keyCodes.length > 0) {
        e.preventDefault();
        for (const keyCode of keyCodes) {
          this.j2me.input.triggerKeyReleased(keyCode);
        }
        this.highlightVirtualKeys(keyCodes, false);
      }
    };

    window.addEventListener('keydown', this.keyboardHandler);
    window.addEventListener('keyup', this.keyboardUpHandler);
  }

  private static readonly J2ME_TO_VIRTUAL_KEY: Partial<
    Record<J2MEKeyCode, string>
  > = {
    [J2MEKeyCode.KEY_UP]: 'ArrowUp',
    [J2MEKeyCode.KEY_DOWN]: 'ArrowDown',
    [J2MEKeyCode.KEY_LEFT]: 'ArrowLeft',
    [J2MEKeyCode.KEY_RIGHT]: 'ArrowRight',
    [J2MEKeyCode.KEY_FIRE]: 'Enter',
    [J2MEKeyCode.KEY_SOFT_LEFT]: 'Backquote',
    [J2MEKeyCode.KEY_SOFT_RIGHT]: 'Backspace',
    [J2MEKeyCode.KEY_NUM0]: 'Digit0',
    [J2MEKeyCode.KEY_NUM1]: 'Digit1',
    [J2MEKeyCode.KEY_NUM2]: 'Digit2',
    [J2MEKeyCode.KEY_NUM3]: 'Digit3',
    [J2MEKeyCode.KEY_NUM4]: 'Digit4',
    [J2MEKeyCode.KEY_NUM5]: 'Digit5',
    [J2MEKeyCode.KEY_NUM6]: 'Digit6',
    [J2MEKeyCode.KEY_NUM7]: 'Digit7',
    [J2MEKeyCode.KEY_NUM8]: 'Digit8',
    [J2MEKeyCode.KEY_NUM9]: 'Digit9',
    [J2MEKeyCode.KEY_STAR]: 'Minus',
    [J2MEKeyCode.KEY_POUND]: 'Equal',
  };

  private highlightVirtualKeys(keyCodes: J2MEKeyCode[], pressed: boolean) {
    for (const keyCode of keyCodes) {
      const dataKey = J2MEPhone.J2ME_TO_VIRTUAL_KEY[keyCode];
      if (dataKey) {
        const virtualKey = this.container.querySelector(
          `[data-key="${dataKey}"]`,
        );
        if (virtualKey) {
          if (pressed) {
            virtualKey.classList.add('pressed');
          } else {
            virtualKey.classList.remove('pressed');
          }
        }
      }
    }
  }

  private async init() {
    if (!this.canvas) {
      const error = new Error('Canvas not found');
      this.options.onError?.(error);
      return;
    }

    try {
      this.j2me = new J2MEWeb({
        canvas: this.canvas,
        jar: this.options.jar,
        midletNumber: this.options.midletNumber,
        midletId: this.options.midletId,
        font: this.options.font,
        performanceMonitor: { enabled: true },
        lifecycleCallbacks: {
          onDestroyed: () => {
            console.log('[J2MEPhone] MIDlet destroyed');
          },
          onPaused: () => {
            console.log('[J2MEPhone] MIDlet paused');
          },
          onResumed: () => {
            console.log('[J2MEPhone] MIDlet resumed');
          },
        },
      });

      this.softKeyLabelsUnsubscribe = this.j2me.onSoftKeyLabelsChanged(
        (labels) => this.setSoftKeyLabels(labels),
      );
      await this.j2me.start();
      if (this.fpsVisible) {
        this.startFpsMonitoring();
      }
      this.setSoftKeyLabels(this.j2me.getSoftKeyLabels());
      this.options.onReady?.(this.j2me);
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      this.options.onError?.(err);
    }
  }

  public getJ2ME(): J2MEWeb | null {
    return this.j2me;
  }

  public getInputConfigManager(): InputConfigManager {
    return this.inputConfigManager;
  }

  public setSoftKeyLabels(labels: J2MESoftKeyLabels): void {
    this.setSoftKeyLabel(this.leftSoftKeyElement, labels.left);
    this.setSoftKeyLabel(this.rightSoftKeyElement, labels.right);
  }

  private setSoftKeyLabel(
    element: HTMLElement | null,
    label: string | undefined,
  ): void {
    if (!element) return;
    element.textContent = label && label.length > 0 ? label : '';
  }

  private initGamepad() {
    window.addEventListener('gamepadconnected', (e) => {
      console.log('Gamepad connected:', e.gamepad);
      if (this.gamepadIndex !== null && this.gamepadIndex !== e.gamepad.index) {
        this.releaseActiveGamepadControls();
        this.releaseAllKeys();
      }
      this.gamepadIndex = e.gamepad.index;
      this.startGamepadPolling();
    });

    window.addEventListener('gamepaddisconnected', (e) => {
      console.log('Gamepad disconnected:', e.gamepad);
      if (this.gamepadIndex === e.gamepad.index) {
        this.releaseActiveGamepadControls(e.gamepad);
        this.releaseAllKeys();
        this.stopGamepadPolling();
        this.gamepadIndex = null;
      }
    });

    const gamepads = navigator.getGamepads();
    for (let i = 0; i < gamepads.length; i++) {
      if (gamepads[i]) {
        this.gamepadIndex = i;
        this.startGamepadPolling();
        break;
      }
    }
  }

  private startGamepadPolling() {
    if (this.gamepadAnimationId !== null) return;

    const poll = () => {
      this.gamepadAnimationId = null;
      this.updateGamepadState();
      if (this.gamepadIndex !== null) {
        this.gamepadAnimationId = requestAnimationFrame(poll);
      }
    };
    this.gamepadAnimationId = requestAnimationFrame(poll);
  }

  private stopGamepadPolling() {
    if (this.gamepadAnimationId !== null) {
      cancelAnimationFrame(this.gamepadAnimationId);
      this.gamepadAnimationId = null;
    }
  }

  private updateGamepadState() {
    if (this.gamepadIndex === null || !this.j2me) return;

    const gamepads = navigator.getGamepads();
    const gamepad = gamepads[this.gamepadIndex];
    if (!gamepad) {
      this.releaseActiveGamepadControls();
      this.releaseAllKeys();
      this.stopGamepadPolling();
      this.gamepadIndex = null;
      return;
    }

    const mapping = this.inputConfigManager.getGamepadMapping();
    const controlBindings = [
      {
        button: this.inputConfigManager.getRewindGamepadButton(),
        handler: this.options.controlButtonHandlers?.gamepad?.rewind,
      },
      {
        button: this.inputConfigManager.getQuickSaveGamepadButton(),
        handler: this.options.controlButtonHandlers?.gamepad?.quickSave,
      },
      {
        button: this.inputConfigManager.getQuickLoadGamepadButton(),
        handler: this.options.controlButtonHandlers?.gamepad?.quickLoad,
      },
      {
        button: this.inputConfigManager.getFastForwardGamepadButton(),
        handler: this.options.controlButtonHandlers?.gamepad?.fastForward
          ? (pressed: boolean, activeGamepad: Gamepad) =>
              this.options.controlButtonHandlers?.gamepad?.fastForward?.(
                pressed,
                this.inputConfigManager.getFastForwardSpeed(),
                activeGamepad,
              )
          : undefined,
      },
    ];

    for (
      let buttonIndex = 0;
      buttonIndex < gamepad.buttons.length;
      buttonIndex++
    ) {
      const button = gamepad.buttons[buttonIndex];
      const pressed = button.pressed;
      const wasPressed = this.gamepadButtonStates.get(buttonIndex) ?? false;

      if (pressed === wasPressed) continue;

      const activeControl = this.activeGamepadControls.get(buttonIndex);
      if (!pressed && activeControl) {
        activeControl.handler(false, gamepad);
        this.activeGamepadControls.delete(buttonIndex);
        this.gamepadButtonStates.set(buttonIndex, false);
        continue;
      }

      const control = controlBindings.find(
        (binding) => binding.handler && binding.button === buttonIndex,
      );
      if (control?.handler) {
        control.handler(pressed, gamepad);
        if (pressed) {
          this.activeGamepadControls.set(buttonIndex, {
            handler: control.handler,
            gamepad,
          });
        }
        this.gamepadButtonStates.set(buttonIndex, pressed);
        continue;
      }

      const keyCode = mapping.buttons[buttonIndex];
      if (keyCode === undefined) {
        this.gamepadButtonStates.set(buttonIndex, pressed);
        continue;
      }

      if (pressed) {
        this.j2me.input.triggerKeyPressed(keyCode);
      } else {
        this.j2me.input.triggerKeyReleased(keyCode);
      }
      this.gamepadButtonStates.set(buttonIndex, pressed);
    }

    if (gamepad.axes.length >= 2) {
      const threshold = mapping.deadzone;
      const axisX = gamepad.axes[0];
      const axisY = gamepad.axes[1];

      if (mapping.axes.leftStickLeft) {
        this.handleAxisButton(
          'axis-left',
          axisX < -threshold,
          mapping.axes.leftStickLeft,
        );
      }
      if (mapping.axes.leftStickRight) {
        this.handleAxisButton(
          'axis-right',
          axisX > threshold,
          mapping.axes.leftStickRight,
        );
      }
      if (mapping.axes.leftStickUp) {
        this.handleAxisButton(
          'axis-up',
          axisY < -threshold,
          mapping.axes.leftStickUp,
        );
      }
      if (mapping.axes.leftStickDown) {
        this.handleAxisButton(
          'axis-down',
          axisY > threshold,
          mapping.axes.leftStickDown,
        );
      }
    }

    if (gamepad.axes.length >= 4) {
      const threshold = mapping.deadzone;
      const axisX = gamepad.axes[2];
      const axisY = gamepad.axes[3];

      if (mapping.axes.rightStickLeft) {
        this.handleAxisButton(
          'axis-right-left',
          axisX < -threshold,
          mapping.axes.rightStickLeft,
        );
      }
      if (mapping.axes.rightStickRight) {
        this.handleAxisButton(
          'axis-right-right',
          axisX > threshold,
          mapping.axes.rightStickRight,
        );
      }
      if (mapping.axes.rightStickUp) {
        this.handleAxisButton(
          'axis-right-up',
          axisY < -threshold,
          mapping.axes.rightStickUp,
        );
      }
      if (mapping.axes.rightStickDown) {
        this.handleAxisButton(
          'axis-right-down',
          axisY > threshold,
          mapping.axes.rightStickDown,
        );
      }
    }
  }

  private handleAxisButton(
    axisKey: string,
    pressed: boolean,
    keyCode: J2MEKeyCode,
  ) {
    if (!this.j2me) return;

    const wasPressed = this.gamepadButtonStates.get(axisKey) ?? false;

    if (pressed && !wasPressed) {
      this.j2me.input.triggerKeyPressed(keyCode);
      this.gamepadButtonStates.set(axisKey, true);
    } else if (!pressed && wasPressed) {
      this.j2me.input.triggerKeyReleased(keyCode);
      this.gamepadButtonStates.set(axisKey, false);
    }
  }

  private releaseActiveGamepadControls(gamepad?: Gamepad): void {
    for (const {
      handler,
      gamepad: pressedGamepad,
    } of this.activeGamepadControls.values()) {
      handler(false, gamepad ?? pressedGamepad);
    }
    this.activeGamepadControls.clear();
  }

  public setKeyboardMapping(mapping: KeyboardMapping): void {
    this.inputConfigManager.setKeyboardMapping(mapping);
  }

  public setGamepadMapping(mapping: GamepadMapping): void {
    this.inputConfigManager.setGamepadMapping(mapping);
  }

  public applyKeyboardPreset(preset: KeyboardPreset): void {
    this.inputConfigManager.applyKeyboardPreset(preset);
  }

  public applyGamepadPreset(preset: GamepadPreset): void {
    this.inputConfigManager.applyGamepadPreset(preset);
  }

  public getInputConfig(): InputConfig {
    return this.inputConfigManager.getConfig();
  }

  public releaseAllKeys(): void {
    if (this.j2me) {
      for (const keyCode of Object.values(J2MEKeyCode)) {
        this.j2me.input.triggerKeyReleased(keyCode);
      }
    }
    this.gamepadButtonStates.clear();
    for (const element of this.container.querySelectorAll('.pressed')) {
      element.classList.remove('pressed');
    }
  }

  public showConfigUI(): void {
    if (!this.inputConfigUI) {
      const phone = this.container.querySelector('.phone') as HTMLElement;
      if (!phone) return;

      this.inputConfigUI = new InputConfigUI({
        container: phone,
        configManager: this.inputConfigManager,
        onConfigChange: () => {},
      });
    }
    this.inputConfigUI.show();
  }

  public hideConfigUI(): void {
    this.inputConfigUI?.hide();
  }

  public toggleConfigUI(): void {
    if (!this.inputConfigUI) {
      this.showConfigUI();
    } else {
      this.inputConfigUI.toggle();
    }
  }

  public destroy() {
    this.releaseActiveGamepadControls();
    this.releaseAllKeys();
    this.stopGamepadPolling();
    this.stopFpsMonitoring();
    this.softKeyLabelsUnsubscribe?.();

    if (this.keyboardHandler) {
      window.removeEventListener('keydown', this.keyboardHandler);
    }
    if (this.keyboardUpHandler) {
      window.removeEventListener('keyup', this.keyboardUpHandler);
    }

    this.inputConfigUI?.destroy();
    this.inputConfigManager.stopCapture();

    this.container.innerHTML = '';
    this.j2me = null;
    this.canvas = null;
    this.inputConfigUI = null;
    this.settingsButton = null;
    this.leftSoftKeyElement = null;
    this.rightSoftKeyElement = null;
    this.softKeyLabelsUnsubscribe = null;
  }
}

export {
  type GamepadMapping,
  type GamepadPreset,
  type InputConfig,
  InputConfigManager,
  type KeyboardMapping,
  type KeyboardPreset,
} from './input-config';

export { InputConfigUI } from './input-config-ui';
