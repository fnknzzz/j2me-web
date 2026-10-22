import type { MediaPlayerHandle, MediaPlayerRequest } from '../env';

export type MediaCommand =
  | {
      type: 'create';
      handle: number;
      request: MediaPlayerRequest;
      loopCount?: number;
      volume?: number;
    }
  | { type: 'play'; handle: number; seekTime: number }
  | { type: 'stop'; handle: number }
  | { type: 'close'; handle: number }
  | { type: 'setVolume'; handle: number; volume: number }
  | { type: 'setMuted'; handle: number; muted: boolean }
  | { type: 'setLoopCount'; handle: number; count: number }
  | { type: 'setCurrentTime'; handle: number; timeMs: number };

export interface MediaCommandExecutorOptions {
  createPlayer?: (request: MediaPlayerRequest) => Promise<MediaPlayerHandle>;
  onPlayerEnded?: (handle: number) => void;
}

export interface MediaCommandSnapshot {
  version: 1;
  players: Array<{
    handle: number;
    loopCount: number;
    volume: number;
    muted: boolean;
    playing: boolean;
  }>;
}

type MediaControlState = MediaCommandSnapshot['players'][number];

/**
 * Executes media commands in order per player handle. Different players remain
 * independent, so loading one media resource never blocks another player or
 * the JVM command loop.
 */
export class MediaCommandExecutor {
  private readonly players = new Map<number, MediaPlayerHandle>();
  private readonly playing = new Set<number>();
  private readonly queues = new Map<number, Promise<void>>();
  private readonly controlStates = new Map<number, MediaControlState>();
  private readonly restoreOverrides = new Map<number, MediaControlState>();
  private generation = 0;

  constructor(private readonly options: MediaCommandExecutorOptions) {}

  submit(command: MediaCommand): Promise<void> {
    let createState: MediaControlState | undefined;
    switch (command.type) {
      case 'create': {
        const restored = this.restoreOverrides.get(command.handle);
        this.restoreOverrides.delete(command.handle);
        const state = {
          handle: command.handle,
          loopCount: restored?.loopCount ?? command.loopCount ?? 1,
          volume: restored?.volume ?? command.volume ?? 100,
          muted: restored?.muted ?? false,
          playing: restored?.playing ?? false,
        };
        this.controlStates.set(command.handle, state);
        createState = { ...state };
        break;
      }
      case 'play':
        this.getOrCreateControlState(command.handle).playing = true;
        this.playing.add(command.handle);
        break;
      case 'stop':
        this.getOrCreateControlState(command.handle).playing = false;
        this.playing.delete(command.handle);
        break;
      case 'close':
        this.controlStates.delete(command.handle);
        this.restoreOverrides.delete(command.handle);
        this.playing.delete(command.handle);
        break;
      case 'setVolume':
        this.getOrCreateControlState(command.handle).volume = command.volume;
        break;
      case 'setMuted':
        this.getOrCreateControlState(command.handle).muted = command.muted;
        break;
      case 'setLoopCount':
        this.getOrCreateControlState(command.handle).loopCount = command.count;
        break;
      case 'setCurrentTime':
        break;
    }

    return this.enqueue(command.handle, async (generation) => {
      switch (command.type) {
        case 'create': {
          const createPlayer = this.options.createPlayer;
          if (!createPlayer) return;
          this.players.get(command.handle)?.close?.();
          const player = await createPlayer(command.request);
          if (generation !== this.generation) {
            player.close?.();
            return;
          }
          this.players.set(command.handle, player);
          player.onEnd = () => {
            if (
              generation !== this.generation ||
              this.players.get(command.handle) !== player
            ) {
              return;
            }
            if (!this.playing.delete(command.handle)) return;
            const state = this.controlStates.get(command.handle);
            if (state) state.playing = false;
            this.options.onPlayerEnded?.(command.handle);
          };
          if (createState && createState.loopCount !== 1) {
            player.setLoopCount?.(createState.loopCount);
          }
          if (command.volume !== undefined || createState?.volume !== 100) {
            player.setVolume?.(createState?.volume ?? command.volume ?? 100);
          }
          if (createState?.muted) {
            player.setMuted?.(true);
          }
          break;
        }
        case 'play': {
          const player = this.players.get(command.handle);
          if (!player) return;
          await player.play();
          if (generation !== this.generation) return;
          if (command.seekTime > 0) {
            player.setCurrentTime?.(command.seekTime);
          }
          break;
        }
        case 'stop':
          this.players.get(command.handle)?.stop();
          break;
        case 'close':
          this.players.get(command.handle)?.close();
          this.players.delete(command.handle);
          break;
        case 'setVolume':
          this.players.get(command.handle)?.setVolume(command.volume);
          break;
        case 'setMuted':
          this.players.get(command.handle)?.setMuted(command.muted);
          break;
        case 'setLoopCount':
          this.players.get(command.handle)?.setLoopCount(command.count);
          break;
        case 'setCurrentTime':
          this.players.get(command.handle)?.setCurrentTime(command.timeMs);
          break;
      }
    });
  }

  getCurrentTime(handle: number): number {
    return this.players.get(handle)?.getCurrentTime?.() ?? 0;
  }

  getDuration(handle: number): number {
    return this.players.get(handle)?.getDuration?.() ?? 0;
  }

  setCurrentTime(handle: number, timeMs: number): void {
    void this.submit({ type: 'setCurrentTime', handle, timeMs });
  }

  captureSnapshot(): MediaCommandSnapshot {
    return {
      version: 1,
      players: Array.from(this.controlStates.values(), (state) => ({
        ...state,
      })),
    };
  }

  restoreSnapshot(snapshot?: MediaCommandSnapshot | null): void {
    this.reset();
    if (!snapshot || snapshot.version !== 1) return;
    for (const state of snapshot.players) {
      this.restoreOverrides.set(state.handle, { ...state });
    }
  }

  finishRestore(): void {
    this.restoreOverrides.clear();
  }

  reset(): void {
    this.generation += 1;
    for (const player of this.players.values()) {
      try {
        player.close?.();
      } catch {
        // Ignore host-side teardown failures while switching snapshots.
      }
    }
    this.players.clear();
    this.playing.clear();
    this.queues.clear();
    this.controlStates.clear();
    this.restoreOverrides.clear();
  }

  waitForIdle(handle: number): Promise<void> {
    return this.queues.get(handle) ?? Promise.resolve();
  }

  private enqueue(
    handle: number,
    operation: (generation: number) => void | Promise<void>,
  ): Promise<void> {
    const generation = this.generation;
    const previous = this.queues.get(handle) ?? Promise.resolve();
    const queued = previous
      .then(async () => {
        if (generation !== this.generation) return;
        await operation(generation);
      })
      .catch((error) =>
        console.warn(
          `[MediaCommandExecutor] player ${handle} command failed`,
          error,
        ),
      );
    const tracked = queued.finally(() => {
      if (this.queues.get(handle) === tracked) this.queues.delete(handle);
    });
    this.queues.set(handle, tracked);
    return tracked;
  }

  private getOrCreateControlState(handle: number): MediaControlState {
    let state = this.controlStates.get(handle);
    if (!state) {
      state = {
        handle,
        loopCount: 1,
        volume: 100,
        muted: false,
        playing: false,
      };
      this.controlStates.set(handle, state);
    }
    return state;
  }
}
