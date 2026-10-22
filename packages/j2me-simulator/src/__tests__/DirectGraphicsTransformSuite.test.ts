import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { createRecordingHooks, HooksRecorder, runMIDlet } from './test-utils';

describe('DirectGraphicsTransformSuite', () => {
  it('should correctly transform images with DirectGraphics.drawImage', async () => {
    const jarPath = join(
      __dirname,
      'j2me-jars',
      'DirectGraphicsTransformSuite.jar',
    );
    const jarBuffer = readFileSync(jarPath);

    const recorder = new HooksRecorder();
    const hooks = createRecordingHooks(recorder);

    const result = await runMIDlet({ jar: jarBuffer, hooks });
    if (result.error) throw result.error;

    const snapshot = recorder.getSnapshot();
    expect(snapshot).toMatchSnapshot();

    expect(snapshot.includes('NONE: x=10')).toBe(true);
    expect(snapshot.includes('FLIP_H: x=10')).toBe(true);
    expect(snapshot.includes('FLIP_V: x=10')).toBe(true);
    expect(snapshot.includes('ROT_90: x=10')).toBe(true);
    expect(snapshot.includes('ROT_180: x=10')).toBe(true);
    expect(snapshot.includes('ROT_270: x=10')).toBe(true);
    expect(snapshot.includes('FLIP_H+ROT_90: x=10')).toBe(true);
    expect(snapshot.includes('DirectGraphicsTransformSuite paint done')).toBe(
      true,
    );
  });

  it('should use correct MIDP transform values for Nokia manipulation constants', async () => {
    const jarPath = join(
      __dirname,
      'j2me-jars',
      'DirectGraphicsTransformSuite.jar',
    );
    const jarBuffer = readFileSync(jarPath);

    const recorder = new HooksRecorder();
    const hooks = createRecordingHooks(recorder);

    const result = await runMIDlet({ jar: jarBuffer, hooks });
    if (result.error) throw result.error;

    const calls = recorder.getCalls();
    const drawRegionCalls = calls.filter(
      (c) => c.category === 'graphics' && c.method === 'drawRegion',
    );

    const transforms = drawRegionCalls.map((c) => c.args[5]);

    expect(transforms.slice(0, 7)).toEqual([2, 1, 6, 3, 5, 7]);
  });
});
