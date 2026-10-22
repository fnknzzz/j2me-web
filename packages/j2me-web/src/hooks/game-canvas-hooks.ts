export const createGameCanvasHooks = (flush: (surfaceId: number) => void) => {
  let keyStates = 0;

  const KEY_STATE_UP = 1 << 1;
  const KEY_STATE_DOWN = 1 << 6;
  const KEY_STATE_LEFT = 1 << 2;
  const KEY_STATE_RIGHT = 1 << 5;
  const KEY_STATE_FIRE = 1 << 8;
  const KEY_STATE_GAME_A = 1 << 9;
  const KEY_STATE_GAME_B = 1 << 10;
  const KEY_STATE_GAME_C = 1 << 11;
  const KEY_STATE_GAME_D = 1 << 12;

  const keyCodeToStateMask: Record<number, number> = {
    38: KEY_STATE_UP,
    40: KEY_STATE_DOWN,
    37: KEY_STATE_LEFT,
    39: KEY_STATE_RIGHT,
    13: KEY_STATE_FIRE,
    32: KEY_STATE_FIRE,
    49: KEY_STATE_GAME_A,
    50: KEY_STATE_GAME_B,
    51: KEY_STATE_GAME_C,
    52: KEY_STATE_GAME_D,
  };

  const isInputTarget = (event: KeyboardEvent): boolean => {
    const t = event.target as HTMLElement | null;
    if (!t) return false;
    const tag = t.tagName;
    return (
      tag === 'INPUT' ||
      tag === 'TEXTAREA' ||
      tag === 'SELECT' ||
      (t as HTMLElement).isContentEditable
    );
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    if (isInputTarget(event)) return;
    const mask = keyCodeToStateMask[event.keyCode];
    if (mask) {
      keyStates |= mask;
    }
  };

  const handleKeyUp = (event: KeyboardEvent) => {
    if (isInputTarget(event)) return;
    const mask = keyCodeToStateMask[event.keyCode];
    if (mask) {
      keyStates &= ~mask;
    }
  };

  window.addEventListener('keydown', handleKeyDown);
  window.addEventListener('keyup', handleKeyUp);

  return {
    gameCanvas: {
      flushGraphics: (surfaceId: number) => {
        flush(surfaceId);
      },

      flushGraphicsRegion: (
        surfaceId: number,
        x: number,
        y: number,
        width: number,
        height: number,
      ) => {
        void x;
        void y;
        void width;
        void height;
        flush(surfaceId);
      },

      getKeyStates: () => {
        return keyStates;
      },
    },

    destroy: () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
    },
  };
};
