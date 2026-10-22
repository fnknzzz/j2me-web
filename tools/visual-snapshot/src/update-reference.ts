import fs from 'node:fs/promises';
import path from 'node:path';
import { expectedScreenshotFiles, filterCases, loadCases } from './cases.js';
import {
  defaultArtifactRoot,
  defaultCasesPath,
  defaultReferencePath,
  findRepoRoot,
  resolveFromRepoRoot,
} from './paths.js';

type CliOptions = {
  casesPath: string;
  caseIds: string[];
  tags: string[];
};

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = findRepoRoot();
  const sourceDir = path.join(
    defaultArtifactRoot(repoRoot),
    'web-webgpu',
    'baseline',
  );
  const destDir = defaultReferencePath(repoRoot);
  const resolvedCases = await loadCases(options.casesPath);
  const selectedCases = filterCases(
    resolvedCases.cases,
    options.caseIds,
    options.tags,
  ).filter((testCase) => testCase.stable);

  await fs.mkdir(destDir, { recursive: true });

  if (selectedCases.length === 0) {
    throw new Error('No stable visual snapshot cases matched the filters');
  }

  const files = selectedCases.flatMap((testCase) =>
    expectedScreenshotFiles(testCase),
  );

  try {
    await fs.access(sourceDir);
  } catch {
    console.error(`Source directory not found: ${sourceDir}`);
    console.error(
      'Run update:baseline first to generate web-webgpu baselines.',
    );
    process.exitCode = 1;
    return;
  }

  if (files.length === 0) {
    console.log(`No baseline PNGs found in ${sourceDir}`);
    return;
  }

  let copied = 0;
  for (const file of files) {
    await fs.copyFile(path.join(sourceDir, file), path.join(destDir, file));
    copied++;
  }

  console.log(`Updated ${copied} reference image(s) in ${destDir}`);
}

function parseArgs(args: string[]): CliOptions {
  const repoRoot = findRepoRoot();
  const caseIds: string[] = [];
  const tags: string[] = [];
  let casesPath: string | undefined;

  const normalizedArgs = args[0] === '--' ? args.slice(1) : args;

  for (let i = 0; i < normalizedArgs.length; i++) {
    const arg = normalizedArgs[i];
    switch (arg) {
      case '--case':
        caseIds.push(requireValue(normalizedArgs, ++i, arg));
        break;
      case '--tag':
        tags.push(requireValue(normalizedArgs, ++i, arg));
        break;
      case '--cases':
        casesPath = requireValue(normalizedArgs, ++i, arg);
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return {
    casesPath: resolveFromRepoRoot(casesPath, defaultCasesPath(repoRoot)),
    caseIds,
    tags,
  };
}

function requireValue(args: string[], index: number, name: string): string {
  const value = args[index];
  if (!value) {
    throw new Error(`${name} requires a value`);
  }
  return value;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
