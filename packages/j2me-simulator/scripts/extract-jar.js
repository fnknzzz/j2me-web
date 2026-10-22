#!/usr/bin/env node

import { spawn } from 'node:child_process';
import crypto from 'node:crypto';
import { promises as fs } from 'node:fs';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const JARS_DIR = resolve(process.cwd(), 'src/__tests__/j2me-jars');
const COMPILED_DIR = resolve(process.cwd(), 'src/__tests__/compiled');
const CACHE_FILE = join(COMPILED_DIR, '.jar-extract-cache.json');

const log = (s) => console.log(s);
const errorLog = (s) => console.error(s);

const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

const readCache = async () => {
  try {
    const txt = await fs.readFile(CACHE_FILE, 'utf8');
    return JSON.parse(txt);
  } catch {
    return {};
  }
};

const writeCache = async (cache) => {
  await fs.writeFile(CACHE_FILE, JSON.stringify(cache, null, 2));
};

const runCmd = (cmd, args, opts = {}) =>
  new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: 'pipe', ...opts });
    p.on('close', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`${cmd} exited with code ${code}`));
    });
    p.on('error', reject);
  });

const extractJarFile = async (jarPath, extractDir) => {
  await fs.mkdir(extractDir, { recursive: true });
  await runCmd('unzip', ['-o', '-q', jarPath, '-d', extractDir]);
};

const generateEntriesJsModule = async (extractDir, jarName) => {
  const entriesJsPath = join(COMPILED_DIR, `${jarName}-entries.js`);
  const collected = [];

  const walk = async (dir) => {
    const dirents = await fs.readdir(dir, { withFileTypes: true });
    for (const d of dirents) {
      const full = join(dir, d.name);
      if (d.isDirectory()) {
        await walk(full);
      } else {
        const rel = relative(extractDir, full);
        const buf = await fs.readFile(full);
        collected.push({
          name: rel.replace(/\\/g, '/'),
          base64Data: buf.toString('base64'),
          isClass: rel.endsWith('.class'),
        });
      }
    }
  };

  await walk(extractDir);

  const jsCode = `export const entries = [
${collected
  .map(
    (entry) => `  {
    name: '${entry.name.replace(/'/g, "\\'")}',
    data: Uint8Array.from(atob('${entry.base64Data}'), c => c.charCodeAt(0)).buffer,
    isClass: ${entry.isClass}
  }`,
  )
  .join(',\n')}
];
`;

  await fs.writeFile(entriesJsPath, jsCode);
};

const extractAndGenerateEntries = async (jarName, cache, verbose = true) => {
  const jarPath = join(JARS_DIR, `${jarName}.jar`);
  const extractDir = join(COMPILED_DIR, `${jarName}-extracted`);
  const entriesPath = join(COMPILED_DIR, `${jarName}-entries.js`);

  try {
    await fs.access(jarPath);
  } catch {
    errorLog(`❌ JAR 文件不存在: ${jarPath}`);
    return false;
  }

  const jarBuffer = await fs.readFile(jarPath);
  const jarHash = sha256(jarBuffer);
  const cached = cache[jarName];

  if (cached && cached.hash === jarHash) {
    try {
      await fs.access(entriesPath);
      if (verbose) {
        log(`⏭️  跳过 (已缓存): ${jarName}`);
      }
      return true;
    } catch {}
  }

  if (verbose) {
    log(`📦 解压 JAR: ${jarName}.jar`);
  }
  await extractJarFile(jarPath, extractDir);

  if (verbose) {
    log(`✨ 生成 entries: ${jarName}`);
  }
  await generateEntriesJsModule(extractDir, jarName);

  cache[jarName] = { hash: jarHash, time: Date.now() };

  if (verbose) {
    log(`✅ 完成: ${jarName}`);
  }
  return true;
};

const extractAll = async () => {
  await fs.mkdir(COMPILED_DIR, { recursive: true });

  const files = await fs.readdir(JARS_DIR);
  const jarFiles = files.filter((f) => f.endsWith('.jar'));

  if (jarFiles.length === 0) {
    log('未找到 JAR 文件');
    return;
  }

  const cache = await readCache();
  let successCount = 0;
  let skippedCount = 0;
  let failureCount = 0;

  log(`\n🔍 发现 ${jarFiles.length} 个 JAR 文件\n`);

  for (const jarFile of jarFiles) {
    const jarName = basename(jarFile, '.jar');
    const jarPath = join(JARS_DIR, jarFile);
    const entriesPath = join(COMPILED_DIR, `${jarName}-entries.js`);

    try {
      const jarBuffer = await fs.readFile(jarPath);
      const jarHash = sha256(jarBuffer);
      const cached = cache[jarName];

      if (cached && cached.hash === jarHash) {
        try {
          await fs.access(entriesPath);
          log(`⏭️  跳过 (已缓存): ${jarName}`);
          skippedCount++;
          continue;
        } catch {}
      }

      log(`📦 处理: ${jarName}`);
      const result = await extractAndGenerateEntries(jarName, cache, false);
      if (result) {
        successCount++;
      } else {
        failureCount++;
      }
    } catch (err) {
      errorLog(`❌ 失败: ${jarName} - ${err.message}`);
      failureCount++;
    }
  }

  await writeCache(cache);

  log(`\n📊 统计:`);
  log(`   总数: ${jarFiles.length}`);
  log(`   成功: ${successCount}`);
  log(`   跳过: ${skippedCount}`);
  log(`   失败: ${failureCount}`);
  log(`\n✨ 完成!\n`);
};

const main = async () => {
  const args = process.argv.slice(2);

  await fs.mkdir(COMPILED_DIR, { recursive: true });

  if (args.length === 0 || args.includes('--all') || args.includes('-a')) {
    await extractAll();
    return;
  }

  if (args.includes('--help') || args.includes('-h')) {
    log('用法:');
    log('  node extract-jar.js [选项] [JAR名称...]');
    log('');
    log('选项:');
    log('  --all, -a     处理所有 JAR 文件');
    log('  --help, -h    显示帮助信息');
    log('');
    log('示例:');
    log('  node extract-jar.js --all                    # 处理所有 JAR');
    log('  node extract-jar.js MIDlet01_Lifecycle       # 处理单个 JAR');
    log('  node extract-jar.js GameAPISuite GraphicsSuite  # 处理多个 JAR');
    return;
  }

  const cache = await readCache();
  let successCount = 0;
  let failureCount = 0;

  for (const jarName of args) {
    try {
      const result = await extractAndGenerateEntries(jarName, cache);
      if (result) {
        successCount++;
      } else {
        failureCount++;
      }
    } catch (err) {
      errorLog(`❌ 错误: ${jarName} - ${err.message}`);
      failureCount++;
    }
  }

  await writeCache(cache);

  if (args.length > 1) {
    log(`\n完成: ${successCount} 成功, ${failureCount} 失败`);
  }
};

main().catch((err) => {
  errorLog(`❌ 错误: ${err.message}`);
  process.exit(1);
});
