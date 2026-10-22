import { expect } from 'vitest';

/**
 * Captures a snapshot of the canvas and compares it against a baseline.
 * In a simulator environment without a real DOM, this might rely on
 * mocked canvas interfaces or simply verify data consistency.
 *
 * @param canvas The HTMLCanvasElement or compatible interface
 * @param snapshotName Optional name for the snapshot
 */
export async function compareCanvas(
  canvas: { toDataURL(): string } | HTMLCanvasElement,
  snapshotName?: string,
): Promise<void> {
  const dataUrl = canvas.toDataURL();
  // Remove the prefix "data:image/png;base64," to just check the data
  const base64Data = dataUrl.replace(/^data:image\/[a-z]+;base64,/, '');

  if (snapshotName) {
    expect(base64Data).toMatchSnapshot(snapshotName);
  } else {
    expect(base64Data).toMatchSnapshot();
  }
}
