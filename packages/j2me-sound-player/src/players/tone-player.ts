export interface ToneOptions {
  note: number;
  duration: number;
  volume: number;
}

export class TonePlayer {
  private audioContext: AudioContext;
  private masterGain: GainNode;
  private speed = 1;
  private activeOscillators: Set<OscillatorNode> = new Set();

  constructor(audioContext?: AudioContext) {
    this.audioContext = audioContext || new AudioContext();
    this.masterGain = this.audioContext.createGain();
    this.masterGain.connect(this.audioContext.destination);
  }

  setSpeed(speed: number): void {
    this.speed = Math.max(0.1, Math.min(10, speed));
  }

  getSpeed(): number {
    return this.speed;
  }

  setVolume(volume: number): void {
    this.masterGain.gain.value = Math.max(0, Math.min(1, volume / 100));
  }

  async playTone(
    note: number,
    duration: number,
    volume: number,
  ): Promise<void> {
    if (this.audioContext.state === 'suspended') {
      await this.audioContext.resume();
    }

    const frequency = this.midiNoteToFrequency(note);
    const adjustedDuration = duration / this.speed;
    const gain = Math.max(0, Math.min(1, volume / 100));

    const oscillator = this.audioContext.createOscillator();
    const gainNode = this.audioContext.createGain();

    oscillator.type = 'square';
    oscillator.frequency.value = frequency;

    gainNode.gain.value = gain;

    oscillator.connect(gainNode);
    gainNode.connect(this.masterGain);

    const startTime = this.audioContext.currentTime;
    const endTime = startTime + adjustedDuration / 1000;

    gainNode.gain.setValueAtTime(gain, startTime);
    gainNode.gain.exponentialRampToValueAtTime(0.001, endTime);

    oscillator.start(startTime);
    oscillator.stop(endTime + 0.01);

    this.activeOscillators.add(oscillator);

    oscillator.onended = () => {
      this.activeOscillators.delete(oscillator);
      oscillator.disconnect();
      gainNode.disconnect();
    };
  }

  private midiNoteToFrequency(note: number): number {
    return 440 * 2 ** ((note - 69) / 12);
  }

  stopAll(): void {
    for (const oscillator of this.activeOscillators) {
      try {
        oscillator.stop();
        oscillator.disconnect();
      } catch {}
    }
    this.activeOscillators.clear();
  }

  dispose(): void {
    this.stopAll();
    if (!this.audioContext.state.includes('closed')) {
      this.audioContext.close().catch(() => {});
    }
  }
}
