import { BasePlayer } from './base-player.js';

export class AudioPlayer extends BasePlayer {
  private audio: HTMLAudioElement | null = null;
  private audioContext: AudioContext | null = null;
  private gainNode: GainNode | null = null;
  private sourceNode: MediaElementAudioSourceNode | null = null;
  private objectUrl: string | null = null;
  private ownsAudioContext = false;

  constructor(
    data: Uint8Array | string,
    contentType: string,
    audioContext?: AudioContext,
  ) {
    super(contentType);
    this.audioContext = audioContext || null;
    this.ownsAudioContext = !audioContext;

    if (typeof data === 'string') {
      this.initAudio(data);
    } else {
      const blob = new Blob([new Uint8Array(data)], { type: contentType });
      this.objectUrl = URL.createObjectURL(blob);
      this.initAudio(this.objectUrl);
    }
  }

  private initAudio(src: string): void {
    this.audio = new Audio(src);
    this.audio.preload = 'auto';

    this.audio.addEventListener('loadedmetadata', () => {
      this.setState('REALIZED');
    });

    this.audio.addEventListener('canplaythrough', () => {
      this.setState('PREFETCHED');
    });

    this.audio.addEventListener('play', () => {
      this.setState('STARTED');
    });

    this.audio.addEventListener('pause', () => {
      if (this.state === 'STARTED') {
        this.setState('PREFETCHED');
      }
    });

    this.audio.addEventListener('ended', () => {
      this.handleEnd();
    });

    this.audio.addEventListener('error', () => {
      const error = new Error(
        `Audio error: ${this.audio?.error?.message || 'Unknown error'}`,
      );
      this.onError?.(error);
    });

    this.setupWebAudio();
  }

  private setupWebAudio(): void {
    if (!this.audio) return;

    try {
      if (!this.audioContext) {
        this.audioContext = new AudioContext();
      }
      this.sourceNode = this.audioContext.createMediaElementSource(this.audio);
      this.gainNode = this.audioContext.createGain();
      this.sourceNode.connect(this.gainNode);
      this.gainNode.connect(this.audioContext.destination);
      this.updateVolume();
    } catch (err) {
      console.warn('Web Audio API not available, using basic audio', err);
    }
  }

  async play(): Promise<void> {
    if (!this.audio) {
      throw new Error('Audio not initialized');
    }

    if (this.audioContext?.state === 'suspended') {
      await this.audioContext.resume();
    }

    // If audio is at or near the end, reset to beginning for replay
    if (
      this.audio.ended ||
      (this.audio.duration && this.audio.currentTime >= this.audio.duration)
    ) {
      this.audio.currentTime = 0;
    }

    this.audio.playbackRate = this.speed;
    await this.audio.play();
  }

  pause(): void {
    this.audio?.pause();
  }

  stop(): void {
    if (this.audio) {
      this.audio.pause();
      this.audio.currentTime = 0;
      this.currentLoop = 0;
      this.setState('PREFETCHED');
    }
  }

  close(): void {
    this.stop();

    if (this.sourceNode) {
      this.sourceNode.disconnect();
      this.sourceNode = null;
    }

    if (this.gainNode) {
      this.gainNode.disconnect();
      this.gainNode = null;
    }

    if (this.audio) {
      this.audio.src = '';
      this.audio.load();
    }

    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }

    if (
      this.ownsAudioContext &&
      this.audioContext &&
      !this.audioContext.state.includes('closed')
    ) {
      this.audioContext.close().catch(() => {});
    }

    this.setState('CLOSED');
  }

  getDuration(): number {
    if (!this.audio || Number.isNaN(this.audio.duration)) {
      return -1;
    }
    return Math.floor(this.audio.duration * 1000);
  }

  getCurrentTime(): number {
    return this.audio ? Math.floor(this.audio.currentTime * 1000) : 0;
  }

  setCurrentTime(time: number): void {
    if (this.audio) {
      this.audio.currentTime = time / 1000;
    }
  }

  protected updateVolume(): void {
    if (!this.audio) return;

    const effectiveVolume = this.muted ? 0 : this.volume / 100;

    if (this.gainNode) {
      this.gainNode.gain.value = effectiveVolume;
    } else {
      this.audio.volume = effectiveVolume;
      this.audio.muted = this.muted;
    }
  }

  protected updateSpeed(): void {
    if (this.audio) {
      this.audio.playbackRate = this.speed;
    }
  }
}
