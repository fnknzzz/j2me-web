import { describe, expect, it } from 'vitest';
import type { MediaCommandSnapshot } from '../backend/media-command-executor';
import {
  unwrapStateWithMediaSnapshot,
  wrapStateWithMediaSnapshot,
} from '../backend/media-snapshot-envelope';

describe('media snapshot save-state envelope', () => {
  it('round-trips media control state without changing VM bytes', () => {
    const vmState = new Uint8Array([99, 108, 115, 118, 1, 2, 3]);
    const mediaSnapshot: MediaCommandSnapshot = {
      version: 1,
      players: [
        {
          handle: 7,
          loopCount: -1,
          volume: 60,
          muted: false,
          playing: true,
        },
      ],
    };

    const wrapped = wrapStateWithMediaSnapshot(vmState, mediaSnapshot);
    const restored = unwrapStateWithMediaSnapshot(wrapped);

    expect(restored.vmState).toEqual(vmState);
    expect(restored.mediaSnapshot).toEqual(mediaSnapshot);
  });

  it('keeps old save states compatible', () => {
    const vmState = new Uint8Array([99, 108, 115, 118, 1, 2, 3]);

    expect(unwrapStateWithMediaSnapshot(vmState)).toEqual({
      vmState,
      mediaSnapshot: null,
    });
  });
});
