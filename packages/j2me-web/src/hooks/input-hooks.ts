import type { J2MEKeyCode } from '../key-codes';

export const createInputHooks = () => {
  const keyPressedHandlers = new Set<(keyCode: J2MEKeyCode) => void>();
  const keyReleasedHandlers = new Set<(keyCode: J2MEKeyCode) => void>();
  const keyRepeatedHandlers = new Set<(keyCode: J2MEKeyCode) => void>();
  const pressedRepeatTimers = new Map<number, number>();
  const pressedRepeatTimeouts = new Map<number, number>();
  const INITIAL_REPEAT_DELAY = 300;
  const REPEAT_INTERVAL = 120;
  const pointerPressedHandlers = new Set<(x: number, y: number) => void>();
  const pointerReleasedHandlers = new Set<(x: number, y: number) => void>();
  const pointerDraggedHandlers = new Set<(x: number, y: number) => void>();

  const triggerKeyPressed = (keyCode: J2MEKeyCode) => {
    for (const handler of keyPressedHandlers) {
      handler(keyCode);
    }

    if (
      !pressedRepeatTimers.has(keyCode) &&
      !pressedRepeatTimeouts.has(keyCode)
    ) {
      const timeoutId = window.setTimeout(() => {
        const intervalId = window.setInterval(() => {
          for (const handler of keyRepeatedHandlers) {
            handler(keyCode);
          }
        }, REPEAT_INTERVAL);
        pressedRepeatTimers.set(keyCode, intervalId);
        pressedRepeatTimeouts.delete(keyCode);
      }, INITIAL_REPEAT_DELAY);
      pressedRepeatTimeouts.set(keyCode, timeoutId);
    }
  };

  const triggerKeyReleased = (keyCode: J2MEKeyCode) => {
    for (const handler of keyReleasedHandlers) {
      handler(keyCode);
    }
    const t = pressedRepeatTimers.get(keyCode);
    if (t !== undefined) {
      window.clearInterval(t);
      pressedRepeatTimers.delete(keyCode);
    }
    const to = pressedRepeatTimeouts.get(keyCode);
    if (to !== undefined) {
      window.clearTimeout(to);
      pressedRepeatTimeouts.delete(keyCode);
    }
  };

  const triggerPointerPressed = (x: number, y: number) => {
    for (const handler of pointerPressedHandlers) {
      handler(x, y);
    }
  };

  const triggerPointerReleased = (x: number, y: number) => {
    for (const handler of pointerReleasedHandlers) {
      handler(x, y);
    }
  };

  const triggerPointerDragged = (x: number, y: number) => {
    for (const handler of pointerDraggedHandlers) {
      handler(x, y);
    }
  };

  return {
    input: {
      onKeyPressed: (handler: (keyCode: J2MEKeyCode) => void) => {
        keyPressedHandlers.add(handler);
      },

      onKeyReleased: (handler: (keyCode: J2MEKeyCode) => void) => {
        keyReleasedHandlers.add(handler);
      },

      onKeyRepeated: (handler: (keyCode: J2MEKeyCode) => void) => {
        keyRepeatedHandlers.add(handler);
      },

      onPointerPressed: (handler: (x: number, y: number) => void) => {
        pointerPressedHandlers.add(handler);
      },

      onPointerReleased: (handler: (x: number, y: number) => void) => {
        pointerReleasedHandlers.add(handler);
      },

      onPointerDragged: (handler: (x: number, y: number) => void) => {
        pointerDraggedHandlers.add(handler);
      },

      triggerKeyPressed,
      triggerKeyReleased,
      triggerPointerPressed,
      triggerPointerReleased,
      triggerPointerDragged,
    },
  };
};
