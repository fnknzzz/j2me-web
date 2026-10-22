export const J2MEKeyCode = {
  KEY_UP: -1,
  KEY_DOWN: -2,
  KEY_LEFT: -3,
  KEY_RIGHT: -4,
  KEY_FIRE: -5,
  KEY_SOFT_LEFT: -6,
  KEY_SOFT_RIGHT: -7,
  KEY_CLEAR: -8,
  KEY_SEND: -9,

  KEY_NUM0: 48,
  KEY_NUM1: 49,
  KEY_NUM2: 50,
  KEY_NUM3: 51,
  KEY_NUM4: 52,
  KEY_NUM5: 53,
  KEY_NUM6: 54,
  KEY_NUM7: 55,
  KEY_NUM8: 56,
  KEY_NUM9: 57,

  KEY_STAR: 42,
  KEY_POUND: 35,
} as const;

export type J2MEKeyCode = (typeof J2MEKeyCode)[keyof typeof J2MEKeyCode];

export const J2MEGameAction = {
  GAME_UP: 1,
  GAME_DOWN: 6,
  GAME_LEFT: 2,
  GAME_RIGHT: 5,
  GAME_FIRE: 8,
  GAME_A: 9,
  GAME_B: 10,
  GAME_C: 11,
  GAME_D: 12,
} as const;

export type J2MEGameAction =
  (typeof J2MEGameAction)[keyof typeof J2MEGameAction];
