import { AudioPlayer } from './players/audio-player.js';
import { SpessaMidiPlayer } from './players/spessa-midi-player.js';
import { TonePlayer } from './players/tone-player.js';
import { SpessaResources } from './spessa-resources.js';
import type { PlayerHandle, SoundPlayerOptions } from './types.js';
import { ALL_SUPPORTED_TYPES, isAudioType, isMidiType } from './types.js';

export class SoundPlayer {
  private audioContext: AudioContext;
  private tonePlayer: TonePlayer;
  private spessaResources: SpessaResources;
  private speed = 1;
  private masterVolume = 100;
  private players: Set<PlayerHandle> = new Set();

  constructor(options: SoundPlayerOptions = {}) {
    this.audioContext = options.audioContext || new AudioContext();
    this.tonePlayer = new TonePlayer(this.audioContext);
    this.spessaResources = new SpessaResources(options);
  }

  async createPlayer(
    data: Uint8Array,
    contentType: string,
  ): Promise<PlayerHandle> {
    const normalizedType = contentType.toLowerCase();

    let player: PlayerHandle;

    if (isMidiType(normalizedType)) {
      player = await this.createMidiPlayer(data, contentType);
    } else if (isAudioType(normalizedType)) {
      player = new AudioPlayer(data, contentType, this.audioContext);
    } else {
      player = new AudioPlayer(data, contentType, this.audioContext);
    }

    player.setSpeed(this.speed);
    player.setVolume(this.masterVolume);

    this.players.add(player);

    const originalClose = player.close.bind(player);
    player.close = () => {
      this.players.delete(player);
      originalClose();
    };

    return player;
  }

  async createPlayerFromUrl(
    url: string,
    contentType?: string,
  ): Promise<PlayerHandle> {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to fetch audio: ${response.statusText}`);
    }

    const data = new Uint8Array(await response.arrayBuffer());
    const type = contentType || this.guessContentType(url);

    return this.createPlayer(data, type);
  }

  private async createMidiPlayer(
    data: Uint8Array,
    contentType: string,
  ): Promise<PlayerHandle> {
    return new SpessaMidiPlayer(
      data,
      contentType,
      this.audioContext,
      this.spessaResources,
    );
  }

  private guessContentType(url: string): string {
    const ext = url.split('.').pop()?.toLowerCase();
    switch (ext) {
      case 'mid':
      case 'midi':
        return 'audio/midi';
      case 'mp3':
        return 'audio/mpeg';
      case 'wav':
        return 'audio/wav';
      case 'ogg':
        return 'audio/ogg';
      case 'webm':
        return 'audio/webm';
      case 'aac':
        return 'audio/aac';
      default:
        return 'audio/mpeg';
    }
  }

  playTone(note: number, duration: number, volume: number): void {
    const adjustedVolume = (volume * this.masterVolume) / 100;
    this.tonePlayer.setSpeed(this.speed);
    this.tonePlayer.playTone(note, duration, adjustedVolume);
  }

  setSpeed(speed: number): void {
    this.speed = Math.max(0.1, Math.min(10, speed));
    this.tonePlayer.setSpeed(this.speed);

    for (const player of this.players) {
      player.setSpeed(this.speed);
    }
  }

  getSpeed(): number {
    return this.speed;
  }

  setMasterVolume(volume: number): void {
    this.masterVolume = Math.max(0, Math.min(100, volume));
    this.tonePlayer.setVolume(this.masterVolume);

    for (const player of this.players) {
      player.setVolume(this.masterVolume);
    }
  }

  getMasterVolume(): number {
    return this.masterVolume;
  }

  getSupportedContentTypes(): string[] {
    return [...ALL_SUPPORTED_TYPES];
  }

  getSupportedProtocols(): string[] {
    return ['http', 'https', 'file', 'data'];
  }

  private pausedByHost: Map<PlayerHandle, 'STARTED' | 'PREFETCHED'> = new Map();

  pauseAll(): void {
    this.pausedByHost.clear();
    for (const player of this.players) {
      const state = player.getState();
      if (state === 'STARTED' || state === 'PREFETCHED') {
        this.pausedByHost.set(player, state);
        if (state === 'STARTED') {
          player.pause();
        }
      }
    }
  }

  resumeAll(): void {
    for (const [player, stateBeforePause] of this.pausedByHost) {
      if (this.players.has(player) && stateBeforePause === 'STARTED') {
        const currentState = player.getState();
        if (currentState !== 'STARTED' && currentState !== 'CLOSED') {
          player.play().catch(() => {});
        }
      }
    }
    this.pausedByHost.clear();
  }

  stopAll(): void {
    for (const player of this.players) {
      player.stop();
    }
    this.tonePlayer.stopAll();
  }

  closeAll(): void {
    for (const player of [...this.players]) {
      player.close();
    }
    this.players.clear();
  }

  dispose(): void {
    this.closeAll();
    this.tonePlayer.dispose();

    if (!this.audioContext.state.includes('closed')) {
      this.audioContext.close().catch(() => {});
    }
  }

  getAudioContext(): AudioContext {
    return this.audioContext;
  }
}
