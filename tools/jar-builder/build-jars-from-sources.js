#!/usr/bin/env node

import { spawn } from 'node:child_process';
import crypto from 'node:crypto';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function parseArgs(argv) {
  const options = {
    buildScript: path.join(__dirname, 'build-jar.sh'),
    runtime: path.join(__dirname, '../../crates/j2me-core/j2me-rt/classes.jar'),
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '--src-dir':
        options.srcDir = argv[++i];
        break;
      case '--resources-dir':
        options.resourcesDir = argv[++i];
        break;
      case '--output-dir':
        options.outputDir = argv[++i];
        break;
      case '--build-script':
        options.buildScript = argv[++i];
        break;
      case '--runtime':
        options.runtime = argv[++i];
        break;
      case '--force':
        options.force = true;
        break;
      case '--help':
      case '-h':
        options.help = true;
        break;
      default:
        throw new Error(`未知参数: ${arg}`);
    }
  }

  return options;
}

function printHelp() {
  console.log(`用法: node build-jars-from-sources.js --src-dir <dir> --output-dir <dir> [--resources-dir <dir>]

按源码目录中的每个 .java 文件构建同名 J2ME JAR。

选项:
  --src-dir        Java 源码目录（递归扫描 .java）
  --resources-dir 资源根目录；若存在 <resources-dir>/<Java文件名>/，会打入对应 JAR
  --output-dir     JAR/JAD 输出目录
  --build-script   build-jar.sh 路径，默认当前工具目录下的 build-jar.sh
  --runtime        J2ME runtime JAR，默认 crates/j2me-core/j2me-rt/classes.jar
  --force          忽略缓存，强制重新构建所有 JAR`);
}

async function exists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function collectJavaFiles(dir) {
  const results = [];

  async function walk(current) {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        await walk(fullPath);
      } else if (entry.isFile() && entry.name.endsWith('.java')) {
        results.push(fullPath);
      }
    }
  }

  await walk(dir);
  return results.sort();
}

async function collectFiles(dir) {
  const results = [];

  async function walk(current) {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        await walk(fullPath);
      } else if (entry.isFile()) {
        results.push(fullPath);
      }
    }
  }

  if (await exists(dir)) {
    await walk(dir);
  }
  return results.sort();
}

async function readJson(filePath, fallback) {
  try {
    return JSON.parse(await fs.readFile(filePath, 'utf8'));
  } catch {
    return fallback;
  }
}

async function writeJson(filePath, data) {
  await fs.writeFile(filePath, `${JSON.stringify(data, null, 2)}\n`);
}

async function hashFiles(files, baseDir) {
  const hash = crypto.createHash('sha256');
  for (const file of files) {
    const relativePath = path.relative(baseDir, file).replace(/\\/g, '/');
    hash.update(relativePath);
    hash.update('\0');
    hash.update(await fs.readFile(file));
    hash.update('\0');
  }
  return hash.digest('hex');
}

async function getBuildHash({ javaFile, resourcePath, buildScript, runtime }) {
  const files = [javaFile, buildScript, runtime];
  if (resourcePath) {
    files.push(...(await collectFiles(resourcePath)));
  }
  return hashFiles(files, path.dirname(javaFile));
}

function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit' });
    child.on('close', (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${command} exited with code ${code}`));
      }
    });
    child.on('error', reject);
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));

  if (options.help) {
    printHelp();
    return;
  }

  if (!options.srcDir || !options.outputDir) {
    printHelp();
    process.exitCode = 1;
    return;
  }

  const srcDir = path.resolve(options.srcDir);
  const outputDir = path.resolve(options.outputDir);
  const resourcesDir = options.resourcesDir
    ? path.resolve(options.resourcesDir)
    : undefined;
  const buildScript = path.resolve(options.buildScript);
  const runtime = path.resolve(options.runtime);
  if (!(await exists(runtime))) {
    throw new Error(`未找到 J2ME runtime JAR: ${runtime}`);
  }

  const javaFiles = await collectJavaFiles(srcDir);
  if (javaFiles.length === 0) {
    throw new Error(`未找到 Java 源码: ${srcDir}`);
  }

  await fs.mkdir(outputDir, { recursive: true });

  const cachePath = path.join(outputDir, '.jar-build-cache.json');
  const cache = await readJson(cachePath, {});
  let builtCount = 0;
  let skippedCount = 0;

  console.log(`发现 ${javaFiles.length} 个 Java 源码，输出到 ${outputDir}`);

  for (const javaFile of javaFiles) {
    const name = path.basename(javaFile, '.java');
    const jarPath = path.join(outputDir, `${name}.jar`);
    const jadPath = path.join(outputDir, `${name}.jad`);
    const resourcePath =
      resourcesDir && (await exists(path.join(resourcesDir, name)))
        ? path.join(resourcesDir, name)
        : undefined;
    const buildHash = await getBuildHash({
      javaFile,
      resourcePath,
      buildScript,
      runtime,
    });

    if (
      !options.force &&
      cache[name]?.hash === buildHash &&
      (await exists(jarPath)) &&
      (await exists(jadPath))
    ) {
      console.log(`⏭️  跳过 (已缓存): ${name}.jar`);
      skippedCount++;
      continue;
    }

    const args = [
      buildScript,
      '--name',
      name,
      '--src',
      javaFile,
      '--output',
      outputDir,
      '--runtime',
      runtime,
    ];

    if (resourcePath) {
      args.push('--resources', resourcePath);
    }

    console.log(`\n==> 构建 ${name}.jar`);
    await run('bash', args);
    cache[name] = { hash: buildHash, time: Date.now() };
    builtCount++;
  }

  await writeJson(cachePath, cache);

  console.log(`\n构建完成：新构建 ${builtCount}，缓存命中 ${skippedCount}，总计 ${javaFiles.length} 个 JAR`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
