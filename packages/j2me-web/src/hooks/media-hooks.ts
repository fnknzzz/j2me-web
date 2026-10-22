import type {
  MediaPlayerHandle,
  MediaPlayerRequest,
  MediaPlayerState,
} from '@jsjvm/j2me-simulator/env';
import {
  type PlayerHandle,
  type PlayerState,
  SoundPlayer,
} from '@jsjvm/j2me-sound-player';

const mapPlayerState = (state: PlayerState): MediaPlayerState => {
  return state as MediaPlayerState;
};

export interface MediaHooksOptions {
  getSpeed?: () => number;
}

export const createMediaHooks = (options: MediaHooksOptions = {}) => {
  const getSpeed = options.getSpeed ?? (() => 1);

  const soundPlayer = new SoundPlayer();

  soundPlayer.setSpeed(getSpeed());

  return {
    media: {
      createPlayer: async (
        request: MediaPlayerRequest,
      ): Promise<MediaPlayerHandle> => {
        soundPlayer.setSpeed(getSpeed());

        let player: PlayerHandle;

        if (request.data) {
          player = await soundPlayer.createPlayer(
            request.data as Uint8Array,
            request.contentType || 'audio/mpeg',
          );
        } else if (request.locator) {
          player = await soundPlayer.createPlayerFromUrl(
            request.locator,
            request.contentType,
          );
        } else {
          throw new Error(
            'MediaPlayerRequest must have either data or locator',
          );
        }

        player.setSpeed(getSpeed());

        const wrappedPlayer: MediaPlayerHandle = {
          play: async () => {
            player.setSpeed(getSpeed());
            await player.play();
          },
          pause: () => player.pause(),
          stop: () => player.stop(),
          close: () => player.close(),
          getDuration: () => player.getDuration(),
          getCurrentTime: () => player.getCurrentTime(),
          setCurrentTime: (time: number) => player.setCurrentTime(time),
          getVolume: () => player.getVolume(),
          setVolume: (volume: number) => player.setVolume(volume),
          isMuted: () => player.isMuted(),
          setMuted: (muted: boolean) => player.setMuted(muted),
          setLoopCount: (count: number) => player.setLoopCount(count),
          getContentType: () => player.getContentType(),
        };

        Object.defineProperty(wrappedPlayer, 'onStateChange', {
          get: () => player.onStateChange,
          set: (callback: ((state: MediaPlayerState) => void) | undefined) => {
            if (callback) {
              player.onStateChange = (state) => callback(mapPlayerState(state));
            } else {
              player.onStateChange = undefined;
            }
          },
        });

        Object.defineProperty(wrappedPlayer, 'onEnd', {
          get: () => player.onEnd,
          set: (callback: (() => void) | undefined) => {
            player.onEnd = callback;
          },
        });

        Object.defineProperty(wrappedPlayer, 'onError', {
          get: () => player.onError,
          set: (callback: ((error: Error) => void) | undefined) => {
            player.onError = callback;
          },
        });

        return wrappedPlayer;
      },

      loadMediaData: async (locator: string): Promise<Uint8Array> => {
        const response = await fetch(locator);
        if (!response.ok) {
          throw new Error(`Failed to load media: ${response.statusText}`);
        }
        const arrayBuffer = await response.arrayBuffer();
        return new Uint8Array(arrayBuffer);
      },

      getSupportedContentTypes: (_protocol: string): string[] => {
        return soundPlayer.getSupportedContentTypes();
      },

      getSupportedProtocols: (_contentType: string): string[] => {
        return soundPlayer.getSupportedProtocols();
      },

      playTone: (note: number, duration: number, volume: number): void => {
        soundPlayer.setSpeed(getSpeed());
        soundPlayer.playTone(note, duration, volume);
      },
    },

    setSpeed: (speed: number) => {
      soundPlayer.setSpeed(speed);
    },

    pauseAll: () => {
      soundPlayer.pauseAll();
    },

    resumeAll: () => {
      soundPlayer.resumeAll();
    },

    dispose: () => {
      soundPlayer.dispose();
    },
  };
};
