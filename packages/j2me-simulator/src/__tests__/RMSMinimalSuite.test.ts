import { beforeEach, describe, expect, it, vi } from 'vitest';
import { entries } from './compiled/RMSMinimalSuite-entries.js';
import {
  createRecordingHooks,
  HooksRecorder,
  runMIDletFromEntries,
} from './test-utils';

describe('RMSMinimalSuite', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T00:00:00.000Z'));
  });

  it(
    'should run RMSMinimalSuite and match snapshot',
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
