import { execFileSync, spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const packagePath = path.join(repoRoot, 'packages/j2me-web/package.json');
const releaseType = process.argv[2];

if (!['patch', 'minor', 'major'].includes(releaseType)) {
  console.error('Usage: pnpm release:<patch|minor|major>');
  process.exit(1);
}

const run = (command, args) => {
  console.log(`\n$ ${command} ${args.join(' ')}`);
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    stdio: 'inherit',
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
};

const output = (command, args) =>
  execFileSync(command, args, { cwd: repoRoot, encoding: 'utf8' }).trim();

const assert = (condition, message) => {
  if (!condition) {
    console.error(`Release aborted: ${message}`);
    process.exit(1);
  }
};

assert(output('git', ['branch', '--show-current']) === 'main', 'not on main');
assert(output('git', ['status', '--porcelain']) === '', 'working tree is not clean');

const upstream = output('git', ['rev-list', '--left-right', '--count', '@{upstream}...HEAD']);
assert(upstream === '0\t0', 'main is not synchronized with its upstream');

run('pnpm', ['biome']);
run('pnpm', ['test:unit']);

run('pnpm', [
  '--filter',
  'j2me-web',
  'version',
  releaseType,
  '--no-git-tag-version',
]);

const { version } = JSON.parse(readFileSync(packagePath, 'utf8'));
const tag = `v${version}`;

run('pnpm', ['pack:check']);
run('pnpm', ['--filter', 'j2me-web', 'publish', '--access', 'public']);
run('git', ['add', 'packages/j2me-web/package.json']);
run('git', ['commit', '-m', `Release ${tag}`]);
run('git', ['tag', '-a', tag, '-m', tag]);
run('git', ['push', 'origin', 'main', '--follow-tags']);

console.log(`\nPublished j2me-web@${version} and pushed ${tag}.`);
