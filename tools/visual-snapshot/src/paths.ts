import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
);

export function findRepoRoot(): string {
  return path.resolve(toolDir, '..', '..');
}

export function resolveFromRepoRoot(
  input: string | undefined,
  fallback: string,
): string {
  const repoRoot = findRepoRoot();
  const value = input ?? fallback;
  return path.isAbsolute(value) ? value : path.resolve(repoRoot, value);
}

export function visualSnapshotsRoot(repoRoot = findRepoRoot()): string {
  return path.join(toolRoot(repoRoot), 'cases');
}

export function defaultCasesPath(repoRoot = findRepoRoot()): string {
  return path.join(visualSnapshotsRoot(repoRoot), 'cases.json');
}

export function defaultArtifactRoot(repoRoot = findRepoRoot()): string {
  return path.join(toolRoot(repoRoot), 'visual-artifacts');
}

export function toolRoot(repoRoot = findRepoRoot()): string {
  return path.join(repoRoot, 'tools/visual-snapshot');
}

export function defaultReferencePath(repoRoot = findRepoRoot()): string {
  return path.join(toolRoot(repoRoot), 'baselines');
}
