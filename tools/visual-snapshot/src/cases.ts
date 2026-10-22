import fs from 'node:fs/promises';
import path from 'node:path';
import { readMidletClassFromJar } from './jar-manifest.js';
import { visualSnapshotsRoot } from './paths.js';

export type CaseStep =
  | { action: 'wait'; ms: number }
  | { action: 'save' }
  | { action: 'load' }
  | { action: 'keyPress'; key: string }
  | { action: 'screenshot' };

export type VisualCase = {
  id: string;
  jar: string;
  midletClass: string;
  midletIndex: number;
  width: number;
  height: number;
  description?: string;
  stable: boolean;
  skip: boolean;
  jarPath: string;
  steps?: CaseStep[];
  tags?: string[];
};

export type ResolvedCases = {
  version: number;
  rootDir: string;
  cases: VisualCase[];
};

type RawCases = {
  version?: number;
  defaults?: {
    width?: number;
    height?: number;
    waitMs?: number;
  };
  cases?: Array<
    Partial<Omit<VisualCase, 'jarPath'>> & {
      steps?: unknown[];
      waitMs?: number;
    }
  >;
};

const CASE_ID_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export async function loadCases(casesPath: string): Promise<ResolvedCases> {
  const text = await fs.readFile(casesPath, 'utf8');
  const raw = JSON.parse(text) as RawCases;
  const rootDir = path.dirname(casesPath);

  if (!Array.isArray(raw.cases)) {
    throw new Error('cases.json must contain a cases array');
  }

  const defaults = {
    width: raw.defaults?.width ?? 360,
    height: raw.defaults?.height ?? 640,
    waitMs: raw.defaults?.waitMs ?? 1000,
  };

  const ids = new Set<string>();
  const cases = await Promise.all(
    raw.cases.map(async (entry, index): Promise<VisualCase> => {
      const id = requireString(entry.id, `cases[${index}].id`);
      if (!CASE_ID_PATTERN.test(id)) {
        throw new Error(`Invalid case id: ${id}`);
      }
      if (ids.has(id)) {
        throw new Error(`Duplicate case id: ${id}`);
      }
      ids.add(id);

      const jar = requireString(entry.jar, `cases[${index}].jar`);
      const jarPath = path.resolve(rootDir, jar);
      const width = entry.width ?? defaults.width;
      const height = entry.height ?? defaults.height;
      const waitMs = entry.waitMs ?? defaults.waitMs;
      const midletIndex =
        typeof (entry as { midletIndex?: unknown }).midletIndex === 'number'
          ? (entry as { midletIndex: number }).midletIndex
          : 1;
      const midletClass =
        typeof entry.midletClass === 'string' && entry.midletClass.length > 0
          ? entry.midletClass
          : await readMidletClassFromJar(jarPath, midletIndex);

      if (width <= 0 || height <= 0) {
        throw new Error(`Invalid dimensions for case: ${id}`);
      }

      let steps: CaseStep[] | undefined;
      if (Array.isArray(entry.steps)) {
        steps = entry.steps.map((s: any, si: number) => {
          const action = s?.action;
          if (action === 'wait') {
            if (typeof s.ms !== 'number' || s.ms <= 0) {
              throw new Error(
                `cases[${index}].steps[${si}].ms must be a positive number`,
              );
            }
            return { action: 'wait' as const, ms: s.ms };
          }
          if (action === 'save') return { action: 'save' as const };
          if (action === 'load') return { action: 'load' as const };
          if (action === 'keyPress') {
            if (typeof s.key !== 'string' || s.key.length === 0) {
              throw new Error(
                `cases[${index}].steps[${si}].key must be a non-empty string`,
              );
            }
            return { action: 'keyPress' as const, key: s.key };
          }
          if (action === 'screenshot') return { action: 'screenshot' as const };
          throw new Error(
            `cases[${index}].steps[${si}] unknown action: ${action}`,
          );
        });
      } else if (waitMs > 0) {
        // No explicit steps: generate a wait step from waitMs
        steps = [{ action: 'wait' as const, ms: waitMs }];
      }

      return {
        id,
        jar,
        midletClass,
        midletIndex,
        width,
        height,
        description: entry.description,
        stable: entry.stable ?? false,
        skip: entry.skip ?? false,
        jarPath,
        steps,
        tags: Array.isArray((entry as any).tags)
          ? (entry as any).tags
          : undefined,
      };
    }),
  );

  return {
    version: raw.version ?? 1,
    rootDir,
    cases,
  };
}

export function filterCases(
  cases: VisualCase[],
  caseIds: string[],
  tags: string[] = [],
): VisualCase[] {
  const runnableCases = cases.filter((testCase) => !testCase.skip);
  return runnableCases.filter((testCase) => {
    if (caseIds.length > 0 && !caseIds.includes(testCase.id)) {
      return false;
    }
    if (tags.length > 0 && !tags.every((tag) => testCase.tags?.includes(tag))) {
      return false;
    }
    return true;
  });
}

export function serializeCasesForBrowser(
  resolved: ResolvedCases,
  selectedCases: VisualCase[],
): string {
  const rootDir = visualSnapshotsRoot();
  return JSON.stringify({
    version: resolved.version,
    rootDir,
    cases: selectedCases.map((testCase) => ({
      id: testCase.id,
      jar: `/__visual_snapshot__/jars/${encodeURIComponent(testCase.id)}.jar`,
      midletClass: testCase.midletClass,
      midletIndex: testCase.midletIndex,
      width: testCase.width,
      height: testCase.height,
      description: testCase.description,
      skip: testCase.skip,
    })),
  });
}

export function expectedScreenshotFiles(testCase: VisualCase): string[] {
  const screenshotCount = testCase.steps
    ? testCase.steps.filter((s) => s.action === 'screenshot').length
    : 0;
  // Chronological: {caseId}_0.png, {caseId}_1.png, ... (in-step screenshots), last one is final
  const total = screenshotCount + 1;
  return Array.from({ length: total }, (_, i) => `${testCase.id}_${i}.png`);
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${field} must be a non-empty string`);
  }
  return value;
}
