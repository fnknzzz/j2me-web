/**
 * MIDI 文件分析工具
 * 用于检测 MIDI 文件的特征，帮助选择合适的播放器
 */

export interface MidiAnalysisResult {
  hasBankSelect: boolean;
  usedBanks: Array<{ channel: number; msb: number; lsb: number }>;
  trackCount: number;
  noteCount: number;
  usedPrograms: number[];
  usedFamilies: MidiInstrumentFamily[];
  usesDrums: boolean;
}

export const MIDI_INSTRUMENT_FAMILIES = [
  'piano',
  'chromatic',
  'organ',
  'guitar',
  'bass',
  'strings',
  'ensemble',
  'brass',
  'reed',
  'pipe',
  'synth-lead',
  'synth-pad',
  'synth-effects',
  'ethnic',
  'percussive',
  'sound-effects',
] as const;

export type MidiInstrumentFamily = (typeof MIDI_INSTRUMENT_FAMILIES)[number];

export const getMidiInstrumentFamily = (
  program: number,
): MidiInstrumentFamily =>
  MIDI_INSTRUMENT_FAMILIES[Math.max(0, Math.min(127, program)) >> 3];

/**
 * 分析 MIDI 文件，检测是否使用了 Bank Select
 * Bank Select 由 CC#0 (MSB) 和 CC#32 (LSB) 组成
 */
export const analyzeMidi = (data: ArrayBuffer): MidiAnalysisResult => {
  const bytes = new Uint8Array(data);
  let pos = 0;

  const result: MidiAnalysisResult = {
    hasBankSelect: false,
    usedBanks: [],
    trackCount: 0,
    noteCount: 0,
    usedPrograms: [],
    usedFamilies: [],
    usesDrums: false,
  };

  const bankMsb: number[] = new Array(16).fill(0);
  const bankLsb: number[] = new Array(16).fill(0);
  const programs: number[] = new Array(16).fill(0);
  const usedPrograms = new Set<number>();
  const usedFamilies = new Set<MidiInstrumentFamily>();
  const usedBankKeys = new Set<string>();

  const readVarLen = (): number => {
    let value = 0;
    let byte: number;
    do {
      byte = bytes[pos++];
      value = (value << 7) | (byte & 0x7f);
    } while (byte & 0x80);
    return value;
  };

  const readInt32 = (): number => {
    const val =
      (bytes[pos] << 24) |
      (bytes[pos + 1] << 16) |
      (bytes[pos + 2] << 8) |
      bytes[pos + 3];
    pos += 4;
    return val;
  };

  const readInt16 = (): number => {
    const val = (bytes[pos] << 8) | bytes[pos + 1];
    pos += 2;
    return val;
  };

  // 检查 MIDI 头
  const header = String.fromCharCode(bytes[0], bytes[1], bytes[2], bytes[3]);
  if (header !== 'MThd') {
    return result;
  }

  pos = 4;
  const headerLen = readInt32();
  pos += 2; // format
  const numTracks = readInt16();
  result.trackCount = numTracks;
  pos = 8 + headerLen;

  // 解析每个轨道
  for (let track = 0; track < numTracks && pos < bytes.length; track++) {
    const trackHeader = String.fromCharCode(
      bytes[pos],
      bytes[pos + 1],
      bytes[pos + 2],
      bytes[pos + 3],
    );
    if (trackHeader !== 'MTrk') break;

    pos += 4;
    const trackLen = readInt32();
    const trackEnd = pos + trackLen;

    let runningStatus = 0;

    while (pos < trackEnd && pos < bytes.length) {
      readVarLen(); // delta time
      let status = bytes[pos];

      if (status < 0x80) {
        status = runningStatus;
      } else {
        pos++;
        if (status < 0xf0) {
          runningStatus = status;
        }
      }

      const cmd = status & 0xf0;
      const channel = status & 0x0f;

      if (status === 0xff) {
        // Meta event
        const type = bytes[pos++];
        const len = readVarLen();
        pos += len;
        if (type === 0x2f) break; // End of track
      } else if (status === 0xf0 || status === 0xf7) {
        // SysEx
        const len = readVarLen();
        pos += len;
      } else if (cmd === 0x80 || cmd === 0x90 || cmd === 0xa0 || cmd === 0xe0) {
        // Note Off, Note On, Poly Aftertouch, Pitch Bend
        if (cmd === 0x90 && bytes[pos + 1] > 0) {
          result.noteCount++;
          if (channel === 9) {
            result.usesDrums = true;
          } else {
            const program = programs[channel];
            usedPrograms.add(program);
            usedFamilies.add(getMidiInstrumentFamily(program));
          }
        }
        pos += 2;
      } else if (cmd === 0xb0) {
        // Control Change
        const controller = bytes[pos++];
        const value = bytes[pos++];

        if (controller === 0) {
          // Bank Select MSB
          bankMsb[channel] = value;
          if (value !== 0) {
            result.hasBankSelect = true;
          }
        } else if (controller === 32) {
          // Bank Select LSB
          bankLsb[channel] = value;
          if (value !== 0) {
            result.hasBankSelect = true;
          }
        }
      } else if (cmd === 0xc0) {
        // Program Change
        programs[channel] = bytes[pos++];
        if (bankMsb[channel] !== 0 || bankLsb[channel] !== 0) {
          const key = `${channel}:${bankMsb[channel]}:${bankLsb[channel]}`;
          if (!usedBankKeys.has(key)) {
            usedBankKeys.add(key);
            result.usedBanks.push({
              channel,
              msb: bankMsb[channel],
              lsb: bankLsb[channel],
            });
          }
        }
        // 记录后重置（下一个 Program Change 需要新的 Bank Select）
        // 不重置，因为 Bank Select 通常在曲子开头设置一次
      } else if (cmd === 0xd0) {
        // Channel Aftertouch
        pos += 1;
      }
    }
  }

  result.usedPrograms = [...usedPrograms].sort((a, b) => a - b);
  result.usedFamilies = MIDI_INSTRUMENT_FAMILIES.filter((family) =>
    usedFamilies.has(family),
  );

  return result;
};
