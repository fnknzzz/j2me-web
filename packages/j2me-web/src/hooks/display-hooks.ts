// J2ME Canvas GameAction constants
const GameAction = {
  UP: 1,
  DOWN: 6,
  LEFT: 2,
  RIGHT: 5,
  FIRE: 8,
  GAME_A: 9,
  GAME_B: 10,
  GAME_C: 11,
  GAME_D: 12,
} as const;

// J2ME Canvas standard key codes (negative values)
// These match the values used in input-hooks.ts
const J2MEKeyCode = {
  UP: -1,
  DOWN: -2,
  LEFT: -3,
  RIGHT: -4,
  FIRE: -5,
  GAME_A: -6,
  GAME_B: -7,
  GAME_C: -8,
  GAME_D: -9,
  NUM_0: 48,
  NUM_1: 49,
  NUM_2: 50,
  NUM_3: 51,
  NUM_4: 52,
  NUM_5: 53,
  NUM_6: 54,
  NUM_7: 55,
  NUM_8: 56,
  NUM_9: 57,
  STAR: 42,
  POUND: 35,
} as const;

// Map J2ME key codes to game actions
const KEY_TO_GAME_ACTION: Record<number, number> = {
  [J2MEKeyCode.UP]: GameAction.UP,
  [J2MEKeyCode.DOWN]: GameAction.DOWN,
  [J2MEKeyCode.LEFT]: GameAction.LEFT,
  [J2MEKeyCode.RIGHT]: GameAction.RIGHT,
  [J2MEKeyCode.FIRE]: GameAction.FIRE,
  [J2MEKeyCode.GAME_A]: GameAction.GAME_A,
  [J2MEKeyCode.NUM_1]: GameAction.GAME_A,
  [J2MEKeyCode.NUM_3]: GameAction.GAME_B,
  [J2MEKeyCode.NUM_7]: GameAction.GAME_C,
  [J2MEKeyCode.NUM_9]: GameAction.GAME_D,
};

// Map game actions back to J2ME key codes
const GAME_ACTION_TO_KEY: Record<number, number> = {
  [GameAction.UP]: J2MEKeyCode.UP,
  [GameAction.DOWN]: J2MEKeyCode.DOWN,
  [GameAction.LEFT]: J2MEKeyCode.LEFT,
  [GameAction.RIGHT]: J2MEKeyCode.RIGHT,
  [GameAction.FIRE]: J2MEKeyCode.FIRE,
  [GameAction.GAME_A]: J2MEKeyCode.NUM_1,
  [GameAction.GAME_B]: J2MEKeyCode.NUM_3,
  [GameAction.GAME_C]: J2MEKeyCode.NUM_7,
  [GameAction.GAME_D]: J2MEKeyCode.NUM_9,
};

// Human-readable key names for J2ME key codes
const KEY_NAMES: Record<number, string> = {
  [J2MEKeyCode.UP]: 'Up',
  [J2MEKeyCode.DOWN]: 'Down',
  [J2MEKeyCode.LEFT]: 'Left',
  [J2MEKeyCode.RIGHT]: 'Right',
  [J2MEKeyCode.FIRE]: 'Fire',
  [J2MEKeyCode.NUM_0]: '0',
  [J2MEKeyCode.NUM_1]: '1',
  [J2MEKeyCode.NUM_2]: '2',
  [J2MEKeyCode.NUM_3]: '3',
  [J2MEKeyCode.NUM_4]: '4',
  [J2MEKeyCode.NUM_5]: '5',
  [J2MEKeyCode.NUM_6]: '6',
  [J2MEKeyCode.NUM_7]: '7',
  [J2MEKeyCode.NUM_8]: '8',
  [J2MEKeyCode.NUM_9]: '9',
  [J2MEKeyCode.STAR]: '*',
  [J2MEKeyCode.POUND]: '#',
};

const DisplayColor = {
  WHITE: 0xffffff,
  BLACK: 0x000000,
  LIGHT_GRAY: 0xcccccc,
  DARK_GRAY: 0x808080,
  BLUE: 0x0000ff,
  RED: 0xff0000,
} as const;

const COLOR_SPECIFIER_MAP: Record<number, number> = {
  0: DisplayColor.WHITE,
  1: DisplayColor.BLACK,
  2: DisplayColor.LIGHT_GRAY,
  3: DisplayColor.DARK_GRAY,
  4: DisplayColor.BLUE,
  5: DisplayColor.RED,
};

const ImageType = {
  LIST_ELEMENT: 1,
  CHOICE_GROUP_ELEMENT: 2,
} as const;

export const createDisplayHooks = (
  canvas: HTMLCanvasElement,
  width: number,
  height: number,
) => {
  return {
    display: {
      getWidth: () => width,

      getHeight: () => height,

      isColor: () => true,

      numColors: () => 16777216,

      numAlphaLevels: () => 256,

      getBestImageWidth: (imageType: number) => {
        if (imageType === ImageType.LIST_ELEMENT) {
          return width;
        }
        if (imageType === ImageType.CHOICE_GROUP_ELEMENT) return 64;
        return 64;
      },

      getBestImageHeight: (imageType: number) => {
        if (imageType === ImageType.LIST_ELEMENT) {
          return height;
        }
        if (imageType === ImageType.CHOICE_GROUP_ELEMENT) return 64;
        return 64;
      },

      getBorderStyle: (highlighted: boolean) => (highlighted ? 1 : 0),

      getColor: (colorSpecifier: number) =>
        COLOR_SPECIFIER_MAP[colorSpecifier] ?? DisplayColor.BLACK,

      flashBacklight: (duration: number) => {
        const originalOpacity = canvas.style.opacity || '1';
        canvas.style.opacity = '0.5';
        setTimeout(() => {
          canvas.style.opacity = originalOpacity;
        }, duration);
        return true;
      },

      vibrate: (duration: number) => {
        if ('vibrate' in navigator) {
          navigator.vibrate(duration);
          return true;
        }
        return false;
      },
    },

    canvas: {
      getGameAction: (keyCode: number) => KEY_TO_GAME_ACTION[keyCode] ?? 0,

      getKeyCode: (gameAction: number) => GAME_ACTION_TO_KEY[gameAction] ?? 0,

      getKeyName: (keyCode: number) => KEY_NAMES[keyCode] ?? `Key${keyCode}`,

      hasPointerEvents: () =>
        'ontouchstart' in window || 'onmousedown' in window,

      hasPointerMotionEvents: () =>
        'ontouchmove' in window || 'onmousemove' in window,

      hasRepeatEvents: () => true,
    },
  };
};
