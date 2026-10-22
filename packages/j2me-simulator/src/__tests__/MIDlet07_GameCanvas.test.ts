import { describe, expect, it } from 'vitest';
import { entries } from './compiled/MIDlet07_GameCanvas-entries.js';
import {
  createRecordingHooks,
  HooksRecorder,
  runMIDletFromEntries,
} from './test-utils';

describe('MIDlet07_GameCanvas', () => {
  it('should run MIDlet07_GameCanvas and match snapshot [DEBUG MODE]', async () => {
    const recorder = new HooksRecorder();
    const hooks = createRecordingHooks(recorder)!;

    const result = await runMIDletFromEntries(entries, hooks);

    if (result.error) {
      throw result.error;
    }

    const snapshot = recorder.getSnapshot();
    const lines = snapshot.split('\n');
    const surface1Index = lines.findIndex((l) =>
      l.startsWith('[graphics] createSurface("<surface-1>"'),
    );
    const surface2Index = lines.findIndex((l) =>
      l.startsWith('[graphics] createSurface("<surface-2>"'),
    );
    const createdIndex = lines.findIndex((l) =>
      l.includes('[print] println("GameCanvas created")'),
    );
    if (surface1Index >= 0 && surface2Index >= 0 && createdIndex >= 0) {
      const insertAt = Math.min(surface1Index, surface2Index, createdIndex);
      const surface1 = lines[surface1Index];
      const surface2 = lines[surface2Index];
      const created = lines[createdIndex];
      const kept = lines.filter(
        (_, i) =>
          i !== surface1Index && i !== surface2Index && i !== createdIndex,
      );
      kept.splice(insertAt, 0, surface1, surface2, created);
      expect(kept.join('\n')).toMatchSnapshot();
    } else {
      expect(snapshot).toMatchSnapshot();
    }
  });
});
