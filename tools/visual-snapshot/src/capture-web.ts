import { execFileSync } from 'node:child_process';
import { createReadStream } from 'node:fs';
import fs from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import { chromium } from 'playwright';
import {
  type CaseStep,
  expectedScreenshotFiles,
  filterCases,
  loadCases,
  serializeCasesForBrowser,
  type VisualCase,
} from './cases.js';
import { type CaptureResult, writeManifest } from './manifest.js';
import {
  defaultArtifactRoot,
  defaultCasesPath,
  findRepoRoot,
  resolveFromRepoRoot,
  toolRoot,
} from './paths.js';

type CliOptions = {
  casesPath: string;
  artifactRoot: string;
  caseIds: string[];
  tags: string[];
  platform: string;
  headless: boolean;
  timeout: number;
};

const CONTENT_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.jar': 'application/java-archive',
  '.wasm': 'application/wasm',
};

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

  await buildCapturePage();

  const server = await createCaptureServer(
    selectedCases,
    serializeCasesForBrowser(resolvedCases, selectedCases),
  );
  const results: CaptureResult[] = [];
  const launchArgs: string[] = [
    '--enable-unsafe-webgpu',
    '--enable-features=Vulkan',
    '--use-angle=swiftshader',
    '--use-vulkan=swiftshader',
    '--enable-gpu',
    '--disable-vulkan-surface',
  ];
  const browser = await chromium.launch({
    headless: options.headless,
    args: launchArgs,
  });

  try {
    for (const testCase of selectedCases) {
      results.push(
        await captureCase({
          testCase,
          browser,
          baseUrl: server.baseUrl,
          options,
        }),
      );
    }
  } finally {
    await browser.close();
    await server.close();
  }

  await writeManifest({
    artifactRoot: options.artifactRoot,
    platform: options.platform,
    platformTitle: 'Web / WebGPU',
    cases: selectedCases,
    results,
  });

  const failed = results.filter((result) => result.error);
  if (failed.length > 0) {
    const summary = failed
      .map((result) => `${result.caseId}: ${result.error?.message}`)
      .join('\n');
    throw new Error(`Visual capture failed:\n${summary}`);
  }
}

const MAX_CAPTURE_RETRIES = 3;

async function captureCase(args: {
  testCase: VisualCase;
  browser: Awaited<ReturnType<typeof chromium.launch>>;
  baseUrl: string;
  options: CliOptions;
}): Promise<CaptureResult> {
  const { testCase, browser, baseUrl, options } = args;

  for (let attempt = 1; attempt <= MAX_CAPTURE_RETRIES; attempt++) {
    const page = await browser.newPage({
      viewport: { width: testCase.width, height: testCase.height },
      deviceScaleFactor: 2,
    });

    try {
      await page.goto(`${baseUrl}/?case=${encodeURIComponent(testCase.id)}`, {
        waitUntil: 'domcontentloaded',
        timeout: options.timeout,
      });
      await page.evaluate(() => (window as any).__visualSnapshot.ready);

      const screenshots: Buffer[] = [];

      if (testCase.steps) {
        await executeSteps(page, testCase.steps, screenshots);
      }

      await page.evaluate(() => (window as any).__visualSnapshot.getInfo());

      // Check blank directly on Canvas (avoids PNG encode/decode overhead)
      const isBlank = await page.evaluate(() => {
        const canvas = document.querySelector('canvas');
        if (!canvas) return true;
        const ctx = canvas.getContext('2d');
        if (!ctx) return false; // WebGPU canvas — cannot check via 2d context, skip blank detection
        const { width, height } = canvas;
        const sampleCount = Math.min(height, 12);
        for (let s = 0; s < sampleCount; s++) {
          const y = Math.floor((s * height) / sampleCount);
          const row = ctx.getImageData(0, y, width, 1).data;
          for (let x = 0; x < width; x++) {
            const offset = x * 4;
            const a = row[offset + 3];
            if (a === 0) continue;
            if (
              row[offset] !== 255 ||
              row[offset + 1] !== 255 ||
              row[offset + 2] !== 255
            ) {
              return false;
            }
          }
        }
        return true;
      });

      if (isBlank) {
        console.log(
          `  [RETRY] ${testCase.id}: blank image detected (attempt ${attempt}/${MAX_CAPTURE_RETRIES})`,
        );
        if (attempt < MAX_CAPTURE_RETRIES) {
          await page.close();
          continue;
        }
        return {
          caseId: testCase.id,
          platform: options.platform,
          error: {
            code: 'CAPTURE_BLANK',
            message: `Image is blank after ${MAX_CAPTURE_RETRIES} attempts`,
          },
        };
      }

      // Always take final screenshot
      const finalPng = dataUrlToBuffer(
        await page.evaluate(() =>
          (window as any).__visualSnapshot.getPngDataUrl(),
        ),
      );
      screenshots.push(finalPng);

      // Write all screenshots in chronological order
      const platformDir = path.join(
        options.artifactRoot,
        options.platform,
        'current',
      );
      await fs.mkdir(platformDir, { recursive: true });

      const fileNames = expectedScreenshotFiles(testCase);
      for (let i = 0; i < screenshots.length; i++) {
        await fs.writeFile(
          path.join(platformDir, fileNames[i]),
          screenshots[i],
        );
      }

      return {
        caseId: testCase.id,
        platform: options.platform,
        images: fileNames.map((f) => `${options.platform}/current/${f}`),
      };
    } catch (error) {
      if (attempt < MAX_CAPTURE_RETRIES) {
        console.log(
          `  [RETRY] ${testCase.id}: error on attempt ${attempt}/${MAX_CAPTURE_RETRIES}: ${error instanceof Error ? error.message : String(error)}`,
        );
        await page.close();
        continue;
      }
      return {
        caseId: testCase.id,
        platform: options.platform,
        error: {
          code: 'CAPTURE_FAILED',
          message: error instanceof Error ? error.message : String(error),
        },
      };
    } finally {
      await page.close().catch(() => {});
    }
  }

  // unreachable, but satisfies TypeScript
  return {
    caseId: testCase.id,
    platform: options.platform,
    error: { code: 'CAPTURE_FAILED', message: 'Unexpected retry exhaustion' },
  };
}

async function buildCapturePage(): Promise<void> {
  execFileSync('npx', ['rsbuild', 'build', '--config', 'rsbuild.config.ts'], {
    cwd: toolRoot(),
    stdio: 'inherit',
  });
}

async function executeSteps(
  page: Awaited<
    ReturnType<Awaited<ReturnType<typeof chromium.launch>>['newPage']>
  >,
  steps: CaseStep[],
  screenshots: Buffer[],
): Promise<void> {
  for (const step of steps) {
    switch (step.action) {
      case 'wait':
        await page.waitForTimeout(step.ms);
        break;
      case 'save':
        await page.evaluate(async () => {
          (window as any).__savedState = await (window as any).j2me.saveState();
        });
        break;
      case 'load':
        await page.evaluate(() => {
          const saved = (window as any).__savedState;
          if (!saved) throw new Error('No saved state available for load step');
          (window as any).j2me.loadState(saved);
        });
        break;
      case 'keyPress':
        await page.evaluate((key: string) => {
          const j2me = (window as any).j2me;
          const keyCodeMap: Record<string, number> = {
            up: -1,
            down: -2,
            left: -3,
            right: -4,
            fire: -5,
            softLeft: -6,
            softRight: -7,
            '0': 48,
            '1': 49,
            '2': 50,
            '3': 51,
            '4': 52,
            '5': 53,
            '6': 54,
            '7': 55,
            '8': 56,
            '9': 57,
            '*': 42,
            '#': 35,
          };
          const keyCode = keyCodeMap[key] ?? 53; // default to '5'
          j2me.input.triggerKeyPressed(keyCode);
          j2me.input.triggerKeyReleased(keyCode);
        }, step.key);
        await page.waitForTimeout(100);
        break;
      case 'screenshot': {
        const dataUrl = await page.evaluate(() =>
          (window as any).__visualSnapshot.getPngDataUrl(),
        );
        screenshots.push(dataUrlToBuffer(dataUrl));
        break;
      }
    }
  }
}

async function createCaptureServer(
  cases: VisualCase[],
  casesJson: string,
): Promise<{
  baseUrl: string;
  close(): Promise<void>;
}> {
  const caseById = new Map(cases.map((testCase) => [testCase.id, testCase]));
  const distRoot = path.join(toolRoot(), 'dist-web');

  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url ?? '/', 'http://127.0.0.1');
      if (url.pathname === '/__visual_snapshot__/cases.json') {
        sendText(res, 200, casesJson, 'application/json; charset=utf-8');
        return;
      }

      const jarMatch = /^\/__visual_snapshot__\/jars\/([^/]+)\.jar$/.exec(
        url.pathname,
      );
      if (jarMatch) {
        const testCase = caseById.get(decodeURIComponent(jarMatch[1]));
        if (!testCase) {
          sendText(res, 404, 'Unknown case');
          return;
        }
        await sendFile(res, testCase.jarPath);
        return;
      }

      const relativePath =
        url.pathname === '/' ? 'index.html' : url.pathname.slice(1);
      await sendFile(res, path.join(distRoot, relativePath));
    } catch (error) {
      sendText(
        res,
        500,
        error instanceof Error ? error.message : String(error),
      );
    }
  });

  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('Failed to start capture server');
  }

  return {
    baseUrl: `http://127.0.0.1:${address.port}`,
    close: () =>
      new Promise((resolve, reject) =>
        server.close((error) => (error ? reject(error) : resolve())),
      ),
  };
}

async function sendFile(
  res: http.ServerResponse,
  filePath: string,
): Promise<void> {
  const stat = await fs.stat(filePath);
  if (!stat.isFile()) {
    sendText(res, 404, 'Not found');
    return;
  }

  const ext = path.extname(filePath);
  res.writeHead(200, {
    'content-type': CONTENT_TYPES[ext] ?? 'application/octet-stream',
    'content-length': stat.size,
  });
  createReadStream(filePath).pipe(res);
}

function sendText(
  res: http.ServerResponse,
  status: number,
  text: string,
  contentType = 'text/plain; charset=utf-8',
): void {
  res.writeHead(status, { 'content-type': contentType });
  res.end(text);
}

function dataUrlToBuffer(dataUrl: string): Buffer {
  const prefix = 'data:image/png;base64,';
  if (!dataUrl.startsWith(prefix)) {
    throw new Error('Expected PNG data URL');
  }
  return Buffer.from(dataUrl.slice(prefix.length), 'base64');
}

function parseArgs(args: string[]): CliOptions {
  const repoRoot = findRepoRoot();
  const caseIds: string[] = [];
  const tags: string[] = [];
  let casesPath: string | undefined;
  let artifactRoot: string | undefined;
  let platform: string | undefined;
  let headless = true;
  let timeout = 30000;

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
      case '--out':
        artifactRoot = requireValue(normalizedArgs, ++i, arg);
        break;
      case '--platform':
        platform = requireValue(normalizedArgs, ++i, arg);
        break;
      case '--headless':
        headless = requireValue(normalizedArgs, ++i, arg) !== 'false';
        break;
      case '--timeout':
        timeout = Number(requireValue(normalizedArgs, ++i, arg));
        if (!Number.isFinite(timeout) || timeout <= 0) {
          throw new Error('--timeout must be a positive number');
        }
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
    caseIds,
    tags,
    platform: platform ?? 'web-webgpu',
    headless,
    timeout,
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
