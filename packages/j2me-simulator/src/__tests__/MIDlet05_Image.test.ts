import { describe, expect, it } from 'vitest';
import { entries } from './compiled/MIDlet05_Image-entries.js';
import {
  createRecordingHooks,
  HooksRecorder,
  runMIDletFromEntries,
} from './test-utils';

describe('MIDlet05_Image', () => {
  it('should run MIDlet05_Image and match snapshot', async () => {
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
