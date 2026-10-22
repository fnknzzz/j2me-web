import { Sequencer, WorkletSynthesizer } from 'spessasynth_lib';
import type { SoundFontFamily, SpessaResources } from '../spessa-resources.js';
import { analyzeMidi } from '../utils/midi-analyzer.js';
import { BasePlayer } from './base-player.js';

export class SpessaMidiPlayer extends BasePlayer {
  private readonly audioContext: AudioContext;
  private readonly midiData: ArrayBuffer;
  private readonly resources: SpessaResources;
  private readonly gainNode: GainNode;
  private synthesizer: WorkletSynthesizer | null = null;
  private sequencer: Sequencer | null = null;
  private initialization?: Promise<void>;
  private duration = 0;
  private pendingTime = 0;
  private pendingStop = false;

  constructor(
    data: Uint8Array,
    contentType: string,
    audioContext: AudioContext,
    resources: SpessaResources,
  ) {
    super(contentType);
    this.audioContext = audioContext;
    this.resources = resources;
    this.midiData = data.buffer.slice(
      data.byteOffset,
      data.byteOffset + data.byteLength,
    ) as ArrayBuffer;
    this.gainNode = audioContext.createGain();
    this.gainNode.connect(audioContext.destination);
    this.updateVolume();
    this.setState('REALIZED');
  }

  private initialize(): Promise<void> {
    this.initialization ??= this.initializeInternal();
    return this.initialization;
  }

  private async initializeInternal(): Promise<void> {
    await this.resources.registerWorklet(this.audioContext);
    if (this.getState() === 'CLOSED') return;

    const synthesizer = new WorkletSynthesizer(this.audioContext);
    this.synthesizer = synthesizer;
    synthesizer.connect(this.gainNode);
    await synthesizer.isReady;
    if (this.getState() === 'CLOSED') return;

    const analysis = analyzeMidi(this.midiData);
    const families: SoundFontFamily[] =
      analysis.usedFamilies.length > 0 ? analysis.usedFamilies : ['piano'];
    const soundFonts: SoundFontFamily[] = analysis.usesDrums
      ? [...families, 'drums']
      : families;

    for (const family of soundFonts) {
      if (this.getState() === 'CLOSED') return;
      const soundFont = await this.resources.loadSoundFont(family);
      if (this.getState() === 'CLOSED') return;
      await synthesizer.soundBankManager.addSoundBank(soundFont, family);
    }

    if (this.getState() === 'CLOSED') return;
    const sequencer = new Sequencer(synthesizer, {
      skipToFirstNoteOn: false,
      initialPlaybackRate: this.speed,
    });
    this.sequencer = sequencer;

    await new Promise<void>((resolve, reject) => {
      sequencer.eventHandler.addEvent('songChange', 'j2me-ready', () => {
        sequencer.eventHandler.removeEvent('songChange', 'j2me-ready');
        this.duration = sequencer.duration * 1000;
        sequencer.currentTime = this.pendingTime / 1000;
        resolve();
      });
      sequencer.eventHandler.addEvent(
        'midiError',
        'j2me-load-error',
        (error) => {
          sequencer.eventHandler.removeEvent('midiError', 'j2me-load-error');
          reject(error);
        },
      );
      sequencer.loadNewSongList([
        { binary: this.midiData.slice(0), fileName: 'j2me.mid' },
      ]);
    });

    sequencer.eventHandler.removeEvent('midiError', 'j2me-load-error');
    sequencer.eventHandler.addEvent('midiError', 'j2me-error', (error) => {
      this.onError?.(error);
    });
    sequencer.eventHandler.addEvent('songEnded', 'j2me-ended', () => {
      if (this.state === 'CLOSED') return;
      this.pendingTime = 0;
      this.setState('PREFETCHED');
      this.onEnd?.();
    });

    this.applyLoopCount();
    this.setState('PREFETCHED');
  }

  async play(): Promise<void> {
    if (this.state === 'CLOSED') {
      throw new Error('Cannot play a closed MIDI player');
    }

    this.pendingStop = false;
    await this.initialize();
    if (this.pendingStop || !this.sequencer) return;

    if (this.audioContext.state === 'suspended') {
      await this.audioContext.resume();
    }
    this.sequencer.play();
    this.setState('STARTED');
  }

  pause(): void {
    if (!this.sequencer || this.state !== 'STARTED') return;
    this.sequencer.pause();
    this.pendingTime = this.sequencer.currentTime * 1000;
    this.setState('PREFETCHED');
  }

  stop(): void {
    this.pendingStop = true;
    if (!this.sequencer) return;
    this.sequencer.pause();
    this.sequencer.currentTime = 0;
    this.pendingTime = 0;
    this.currentLoop = 0;
    this.applyLoopCount();
    this.setState('PREFETCHED');
  }

  close(): void {
    if (this.state === 'CLOSED') return;
    this.pendingStop = true;
    this.sequencer?.pause();
    this.synthesizer?.destroy();
    this.synthesizer = null;
    this.sequencer = null;
    this.gainNode.disconnect();
    this.setState('CLOSED');
  }

  getDuration(): number {
    return Math.floor(this.duration);
  }

  getCurrentTime(): number {
    return this.sequencer
      ? Math.max(0, this.sequencer.currentTime * 1000)
      : this.pendingTime;
  }

  setCurrentTime(time: number): void {
    const upperBound =
      this.duration > 0 ? this.duration : Number.POSITIVE_INFINITY;
    this.pendingTime = Math.max(0, Math.min(time, upperBound));
    if (this.sequencer) {
      this.sequencer.currentTime = this.pendingTime / 1000;
    }
  }

  setLoopCount(count: number): void {
    super.setLoopCount(count);
    this.applyLoopCount();
  }

  private applyLoopCount(): void {
    if (!this.sequencer) return;
    this.sequencer.loopCount =
      this.loopCount === -1
        ? Number.POSITIVE_INFINITY
        : Math.max(0, this.loopCount - 1);
  }

  protected updateVolume(): void {
    const gain = this.muted ? 0 : this.volume / 100;
    this.gainNode.gain.setValueAtTime(gain, this.audioContext.currentTime);
  }

  protected updateSpeed(): void {
    if (this.sequencer) {
      this.sequencer.playbackRate = this.speed;
    }
  }
}
