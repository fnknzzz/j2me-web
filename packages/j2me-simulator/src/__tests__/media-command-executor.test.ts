import { describe, expect, it, vi } from 'vitest';
import { MediaCommandExecutor } from '../backend/media-command-executor';
import type { MediaPlayerHandle } from '../env';

const createPlayerHandle = (duration: number): MediaPlayerHandle => ({
  play: vi.fn().mockResolvedValue(undefined),
  pause: vi.fn(),
  stop: vi.fn(),
  close: vi.fn(),
  getDuration: vi.fn(() => duration),
  getCurrentTime: vi.fn(() => 0),
  setCurrentTime: vi.fn(),
  getVolume: vi.fn(() => 100),
  setVolume: vi.fn(),
  isMuted: vi.fn(() => false),
  setMuted: vi.fn(),
  setLoopCount: vi.fn(),
  getContentType: vi.fn(() => 'audio/midi'),
});

describe('MediaCommandExecutor', () => {
  it('does not let one pending player block another handle', async () => {
    const firstPlayer = createPlayerHandle(1000);
    const secondPlayer = createPlayerHandle(2000);
    let resolveFirst!: (player: MediaPlayerHandle) => void;
    const firstPending = new Promise<MediaPlayerHandle>((resolve) => {
      resolveFirst = resolve;
    });
    const createPlayer = vi
      .fn()
      .mockReturnValueOnce(firstPending)
      .mockResolvedValueOnce(secondPlayer);
    const executor = new MediaCommandExecutor({ createPlayer });

    void executor.submit({
      type: 'create',
      handle: 1,
      request: { locator: 'first.mid' },
    });
    await executor.submit({
      type: 'create',
      handle: 2,
      request: { locator: 'second.mid' },
    });

    expect(executor.getDuration(2)).toBe(2000);
    expect(executor.getDuration(1)).toBe(0);

    resolveFirst(firstPlayer);
    await executor.waitForIdle(1);
  });

  it('closes a player that finishes loading after reset', async () => {
    const stalePlayer = createPlayerHandle(1000);
    const currentPlayer = createPlayerHandle(2000);
    let resolveStale!: (player: MediaPlayerHandle) => void;
    const stalePending = new Promise<MediaPlayerHandle>((resolve) => {
      resolveStale = resolve;
    });
    const createPlayer = vi
      .fn()
      .mockReturnValueOnce(stalePending)
      .mockResolvedValueOnce(currentPlayer);
    const executor = new MediaCommandExecutor({ createPlayer });

    const staleCreate = executor.submit({
      type: 'create',
      handle: 1,
      request: { locator: 'stale.mid' },
    });
    const stalePlay = executor.submit({
      type: 'play',
      handle: 1,
      seekTime: 0,
    });
    await Promise.resolve();

    executor.reset();
    await executor.submit({
      type: 'create',
      handle: 1,
      request: { locator: 'current.mid' },
    });
    resolveStale(stalePlayer);
    await Promise.all([staleCreate, stalePlay]);

    expect(stalePlayer.close).toHaveBeenCalledOnce();
    expect(stalePlayer.play).not.toHaveBeenCalled();
    expect(executor.getDuration(1)).toBe(2000);
  });

  it('restores control state when Rust recreates a player', async () => {
    const originalPlayer = createPlayerHandle(1000);
    const restoredPlayer = createPlayerHandle(1000);
    const createPlayer = vi
      .fn()
      .mockResolvedValueOnce(originalPlayer)
      .mockResolvedValueOnce(restoredPlayer);
    const executor = new MediaCommandExecutor({ createPlayer });

    await executor.submit({
      type: 'create',
      handle: 7,
      request: { data: new Uint8Array([1]), contentType: 'audio/midi' },
      loopCount: 1,
      volume: 100,
    });
    await executor.submit({ type: 'setLoopCount', handle: 7, count: -1 });
    await executor.submit({ type: 'setVolume', handle: 7, volume: 60 });
    await executor.submit({ type: 'setMuted', handle: 7, muted: true });
    await executor.submit({ type: 'play', handle: 7, seekTime: 0 });
    const snapshot = executor.captureSnapshot();

    executor.restoreSnapshot(snapshot);
    await executor.submit({
      type: 'create',
      handle: 7,
      request: { data: new Uint8Array([1]), contentType: 'audio/midi' },
      loopCount: 1,
      volume: 100,
    });

    expect(restoredPlayer.setLoopCount).toHaveBeenCalledWith(-1);
    expect(restoredPlayer.setVolume).toHaveBeenCalledWith(60);
    expect(restoredPlayer.setMuted).toHaveBeenCalledWith(true);
  });
});
