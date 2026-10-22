import type { PlayerHandle, PlayerState } from '../types.js';

export abstract class BasePlayer implements PlayerHandle {
  protected state: PlayerState = 'UNREALIZED';
  protected volume = 100;
  protected muted = false;
  protected loopCount = 1;
  protected currentLoop = 0;
  protected speed = 1;
  protected contentType: string;

  onStateChange?: (state: PlayerState) => void;
  onEnd?: () => void;
  onError?: (error: Error) => void;

  constructor(contentType: string) {
    this.contentType = contentType;
  }

  abstract play(): Promise<void>;
  abstract pause(): void;
  abstract stop(): void;
  abstract close(): void;
  abstract getDuration(): number;
  abstract getCurrentTime(): number;
  abstract setCurrentTime(time: number): void;

  getVolume(): number {
    return this.volume;
  }

  setVolume(volume: number): void {
    this.volume = Math.max(0, Math.min(100, volume));
    this.updateVolume();
  }

  isMuted(): boolean {
    return this.muted;
  }

  setMuted(muted: boolean): void {
    this.muted = muted;
    this.updateVolume();
  }

  setLoopCount(count: number): void {
    this.loopCount = count;
    this.currentLoop = 0;
  }

  getContentType(): string {
    return this.contentType;
  }

  getState(): PlayerState {
    return this.state;
  }

  setSpeed(speed: number): void {
    this.speed = Math.max(0.1, Math.min(10, speed));
    this.updateSpeed();
  }

  getSpeed(): number {
    return this.speed;
  }

  protected setState(newState: PlayerState): void {
    if (this.state !== newState) {
      this.state = newState;
      this.onStateChange?.(newState);
    }
  }

  protected handleEnd(): void {
    this.currentLoop++;
    if (this.loopCount === -1 || this.currentLoop < this.loopCount) {
      this.setCurrentTime(0);
      this.play().catch((err) => this.onError?.(err));
    } else {
      this.setState('PREFETCHED');
      this.currentLoop = 0;
      this.onEnd?.();
    }
  }

  protected abstract updateVolume(): void;
  protected abstract updateSpeed(): void;
}
