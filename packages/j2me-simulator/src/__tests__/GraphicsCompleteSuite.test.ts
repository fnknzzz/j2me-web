import { describe, expect, it } from 'vitest';
import { entries } from './compiled/GraphicsCompleteSuite-entries.js';
import {
  createRecordingHooks,
  HooksRecorder,
  runMIDletFromEntries,
} from './test-utils';

describe('GraphicsCompleteSuite', () => {
  it(
    'should run GraphicsCompleteSuite and match snapshot',
    { timeout: 10000 },
    async () => {
      const recorder = new HooksRecorder();
      const hooks = createRecordingHooks(recorder)!;

      const result = await runMIDletFromEntries(entries, hooks);

      if (result.error) {
        throw result.error;
      }

      const snapshot = recorder.getSnapshot();
      expect(snapshot).toMatchSnapshot();
    },
  );
});
