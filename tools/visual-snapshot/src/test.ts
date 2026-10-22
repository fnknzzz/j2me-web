import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { filterCases, loadCases } from './cases.js';
import {
  defaultCasesPath,
  findRepoRoot,
  resolveFromRepoRoot,
} from './paths.js';
import { type CapturePlatform, selectPlatforms } from './platforms.js';

async function main(): Promise<void> {
  const { platforms, restArgs } = await selectPlatforms(process.argv.slice(2));
  for (const target of platforms) {
    const platformArgs = await filteredArgsForPlatform(target, restArgs);
    if (!platformArgs) {
      console.log(`\n=== Skip ${target.name}: no matching cases ===\n`);
      continue;
    }
    runCapture(target, platformArgs);
    runCompare(target, platformArgs);
  }
}

async function filteredArgsForPlatform(
  platform: CapturePlatform,
  restArgs: string[],
): Promise<string[] | null> {
  const caseIds = collectCaseIds(restArgs);
  if (caseIds.length === 0 || !platform.excludedTags?.length) {
    return restArgs;
  }

  const repoRoot = findRepoRoot();
  const casesPath = resolveFromRepoRoot(
    readOption(restArgs, '--cases'),
    defaultCasesPath(repoRoot),
  );
  const resolved = await loadCases(casesPath);
  const selected = filterCases(resolved.cases, caseIds)
    .filter(
      (testCase) =>
        !platform.excludedTags!.some((tag) => testCase.tags?.includes(tag)),
    )
    .map((testCase) => testCase.id);
  if (selected.length === 0) {
    return null;
  }
  return replaceCaseArgs(restArgs, selected);
}

function collectCaseIds(args: string[]): string[] {
  const ids: string[] = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--case') {
      ids.push(args[++i]);
    }
  }
  return ids.filter(Boolean);
}

function readOption(args: string[], name: string): string | undefined {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

function replaceCaseArgs(args: string[], caseIds: string[]): string[] {
  const next: string[] = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--case') {
      i += 1;
      continue;
    }
    next.push(args[i]);
  }
  for (const caseId of caseIds) {
    next.push('--case', caseId);
  }
  return next;
}

function runCapture(platform: CapturePlatform, restArgs: string[]): void {
  const scriptPath = path.join(import.meta.dirname, platform.captureScript);
  const args = [scriptPath, ...platform.captureArgs, ...restArgs];
  console.log(`\n=== Capture ${platform.name} ===\n`);
  runNode(args);
}

function runCompare(platform: CapturePlatform, restArgs: string[]): void {
  const scriptPath = path.join(import.meta.dirname, 'compare.js');
  const args = [
    scriptPath,
    '--platform',
    platform.comparePlatform,
    ...restArgs,
  ];
  console.log(`\n=== Compare ${platform.name} ===\n`);
  runNode(args);
}

function runNode(args: string[]): void {
  const result = spawnSync(process.execPath, args, { stdio: 'inherit' });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
