import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { createRecordingHooks, HooksRecorder, runMIDlet } from './test-utils';

describe('DirectGraphicsSuite', () => {
  it('should run and match snapshot, fmt=0', async () => {
    const jarPath = join(__dirname, 'j2me-jars', 'DirectGraphicsSuite.jar');
    const jarBuffer = readFileSync(jarPath);

    const recorder = new HooksRecorder();
    const hooks = createRecordingHooks(recorder);

    const result = await runMIDlet({ jar: jarBuffer, hooks });
    if (result.error) throw result.error;

    const snapshot = recorder.getSnapshot();
    expect(snapshot).toMatchSnapshot();
    expect(snapshot.includes('fmt=8888')).toBe(true);
  });
});
