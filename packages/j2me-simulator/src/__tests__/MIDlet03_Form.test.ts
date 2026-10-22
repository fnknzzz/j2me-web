import { describe, expect, it } from 'vitest';
import { entries } from './compiled/MIDlet03_Form-entries.js';
import {
  createRecordingHooks,
  HooksRecorder,
  runMIDletFromEntries,
} from './test-utils';

describe('MIDlet03_Form', () => {
  it('should run MIDlet03_Form and match snapshot', async () => {
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
