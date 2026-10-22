import { execFileSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import type { VisualCase } from './cases.js';

export type CaptureResult = {
  caseId: string;
  platform: string;
  images?: string[];
  error?: {
    code: string;
    message: string;
  };
};

export async function writeManifest(options: {
  artifactRoot: string;
  platform: string;
  platformTitle: string;
  cases: VisualCase[];
  results: CaptureResult[];
}): Promise<void> {
  const repo = getRepoInfo();
  const cases = options.cases.map((testCase) => {
    const results = options.results.filter(
      (result) => result.caseId === testCase.id,
    );
    const outputs: Record<string, string[]> = {};
    const errors: Record<string, { code: string; message: string }> = {};

    for (const result of results) {
      if (result.images) {
        outputs[result.platform] = result.images;
      }
      if (result.error) {
        errors[result.platform] = result.error;
      }
    }

    return {
      id: testCase.id,
      width: testCase.width,
      height: testCase.height,
      description: testCase.description,
      outputs,
      errors,
    };
  });

  const manifest = {
    version: 1,
    generatedAt: new Date().toISOString(),
    repo,
    platforms: [
      {
        id: options.platform,
        title: options.platformTitle,
      },
    ],
    cases,
  };

  await fs.mkdir(options.artifactRoot, { recursive: true });
  await fs.writeFile(
    path.join(options.artifactRoot, 'manifest.json'),
    `${JSON.stringify(manifest, null, 2)}\n`,
  );
}

function getRepoInfo(): {
  commit: string | null;
  branch: string | null;
  dirty: boolean;
} {
  return {
    commit: runGit(['rev-parse', '--short', 'HEAD']),
    branch: runGit(['branch', '--show-current']),
    dirty: runGit(['status', '--porcelain']) !== '',
  };
}

function runGit(args: string[]): string | null {
  try {
    return execFileSync('git', args, { encoding: 'utf8' }).trim();
  } catch {
    return null;
  }
}
