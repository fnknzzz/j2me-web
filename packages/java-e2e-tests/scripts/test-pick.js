#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path, { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createConfig } from './config.js';

const __file = fileURLToPath(import.meta.url);
const __dir = dirname(__file);

const getConfig = () => {
  let rootDir = process.env.JAR_CLI_ROOT;
  if (!rootDir && process.env.npm_package_json) {
    rootDir = dirname(process.env.npm_package_json);
  }
  if (!rootDir) {
    rootDir = process.cwd();
  }

  const javaVersion = process.env.JAVA_VERSION;
  const options = javaVersion
    ? { javaSource: javaVersion, javaTarget: javaVersion }
    : {};

  return createConfig(rootDir, options);
};

const args = process.argv.slice(2);
const name = args[0];

const runSync = (cmd, cmdArgs = []) =>
  spawnSync(cmd, cmdArgs, { stdio: 'inherit' });
const exitIfFailed = (result) => {
  const code = result.status ?? 0;
  if (code !== 0) process.exit(code);
};

const listSuites = () => {
  const config = getConfig();
  const dir = config.paths.tests;
  try {
    const files = fs.readdirSync(dir);
    const names = files
      .filter((f) => f.endsWith('.test.ts'))
      .map((f) => f.replace(/\.test\.ts$/, ''));
    process.stdout.write(`${names.join('\n')}`);
  } catch {}
  process.exit(0);
};

if (args.includes('--list-suites') || args.includes('--list')) {
  listSuites();
}

if (!name) {
  const r = runSync('vitest', ['run']);
  process.exit(r.status ?? 0);
} else {
  const jarCli = path.resolve(__dir, 'jar-cli.js');
  exitIfFailed(runSync('node', [jarCli, 'build', name, '--silent']));
  exitIfFailed(runSync('node', [jarCli, 'run', name, '--silent']));

  const config = getConfig();
  const file = path.join(config.paths.tests, `${name}.test.ts`);
  if (fs.existsSync(file)) {
    const r = runSync('vitest', ['run', '-u', file]);
    process.exit(r.status ?? 0);
  } else {
    const r = runSync('vitest', ['run', '-u', '--grep', name]);
    process.exit(r.status ?? 0);
  }
}
