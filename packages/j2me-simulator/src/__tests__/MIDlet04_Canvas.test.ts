import { describe, expect, it } from 'vitest';
import { entries } from './compiled/MIDlet04_Canvas-entries.js';
import {
  createRecordingHooks,
  HooksRecorder,
  runMIDletFromEntries,
} from './test-utils';

describe('MIDlet04_Canvas', () => {
  it('should run MIDlet04_Canvas and match snapshot', async () => {
    const recorder = new HooksRecorder();
    const hooks = createRecordingHooks(recorder)!;

    const result = await runMIDletFromEntries(entries, hooks);

    if (result.error) {
      throw result.error;
    }

    const snapshot = recorder.getSnapshot();
    expect(snapshot).toMatchSnapshot();
  });
});
