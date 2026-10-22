import type { MediaCommandSnapshot } from './media-command-executor';

const TRAILER_MAGIC = new TextEncoder().encode('jsjvmed1');
const TRAILER_LENGTH_BYTES = 4;

export interface SaveStateWithMediaSnapshot {
  vmState: Uint8Array;
  mediaSnapshot: MediaCommandSnapshot | null;
}

export const wrapStateWithMediaSnapshot = (
  vmState: Uint8Array,
  mediaSnapshot: MediaCommandSnapshot,
): Uint8Array => {
  if (mediaSnapshot.players.length === 0) return vmState;

  const metadata = new TextEncoder().encode(JSON.stringify(mediaSnapshot));
  const result = new Uint8Array(
    vmState.length +
      metadata.length +
      TRAILER_LENGTH_BYTES +
      TRAILER_MAGIC.length,
  );
  result.set(vmState);
  result.set(metadata, vmState.length);
  new DataView(result.buffer).setUint32(
    vmState.length + metadata.length,
    metadata.length,
    true,
  );
  result.set(
    TRAILER_MAGIC,
    vmState.length + metadata.length + TRAILER_LENGTH_BYTES,
  );
  return result;
};

export const unwrapStateWithMediaSnapshot = (
  data: Uint8Array,
): SaveStateWithMediaSnapshot => {
  const trailerLength = TRAILER_LENGTH_BYTES + TRAILER_MAGIC.length;
  if (data.length < trailerLength) {
    return { vmState: data, mediaSnapshot: null };
  }

  const magicOffset = data.length - TRAILER_MAGIC.length;
  for (let index = 0; index < TRAILER_MAGIC.length; index += 1) {
    if (data[magicOffset + index] !== TRAILER_MAGIC[index]) {
      return { vmState: data, mediaSnapshot: null };
    }
  }

  const lengthOffset = magicOffset - TRAILER_LENGTH_BYTES;
  const metadataLength = new DataView(
    data.buffer,
    data.byteOffset + lengthOffset,
    TRAILER_LENGTH_BYTES,
  ).getUint32(0, true);
  const metadataOffset = lengthOffset - metadataLength;
  if (metadataOffset < 0) {
    return { vmState: data, mediaSnapshot: null };
  }

  try {
    const value: unknown = JSON.parse(
      new TextDecoder().decode(data.subarray(metadataOffset, lengthOffset)),
    );
    if (!isMediaCommandSnapshot(value)) {
      return { vmState: data, mediaSnapshot: null };
    }
    return {
      vmState: data.subarray(0, metadataOffset),
      mediaSnapshot: value,
    };
  } catch {
    return { vmState: data, mediaSnapshot: null };
  }
};

const isMediaCommandSnapshot = (
  value: unknown,
): value is MediaCommandSnapshot => {
  if (!value || typeof value !== 'object') return false;
  const snapshot = value as Partial<MediaCommandSnapshot>;
  return (
    snapshot.version === 1 &&
    Array.isArray(snapshot.players) &&
    snapshot.players.every(
      (player) =>
        player !== null &&
        typeof player === 'object' &&
        Number.isInteger(player.handle) &&
        Number.isFinite(player.loopCount) &&
        Number.isFinite(player.volume) &&
        typeof player.muted === 'boolean' &&
        typeof player.playing === 'boolean',
    )
  );
};
