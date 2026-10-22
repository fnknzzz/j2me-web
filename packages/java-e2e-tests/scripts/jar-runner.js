#!/usr/bin/env node

import { exec } from 'node:child_process';
import { promises as fs } from 'node:fs';
import { basename, join } from 'node:path';
import { promisify } from 'node:util';
import { CacheManager } from './cache-manager.js';
import { applyAllNormalizers } from './normalizers.js';

const execPromise = promisify(exec);

export class JarRunner {
  constructor(config, options = {}) {
    this.config = config;
    this.jarDir = options.jarDir || config.paths.jars;
    this.outputDir = options.outputDir || config.paths.jarOutputs;
    this.cache = new CacheManager(this.jarDir, config.cache.runCacheFile);
    this.stats = { ran: 0, cached: 0, failed: 0, skipped: 0 };
  }

  async init() {
    await fs.mkdir(this.outputDir, { recursive: true });
    await this.cache.load();
  }

  async parseTestMetadata(jarPath) {
    try {
      const jarName = basename(jarPath, '.jar');
      const className = jarName;

      const findJavaFile = async (dir, fileName) => {
        try {
          const items = await fs.readdir(dir, { withFileTypes: true });
          for (const item of items) {
            const fullPath = join(dir, item.name);
            if (item.isDirectory()) {
              const found = await findJavaFile(fullPath, fileName);
              if (found) return found;
            } else if (item.name === fileName) {
              return fullPath;
            }
          }
        } catch {}
        return null;
      };

      const javaPath = await findJavaFile(
        this.config.paths.javaSources,
        `${className}.java`,
      );
      if (!javaPath) return null;

      const content = await fs.readFile(javaPath, 'utf8');
      const lines = content.split('\n').slice(0, 20);

      const metadata = {};

      for (const line of lines) {
        const skipMatch = line.match(/@Skip:\s*(true|false)/);
        if (skipMatch && skipMatch[1] === 'true') {
          metadata.skip = true;
        }

        const reasonMatch = line.match(/@Reason:\s*(.+)/);
        if (reasonMatch) {
          metadata.reason = reasonMatch[1].trim();
        }
      }

      return Object.keys(metadata).length > 0 ? metadata : null;
    } catch {}
    return null;
  }

  async runJar(jarPath, options = {}) {
    const jarName = basename(jarPath);
    const jarNameWithoutExt = jarName.replace('.jar', '');
    const parts = jarNameWithoutExt.split('.');
    const simpleClassName = parts[parts.length - 1];
    const outputPath = join(this.outputDir, `${simpleClassName}.txt`);

    const metadata = await this.parseTestMetadata(jarPath);

    if (metadata?.skip) {
      this.stats.skipped++;

      const output = `跳过: ${metadata.reason || '测试被标记为跳过'}\n`;
      await fs.writeFile(outputPath, output, 'utf8');

      if (!options.silent) {
        console.log(`[跳过] ${jarName}`);
      }

      return {
        jarPath,
        outputPath,
        skipped: true,
        reason: metadata.reason,
        success: true,
      };
    }

    const stat = await fs.stat(jarPath);
    const jarHashInfo = {
      hash: await this.cache.computeFileHash(jarPath),
      mtime: stat.mtimeMs,
      size: stat.size,
    };

    if (!jarHashInfo.hash) {
      throw new Error(`无法读取 JAR 文件: ${jarPath}`);
    }

    if (
      this.cache.isCacheValidWithHashes(jarName, { [jarPath]: jarHashInfo }, [
        outputPath,
      ])
    ) {
      this.stats.cached++;
      if (!options.silent) {
        console.log(`[缓存] ${jarName}`);
      }
      return { jarPath, outputPath, cached: true, success: true };
    }

    try {
      const { stdout, stderr } = await execPromise(`java -jar "${jarPath}"`, {
        encoding: 'utf8',
        maxBuffer: 10 * 1024 * 1024,
        timeout: 30000,
      });

      let output = stdout || stderr || '';

      output = applyAllNormalizers(output);

      await fs.writeFile(outputPath, output, 'utf8');

      this.cache.set(jarName, {
        fileHashes: { [jarPath]: jarHashInfo },
        timestamp: Date.now(),
      });

      this.stats.ran++;
      if (!options.silent) {
        console.log(`[运行] ${jarName}`);
      }

      return { jarPath, outputPath, output, cached: false, success: true };
    } catch (error) {
      this.stats.failed++;
      throw new Error(`运行失败 ${jarName}: ${error.message}`);
    }
  }

  async runAll(jarNames = null, options = {}) {
    const files = await fs.readdir(this.jarDir);
    let jarFiles = files.filter((f) => f.endsWith('.jar'));

    if (jarNames) {
      const nameSet = new Set(jarNames);
      jarFiles = jarFiles.filter((f) => nameSet.has(f));
    }

    const results = [];
    const concurrency = options.concurrency || 8;
    const total = jarFiles.length;

    for (let i = 0; i < jarFiles.length; i += concurrency) {
      const batch = jarFiles.slice(i, i + concurrency);
      const batchResults = await Promise.all(
        batch.map(async (jarFile) => {
          const jarPath = join(this.jarDir, jarFile);
          try {
            const result = await this.runJar(jarPath, options);
            return result;
          } catch (error) {
            return {
              jarPath,
              error: error.message,
              success: false,
            };
          }
        }),
      );
      results.push(...batchResults);

      if (!options.silent) {
        const progress = Math.min(i + concurrency, total);
        const percent = ((progress / total) * 100).toFixed(0);
        console.log(`\n进度: ${progress}/${total} (${percent}%)`);
      }
    }

    return results;
  }

  async saveCache() {
    await this.cache.save();
  }

  getStats() {
    return { ...this.stats };
  }
}
