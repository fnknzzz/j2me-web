import { J2MEKeyCode } from 'j2me-web';

export interface KeyboardMapping {
  [physicalKey: string]: J2MEKeyCode[];
}

export interface GamepadButtonMapping {
  [buttonIndex: number]: J2MEKeyCode;
}

export interface GamepadAxesMapping {
  leftStickUp?: J2MEKeyCode;
  leftStickDown?: J2MEKeyCode;
  leftStickLeft?: J2MEKeyCode;
  leftStickRight?: J2MEKeyCode;
  rightStickUp?: J2MEKeyCode;
  rightStickDown?: J2MEKeyCode;
  rightStickLeft?: J2MEKeyCode;
  rightStickRight?: J2MEKeyCode;
}

export interface GamepadMapping {
  buttons: GamepadButtonMapping;
  axes: GamepadAxesMapping;
  deadzone: number;
}

export interface InputConfig {
  keyboard: KeyboardMapping;
  gamepad: GamepadMapping;
  controls: {
    rewindGamepadButton: number;
    quickSaveGamepadButton: number;
    quickLoadGamepadButton: number;
    fastForwardGamepadButton: number;
    fastForwardSpeed: number;
  };
}

const STORAGE_KEY_PREFIX = 'j2me-input-config:';

const DEFAULT_KEYBOARD_MAPPING: KeyboardMapping = {
  ArrowUp: [J2MEKeyCode.KEY_UP],
  ArrowDown: [J2MEKeyCode.KEY_DOWN],
  ArrowLeft: [J2MEKeyCode.KEY_LEFT],
  ArrowRight: [J2MEKeyCode.KEY_RIGHT],
  Enter: [J2MEKeyCode.KEY_FIRE],
  Backquote: [J2MEKeyCode.KEY_SOFT_LEFT],
  Backspace: [J2MEKeyCode.KEY_SOFT_RIGHT],
  Escape: [J2MEKeyCode.KEY_SOFT_RIGHT],
  Digit0: [J2MEKeyCode.KEY_NUM0],
  Digit1: [J2MEKeyCode.KEY_NUM1],
  Digit2: [J2MEKeyCode.KEY_NUM2],
  Digit3: [J2MEKeyCode.KEY_NUM3],
  Digit4: [J2MEKeyCode.KEY_NUM4],
  Digit5: [J2MEKeyCode.KEY_NUM5],
  Digit6: [J2MEKeyCode.KEY_NUM6],
  Digit7: [J2MEKeyCode.KEY_NUM7],
  Digit8: [J2MEKeyCode.KEY_NUM8],
  Digit9: [J2MEKeyCode.KEY_NUM9],
  Minus: [J2MEKeyCode.KEY_STAR],
  Equal: [J2MEKeyCode.KEY_POUND],
};

const WASD_KEYBOARD_MAPPING: KeyboardMapping = {
  ...DEFAULT_KEYBOARD_MAPPING,
  KeyW: [J2MEKeyCode.KEY_UP],
  KeyS: [J2MEKeyCode.KEY_DOWN],
  KeyA: [J2MEKeyCode.KEY_LEFT],
  KeyD: [J2MEKeyCode.KEY_RIGHT],
  Space: [J2MEKeyCode.KEY_FIRE],
  KeyQ: [J2MEKeyCode.KEY_SOFT_LEFT],
  KeyE: [J2MEKeyCode.KEY_SOFT_RIGHT],
};

const NUMPAD_KEYBOARD_MAPPING: KeyboardMapping = {
  ...DEFAULT_KEYBOARD_MAPPING,
  Numpad8: [J2MEKeyCode.KEY_UP],
  Numpad2: [J2MEKeyCode.KEY_DOWN],
  Numpad4: [J2MEKeyCode.KEY_LEFT],
  Numpad6: [J2MEKeyCode.KEY_RIGHT],
  Numpad5: [J2MEKeyCode.KEY_FIRE],
  Numpad7: [J2MEKeyCode.KEY_SOFT_LEFT],
  Numpad9: [J2MEKeyCode.KEY_SOFT_RIGHT],
  Numpad0: [J2MEKeyCode.KEY_NUM0],
  Numpad1: [J2MEKeyCode.KEY_NUM1],
  Numpad3: [J2MEKeyCode.KEY_NUM3],
  NumpadMultiply: [J2MEKeyCode.KEY_STAR],
  NumpadSubtract: [J2MEKeyCode.KEY_POUND],
};

const DEFAULT_GAMEPAD_BUTTONS: GamepadButtonMapping = {
  0: J2MEKeyCode.KEY_FIRE,
  1: J2MEKeyCode.KEY_SOFT_RIGHT,
  8: J2MEKeyCode.KEY_SOFT_LEFT,
  9: J2MEKeyCode.KEY_SOFT_RIGHT,
  12: J2MEKeyCode.KEY_UP,
  13: J2MEKeyCode.KEY_DOWN,
  14: J2MEKeyCode.KEY_LEFT,
  15: J2MEKeyCode.KEY_RIGHT,
};

const LEGACY_DEFAULT_GAMEPAD_BUTTONS: GamepadButtonMapping = {
  0: J2MEKeyCode.KEY_FIRE,
  1: J2MEKeyCode.KEY_SOFT_RIGHT,
  2: J2MEKeyCode.KEY_NUM0,
  3: J2MEKeyCode.KEY_NUM5,
  8: J2MEKeyCode.KEY_SOFT_RIGHT,
  9: J2MEKeyCode.KEY_SOFT_LEFT,
  12: J2MEKeyCode.KEY_UP,
  13: J2MEKeyCode.KEY_DOWN,
  14: J2MEKeyCode.KEY_LEFT,
  15: J2MEKeyCode.KEY_RIGHT,
};

const LEGACY_PRESET_GAMEPAD_BUTTONS: GamepadButtonMapping = {
  ...LEGACY_DEFAULT_GAMEPAD_BUTTONS,
  4: J2MEKeyCode.KEY_NUM7,
  5: J2MEKeyCode.KEY_NUM9,
};

const DEFAULT_GAMEPAD_CONTROLS: InputConfig['controls'] = {
  rewindGamepadButton: 6,
  quickSaveGamepadButton: 4,
  quickLoadGamepadButton: 5,
  fastForwardGamepadButton: 7,
  fastForwardSpeed: 3,
};

const gamepadButtonsEqual = (
  left: GamepadButtonMapping,
  right: GamepadButtonMapping,
): boolean => {
  const leftKeys = Object.keys(left);
  const rightKeys = Object.keys(right);
  return (
    leftKeys.length === rightKeys.length &&
    leftKeys.every((button) => left[Number(button)] === right[Number(button)])
  );
};

const DEFAULT_GAMEPAD_MAPPING: GamepadMapping = {
  buttons: { ...DEFAULT_GAMEPAD_BUTTONS },
  axes: {
    leftStickUp: J2MEKeyCode.KEY_UP,
    leftStickDown: J2MEKeyCode.KEY_DOWN,
    leftStickLeft: J2MEKeyCode.KEY_LEFT,
    leftStickRight: J2MEKeyCode.KEY_RIGHT,
  },
  deadzone: 0.5,
};

const XBOX_GAMEPAD_MAPPING: GamepadMapping = {
  buttons: { ...DEFAULT_GAMEPAD_BUTTONS },
  axes: {
    leftStickUp: J2MEKeyCode.KEY_UP,
    leftStickDown: J2MEKeyCode.KEY_DOWN,
    leftStickLeft: J2MEKeyCode.KEY_LEFT,
    leftStickRight: J2MEKeyCode.KEY_RIGHT,
  },
  deadzone: 0.5,
};

const PLAYSTATION_GAMEPAD_MAPPING: GamepadMapping = {
  buttons: { ...DEFAULT_GAMEPAD_BUTTONS },
  axes: {
    leftStickUp: J2MEKeyCode.KEY_UP,
    leftStickDown: J2MEKeyCode.KEY_DOWN,
    leftStickLeft: J2MEKeyCode.KEY_LEFT,
    leftStickRight: J2MEKeyCode.KEY_RIGHT,
  },
  deadzone: 0.4,
};

const SWITCH_GAMEPAD_MAPPING: GamepadMapping = {
  buttons: { ...DEFAULT_GAMEPAD_BUTTONS },
  axes: {
    leftStickUp: J2MEKeyCode.KEY_UP,
    leftStickDown: J2MEKeyCode.KEY_DOWN,
    leftStickLeft: J2MEKeyCode.KEY_LEFT,
    leftStickRight: J2MEKeyCode.KEY_RIGHT,
  },
  deadzone: 0.5,
};

export type KeyboardPreset = 'default' | 'wasd' | 'numpad';
export type GamepadPreset = 'default' | 'xbox' | 'playstation' | 'switch';

export const KEYBOARD_PRESETS: Record<KeyboardPreset, KeyboardMapping> = {
  default: DEFAULT_KEYBOARD_MAPPING,
  wasd: WASD_KEYBOARD_MAPPING,
  numpad: NUMPAD_KEYBOARD_MAPPING,
};

export const GAMEPAD_PRESETS: Record<GamepadPreset, GamepadMapping> = {
  default: DEFAULT_GAMEPAD_MAPPING,
  xbox: XBOX_GAMEPAD_MAPPING,
  playstation: PLAYSTATION_GAMEPAD_MAPPING,
  switch: SWITCH_GAMEPAD_MAPPING,
};

function cloneGamepadMapping(mapping: GamepadMapping): GamepadMapping {
  return {
    buttons: { ...mapping.buttons },
    axes: { ...mapping.axes },
    deadzone: mapping.deadzone,
  };
}

export const J2ME_KEY_NAMES: Record<J2MEKeyCode, string> = {
  [J2MEKeyCode.KEY_UP]: '上',
  [J2MEKeyCode.KEY_DOWN]: '下',
  [J2MEKeyCode.KEY_LEFT]: '左',
  [J2MEKeyCode.KEY_RIGHT]: '右',
  [J2MEKeyCode.KEY_FIRE]: '确认',
  [J2MEKeyCode.KEY_SOFT_LEFT]: '左软键',
  [J2MEKeyCode.KEY_SOFT_RIGHT]: '右软键',
  [J2MEKeyCode.KEY_CLEAR]: '清除',
  [J2MEKeyCode.KEY_SEND]: '发送',
  [J2MEKeyCode.KEY_NUM0]: '0',
  [J2MEKeyCode.KEY_NUM1]: '1',
  [J2MEKeyCode.KEY_NUM2]: '2',
  [J2MEKeyCode.KEY_NUM3]: '3',
  [J2MEKeyCode.KEY_NUM4]: '4',
  [J2MEKeyCode.KEY_NUM5]: '5',
  [J2MEKeyCode.KEY_NUM6]: '6',
  [J2MEKeyCode.KEY_NUM7]: '7',
  [J2MEKeyCode.KEY_NUM8]: '8',
  [J2MEKeyCode.KEY_NUM9]: '9',
  [J2MEKeyCode.KEY_STAR]: '*',
  [J2MEKeyCode.KEY_POUND]: '#',
};

export class InputConfigManager {
  private config: InputConfig;
  private midletId?: string;
  private keyboardCaptureCallback: ((key: string) => void) | null = null;
  private gamepadCaptureCallback: ((button: number) => void) | null = null;
  private gamepadCaptureAnimationId: number | null = null;
  private keyboardCaptureHandler: ((e: KeyboardEvent) => void) | null = null;

  constructor(midletId?: string) {
    this.midletId = midletId;
    this.config = this.load();
  }

  private getStorageKey(): string {
    return `${STORAGE_KEY_PREFIX}${this.midletId ?? 'global'}`;
  }

  load(): InputConfig {
    try {
      const stored = localStorage.getItem(this.getStorageKey());
      if (stored) {
        const parsed = JSON.parse(stored);
        const validJ2MEKeys = new Set<number>(
          Object.keys(J2ME_KEY_NAMES).map(Number),
        );
        const filteredKeyboard: KeyboardMapping = {};
        for (const [key, value] of Object.entries(parsed.keyboard ?? {})) {
          if (
            key.length > 0 &&
            Array.isArray(value) &&
            value.every(
              (code): code is J2MEKeyCode =>
                typeof code === 'number' && validJ2MEKeys.has(code),
            )
          ) {
            filteredKeyboard[key] = value;
          }
        }
        const filteredGamepadButtons: GamepadButtonMapping = {};
        for (const [button, value] of Object.entries(
          parsed.gamepad?.buttons ?? {},
        )) {
          const buttonIndex = Number(button);
          if (
            Number.isInteger(buttonIndex) &&
            buttonIndex >= 0 &&
            typeof value === 'number' &&
            validJ2MEKeys.has(value)
          ) {
            filteredGamepadButtons[buttonIndex] = value as J2MEKeyCode;
          }
        }
        const usesLegacyDefaultGamepad =
          gamepadButtonsEqual(
            filteredGamepadButtons,
            LEGACY_DEFAULT_GAMEPAD_BUTTONS,
          ) ||
          gamepadButtonsEqual(
            filteredGamepadButtons,
            LEGACY_PRESET_GAMEPAD_BUTTONS,
          );
        const usesLegacyDefaultControls =
          usesLegacyDefaultGamepad &&
          parsed.controls?.rewindGamepadButton === 4 &&
          parsed.controls?.quickSaveGamepadButton === 6 &&
          parsed.controls?.quickLoadGamepadButton === 7 &&
          parsed.controls?.fastForwardGamepadButton === 5;
        const storedFastForwardSpeed = parsed.controls?.fastForwardSpeed;
        return {
          keyboard: filteredKeyboard,
          gamepad: {
            ...DEFAULT_GAMEPAD_MAPPING,
            ...parsed.gamepad,
            buttons: usesLegacyDefaultGamepad
              ? { ...DEFAULT_GAMEPAD_BUTTONS }
              : filteredGamepadButtons,
            axes: { ...DEFAULT_GAMEPAD_MAPPING.axes, ...parsed.gamepad?.axes },
          },
          controls: {
            rewindGamepadButton: usesLegacyDefaultControls
              ? DEFAULT_GAMEPAD_CONTROLS.rewindGamepadButton
              : typeof parsed.controls?.rewindGamepadButton === 'number'
                ? parsed.controls.rewindGamepadButton
                : DEFAULT_GAMEPAD_CONTROLS.rewindGamepadButton,
            quickSaveGamepadButton: usesLegacyDefaultControls
              ? DEFAULT_GAMEPAD_CONTROLS.quickSaveGamepadButton
              : typeof parsed.controls?.quickSaveGamepadButton === 'number'
                ? parsed.controls.quickSaveGamepadButton
                : DEFAULT_GAMEPAD_CONTROLS.quickSaveGamepadButton,
            quickLoadGamepadButton: usesLegacyDefaultControls
              ? DEFAULT_GAMEPAD_CONTROLS.quickLoadGamepadButton
              : typeof parsed.controls?.quickLoadGamepadButton === 'number'
                ? parsed.controls.quickLoadGamepadButton
                : DEFAULT_GAMEPAD_CONTROLS.quickLoadGamepadButton,
            fastForwardGamepadButton: usesLegacyDefaultControls
              ? DEFAULT_GAMEPAD_CONTROLS.fastForwardGamepadButton
              : typeof parsed.controls?.fastForwardGamepadButton === 'number'
                ? parsed.controls.fastForwardGamepadButton
                : DEFAULT_GAMEPAD_CONTROLS.fastForwardGamepadButton,
            fastForwardSpeed:
              usesLegacyDefaultControls && storedFastForwardSpeed === 4
                ? DEFAULT_GAMEPAD_CONTROLS.fastForwardSpeed
                : typeof storedFastForwardSpeed === 'number' &&
                    Number.isFinite(storedFastForwardSpeed)
                  ? Math.max(1, Math.min(10, storedFastForwardSpeed))
                  : DEFAULT_GAMEPAD_CONTROLS.fastForwardSpeed,
          },
        };
      }
    } catch (e) {
      console.warn('[InputConfigManager] Failed to load config:', e);
    }
    return this.getDefaultConfig();
  }

  save(): void {
    try {
      localStorage.setItem(this.getStorageKey(), JSON.stringify(this.config));
    } catch (e) {
      console.error('[InputConfigManager] Failed to save config:', e);
    }
  }

  getDefaultConfig(): InputConfig {
    return {
      keyboard: { ...DEFAULT_KEYBOARD_MAPPING },
      gamepad: cloneGamepadMapping(DEFAULT_GAMEPAD_MAPPING),
      controls: { ...DEFAULT_GAMEPAD_CONTROLS },
    };
  }

  getConfig(): InputConfig {
    return this.config;
  }

  setConfig(config: InputConfig): void {
    this.config = config;
    this.save();
  }

  getKeyboardMapping(): KeyboardMapping {
    return this.config.keyboard;
  }

  setKeyboardMapping(mapping: KeyboardMapping): void {
    this.config.keyboard = mapping;
    this.save();
  }

  getGamepadMapping(): GamepadMapping {
    return this.config.gamepad;
  }

  setGamepadMapping(mapping: GamepadMapping): void {
    this.config.gamepad = mapping;
    this.save();
  }

  applyKeyboardPreset(preset: KeyboardPreset): void {
    this.config.keyboard = { ...KEYBOARD_PRESETS[preset] };
    this.save();
  }

  applyGamepadPreset(preset: GamepadPreset): void {
    this.config.gamepad = cloneGamepadMapping(GAMEPAD_PRESETS[preset]);
    this.save();
  }

  setKeyBinding(physicalKey: string, j2meKeyCodes: J2MEKeyCode[]): void {
    this.config.keyboard[physicalKey] = j2meKeyCodes;
    this.save();
  }

  removeKeyBinding(physicalKey: string): void {
    delete this.config.keyboard[physicalKey];
    this.save();
  }

  setGamepadButtonBinding(buttonIndex: number, j2meKeyCode: J2MEKeyCode): void {
    this.config.gamepad.buttons[buttonIndex] = j2meKeyCode;
    this.save();
  }

  removeGamepadButtonBinding(buttonIndex: number): void {
    delete this.config.gamepad.buttons[buttonIndex];
    this.save();
  }

  setGamepadDeadzone(deadzone: number): void {
    this.config.gamepad.deadzone = Math.max(0, Math.min(1, deadzone));
    this.save();
  }

  getRewindGamepadButton(): number {
    return this.config.controls.rewindGamepadButton;
  }

  setRewindGamepadButton(buttonIndex: number): void {
    this.config.controls.rewindGamepadButton = buttonIndex;
    this.save();
  }

  getQuickSaveGamepadButton(): number {
    return this.config.controls.quickSaveGamepadButton;
  }

  setQuickSaveGamepadButton(buttonIndex: number): void {
    this.config.controls.quickSaveGamepadButton = buttonIndex;
    this.save();
  }

  getQuickLoadGamepadButton(): number {
    return this.config.controls.quickLoadGamepadButton;
  }

  setQuickLoadGamepadButton(buttonIndex: number): void {
    this.config.controls.quickLoadGamepadButton = buttonIndex;
    this.save();
  }

  getFastForwardGamepadButton(): number {
    return this.config.controls.fastForwardGamepadButton;
  }

  setFastForwardGamepadButton(buttonIndex: number): void {
    this.config.controls.fastForwardGamepadButton = buttonIndex;
    this.save();
  }

  getFastForwardSpeed(): number {
    return this.config.controls.fastForwardSpeed;
  }

  setFastForwardSpeed(speed: number): void {
    this.config.controls.fastForwardSpeed = Math.max(1, Math.min(10, speed));
    this.save();
  }

  exportConfig(): string {
    return JSON.stringify(this.config, null, 2);
  }

  importConfig(json: string): boolean {
    try {
      const parsed = JSON.parse(json);
      if (parsed.keyboard && parsed.gamepad) {
        const defaults = this.getDefaultConfig();
        const importedFastForwardSpeed = parsed.controls?.fastForwardSpeed;
        this.config = {
          ...defaults,
          ...parsed,
          controls: {
            ...defaults.controls,
            ...parsed.controls,
            fastForwardSpeed:
              typeof importedFastForwardSpeed === 'number' &&
              Number.isFinite(importedFastForwardSpeed)
                ? Math.max(1, Math.min(10, importedFastForwardSpeed))
                : defaults.controls.fastForwardSpeed,
          },
        };
        this.save();
        return true;
      }
    } catch (e) {
      console.error('[InputConfigManager] Failed to import config:', e);
    }
    return false;
  }

  reset(): void {
    this.config = this.getDefaultConfig();
    this.save();
  }

  startKeyboardCapture(callback: (key: string) => void): void {
    this.stopCapture();
    this.keyboardCaptureCallback = callback;
    this.keyboardCaptureHandler = (e: KeyboardEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (this.keyboardCaptureCallback) {
        this.keyboardCaptureCallback(e.code || e.key);
      }
    };
    window.addEventListener('keydown', this.keyboardCaptureHandler, true);
  }

  startGamepadCapture(callback: (button: number) => void): void {
    this.stopCapture();
    this.gamepadCaptureCallback = callback;

    const previousStates = new Map<number, boolean>();

    const poll = () => {
      const gamepads = navigator.getGamepads();
      for (const gamepad of gamepads) {
        if (!gamepad) continue;
        for (let i = 0; i < gamepad.buttons.length; i++) {
          const pressed = gamepad.buttons[i].pressed;
          const wasPressed = previousStates.get(i) ?? false;
          if (pressed && !wasPressed && this.gamepadCaptureCallback) {
            this.gamepadCaptureCallback(i);
          }
          previousStates.set(i, pressed);
        }
      }
      this.gamepadCaptureAnimationId = requestAnimationFrame(poll);
    };
    poll();
  }

  stopCapture(): void {
    if (this.keyboardCaptureHandler) {
      window.removeEventListener('keydown', this.keyboardCaptureHandler, true);
      this.keyboardCaptureHandler = null;
    }
    this.keyboardCaptureCallback = null;

    if (this.gamepadCaptureAnimationId !== null) {
      cancelAnimationFrame(this.gamepadCaptureAnimationId);
      this.gamepadCaptureAnimationId = null;
    }
    this.gamepadCaptureCallback = null;
  }

  getConnectedGamepads(): Gamepad[] {
    return Array.from(navigator.getGamepads()).filter(
      (g): g is Gamepad => g !== null,
    );
  }
}
