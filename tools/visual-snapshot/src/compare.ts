import fs from 'node:fs/promises';
import path from 'node:path';
// @ts-expect-error pixelmatch v6 has no type declarations
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';
import {
  expectedScreenshotFiles,
  filterCases,
  loadCases,
  type VisualCase,
} from './cases.js';
import {
  defaultArtifactRoot,
  defaultCasesPath,
  findRepoRoot,
  resolveFromRepoRoot,
} from './paths.js';
import { resolvePlatform } from './platforms.js';

type CompareOptions = {
  casesPath: string;
  artifactRoot: string;
  platform: string | undefined;
  caseIds: string[];
  tags: string[];
  threshold: number;
  updateBaseline: boolean;
};

type CompareResult = {
  caseId: string;
  platform: string;
  totalPixels: number;
  mismatchedPixels: number;
  mismatchPercentage: number;
  passed: boolean;
  diffImage?: string;
  error?: string;
};

async function discoverPlatforms(artifactRoot: string): Promise<string[]> {
  const entries = await fs.readdir(artifactRoot, { withFileTypes: true });
  return entries.filter((e) => e.isDirectory()).map((e) => e.name);
}

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));
  const resolvedCases = await loadCases(options.casesPath);
  const selectedCases = filterCases(
    resolvedCases.cases,
    options.caseIds,
    options.tags,
  );

  if (selectedCases.length === 0) {
    throw new Error('No visual snapshot cases matched the filters');
  }

  const platforms = options.platform
    ? [options.platform]
    : await discoverPlatforms(options.artifactRoot);

  if (platforms.length === 0) {
    throw new Error(`No platform directories found in ${options.artifactRoot}`);
  }

  if (options.updateBaseline) {
    const stableCases = selectedCases.filter((c) => c.stable);
    if (stableCases.length === 0) {
      throw new Error('No stable cases to update baseline for');
    }
    for (const platform of platforms) {
      await updateBaseline(stableCases, { ...options, platform });
    }
    return;
  }

  const stableCases = selectedCases.filter((c) => c.stable);
  if (stableCases.length === 0) {
    console.log(
      'No stable cases to compare. Mark cases as stable in the viewer first.',
    );
    return;
  }

  const results: CompareResult[] = [];

  for (const platform of platforms) {
    for (const testCase of stableCases) {
      const files = expectedScreenshotFiles(testCase);
      for (const file of files) {
        const caseId = path.basename(file, '.png');
        results.push(await compareCase(caseId, { ...options, platform }));
      }
    }
  }

  console.log('\n=== Visual Comparison Summary ===\n');

  // Build a matrix: rows = cases, columns = platforms
  const resultMap = new Map<string, Map<string, CompareResult>>();
  for (const r of results) {
    if (!resultMap.has(r.caseId)) resultMap.set(r.caseId, new Map());
    resultMap.get(r.caseId)!.set(r.platform, r);
  }

  const caseIds = [...new Set(results.map((r) => r.caseId))];
  const platformOrder = ['web-webgpu'];
  const displayPlatforms = platformOrder.filter((p) => platforms.includes(p));

  const statusOf = (r: CompareResult | undefined): string => {
    if (!r) return '-';
    if (r.error) return 'FAIL';
    return r.passed ? 'PASS' : 'FAIL';
  };

  const colCase = 'Case';
  const caseWidth = Math.max(colCase.length, ...caseIds.map((id) => id.length));
  const colWidths = displayPlatforms.map((p) => Math.max(p.length, 4));

  const pad = (s: string, w: number) => s.padEnd(w);
  const header = `| ${pad(colCase, caseWidth)} | ${displayPlatforms.map((p, i) => pad(p, colWidths[i])).join(' | ')} |`;
  const separator = `|${'-'.repeat(caseWidth + 2)}|${colWidths.map((w) => '-'.repeat(w + 2)).join('|')}|`;

  console.log(header);
  console.log(separator);
  for (const caseId of caseIds) {
    const row = resultMap.get(caseId)!;
    const cells = displayPlatforms.map((p, i) =>
      pad(statusOf(row.get(p)), colWidths[i]),
    );
    console.log(`| ${pad(caseId, caseWidth)} | ${cells.join(' | ')} |`);
  }

  const passed = results.filter((r) => r.passed && !r.error).length;
  const failed = results.length - passed;
  console.log(
    `\n${results.length} case(s) compared, ${passed} passed, ${failed} failed.`,
  );

  if (failed > 0) {
    process.exitCode = 1;
  }
}

async function updateBaseline(
  cases: VisualCase[],
  options: CompareOptions & { platform: string },
): Promise<void> {
  const currentDir = path.join(
    options.artifactRoot,
    options.platform,
    'current',
  );
  const baselineDir = path.join(
    options.artifactRoot,
    options.platform,
    'baseline',
  );
  await fs.mkdir(baselineDir, { recursive: true });

  let fileCount = 0;
  for (const testCase of cases) {
    const files = expectedScreenshotFiles(testCase);
    for (const file of files) {
      const src = path.join(currentDir, file);
      const dest = path.join(baselineDir, file);
      await fs.copyFile(src, dest);
      console.log(`  [BASELINE] ${options.platform}/${file} updated`);
      fileCount++;
    }
  }

  console.log(
    `\n${fileCount} baseline(s) updated for platform: ${options.platform}\n`,
  );
}

async function compareCase(
  caseId: string,
  options: CompareOptions & { platform: string },
): Promise<CompareResult> {
  const baselinePath = path.join(
    options.artifactRoot,
    options.platform,
    'baseline',
    `${caseId}.png`,
  );
  const currentPath = path.join(
    options.artifactRoot,
    options.platform,
    'current',
    `${caseId}.png`,
  );

  try {
    // Check baseline exists
    try {
      await fs.access(baselinePath);
    } catch {
      // No baseline — adopt current as baseline
      const baselineDir = path.dirname(baselinePath);
      await fs.mkdir(baselineDir, { recursive: true });
      await fs.copyFile(currentPath, baselinePath);
      console.log(
        `  [NEW BASELINE] ${options.platform}/${caseId}.png adopted from current`,
      );
      return {
        caseId,
        platform: options.platform,
        totalPixels: 0,
        mismatchedPixels: 0,
        mismatchPercentage: 0,
        passed: true,
      };
    }

    const [baselineBuffer, currentBuffer] = await Promise.all([
      fs.readFile(baselinePath),
      fs.readFile(currentPath),
    ]);

    const baselinePng = PNG.sync.read(baselineBuffer);
    const currentPng = PNG.sync.read(currentBuffer);

    if (
      baselinePng.width !== currentPng.width ||
      baselinePng.height !== currentPng.height
    ) {
      return {
        caseId,
        platform: options.platform,
        totalPixels: 0,
        mismatchedPixels: 0,
        mismatchPercentage: 100,
        passed: false,
        error: `Dimension mismatch: baseline=${baselinePng.width}x${baselinePng.height}, current=${currentPng.width}x${currentPng.height}`,
      };
    }

    const { width, height } = baselinePng;
    const diff = new PNG({ width, height });
    const totalPixels = width * height;

    const mismatchedPixels = pixelmatch(
      baselinePng.data,
      currentPng.data,
      diff.data,
      width,
      height,
      { threshold: options.threshold },
    );

    const mismatchPercentage = (mismatchedPixels / totalPixels) * 100;
    const passed = mismatchedPixels === 0;

    // Write diff image
    const diffDir = path.join(options.artifactRoot, options.platform, 'diff');
    await fs.mkdir(diffDir, { recursive: true });
    const diffPath = path.join(diffDir, `${caseId}.diff.png`);
    await fs.writeFile(diffPath, PNG.sync.write(diff));

    return {
      caseId,
      platform: options.platform,
      totalPixels,
      mismatchedPixels,
      mismatchPercentage,
      passed,
      diffImage: `${options.platform}/diff/${caseId}.diff.png`,
    };
  } catch (error) {
    return {
      caseId,
      platform: options.platform,
      totalPixels: 0,
      mismatchedPixels: 0,
      mismatchPercentage: 100,
      passed: false,
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

function parseArgs(args: string[]): CompareOptions {
  const repoRoot = findRepoRoot();
  const caseIds: string[] = [];
  const tags: string[] = [];
  let casesPath: string | undefined;
  let artifactRoot: string | undefined;
  let platform: string | undefined;
  let threshold = 0.1;
  let updateBaseline = false;

  const normalizedArgs = args[0] === '--' ? args.slice(1) : args;

  for (let i = 0; i < normalizedArgs.length; i++) {
    const arg = normalizedArgs[i];
    if (!arg.startsWith('-')) {
      if (arg.toLowerCase() !== 'all') {
        platform = resolvePlatform(arg).comparePlatform;
      }
      continue;
    }
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
      case '--out':
        artifactRoot = requireValue(normalizedArgs, ++i, arg);
        break;
      case '--platform':
        {
          const value = requireValue(normalizedArgs, ++i, arg);
          platform =
            value.toLowerCase() === 'all'
              ? undefined
              : resolvePlatform(value).comparePlatform;
        }
        break;
      case '--all':
        platform = undefined;
        break;
      case '--threshold':
        threshold = Number(requireValue(normalizedArgs, ++i, arg));
        if (!Number.isFinite(threshold) || threshold < 0 || threshold > 1) {
          throw new Error('--threshold must be a number between 0 and 1');
        }
        break;
      case '--update-baseline':
        updateBaseline = true;
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return {
    casesPath: resolveFromRepoRoot(casesPath, defaultCasesPath(repoRoot)),
    artifactRoot: resolveFromRepoRoot(
      artifactRoot,
      defaultArtifactRoot(repoRoot),
    ),
    platform,
    caseIds,
    tags,
    threshold,
    updateBaseline,
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
