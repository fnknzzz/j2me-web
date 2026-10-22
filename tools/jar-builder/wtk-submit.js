#!/usr/bin/env node

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PREVIEW_PORT = 8787;

function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '--host':
        options.host = argv[++i];
        break;
      case '--name':
        options.name = argv[++i];
        break;
      case '--main':
        options.main = argv[++i];
        break;
      case '--src':
        options.src = argv[++i];
        break;
      case '--resources':
        options.resources = argv[++i];
        break;
      case '--device':
        options.device = argv[++i];
        break;
      case '--help':
      case '-h':
        options.help = true;
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

function printHelp() {
  console.log(`Usage: node tools/wtk-build-server/wtk-submit.js --name <MIDletName> --src <JavaFileOrDir> [options]

Sends Java source files and optional resources from Mac to the Windows WTK preview server.

Options:
  --host      Server URL, e.g. http://192.168.1.100:8787
              Defaults to http://WINDOWS_IP:8787 from tools/wtk-build-server/.env.local
  --main      Main MIDlet class. Defaults to --name
  --resources Resource directory to include in the JAR
  --device    WTK device. Default: QwertyDevice`);
}

async function loadEnvLocal() {
  const filePath = path.join(__dirname, '.env.local');
  let text;
  try {
    text = await fs.readFile(filePath, 'utf8');
  } catch {
    return {};
  }

  const env = {};
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq < 0) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (key && value) env[key] = value;
  }
  return env;
}

function resolveHost(options, env) {
  if (options.host) return options.host.replace(/\/$/, '');
  if (!env.WINDOWS_IP) {
    throw new Error('Missing WINDOWS_IP in tools/wtk-build-server/.env.local or pass --host');
  }
  return `http://${env.WINDOWS_IP}:${PREVIEW_PORT}`;
}

function toPackagePath(filePath) {
  return filePath.split(path.sep).join('/');
}

async function pathExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function collectFiles(root, predicate = () => true) {
  const stat = await fs.stat(root);
  if (stat.isFile()) {
    return predicate(root) ? [root] : [];
  }
  if (!stat.isDirectory()) {
    throw new Error(`Not a file or directory: ${root}`);
  }

  const results = [];
  async function walk(dir) {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    entries.sort((a, b) => a.name.localeCompare(b.name));
    for (const entry of entries) {
      const entryPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        await walk(entryPath);
      } else if (entry.isFile() && predicate(entryPath)) {
        results.push(entryPath);
      }
    }
  }
  await walk(root);
  return results;
}

async function packageFiles(kind, root, files) {
  const rootStat = await fs.stat(root);
  const baseDir = rootStat.isDirectory() ? root : path.dirname(root);
  const packaged = [];
  let totalBytes = 0;

  for (const filePath of files) {
    const content = await fs.readFile(filePath);
    totalBytes += content.length;
    packaged.push({
      kind,
      path: toPackagePath(path.relative(baseDir, filePath)),
      encoding: 'base64',
      content: content.toString('base64'),
    });
  }

  return { files: packaged, totalBytes };
}

async function inferResourcesPath(repoRoot, options, name) {
  if (options.resources) {
    return path.resolve(repoRoot, options.resources);
  }

  const visualCaseResources = path.join(repoRoot, 'apps', 'visual-snapshot', 'cases', 'res', name);
  if (await pathExists(visualCaseResources)) {
    return visualCaseResources;
  }

  return null;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.src) {
    printHelp();
    process.exit(1);
  }

  const repoRoot = path.resolve(__dirname, '..', '..');
  const env = await loadEnvLocal();
  const src = path.resolve(repoRoot, options.src);
  const name = options.name || path.basename(src, '.java');
  const host = resolveHost(options, env);
  const device = options.device || 'QwertyDevice';
  const sourceFiles = await collectFiles(src, (filePath) => filePath.endsWith('.java'));
  if (sourceFiles.length === 0) {
    throw new Error(`No Java source files found under ${src}`);
  }

  const sourcePackage = await packageFiles('source', src, sourceFiles);
  const resourceRoot = await inferResourcesPath(repoRoot, options, name);
  const resourceFiles = resourceRoot ? await collectFiles(resourceRoot) : [];
  const resourcePackage = resourceRoot ? await packageFiles('resource', resourceRoot, resourceFiles) : { files: [], totalBytes: 0 };

  const payload = {
    name,
    main: options.main || name,
    device,
    sourceRoot: 'src',
    resourceRoot: resourceFiles.length > 0 ? 'res' : undefined,
    files: [...sourcePackage.files, ...resourcePackage.files],
  };

  console.log(`POST ${host}/run`);
  console.log(`MIDlet: ${name}`);
  console.log(`Device: ${device}`);
  console.log(`Source: ${src}`);
  console.log(`Source files: ${sourceFiles.length} (${sourcePackage.totalBytes} bytes)`);
  if (resourceRoot) {
    console.log(`Resources: ${resourceRoot}`);
    console.log(`Resource files: ${resourceFiles.length} (${resourcePackage.totalBytes} bytes)`);
  }

  const response = await fetch(`${host}/run`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const text = await response.text();
  let result;
  try {
    result = JSON.parse(text);
  } catch {
    result = { ok: false, error: text };
  }

  if (!response.ok || !result.ok) {
    console.error(result.error || `HTTP ${response.status}`);
    if (result.output) console.error(result.output);
    process.exit(1);
  }

  console.log('WTK emulator launched.');
  console.log(`JAD: ${result.jadPath}`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
