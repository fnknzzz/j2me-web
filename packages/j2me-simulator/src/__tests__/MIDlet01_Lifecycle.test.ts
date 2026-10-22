import { describe, expect, it } from 'vitest';
import { MIDletState } from '../midlet-state';
import { J2MESimulator } from '../simulator';
import { entries } from './compiled/MIDlet01_Lifecycle-entries.js';
import { createRecordingHooks, HooksRecorder } from './test-utils';

describe('MIDlet01_Lifecycle', () => {
  it('should run MIDlet01_Lifecycle and match snapshot', async () => {
    const recorder = new HooksRecorder();
    const hooks = createRecordingHooks(recorder)!;

    const sim = new J2MESimulator({
      jar: entries,
      hooks,
    });
    await sim.initialize();
    expect(sim.getMIDletState()).toBe(MIDletState.PAUSED);
    await sim.start();
    expect(sim.getMIDletState()).toBe(MIDletState.ACTIVE);
    await sim.pause();
    expect(sim.getMIDletState()).toBe(MIDletState.PAUSED);
    await sim.destroy(true);
    expect(sim.getMIDletState()).toBe(MIDletState.DESTROYED);

    const snapshot = recorder.getSnapshot();
    expect(snapshot).toMatchSnapshot();
  });
});
