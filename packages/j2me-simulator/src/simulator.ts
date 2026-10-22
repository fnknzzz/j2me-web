import type { JvmRewindSnapshot } from '@jsjvm/j2me-core';
import { RustJ2MESimulatorBackend } from './backend/rust';
import type { MIDletState } from './midlet-state';
import type { J2MESimulatorOptions } from './types';

export type { J2MESimulatorOptions } from './types';

export class J2MESimulator {
  private backend: RustJ2MESimulatorBackend;

  constructor(options: J2MESimulatorOptions) {
    this.backend = new RustJ2MESimulatorBackend(options);
  }

  initialize(): Promise<void> {
    return this.backend.initialize();
  }

  getMIDletState(): MIDletState {
    return this.backend.getMIDletState();
  }

  start(): Promise<void> {
    return this.backend.start();
  }

  pause(): Promise<void> {
    return this.backend.pause();
  }

  suspendExecution(): Promise<void> {
    return this.backend.suspendExecution();
  }

  resumeExecution(): Promise<void> {
    return this.backend.resumeExecution();
  }

  destroy(unconditional = true): Promise<void> {
    return this.backend.destroy(unconditional);
  }

  keyPressed(keyCode: number): Promise<void> {
    return this.backend.keyPressed(keyCode);
  }

  keyReleased(keyCode: number): Promise<void> {
    return this.backend.keyReleased(keyCode);
  }

  keyRepeated(keyCode: number): Promise<void> {
    return this.backend.keyRepeated(keyCode);
  }

  pointerPressed(x: number, y: number): Promise<void> {
    return this.backend.pointerPressed(x, y);
  }

  pointerDragged(x: number, y: number): Promise<void> {
    return this.backend.pointerDragged(x, y);
  }

  pointerReleased(x: number, y: number): Promise<void> {
    return this.backend.pointerReleased(x, y);
  }

  showNotify(): Promise<void> {
    return this.backend.showNotify();
  }

  hideNotify(): Promise<void> {
    return this.backend.hideNotify();
  }

  sizeChanged(width: number, height: number): Promise<void> {
    return this.backend.sizeChanged(width, height);
  }

  commandAction(commandHandle: number): Promise<void> {
    return this.backend.commandAction(commandHandle);
  }

  itemCommandAction(commandHandle: number, itemHandle: number): Promise<void> {
    return this.backend.itemCommandAction(commandHandle, itemHandle);
  }

  itemStateChanged(itemHandle: number): Promise<void> {
    return this.backend.itemStateChanged(itemHandle);
  }

  repaint(): Promise<void> {
    return this.backend.repaint();
  }

  repaintRegion(
    x: number,
    y: number,
    width: number,
    height: number,
  ): Promise<void> {
    return this.backend.repaintRegion(x, y, width, height);
  }

  serviceRepaints(): Promise<void> {
    return this.backend.serviceRepaints();
  }

  dumpThreads(): string {
    const result = this.backend.dumpThreads();
    // Auto-download as file for convenience
    if (typeof document !== 'undefined') {
      const blob = new Blob([result], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `thread-dump-${Date.now()}.txt`;
      a.click();
      URL.revokeObjectURL(url);
    }
    return result;
  }

  setSpeed(multiplier: number): void {
    this.backend.setSpeed(multiplier);
  }

  getSpeed(): number {
    return this.backend.getSpeed();
  }

  async saveState(): Promise<Uint8Array | null> {
    return this.backend.saveState();
  }

  saveStateFast(): Promise<Uint8Array | null> {
    return this.backend.saveStateFast();
  }

  captureRewindSnapshot(): Promise<JvmRewindSnapshot | null> {
    return this.backend.captureRewindSnapshot();
  }

  loadState(data: Uint8Array): void {
    this.backend.loadState(data);
  }

  restoreRewindSnapshot(snapshot: JvmRewindSnapshot): Promise<void> {
    return this.backend.restoreRewindSnapshot(snapshot);
  }

  getJvm() {
    return this.backend.getJvm();
  }
}
