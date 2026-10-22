import { describe, expect, it, vi } from 'vitest';
import { RustJ2MESimulatorBackend } from '../backend/rust';

const createMediaCommandBuffer = (includeLoopCommand = true): Uint8Array => {
  const contentType = new TextEncoder().encode('audio/midi');
  const contentTypeLength = (contentType.length + 3) & ~3;
  const data = new Uint8Array([1, 2, 3]);
  const dataLength = (data.length + 3) & ~3;
  const bytes = new Uint8Array(
    1 +
      12 +
      4 +
      contentTypeLength +
      4 +
      dataLength +
      (includeLoopCommand ? 1 + 8 : 0) +
      1 +
      12,
  );
  const view = new DataView(bytes.buffer);
  let offset = 0;

  bytes[offset++] = 73; // CMD_MEDIA_CREATE_PLAYER_BINARY
  view.setInt32(offset, 7, true);
  view.setInt32(offset + 4, 1, true);
  view.setInt32(offset + 8, 100, true);
  offset += 12;
  view.setUint32(offset, contentType.length, true);
  offset += 4;
  bytes.set(contentType, offset);
  offset += contentTypeLength;
  view.setUint32(offset, data.length, true);
  offset += 4;
  bytes.set(data, offset);
  offset += dataLength;

  if (includeLoopCommand) {
    bytes[offset++] = 70; // CMD_MEDIA_SET_LOOP_COUNT
    view.setInt32(offset, 7, true);
    view.setInt32(offset + 4, -1, true);
    offset += 8;
  }

  bytes[offset++] = 64; // CMD_MEDIA_PLAY
  view.setInt32(offset, 7, true);
  view.setInt32(offset + 4, 0, true);
  view.setInt32(offset + 8, 0, true);

  return bytes;
};

describe('RustJ2MESimulatorBackend async claim flow', () => {
  it('should claim pending async requests before resolving and resuming', async () => {
    const platformDecodeImageData = vi
      .fn()
      .mockResolvedValue({ width: 17, height: 23 });
    const claimPendingAsync = vi
      .fn()
      .mockReturnValueOnce([
        { id: 42, type: 'decode_image', surface_id: 7, bytes_len: 3 },
      ])
      .mockReturnValueOnce([]);
    const resolveAsyncImage = vi.fn();
    const resumeAsync = vi.fn().mockReturnValue(0);

    const backend = new RustJ2MESimulatorBackend({
      hooks: {
        j2me: {
          image: {
            platformDecodeImageData,
          },
        },
      },
    } as any) as any;

    backend.jvm = {
      claimPendingAsync,
      getAsyncImageBytes: vi.fn().mockReturnValue(new Uint8Array([1, 2, 3])),
      resolveAsyncImage,
      resumeAsync,
      flushCommandBuffer: vi.fn().mockReturnValue(null),
    };

    await backend.processAsyncLoop();

    expect(claimPendingAsync).toHaveBeenCalledTimes(1);
    expect(platformDecodeImageData).toHaveBeenCalledTimes(1);
    expect(resolveAsyncImage).toHaveBeenCalledWith(42, 17, 23);
    expect(resumeAsync).toHaveBeenCalledTimes(1);
    expect(backend.pendingDecodedImages.get(7)).toEqual({
      width: 17,
      height: 23,
    });
  });

  it('should treat zero async state counts as a clean snapshot boundary without claiming', async () => {
    const claimPendingAsync = vi.fn();
    const backend = new RustJ2MESimulatorBackend({} as any) as any;

    backend.jvm = {
      getAsyncStateCounts: vi.fn().mockReturnValue([0, 0, 0]),
      claimPendingAsync,
    };

    await expect(backend.waitForSnapshotSafePoint()).resolves.toBe(true);
    expect(claimPendingAsync).not.toHaveBeenCalled();
  });

  it('should reject saveState when claimed async is still inflight', async () => {
    const saveState = vi.fn();
    const backend = new RustJ2MESimulatorBackend({} as any) as any;

    backend.jvm = {
      getAsyncStateCounts: vi.fn().mockReturnValue([0, 1, 0]),
      saveState,
    };

    await expect(backend.saveState()).resolves.toBeNull();
    expect(saveState).not.toHaveBeenCalled();
  });

  it('captures rewind state without using the serialized save path', async () => {
    const rewindSnapshot = { dispose: vi.fn() };
    const captureRewindSnapshot = vi.fn().mockReturnValue(rewindSnapshot);
    const saveState = vi.fn();
    const backend = new RustJ2MESimulatorBackend({} as any) as any;

    backend.jvm = {
      getAsyncStateCounts: vi.fn().mockReturnValue([0, 0, 0]),
      flushCommandBuffer: vi.fn().mockReturnValue(null),
      clearImageSurfaceSnapshots: vi.fn(),
      captureRewindSnapshot,
      saveState,
    };

    await expect(backend.captureRewindSnapshot()).resolves.toBe(rewindSnapshot);
    expect(captureRewindSnapshot).toHaveBeenCalledTimes(1);
    expect(saveState).not.toHaveBeenCalled();
  });

  it('uses the fast persistable path for automatic saves', async () => {
    const fastState = new Uint8Array([4, 5, 6]);
    const saveStateFast = vi.fn().mockReturnValue(fastState);
    const saveState = vi.fn();
    const backend = new RustJ2MESimulatorBackend({} as any) as any;

    backend.jvm = {
      getAsyncStateCounts: vi.fn().mockReturnValue([0, 0, 0]),
      flushCommandBuffer: vi.fn().mockReturnValue(null),
      clearImageSurfaceSnapshots: vi.fn(),
      saveStateFast,
      saveState,
    };

    await expect(backend.saveStateFast()).resolves.toBe(fastState);
    expect(saveStateFast).toHaveBeenCalledTimes(1);
    expect(saveState).not.toHaveBeenCalled();
  });

  it('should clear host overlay state before loading a snapshot', () => {
    const updateSoftKeyLabels = vi.fn();
    const backend = new RustJ2MESimulatorBackend({
      hooks: {
        j2me: {
          lcdUi: {
            updateSoftKeyLabels,
          },
        },
      },
    } as any) as any;

    backend.jvm = {
      parseSaveMeta: vi.fn().mockReturnValue(null),
      loadState: vi.fn(),
      flushCommandBuffer: vi.fn().mockReturnValue(null),
      takePendingResourceImageRestores: vi.fn().mockReturnValue([]),
      takePendingDerivedImageRestores: vi.fn().mockReturnValue([]),
      repaint: vi.fn(),
    };
    backend.started = true;

    backend.loadState(new Uint8Array([1, 2, 3]));

    expect(updateSoftKeyLabels).toHaveBeenCalledWith({});
  });

  it('routes coarse Bluetooth requests through the injected host bridge', async () => {
    const resolveAsyncBluetooth = vi.fn();
    const backend = new RustJ2MESimulatorBackend({
      hooks: {
        j2me: {
          bluetooth: {
            getCapabilities: vi.fn().mockResolvedValue({
              peerId: 'local-peer',
              name: 'Local device',
            }),
          },
        },
      },
    } as any) as any;
    backend.jvm = {
      claimPendingAsync: vi
        .fn()
        .mockReturnValueOnce([
          { id: 9, type: 'bluetooth', operation: 'capabilities', payload: '' },
        ])
        .mockReturnValueOnce([]),
      resolveAsyncBluetooth,
      resumeAsync: vi.fn().mockReturnValue(0),
      flushCommandBuffer: vi.fn().mockReturnValue(null),
    };

    await backend.processAsyncLoop();

    expect(resolveAsyncBluetooth).toHaveBeenCalledTimes(1);
    expect(resolveAsyncBluetooth.mock.calls[0][0]).toBe(9);
    expect(resolveAsyncBluetooth.mock.calls[0][1]).toMatch(
      /^OK\t[0-9A-F]{12}\tLocal device$/,
    );
  });

  it('applies loop count before play when player creation is still pending', async () => {
    const calls: string[] = [];
    const player = {
      close: vi.fn(),
      play: vi.fn(async () => {
        calls.push('play');
      }),
      setLoopCount: vi.fn((count: number) => {
        calls.push(`loop:${count}`);
      }),
      setVolume: vi.fn((volume: number) => {
        calls.push(`volume:${volume}`);
      }),
    };
    let resolvePlayer!: (value: typeof player) => void;
    const pendingPlayer = new Promise<typeof player>((resolve) => {
      resolvePlayer = resolve;
    });
    const commands = createMediaCommandBuffer();
    const backend = new RustJ2MESimulatorBackend({
      hooks: {
        j2me: {
          media: {
            createPlayer: vi.fn().mockReturnValue(pendingPlayer),
          },
        },
      },
    } as any) as any;
    backend.jvm = {
      flushCommandBuffer: vi
        .fn()
        .mockReturnValueOnce({ data: commands, len: commands.length })
        .mockReturnValue(null),
    };

    backend.flushOutputToHooksSync();
    const queued = backend.mediaCommands.waitForIdle(7) as Promise<void>;

    expect(player.setLoopCount).not.toHaveBeenCalled();
    expect(player.play).not.toHaveBeenCalled();

    resolvePlayer(player);
    await queued;

    expect(calls).toEqual(['volume:100', 'loop:-1', 'play']);
  });

  it('restores media control state from a fast save', async () => {
    const sourcePlayer = {
      close: vi.fn(),
      play: vi.fn().mockResolvedValue(undefined),
      setLoopCount: vi.fn(),
      setVolume: vi.fn(),
    };
    const sourceBackend = new RustJ2MESimulatorBackend({
      hooks: {
        j2me: {
          media: {
            createPlayer: vi.fn().mockResolvedValue(sourcePlayer),
          },
        },
      },
    } as any) as any;
    const vmState = new Uint8Array([99, 108, 115, 118, 1, 2, 3]);
    sourceBackend.jvm = {
      flushCommandBuffer: vi
        .fn()
        .mockReturnValueOnce({
          data: createMediaCommandBuffer(),
          len: createMediaCommandBuffer().length,
        })
        .mockReturnValue(null),
      getAsyncStateCounts: vi.fn().mockReturnValue([0, 0, 0]),
      clearImageSurfaceSnapshots: vi.fn(),
      saveStateFast: vi.fn().mockReturnValue(vmState),
    };
    sourceBackend.flushOutputToHooksSync();
    await sourceBackend.mediaCommands.waitForIdle(7);

    const saved = await sourceBackend.saveStateFast();
    if (!saved) throw new Error('expected fast save state');
    expect(saved.length).toBeGreaterThan(vmState.length);

    const restoredPlayer = {
      close: vi.fn(),
      play: vi.fn().mockResolvedValue(undefined),
      setLoopCount: vi.fn(),
      setVolume: vi.fn(),
    };
    const restoredBackend = new RustJ2MESimulatorBackend({
      hooks: {
        j2me: {
          media: {
            createPlayer: vi.fn().mockResolvedValue(restoredPlayer),
          },
        },
      },
    } as any) as any;
    const restoreCommands = createMediaCommandBuffer(false);
    const loadState = vi.fn();
    restoredBackend.jvm = {
      parseSaveMeta: vi.fn().mockReturnValue(null),
      loadState,
      flushCommandBuffer: vi
        .fn()
        .mockReturnValueOnce({
          data: restoreCommands,
          len: restoreCommands.length,
        })
        .mockReturnValue(null),
      takePendingResourceImageRestores: vi.fn().mockReturnValue([]),
      takePendingDerivedImageRestores: vi.fn().mockReturnValue([]),
      repaint: vi.fn(),
    };

    restoredBackend.loadState(saved);
    await restoredBackend.mediaCommands.waitForIdle(7);

    expect(loadState).toHaveBeenCalledWith(vmState);
    expect(restoredPlayer.setLoopCount).toHaveBeenCalledWith(-1);
  });

  it('restores media control state with an in-memory rewind snapshot', async () => {
    const sourcePlayer = {
      close: vi.fn(),
      play: vi.fn().mockResolvedValue(undefined),
      setLoopCount: vi.fn(),
      setVolume: vi.fn(),
    };
    const restoredPlayer = {
      close: vi.fn(),
      play: vi.fn().mockResolvedValue(undefined),
      setLoopCount: vi.fn(),
      setVolume: vi.fn(),
    };
    const backend = new RustJ2MESimulatorBackend({
      hooks: {
        j2me: {
          media: {
            createPlayer: vi
              .fn()
              .mockResolvedValueOnce(sourcePlayer)
              .mockResolvedValueOnce(restoredPlayer),
          },
        },
      },
    } as any) as any;
    const snapshot = { dispose: vi.fn() };
    const jvm = {
      flushCommandBuffer: vi
        .fn()
        .mockReturnValueOnce({
          data: createMediaCommandBuffer(),
          len: createMediaCommandBuffer().length,
        })
        .mockReturnValue(null),
      getAsyncStateCounts: vi.fn().mockReturnValue([0, 0, 0]),
      clearImageSurfaceSnapshots: vi.fn(),
      captureRewindSnapshot: vi.fn().mockReturnValue(snapshot),
    };
    backend.jvm = jvm;
    backend.flushOutputToHooksSync();
    await backend.mediaCommands.waitForIdle(7);

    await expect(backend.captureRewindSnapshot()).resolves.toBe(snapshot);

    const restoreCommands = createMediaCommandBuffer(false);
    jvm.flushCommandBuffer = vi
      .fn()
      .mockReturnValueOnce({
        data: restoreCommands,
        len: restoreCommands.length,
      })
      .mockReturnValue(null);
    Object.assign(jvm, {
      restoreRewindSnapshot: vi.fn(),
      takePendingResourceImageRestores: vi.fn().mockReturnValue([]),
      takePendingDerivedImageRestores: vi.fn().mockReturnValue([]),
      repaint: vi.fn(),
    });

    await backend.restoreRewindSnapshot(snapshot);

    expect(restoredPlayer.setLoopCount).toHaveBeenCalledWith(-1);
  });
});
