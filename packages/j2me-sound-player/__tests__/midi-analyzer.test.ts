import { describe, expect, it } from 'vitest';
import {
  analyzeMidi,
  getMidiInstrumentFamily,
} from '../src/utils/midi-analyzer.js';

const makeMidi = (events: number[]): ArrayBuffer =>
  new Uint8Array([
    0x4d,
    0x54,
    0x68,
    0x64,
    0,
    0,
    0,
    6,
    0,
    0,
    0,
    1,
    0,
    96,
    0x4d,
    0x54,
    0x72,
    0x6b,
    0,
    0,
    0,
    events.length,
    ...events,
  ]).buffer;

describe('analyzeMidi', () => {
  it('collects only the melodic families actually played plus drums', () => {
    const midi = makeMidi([
      0,
      0xc0,
      24, // guitar
      0,
      0x90,
      60,
      100,
      0,
      0x99,
      36,
      100, // channel 10 drums
      0,
      0xb0,
      0,
      1, // variation bank
      0,
      0xff,
      0x2f,
      0,
    ]);

    expect(analyzeMidi(midi)).toMatchObject({
      hasBankSelect: true,
      noteCount: 2,
      usedPrograms: [24],
      usedFamilies: ['guitar'],
      usesDrums: true,
    });
  });

  it('maps every GM program group to an eight-program family', () => {
    expect(getMidiInstrumentFamily(0)).toBe('piano');
    expect(getMidiInstrumentFamily(31)).toBe('guitar');
    expect(getMidiInstrumentFamily(32)).toBe('bass');
    expect(getMidiInstrumentFamily(127)).toBe('sound-effects');
  });
});
