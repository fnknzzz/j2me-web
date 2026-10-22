import { describe, expect, it } from 'vitest';
import { entries } from './compiled/MIDlet06_Graphics-entries.js';
import {
  createRecordingHooks,
  HooksRecorder,
  runMIDletFromEntries,
} from './test-utils';

describe('MIDlet06_Graphics', () => {
  it('should run MIDlet06_Graphics and match snapshot', async () => {
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
