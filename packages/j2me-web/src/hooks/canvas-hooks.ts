import { J2MEGameAction, J2MEKeyCode } from '../key-codes';

let caps = { events: false, motion: false, repeat: true };
export const setCanvasPointerCapabilities = (c: Partial<typeof caps>) => {
  caps = { ...caps, ...c };
};

export const createCanvasHooks = () => {
  const keyCodeToGameAction: Record<number, number> = {
    [J2MEKeyCode.KEY_UP]: J2MEGameAction.GAME_UP,
    [J2MEKeyCode.KEY_DOWN]: J2MEGameAction.GAME_DOWN,
    [J2MEKeyCode.KEY_LEFT]: J2MEGameAction.GAME_LEFT,
    [J2MEKeyCode.KEY_RIGHT]: J2MEGameAction.GAME_RIGHT,
    [J2MEKeyCode.KEY_FIRE]: J2MEGameAction.GAME_FIRE,
    [J2MEKeyCode.KEY_NUM2]: J2MEGameAction.GAME_UP,
    [J2MEKeyCode.KEY_NUM8]: J2MEGameAction.GAME_DOWN,
    [J2MEKeyCode.KEY_NUM4]: J2MEGameAction.GAME_LEFT,
    [J2MEKeyCode.KEY_NUM6]: J2MEGameAction.GAME_RIGHT,
    [J2MEKeyCode.KEY_NUM5]: J2MEGameAction.GAME_FIRE,
    [J2MEKeyCode.KEY_NUM1]: J2MEGameAction.GAME_A,
    [J2MEKeyCode.KEY_NUM3]: J2MEGameAction.GAME_B,
    [J2MEKeyCode.KEY_NUM7]: J2MEGameAction.GAME_C,
    [J2MEKeyCode.KEY_NUM9]: J2MEGameAction.GAME_D,
  };

  const gameActionToKeyCode: Record<number, number> = {
    [J2MEGameAction.GAME_UP]: J2MEKeyCode.KEY_UP,
    [J2MEGameAction.GAME_DOWN]: J2MEKeyCode.KEY_DOWN,
    [J2MEGameAction.GAME_LEFT]: J2MEKeyCode.KEY_LEFT,
    [J2MEGameAction.GAME_RIGHT]: J2MEKeyCode.KEY_RIGHT,
    [J2MEGameAction.GAME_FIRE]: J2MEKeyCode.KEY_FIRE,
    [J2MEGameAction.GAME_A]: J2MEKeyCode.KEY_NUM1,
    [J2MEGameAction.GAME_B]: J2MEKeyCode.KEY_NUM3,
    [J2MEGameAction.GAME_C]: J2MEKeyCode.KEY_NUM7,
    [J2MEGameAction.GAME_D]: J2MEKeyCode.KEY_NUM9,
  };

  const keyNames: Record<number, string> = {
    [J2MEKeyCode.KEY_UP]: 'Up',
    [J2MEKeyCode.KEY_DOWN]: 'Down',
    [J2MEKeyCode.KEY_LEFT]: 'Left',
    [J2MEKeyCode.KEY_RIGHT]: 'Right',
    [J2MEKeyCode.KEY_FIRE]: 'Select',
    [J2MEKeyCode.KEY_SOFT_LEFT]: 'Soft1',
    [J2MEKeyCode.KEY_SOFT_RIGHT]: 'Soft2',
    [J2MEKeyCode.KEY_CLEAR]: 'Clear',
    [J2MEKeyCode.KEY_SEND]: 'Send',
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

  return {
    canvas: {
      getGameAction: (keyCode: number): number => {
        return keyCodeToGameAction[keyCode] ?? 0;
      },

      getKeyCode: (gameAction: number): number => {
        return gameActionToKeyCode[gameAction] ?? 0;
      },

      getKeyName: (keyCode: number): string => {
        return keyNames[keyCode] ?? `Key${keyCode}`;
      },

      hasPointerEvents: (): boolean => {
        return 'ontouchstart' in window || 'onmousedown' in window;
      },

      hasPointerMotionEvents: (): boolean => {
        return 'ontouchmove' in window || 'onmousemove' in window;
      },

      hasRepeatEvents: (): boolean => {
        return true;
      },
    },
  };
};
