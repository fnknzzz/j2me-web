import type { SoundPlayerOptions } from './types.js';
import type { MidiInstrumentFamily } from './utils/midi-analyzer.js';

export type SoundFontFamily = MidiInstrumentFamily | 'drums';

const DEFAULT_SOUNDFONT_URLS: Record<SoundFontFamily, URL> = {
  piano: new URL('../assets/soundfonts/piano.sf3', import.meta.url),
  chromatic: new URL('../assets/soundfonts/chromatic.sf3', import.meta.url),
  organ: new URL('../assets/soundfonts/organ.sf3', import.meta.url),
  guitar: new URL('../assets/soundfonts/guitar.sf3', import.meta.url),
  bass: new URL('../assets/soundfonts/bass.sf3', import.meta.url),
  strings: new URL('../assets/soundfonts/strings.sf3', import.meta.url),
  ensemble: new URL('../assets/soundfonts/ensemble.sf3', import.meta.url),
  brass: new URL('../assets/soundfonts/brass.sf3', import.meta.url),
  reed: new URL('../assets/soundfonts/reed.sf3', import.meta.url),
  pipe: new URL('../assets/soundfonts/pipe.sf3', import.meta.url),
  'synth-lead': new URL('../assets/soundfonts/synth-lead.sf3', import.meta.url),
  'synth-pad': new URL('../assets/soundfonts/synth-pad.sf3', import.meta.url),
  'synth-effects': new URL(
    '../assets/soundfonts/synth-effects.sf3',
    import.meta.url,
  ),
  ethnic: new URL('../assets/soundfonts/ethnic.sf3', import.meta.url),
  percussive: new URL('../assets/soundfonts/percussive.sf3', import.meta.url),
  'sound-effects': new URL(
    '../assets/soundfonts/sound-effects.sf3',
    import.meta.url,
  ),
  drums: new URL('../assets/soundfonts/drums.sf3', import.meta.url),
};

const workletRegistrations = new WeakMap<BaseAudioContext, Promise<void>>();
const soundFontCache = new Map<string, Promise<ArrayBuffer>>();
const spessaWorkletUrl = new URL(
  'spessasynth_lib/dist/spessasynth_processor.min.js',
  import.meta.url,
).href;

export class SpessaResources {
  private readonly soundFontBaseUrl?: string;
  private readonly workletUrl: string;
  private readonly fetcher: typeof fetch;

  constructor(options: SoundPlayerOptions = {}) {
    const resolutionBase = globalThis.document?.baseURI ?? import.meta.url;
    const baseUrl = options.soundFontBaseUrl?.toString();
    if (baseUrl) {
      const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
      this.soundFontBaseUrl = new URL(normalizedBase, resolutionBase).href;
    }
    this.workletUrl = options.spessaWorkletUrl
      ? new URL(options.spessaWorkletUrl.toString(), resolutionBase).href
      : spessaWorkletUrl;
    this.fetcher = options.fetcher ?? globalThis.fetch.bind(globalThis);
  }

  registerWorklet(context: AudioContext): Promise<void> {
    const existing = workletRegistrations.get(context);
    if (existing) return existing;

    const registration = context.audioWorklet
      .addModule(this.workletUrl)
      .catch((error) => {
        workletRegistrations.delete(context);
        throw error;
      });
    workletRegistrations.set(context, registration);
    return registration;
  }

  async loadSoundFont(family: SoundFontFamily): Promise<ArrayBuffer> {
    const url = this.resolveSoundFontUrl(family);
    let cached = soundFontCache.get(url);
    if (!cached) {
      cached = this.fetcher(url)
        .then(async (response) => {
          if (!response.ok) {
            throw new Error(
              `Failed to load SoundFont ${family}: ${response.status} ${response.statusText}`,
            );
          }
          return response.arrayBuffer();
        })
        .catch((error) => {
          soundFontCache.delete(url);
          throw error;
        });
      soundFontCache.set(url, cached);
    }

    // addSoundBank transfers and detaches its input buffer.
    return (await cached).slice(0);
  }

  private resolveSoundFontUrl(family: SoundFontFamily): string {
    if (this.soundFontBaseUrl) {
      return new URL(`${family}.sf3`, this.soundFontBaseUrl).href;
    }
    return DEFAULT_SOUNDFONT_URLS[family].href;
  }
}
