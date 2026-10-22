import type { J2MPlatformBridge } from '@jsjvm/j2me-simulator/env';
import type { FontManager } from '../font-manager';

export const createFontHooks = (
  fontManager: FontManager,
): { font: Required<NonNullable<J2MPlatformBridge['j2me']>>['font'] } => ({
  font: {
    stringWidth: (
      text: string,
      face: number,
      style: number,
      size: number,
    ): number => fontManager.stringWidth(text, face, style, size),
    charWidth: (
      charCode: number,
      face: number,
      style: number,
      size: number,
    ): number => fontManager.charWidth(charCode, face, style, size),
    charsWidth: (
      charCodes: number[],
      face: number,
      style: number,
      size: number,
    ): number => fontManager.charsWidth(charCodes, face, style, size),
    getHeight: (face: number, style: number, size: number): number =>
      fontManager.getHeight(face, style, size),
    getBaselinePosition: (face: number, style: number, size: number): number =>
      fontManager.getBaselinePosition(face, style, size),
  },
});
