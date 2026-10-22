import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { type CapturePlatform, selectPlatforms } from './platforms.js';

async function main(): Promise<void> {
  const { platforms, restArgs } = await selectPlatforms(process.argv.slice(2));
  for (const target of platforms) {
    runCapture(target, restArgs);
  }
}

function runCapture(platform: CapturePlatform, restArgs: string[]): void {
  const scriptPath = path.join(import.meta.dirname, platform.captureScript);
  const args = [scriptPath, ...platform.captureArgs, ...restArgs];
  console.log(`\n=== Capture ${platform.name} ===\n`);
  const result = spawnSync(process.execPath, args, { stdio: 'inherit' });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
