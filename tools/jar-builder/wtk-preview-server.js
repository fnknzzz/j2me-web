#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DEFAULT_HOST = '0.0.0.0';
const PREVIEW_PORT = 8787;
const MAX_BODY_BYTES = 50 * 1024 * 1024;

function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
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
  console.log(`Usage: node wtk-preview-server.js

Runs on Windows. Accepts a source/resource file package over HTTP, restores it
to a temp tree, builds it with WTK, then opens the generated JAD in WTK emulator.

Required environment:
  JAVA_HOME   JDK 8 path
  WTK_HOME    WTK 2.5.2 path`);
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
    if (key && value) {
      env[key] = value;
    }
  }
  return env;
}

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload, null, 2);
  res.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
  });
  res.end(body);
}

function readRequestJson(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error('Request body too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      try {
        const text = Buffer.concat(chunks).toString('utf8');
        resolve(text ? JSON.parse(text) : {});
      } catch (error) {
        reject(error);
      }
    });
    req.on('error', reject);
  });
}

function validateName(name) {
  return typeof name === 'string' && /^[A-Za-z0-9_.-]+$/.test(name);
}

function safeRelativePath(filePath) {
  if (typeof filePath !== 'string' || filePath.length === 0) {
    throw new Error('Invalid file path');
  }
  const normalized = path.normalize(filePath).replace(/^([/\\])+/, '');
  if (normalized.startsWith('..') || path.isAbsolute(normalized)) {
    throw new Error(`Unsafe file path: ${filePath}`);
  }
  return normalized;
}

async function writeSourceFiles(workDir, payload) {
  const srcDir = path.join(workDir, 'src');
  await fs.mkdir(srcDir, { recursive: true });

  if (Array.isArray(payload.files) && payload.files.length > 0) {
    const resDir = path.join(workDir, 'res');
    let sourceCount = 0;
    let resourceCount = 0;

    for (const file of payload.files) {
      const relativePath = safeRelativePath(file.path);
      const kind = file.kind || 'source';
      if (kind !== 'source' && kind !== 'resource') {
        throw new Error(`Invalid file kind for ${relativePath}: ${kind}`);
      }
      if (file.encoding !== 'base64') {
        throw new Error(`Invalid file encoding for ${relativePath}: ${file.encoding || 'missing'}`);
      }
      if (typeof file.content !== 'string') {
        throw new Error(`Missing content for ${relativePath}`);
      }
      const targetRoot = kind === 'resource' ? resDir : srcDir;
      const target = path.join(targetRoot, relativePath);
      await fs.mkdir(path.dirname(target), { recursive: true });
      await fs.writeFile(target, Buffer.from(file.content, 'base64'));
      if (kind === 'resource') {
        resourceCount++;
      } else {
        sourceCount++;
      }
    }

    if (sourceCount === 0) {
      throw new Error('Request must include at least one source file');
    }
    return {
      srcArg: srcDir,
      resourcesArg: resourceCount > 0 ? resDir : null,
      sourceCount,
      resourceCount,
    };
  }

  if (typeof payload.source !== 'string') {
    throw new Error('Request must include source or files');
  }
  const relativePath = safeRelativePath(payload.srcPath || `${payload.name}.java`);
  const target = path.join(srcDir, relativePath);
  await fs.mkdir(path.dirname(target), { recursive: true });
  await fs.writeFile(target, payload.source, 'utf8');
  return {
    srcArg: target,
    resourcesArg: null,
    sourceCount: 1,
    resourceCount: 0,
  };
}

function quoteCmd(value) {
  return `"${String(value).replace(/"/g, '""')}"`;
}

async function runBatch(batchPath, args, env) {
  const launcherPath = path.join(
    env.TEMP || env.TMP || __dirname,
    `wtk-preview-${Date.now()}-${process.pid}.cmd`,
  );
  const launcherBody = [
    '@echo off',
    'chcp 65001>nul',
    ['call', quoteCmd(batchPath), ...args.map(quoteCmd)].join(' '),
    'exit /b %ERRORLEVEL%',
    '',
  ].join('\r\n');
  await fs.writeFile(launcherPath, launcherBody, 'utf8');

  return new Promise((resolve, reject) => {
    const child = spawn('cmd.exe', ['/d', '/c', launcherPath], {
      cwd: __dirname,
      env,
      windowsHide: false,
    });

    let output = '';
    child.stdout.on('data', (chunk) => {
      const text = chunk.toString();
      output += text;
      process.stdout.write(text);
    });
    child.stderr.on('data', (chunk) => {
      const text = chunk.toString();
      output += text;
      process.stderr.write(text);
    });
    child.on('error', reject);
    child.on('close', (code) => {
      fs.unlink(launcherPath).catch(() => {});
      if (code === 0) {
        resolve(output);
      } else {
        const error = new Error(`build-jar.bat exited with code ${code}`);
        error.output = output;
        reject(error);
      }
    });
  });
}

function launchEmulator({ env, jadPath, device }) {
  if (!env.WTK_HOME) {
    throw new Error('WTK_HOME is required');
  }
  const emulator = path.join(env.WTK_HOME, 'bin', 'emulator.exe');
  const child = spawn(emulator, [`-Xdevice:${device}`, `-Xdescriptor:${jadPath}`], {
    cwd: __dirname,
    env,
    detached: true,
    stdio: 'ignore',
    windowsHide: false,
  });
  child.unref();
}

async function handleRun(payload, env) {
  if (!validateName(payload.name)) {
    throw new Error('Invalid or missing name');
  }

  const device = payload.device || 'QwertyDevice';
  const tempRoot = path.join(__dirname, '.temp');
  await fs.mkdir(tempRoot, { recursive: true });
  const workDir = await fs.mkdtemp(path.join(tempRoot, 'wtk-preview-'));
  const outputDir = path.join(workDir, 'out');
  await fs.mkdir(outputDir, { recursive: true });

  const restored = await writeSourceFiles(workDir, payload);
  const buildScript = path.join(__dirname, 'build-jar.bat');
  const buildArgs = [
    '--name',
    payload.name,
    '--src',
    restored.srcArg,
    '--output',
    outputDir,
  ];
  if (payload.main) {
    buildArgs.push('--main', payload.main);
  }
  if (restored.resourcesArg) {
    buildArgs.push('--resources', restored.resourcesArg);
  }

  console.log(`\n==> WTK preview build: ${payload.name}`);
  console.log(`==> Restored files: ${restored.sourceCount} source, ${restored.resourceCount} resource`);
  const buildLog = await runBatch(buildScript, buildArgs, env);

  const jadPath = path.join(outputDir, `${payload.name}.jad`);
  const jarPath = path.join(outputDir, `${payload.name}.jar`);
  await fs.access(jadPath);
  await fs.access(jarPath);

  console.log(`==> Launching WTK emulator: ${device}`);
  launchEmulator({ env, jadPath, device });

  return {
    ok: true,
    name: payload.name,
    device,
    workDir,
    jarPath,
    jadPath,
    buildLog,
  };
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const localEnv = await loadEnvLocal();
  const env = { ...process.env, ...localEnv };
  const host = DEFAULT_HOST;
  const port = PREVIEW_PORT;

  const server = createServer(async (req, res) => {
    try {
      if (req.method === 'GET' && req.url === '/health') {
        sendJson(res, 200, { ok: true });
        return;
      }
      if (req.method !== 'POST' || req.url !== '/run') {
        sendJson(res, 404, { ok: false, error: 'Not found' });
        return;
      }
      const payload = await readRequestJson(req);
      const result = await handleRun(payload, env);
      sendJson(res, 200, result);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const output = error && typeof error === 'object' && 'output' in error ? error.output : undefined;
      console.error(message);
      sendJson(res, 500, { ok: false, error: message, output });
    }
  });

  server.listen(port, host, () => {
    console.log(`WTK preview server listening on http://${host}:${port}`);
    console.log('POST /run to build and open a MIDlet in WTK emulator.');
  });
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
