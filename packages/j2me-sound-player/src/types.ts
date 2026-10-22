export type PlayerState =
  | 'UNREALIZED'
  | 'REALIZED'
  | 'PREFETCHED'
  | 'STARTED'
  | 'CLOSED';

export interface PlayerHandle {
  play(): Promise<void>;
  pause(): void;
  stop(): void;
  close(): void;

  getDuration(): number;
  getCurrentTime(): number;
  setCurrentTime(time: number): void;

  getVolume(): number;
  setVolume(volume: number): void;
  isMuted(): boolean;
  setMuted(muted: boolean): void;

  setLoopCount(count: number): void;
  getContentType(): string;
  getState(): PlayerState;

  setSpeed(speed: number): void;
  getSpeed(): number;

  onStateChange?: (state: PlayerState) => void;
  onEnd?: () => void;
  onError?: (error: Error) => void;
}

export interface SoundPlayerOptions {
  audioContext?: AudioContext;
  /** Base URL containing the generated `<family>.sf3` files. */
  soundFontBaseUrl?: string | URL;
  /** URL of `spessasynth_processor.min.js`. */
  spessaWorkletUrl?: string | URL;
  /** Optional fetch implementation, primarily for custom asset hosts. */
  fetcher?: typeof fetch;
}

export interface ToneOptions {
  note: number;
  duration: number;
  volume: number;
}

export const SUPPORTED_AUDIO_TYPES = [
  'audio/mpeg',
  'audio/mp3',
  'audio/wav',
  'audio/x-wav',
  'audio/ogg',
  'audio/webm',
  'audio/aac',
] as const;

export const SUPPORTED_MIDI_TYPES = [
  'audio/midi',
  'audio/x-midi',
  'audio/mid',
] as const;

export const ALL_SUPPORTED_TYPES = [
  ...SUPPORTED_AUDIO_TYPES,
  ...SUPPORTED_MIDI_TYPES,
] as const;

export type SupportedAudioType = (typeof SUPPORTED_AUDIO_TYPES)[number];
export type SupportedMidiType = (typeof SUPPORTED_MIDI_TYPES)[number];
export type SupportedContentType = (typeof ALL_SUPPORTED_TYPES)[number];

export const isMidiType = (contentType: string): boolean => {
  const normalized = contentType.toLowerCase();
  return (
    normalized.includes('midi') ||
    normalized.includes('mid') ||
    normalized === 'audio/x-midi'
  );
};

export const isAudioType = (contentType: string): boolean => {
  const normalized = contentType.toLowerCase();
  return (
    normalized.includes('mpeg') ||
    normalized.includes('mp3') ||
    normalized.includes('wav') ||
    normalized.includes('ogg') ||
    normalized.includes('webm') ||
    normalized.includes('aac')
  );
};
