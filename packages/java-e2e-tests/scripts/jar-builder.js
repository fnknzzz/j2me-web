#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { promises as fs } from 'node:fs';
import { basename, join, relative } from 'node:path';
import { CacheManager } from './cache-manager.js';

const runCmd = (cmd, args, opts = {}) =>
  new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: 'inherit', ...opts });
    p.on('close', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`${cmd} exited with code ${code}`));
    });
    p.on('error', reject);
  });

const runCmdCapture = (cmd, args, opts = {}) =>
  new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'], ...opts });
    let out = '';
    let err = '';
    p.stdout.on('data', (d) => {
      out += String(d);
    });
    p.stderr.on('data', (d) => {
      err += String(d);
    });
    p.on('close', (code) => {
      if (code === 0) resolve(out || err);
      else reject(new Error(`${cmd} failed: ${err || out}`));
    });
    p.on('error', reject);
  });

async function findJava8Tools() {
  const candidates = [];
  if (process.env.JAVA8_HOME) candidates.push(`${process.env.JAVA8_HOME}/bin`);
  if (process.env.JAVA_HOME) candidates.push(`${process.env.JAVA_HOME}/bin`);

  try {
    const home8 = (
      await runCmdCapture('/usr/libexec/java_home', ['-v', '1.8'])
    ).trim();
    if (home8) candidates.unshift(`${home8}/bin`);
  } catch {}

  for (const bin of candidates) {
    try {
      const v = await runCmdCapture(`${bin}/javac`, ['-version']);
      if (/^javac (1\.8\.|8(\.|\s|$))/.test(v.trim())) {
        return { javac: `${bin}/javac`, jar: `${bin}/jar` };
      }
    } catch {}
  }

  try {
    const v = await runCmdCapture('javac', ['-version']);
    if (/^javac (1\.8\.|8(\.|\s|$))/.test(v.trim())) {
      return { javac: 'javac', jar: 'jar' };
    }
  } catch {}

  throw new Error('未找到 JDK 8，请设置 JAVA8_HOME 或 JAVA_HOME');
}

async function findJavaFiles(dir) {
  const results = [];
  const walk = async (d) => {
    const items = await fs.readdir(d, { withFileTypes: true });
    for (const item of items) {
      const full = join(d, item.name);
      if (item.isDirectory()) {
        await walk(full);
      } else if (item.name.endsWith('.java')) {
        results.push(full);
      }
    }
  };
  await walk(dir);
  return results;
}

function parseJarIncludes(content) {
  const includes = [];
  const header = content.split('\n').slice(0, 30).join('\n');
  const regex = /@jar-include:\s*([^\n*]+)/g;
  let m = regex.exec(header);
  while (m) {
    const list = m[1]
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    includes.push(...list);
    m = regex.exec(header);
  }
  return Array.from(new Set(includes));
}

async function findMainClass(javaFile) {
  const content = await fs.readFile(javaFile, 'utf8');
  if (!content.includes('public static void main(String[] args)')) {
    return null;
  }

  const pkgMatch = content.match(/\bpackage\s+([\w.]+)\s*;/);
  const pkg = pkgMatch ? pkgMatch[1] : '';

  const classMatch = content.match(
    /\bpublic\s+class\s+([A-Za-z_][A-Za-z0-9_]*)/,
  );
  if (!classMatch) return null;

  const className = classMatch[1];
  return pkg ? `${pkg}.${className}` : className;
}

export class JarBuilder {
  constructor(config, options = {}) {
    this.config = config;
    this.javaSources = options.javaSources || config.paths.javaSources;
    this.outputDir = options.outputDir || config.paths.jars;
    this.runtimeJar = options.runtimeJar || config.paths.runtimeJar;
    this.cache = new CacheManager(this.outputDir, config.cache.buildCacheFile);
    this.tools = null;
    this.stats = { built: 0, cached: 0 };
    this.skipEntries = options.skipEntries || false;
  }

  async init() {
    await fs.mkdir(this.outputDir, { recursive: true });
    await fs.access(this.runtimeJar);
    await this.cache.load();
    this.tools = await findJava8Tools();
  }

  async buildJar(javaFiles, options = {}) {
    if (!Array.isArray(javaFiles)) {
      javaFiles = [javaFiles];
    }

    const mainFile = javaFiles[0];
    const mainClass = await findMainClass(mainFile);
    if (!mainClass) {
      throw new Error(`未找到 main 方法: ${mainFile}`);
    }

    const shortName = basename(mainFile, '.java');
    const jarName = `${shortName}.jar`;
    const jarPath = join(this.outputDir, jarName);

    const allFiles = await this.resolveAllFiles(javaFiles);
    const cacheFiles = [...allFiles, this.runtimeJar];
    const requiredFiles = this.skipEntries
      ? [jarPath]
      : [jarPath, jarPath.replace('.jar', '-entries.js')];

    let fileHashes = await this.cache.quickCheckFiles(jarName, cacheFiles);
    let isCacheValid = false;

    if (fileHashes) {
      isCacheValid = this.cache.isCacheValidWithHashes(
        jarName,
        fileHashes,
        requiredFiles,
      );
    } else {
      fileHashes = await this.cache.computeFilesHash(cacheFiles);
      isCacheValid = this.cache.isCacheValidWithHashes(
        jarName,
        fileHashes,
        requiredFiles,
      );
    }

    if (isCacheValid) {
      this.stats.cached++;
      return jarPath;
    }

    const tempDir = join(this.outputDir, '.temp', shortName);
    await fs.rm(tempDir, { recursive: true, force: true });
    await fs.mkdir(tempDir, { recursive: true });

    try {
      await runCmd(
        this.tools.javac,
        [
          '-source',
          this.config.java.source,
          '-target',
          this.config.java.target,
          '-encoding',
          this.config.java.encoding,
          '-bootclasspath',
          this.runtimeJar,
          '-Xlint:none',
          '-nowarn',
          '-d',
          tempDir,
          ...allFiles,
        ],
        { stdio: options.silent ? 'ignore' : 'inherit' },
      );

      await runCmd(
        this.tools.jar,
        ['cfe', jarPath, mainClass, '-C', tempDir, '.'],
        { stdio: options.silent ? 'ignore' : 'inherit' },
      );

      if (!this.skipEntries) {
        await this.generateEntries(jarPath);
      }

      this.cache.set(jarName, {
        fileHashes,
        files: allFiles.map((f) => relative(this.javaSources, f)),
        mainClass,
        timestamp: Date.now(),
      });

      this.stats.built++;
      if (!options.silent) {
        console.log(`[构建] ${jarName}`);
      }

      return jarPath;
    } finally {
      await fs.rm(tempDir, { recursive: true, force: true });
    }
  }

  async resolveAllFiles(javaFiles) {
    const resolved = new Set(javaFiles);

    for (const file of javaFiles) {
      const content = await fs.readFile(file, 'utf8');
      const includes = parseJarIncludes(content);

      for (const inc of includes) {
        const incPath = join(
          this.javaSources,
          `${inc.split('.').join('/')}.java`,
        );
        try {
          await fs.access(incPath);
          resolved.add(incPath);
        } catch {}
      }
    }

    return Array.from(resolved);
  }

  async generateEntries(jarPath) {
    const extractDir = jarPath.replace('.jar', '-extracted');
    await fs.rm(extractDir, { recursive: true, force: true });
    await fs.mkdir(extractDir, { recursive: true });

    await runCmd(this.tools.jar, ['xf', jarPath], {
      cwd: extractDir,
      stdio: 'ignore',
    });

    const entries = [];
    const walk = async (dir) => {
      const items = await fs.readdir(dir, { withFileTypes: true });
      for (const item of items) {
        const full = join(dir, item.name);
        if (item.isDirectory()) {
          await walk(full);
        } else {
          const rel = relative(extractDir, full).replace(/\\/g, '/');
          const buf = await fs.readFile(full);
          entries.push({
            name: rel,
            data: buf.toString('base64'),
            isClass: rel.endsWith('.class'),
          });
        }
      }
    };
    await walk(extractDir);

    const entriesPath = jarPath.replace('.jar', '-entries.js');
    const code = `export const entries = [
${entries
  .map(
    (e) => `  {
    name: '${e.name.replace(/'/g, "\\'")}',
    data: Uint8Array.from(atob('${e.data}'), c => c.charCodeAt(0)).buffer,
    isClass: ${e.isClass}
  }`,
  )
  .join(',\n')}
];
`;
    await fs.writeFile(entriesPath, code);
    await fs.rm(extractDir, { recursive: true, force: true });
  }

  async buildAll(_pattern = '**/*.java', options = {}) {
    const allFiles = await findJavaFiles(this.javaSources);
    const mainFiles = [];

    for (const file of allFiles) {
      const mainClass = await findMainClass(file);
      if (mainClass) {
        mainFiles.push(file);
      }
    }

    this.cache.clearFileHashCache();

    const concurrency = options.concurrency || 4;
    const results = [];

    for (let i = 0; i < mainFiles.length; i += concurrency) {
      const batch = mainFiles.slice(i, i + concurrency);
      const batchResults = await Promise.allSettled(
        batch.map(async (file) => {
          try {
            const jarPath = await this.buildJar(file, options);
            return { file, jarPath, success: true };
          } catch (error) {
            return { file, error, success: false };
          }
        }),
      );

      for (const result of batchResults) {
        if (result.status === 'fulfilled') {
          results.push(result.value);
        } else {
          results.push({
            file: 'unknown',
            error: result.reason,
            success: false,
          });
        }
      }
    }

    this.cache.clearFileHashCache();

    return results;
  }

  async saveCache() {
    await this.cache.save();
  }

  getStats() {
    return { ...this.stats };
  }
}

export { findJavaFiles, findMainClass };
