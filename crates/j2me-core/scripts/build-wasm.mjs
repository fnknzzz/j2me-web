import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDir, '../../..');
const output = process.argv[2] || 'release';
const cargoArgs = ['build', '--target', 'wasm32-unknown-unknown'];

if (output !== 'debug') {
  if (output === 'release') {
    cargoArgs.push('--release');
  } else {
    cargoArgs.push('--profile', output);
  }
}

const home = os.homedir();
const remapFlag = `--remap-path-prefix=${home}=/src`;
// `host_*` functions are supplied by JavaScript as WASM imports. This script
// sets RUSTFLAGS for path remapping, which takes precedence over Cargo config,
// so the linker setting must live here as well.
const allowUndefinedFlag = '-C link-arg=--allow-undefined';
const rustflags = [process.env.RUSTFLAGS, remapFlag, allowUndefinedFlag]
  .filter(Boolean)
  .join(' ');
const result = spawnSync('cargo', cargoArgs, {
  cwd: repositoryRoot,
  env: { ...process.env, RUSTFLAGS: rustflags },
  stdio: 'inherit',
});

if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);

const profile = output === 'debug' ? 'debug' : output;
const wasmPath = path.join(
  repositoryRoot,
  'target',
  'wasm32-unknown-unknown',
  profile,
  'j2me_core.wasm',
);
const wasm = fs.readFileSync(wasmPath);
const pathsToRemap = [home, '/build-home'];

for (const sourcePath of pathsToRemap) {
  const localPath = Buffer.from(sourcePath);
  const replacement = Buffer.from('/src'.padEnd(localPath.length, '/'));
  let offset = 0;

  while ((offset = wasm.indexOf(localPath, offset)) !== -1) {
    replacement.copy(wasm, offset);
    offset += localPath.length;
  }
}

fs.writeFileSync(wasmPath, wasm);
