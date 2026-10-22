import { J2MEKeyCode } from 'j2me-web';
import {
  type GamepadPreset,
  type InputConfig,
  type InputConfigManager,
  J2ME_KEY_NAMES,
  type KeyboardPreset,
} from './input-config';

export interface InputConfigUIOptions {
  container: HTMLElement;
  configManager: InputConfigManager;
  onConfigChange?: (config: InputConfig) => void;
}

type InputMode = 'keyboard' | 'gamepad';
type EmulatorControl = 'rewind' | 'quickSave' | 'quickLoad' | 'fastForward';

const GAMEPAD_BUTTON_NAMES: Record<number, string> = {
  0: 'A',
  1: 'B',
  2: 'X',
  3: 'Y',
  4: 'LB',
  5: 'RB',
  6: 'LT',
  7: 'RT',
  8: 'Select',
  9: 'Start',
  10: 'LS',
  11: 'RS',
  12: '↑',
  13: '↓',
  14: '←',
  15: '→',
  16: 'Xbox',
};

const NAVIGATION_KEYS: Array<J2MEKeyCode | null> = [
  J2MEKeyCode.KEY_SOFT_LEFT,
  J2MEKeyCode.KEY_UP,
  J2MEKeyCode.KEY_SOFT_RIGHT,
  J2MEKeyCode.KEY_LEFT,
  J2MEKeyCode.KEY_FIRE,
  J2MEKeyCode.KEY_RIGHT,
  J2MEKeyCode.KEY_SEND,
  J2MEKeyCode.KEY_DOWN,
  J2MEKeyCode.KEY_CLEAR,
];

const NUMBER_KEYS: J2MEKeyCode[] = [
  J2MEKeyCode.KEY_NUM1,
  J2MEKeyCode.KEY_NUM2,
  J2MEKeyCode.KEY_NUM3,
  J2MEKeyCode.KEY_NUM4,
  J2MEKeyCode.KEY_NUM5,
  J2MEKeyCode.KEY_NUM6,
  J2MEKeyCode.KEY_NUM7,
  J2MEKeyCode.KEY_NUM8,
  J2MEKeyCode.KEY_NUM9,
  J2MEKeyCode.KEY_STAR,
  J2MEKeyCode.KEY_NUM0,
  J2MEKeyCode.KEY_POUND,
];

function getKeyDisplayName(code: string): string {
  if (code.startsWith('Key')) return code.slice(3);
  if (code.startsWith('Digit')) return code.slice(5);
  if (code.startsWith('Numpad')) return `小键盘 ${code.slice(6)}`;
  const specialKeys: Record<string, string> = {
    Minus: '-',
    Equal: '=',
    Backquote: '`',
    Backspace: 'Backspace',
    Escape: 'Esc',
    Enter: 'Enter',
    Space: '空格',
    ArrowUp: '↑',
    ArrowDown: '↓',
    ArrowLeft: '←',
    ArrowRight: '→',
    NumpadMultiply: '小键盘 *',
    NumpadSubtract: '小键盘 -',
    NumpadAdd: '小键盘 +',
    NumpadDivide: '小键盘 /',
  };
  return specialKeys[code] ?? code;
}

export class InputConfigUI {
  private container: HTMLElement;
  private configManager: InputConfigManager;
  private onConfigChange?: (config: InputConfig) => void;
  private panel: HTMLElement | null = null;
  private isVisible = false;
  private captureModal: HTMLElement | null = null;
  private gamepadHandler: (() => void) | null = null;
  private inputMode: InputMode = 'keyboard';

  constructor(options: InputConfigUIOptions) {
    this.container = options.container;
    this.configManager = options.configManager;
    this.onConfigChange = options.onConfigChange;
  }

  show(): void {
    if (this.isVisible) return;
    this.isVisible = true;
    this.render();
    this.setupGamepadListener();
  }

  hide(): void {
    if (!this.isVisible) return;
    this.isVisible = false;
    this.configManager.stopCapture();
    this.removeGamepadListener();
    this.panel?.remove();
    this.panel = null;
    this.captureModal?.remove();
    this.captureModal = null;
  }

  toggle(): void {
    if (this.isVisible) this.hide();
    else this.show();
  }

  private setupGamepadListener(): void {
    this.gamepadHandler = () => this.refreshGamepadStatus();
    window.addEventListener('gamepadconnected', this.gamepadHandler);
    window.addEventListener('gamepaddisconnected', this.gamepadHandler);
  }

  private removeGamepadListener(): void {
    if (!this.gamepadHandler) return;
    window.removeEventListener('gamepadconnected', this.gamepadHandler);
    window.removeEventListener('gamepaddisconnected', this.gamepadHandler);
    this.gamepadHandler = null;
  }

  private render(): void {
    this.panel = document.createElement('div');
    this.panel.className = 'j2me-input-config-panel';
    this.panel.dataset.inputMode = this.inputMode;
    this.panel.innerHTML = this.generatePanelHTML();
    this.container.appendChild(this.panel);
    this.setupEventListeners();
  }

  private generatePanelHTML(): string {
    const config = this.configManager.getConfig();
    return `
      <div class="j2me-config-header">
        <div>
          <h3>按键与手柄配置</h3>
          <p>先选择输入设备，再点击要绑定的手机按键</p>
        </div>
        <button class="j2me-config-close" type="button" title="关闭" aria-label="关闭">✕</button>
      </div>

      <div class="j2me-input-mode-switch" role="tablist" aria-label="输入设备">
        <button class="j2me-input-mode active" type="button" data-mode="keyboard" role="tab">
          <span class="j2me-mode-icon">⌨</span>
          <span><strong>键盘绑定</strong><small>点击手机键后按下键盘按键</small></span>
        </button>
        <button class="j2me-input-mode" type="button" data-mode="gamepad" role="tab">
          <span class="j2me-mode-icon">🎮</span>
          <span><strong>手柄绑定</strong><small>点击手机键后按下手柄按钮</small></span>
        </button>
      </div>

      <div class="j2me-config-content">
        <main class="j2me-phone-key-area">
          <div class="j2me-keyboard-heading">
            <div>
              <h4>手机按键</h4>
              <p>点击手机按键重新绑定；点击标签上的 × 可单独删除</p>
            </div>
            <div class="j2me-binding-legend">
              <span class="j2me-binding-chip keyboard">键盘</span>
              <span class="j2me-binding-chip gamepad">手柄</span>
            </div>
          </div>
          <div class="j2me-phone-keys">
            ${this.generatePhoneKeysHTML(config)}
          </div>
        </main>

        <aside class="j2me-config-sidebar">
          <section class="j2me-device-options active" data-mode="keyboard">
            <div class="j2me-config-section">
              <label>键盘预设</label>
              <div class="j2me-config-presets">
                <button class="j2me-preset-btn" type="button" data-preset="default">默认</button>
                <button class="j2me-preset-btn" type="button" data-preset="wasd">WASD</button>
                <button class="j2me-preset-btn" type="button" data-preset="numpad">数字键盘</button>
              </div>
            </div>
            <div class="j2me-config-tip">
              <strong>如何绑定</strong>
              <span>点击左侧任意手机按键，再按下希望使用的键盘键。</span>
            </div>
          </section>

          <section class="j2me-device-options" data-mode="gamepad">
            <div class="j2me-config-section">
              <label>已连接手柄</label>
              <div class="j2me-gamepad-status">${this.generateGamepadStatusHTML()}</div>
            </div>
            <div class="j2me-config-section">
              <label>手柄预设</label>
              <div class="j2me-config-presets">
                <button class="j2me-gamepad-preset-btn" type="button" data-preset="default">默认</button>
                <button class="j2me-gamepad-preset-btn" type="button" data-preset="xbox">Xbox</button>
                <button class="j2me-gamepad-preset-btn" type="button" data-preset="playstation">PS</button>
                <button class="j2me-gamepad-preset-btn" type="button" data-preset="switch">Switch</button>
              </div>
            </div>
            <div class="j2me-config-section">
              <label>摇杆死区 <span class="j2me-deadzone-value">${config.gamepad.deadzone}</span></label>
              <input type="range" class="j2me-deadzone-slider" min="0" max="1" step="0.05" value="${config.gamepad.deadzone}">
            </div>
            <div class="j2me-config-section">
              <label>模拟器控制</label>
              <div class="j2me-control-mappings">${this.generateControlMappingsHTML(config)}</div>
            </div>
            <div class="j2me-config-section">
              <label>按住快进倍速 <span class="j2me-fast-forward-speed-value">${config.controls.fastForwardSpeed.toFixed(1)}×</span></label>
              <input type="range" class="j2me-fast-forward-speed-slider" min="1" max="10" step="0.5" value="${config.controls.fastForwardSpeed}">
            </div>
          </section>
        </aside>
      </div>

      <div class="j2me-config-footer">
        <button class="j2me-config-reset" type="button">恢复默认</button>
        <span class="j2me-config-footer-spacer"></span>
        <button class="j2me-config-export" type="button">导出配置</button>
        <button class="j2me-config-import" type="button">导入配置</button>
      </div>
    `;
  }

  private generatePhoneKeysHTML(config: InputConfig): string {
    return `
      <section class="j2me-phone-key-group">
        <div class="j2me-phone-key-group-title">功能与方向</div>
        <div class="j2me-phone-key-grid j2me-navigation-grid">
          ${NAVIGATION_KEYS.map((code) => (code === null ? '<div></div>' : this.generatePhoneKeyHTML(config, code))).join('')}
        </div>
      </section>
      <section class="j2me-phone-key-group">
        <div class="j2me-phone-key-group-title">数字键盘</div>
        <div class="j2me-phone-key-grid j2me-number-grid">
          ${NUMBER_KEYS.map((code) => this.generatePhoneKeyHTML(config, code)).join('')}
        </div>
      </section>
    `;
  }

  private generatePhoneKeyHTML(
    config: InputConfig,
    j2meKeyCode: J2MEKeyCode,
  ): string {
    const keyboardBindings = Object.entries(config.keyboard).filter(
      ([, codes]) => codes.includes(j2meKeyCode),
    );
    const gamepadBindings = Object.entries(config.gamepad.buttons).filter(
      ([, code]) => code === j2meKeyCode,
    );
    const keyboardChips = keyboardBindings
      .map(
        ([key]) => `
          <button class="j2me-binding-chip keyboard removable" type="button"
            data-remove-device="keyboard" data-binding="${this.escapeHtml(key)}"
            data-j2me-key="${j2meKeyCode}" title="删除键盘绑定">
            <span>${this.escapeHtml(getKeyDisplayName(key))}</span><b>×</b>
          </button>`,
      )
      .join('');
    const gamepadChips = gamepadBindings
      .map(([button]) => {
        const name = GAMEPAD_BUTTON_NAMES[Number(button)] ?? `Button ${button}`;
        return `
          <button class="j2me-binding-chip gamepad removable" type="button"
            data-remove-device="gamepad" data-binding="${button}"
            data-j2me-key="${j2meKeyCode}" title="删除手柄绑定">
            <span>${this.escapeHtml(name)}</span><b>×</b>
          </button>`;
      })
      .join('');

    return `
      <div class="j2me-phone-key" role="button" tabindex="0" data-j2me-key="${j2meKeyCode}"
        aria-label="绑定${J2ME_KEY_NAMES[j2meKeyCode]}">
        <span class="j2me-phone-key-name">${J2ME_KEY_NAMES[j2meKeyCode]}</span>
        <div class="j2me-phone-key-bindings">
          ${keyboardChips || '<span class="j2me-unbound keyboard">未绑定</span>'}
          ${gamepadChips || '<span class="j2me-unbound gamepad">未绑定</span>'}
        </div>
      </div>
    `;
  }

  private generateGamepadStatusHTML(): string {
    const gamepads = this.configManager.getConnectedGamepads();
    if (gamepads.length === 0) {
      return '<div class="j2me-no-gamepad">未检测到手柄，请按任意手柄按钮连接</div>';
    }
    return gamepads
      .map(
        (gamepad) =>
          `<div class="j2me-gamepad-item">🎮 ${this.escapeHtml(gamepad.id)}</div>`,
      )
      .join('');
  }

  private generateControlMappingsHTML(config: InputConfig): string {
    const controls: Array<[EmulatorControl, string, number]> = [
      ['rewind', '时间回溯', config.controls.rewindGamepadButton],
      ['quickSave', '快速存档', config.controls.quickSaveGamepadButton],
      ['quickLoad', '快速读档', config.controls.quickLoadGamepadButton],
      ['fastForward', '按住快进', config.controls.fastForwardGamepadButton],
    ];
    return controls
      .map(([control, label, button]) => {
        const buttonName = GAMEPAD_BUTTON_NAMES[button] ?? `Button ${button}`;
        return `
          <div class="j2me-control-mapping" data-control="${control}">
            <span>${label}</span>
            <button class="j2me-capture-control-gamepad" type="button">${this.escapeHtml(buttonName)}</button>
          </div>`;
      })
      .join('');
  }

  private escapeHtml(value: string): string {
    const div = document.createElement('div');
    div.textContent = value;
    return div.innerHTML;
  }

  private setupEventListeners(): void {
    if (!this.panel) return;

    this.panel
      .querySelector('.j2me-config-close')
      ?.addEventListener('click', () => this.hide());

    this.panel.addEventListener('click', (event) => {
      const target = event.target as HTMLElement;
      const modeButton = target.closest<HTMLElement>('.j2me-input-mode');
      if (modeButton?.dataset.mode) {
        this.switchInputMode(modeButton.dataset.mode as InputMode);
        return;
      }

      const removeButton = target.closest<HTMLElement>('[data-remove-device]');
      if (removeButton) {
        event.stopPropagation();
        this.removeBinding(removeButton);
        return;
      }

      const phoneKey = target.closest<HTMLElement>('.j2me-phone-key');
      if (phoneKey?.dataset.j2meKey) {
        this.showCaptureModal(
          this.inputMode,
          Number(phoneKey.dataset.j2meKey) as J2MEKeyCode,
        );
        return;
      }

      const controlButton = target.closest<HTMLElement>(
        '.j2me-capture-control-gamepad',
      );
      const control = controlButton?.closest<HTMLElement>('[data-control]')
        ?.dataset.control as EmulatorControl | undefined;
      if (control) this.showControlCaptureModal(control);
    });

    this.panel.addEventListener('keydown', (event) => {
      const target = event.target as HTMLElement;
      if (
        (event.key === 'Enter' || event.key === ' ') &&
        target.classList.contains('j2me-phone-key')
      ) {
        event.preventDefault();
        this.showCaptureModal(
          this.inputMode,
          Number(target.dataset.j2meKey) as J2MEKeyCode,
        );
      }
    });

    this.panel.querySelectorAll('.j2me-preset-btn').forEach((button) => {
      button.addEventListener('click', (event) => {
        const preset = (event.currentTarget as HTMLElement).dataset
          .preset as KeyboardPreset;
        this.configManager.applyKeyboardPreset(preset);
        this.configChanged();
      });
    });

    this.panel
      .querySelectorAll('.j2me-gamepad-preset-btn')
      .forEach((button) => {
        button.addEventListener('click', (event) => {
          const preset = (event.currentTarget as HTMLElement).dataset
            .preset as GamepadPreset;
          this.configManager.applyGamepadPreset(preset);
          this.configChanged();
        });
      });

    const deadzoneSlider = this.panel.querySelector<HTMLInputElement>(
      '.j2me-deadzone-slider',
    );
    deadzoneSlider?.addEventListener('input', () => {
      this.configManager.setGamepadDeadzone(Number(deadzoneSlider.value));
      const value = this.panel?.querySelector('.j2me-deadzone-value');
      if (value) value.textContent = deadzoneSlider.value;
      this.notifyChange();
    });

    const fastForwardSpeedSlider = this.panel.querySelector<HTMLInputElement>(
      '.j2me-fast-forward-speed-slider',
    );
    fastForwardSpeedSlider?.addEventListener('input', () => {
      const speed = Number(fastForwardSpeedSlider.value);
      this.configManager.setFastForwardSpeed(speed);
      const value = this.panel?.querySelector('.j2me-fast-forward-speed-value');
      if (value) value.textContent = `${speed.toFixed(1)}×`;
      this.notifyChange();
    });

    this.panel
      .querySelector('.j2me-config-reset')
      ?.addEventListener('click', () => {
        this.configManager.reset();
        this.configChanged();
      });
    this.panel
      .querySelector('.j2me-config-export')
      ?.addEventListener('click', () => this.exportConfig());
    this.panel
      .querySelector('.j2me-config-import')
      ?.addEventListener('click', () => this.importConfig());
  }

  private switchInputMode(mode: InputMode): void {
    if (!this.panel) return;
    this.inputMode = mode;
    this.panel.dataset.inputMode = mode;
    this.panel
      .querySelectorAll<HTMLElement>('.j2me-input-mode')
      .forEach((button) => {
        button.classList.toggle('active', button.dataset.mode === mode);
      });
    this.panel
      .querySelectorAll<HTMLElement>('.j2me-device-options')
      .forEach((section) => {
        section.classList.toggle('active', section.dataset.mode === mode);
      });
  }

  private removeBinding(button: HTMLElement): void {
    const device = button.dataset.removeDevice as InputMode;
    const binding = button.dataset.binding;
    const targetCode = Number(button.dataset.j2meKey) as J2MEKeyCode;
    if (binding === undefined) return;

    if (device === 'gamepad') {
      this.configManager.removeGamepadButtonBinding(Number(binding));
    } else {
      const currentCodes =
        this.configManager.getKeyboardMapping()[binding] ?? [];
      const nextCodes = currentCodes.filter((code) => code !== targetCode);
      if (nextCodes.length === 0) this.configManager.removeKeyBinding(binding);
      else this.configManager.setKeyBinding(binding, nextCodes);
    }
    this.configChanged();
  }

  private showCaptureModal(type: InputMode, j2meKeyCode: J2MEKeyCode): void {
    this.hideCaptureModal();
    this.captureModal = document.createElement('div');
    this.captureModal.className = `j2me-capture-modal ${type}`;
    this.captureModal.innerHTML = `
      <div class="j2me-capture-modal-content">
        <div class="j2me-capture-device">${type === 'keyboard' ? '⌨ 键盘绑定' : '🎮 手柄绑定'}</div>
        <h4>${type === 'keyboard' ? '请按下键盘按键' : '请按下手柄按钮'}</h4>
        <p>目标手机按键：<strong>${J2ME_KEY_NAMES[j2meKeyCode]}</strong></p>
        <button class="j2me-capture-cancel" type="button">取消</button>
      </div>`;
    this.container.appendChild(this.captureModal);
    this.captureModal
      .querySelector('.j2me-capture-cancel')
      ?.addEventListener('click', () => this.hideCaptureModal());

    if (type === 'keyboard') {
      this.configManager.startKeyboardCapture((key) => {
        this.configManager.setKeyBinding(key, [j2meKeyCode]);
        this.configChanged();
        this.hideCaptureModal();
      });
    } else {
      this.configManager.startGamepadCapture((button) => {
        this.configManager.setGamepadButtonBinding(button, j2meKeyCode);
        this.configChanged();
        this.hideCaptureModal();
      });
    }
  }

  private showControlCaptureModal(control: EmulatorControl): void {
    const labels: Record<EmulatorControl, string> = {
      rewind: '时间回溯',
      quickSave: '快速存档',
      quickLoad: '快速读档',
      fastForward: '按住快进',
    };
    this.hideCaptureModal();
    this.captureModal = document.createElement('div');
    this.captureModal.className = 'j2me-capture-modal gamepad';
    this.captureModal.innerHTML = `
      <div class="j2me-capture-modal-content">
        <div class="j2me-capture-device">🎮 手柄绑定</div>
        <h4>请按下手柄按钮</h4>
        <p>目标模拟器控制：<strong>${labels[control]}</strong></p>
        <button class="j2me-capture-cancel" type="button">取消</button>
      </div>`;
    this.container.appendChild(this.captureModal);
    this.captureModal
      .querySelector('.j2me-capture-cancel')
      ?.addEventListener('click', () => this.hideCaptureModal());
    this.configManager.startGamepadCapture((button) => {
      if (control === 'rewind')
        this.configManager.setRewindGamepadButton(button);
      else if (control === 'quickSave')
        this.configManager.setQuickSaveGamepadButton(button);
      else if (control === 'quickLoad')
        this.configManager.setQuickLoadGamepadButton(button);
      else this.configManager.setFastForwardGamepadButton(button);
      this.configChanged();
      this.hideCaptureModal();
    });
  }

  private hideCaptureModal(): void {
    this.configManager.stopCapture();
    this.captureModal?.remove();
    this.captureModal = null;
  }

  private configChanged(): void {
    this.notifyChange();
    this.refreshBindings();
  }

  private refreshBindings(): void {
    if (!this.panel) return;
    const config = this.configManager.getConfig();
    const phoneKeys = this.panel.querySelector('.j2me-phone-keys');
    if (phoneKeys) phoneKeys.innerHTML = this.generatePhoneKeysHTML(config);
    const controls = this.panel.querySelector('.j2me-control-mappings');
    if (controls) controls.innerHTML = this.generateControlMappingsHTML(config);
    const slider = this.panel.querySelector<HTMLInputElement>(
      '.j2me-deadzone-slider',
    );
    const value = this.panel.querySelector('.j2me-deadzone-value');
    if (slider) slider.value = String(config.gamepad.deadzone);
    if (value) value.textContent = String(config.gamepad.deadzone);
    const fastForwardSpeedSlider = this.panel.querySelector<HTMLInputElement>(
      '.j2me-fast-forward-speed-slider',
    );
    const fastForwardSpeedValue = this.panel.querySelector(
      '.j2me-fast-forward-speed-value',
    );
    if (fastForwardSpeedSlider) {
      fastForwardSpeedSlider.value = String(config.controls.fastForwardSpeed);
    }
    if (fastForwardSpeedValue) {
      fastForwardSpeedValue.textContent = `${config.controls.fastForwardSpeed.toFixed(1)}×`;
    }
  }

  private refreshGamepadStatus(): void {
    const status = this.panel?.querySelector('.j2me-gamepad-status');
    if (status) status.innerHTML = this.generateGamepadStatusHTML();
  }

  private exportConfig(): void {
    const blob = new Blob([this.configManager.exportConfig()], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'j2me-input-config.json';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private importConfig(): void {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = () => {
      const file = input.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        if (this.configManager.importConfig(String(reader.result))) {
          this.configChanged();
          alert('导入成功！');
        } else {
          alert('导入失败，请检查文件格式。');
        }
      };
      reader.readAsText(file);
    };
    input.click();
  }

  private notifyChange(): void {
    this.onConfigChange?.(this.configManager.getConfig());
  }

  destroy(): void {
    this.hide();
  }
}
