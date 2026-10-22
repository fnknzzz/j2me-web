#!/usr/bin/env node

import { promises as fs } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createConfig } from './config.js';
import { JarBuilder } from './jar-builder.js';
import { JarRunner } from './jar-runner.js';

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
  const options = {};

  if (javaVersion) {
    options.javaSource = javaVersion;
    options.javaTarget = javaVersion;
  }

  if (process.env.JAR_CLI_JAVA_SOURCES) {
    options.javaSources = process.env.JAR_CLI_JAVA_SOURCES;
  }
  if (process.env.JAR_CLI_JARS) {
    options.jars = process.env.JAR_CLI_JARS;
  }
  if (process.env.JAR_CLI_JAR_OUTPUTS) {
    options.jarOutputs = process.env.JAR_CLI_JAR_OUTPUTS;
  }
  if (process.env.JAR_CLI_RUNTIME_JAR) {
    options.runtimeJar = process.env.JAR_CLI_RUNTIME_JAR;
  }

  if (process.env.JAR_CLI_SKIP_ENTRIES === 'true') {
    options.skipEntries = true;
  }

  return createConfig(rootDir, options);
};

async function findJavaFile(config, name) {
  const walk = async (dir) => {
    const items = await fs.readdir(dir, { withFileTypes: true });
    for (const item of items) {
      const full = join(dir, item.name);
      if (item.isDirectory()) {
        const result = await walk(full);
        if (result) return result;
      } else if (item.name === `${name}.java`) {
        return full;
      }
    }
    return null;
  };
  return await walk(config.paths.javaSources);
}

async function main() {
  const config = getConfig();
  const args = process.argv.slice(2);
  const command = args[0];

  if (!command || command === '--help' || command === '-h') {
    console.log(`
用法:
  jar-cli build <Name>          # 构建单个 JAR
  jar-cli build-all             # 构建所有 JAR
  jar-cli run <Name>            # 运行单个 JAR
  jar-cli run-all               # 运行所有 JAR
  jar-cli test <Name>           # 构建 + 运行 + 测试
  jar-cli clean                 # 清理缓存

选项:
  --silent                      # 静默模式
  --no-cache                    # 忽略缓存

环境变量:
  JAR_CLI_ROOT                  # 项目根目录
`);
    process.exit(0);
  }

  const silent = args.includes('--silent');
  const noCache = args.includes('--no-cache');

  try {
    if (command === 'build') {
      const name = args[1];
      if (!name) {
        console.error('错误: 请指定类名');
        process.exit(1);
      }

      const javaFile = await findJavaFile(config, name);
      if (!javaFile) {
        console.error(`错误: 未找到 ${name}.java`);
        process.exit(1);
      }

      const builder = new JarBuilder(config);
      await builder.init();

      if (noCache) {
        builder.cache.cache = {};
      }

      const jarPath = await builder.buildJar(javaFile, { silent });
      await builder.saveCache();

      const stats = builder.getStats();
      console.log(`\n✓ 构建完成: ${basename(jarPath)}`);
      console.log(`  新构建: ${stats.built}, 缓存命中: ${stats.cached}`);
    } else if (command === 'build-all') {
      const builder = new JarBuilder(config);
      await builder.init();

      if (noCache) {
        builder.cache.cache = {};
      }

      console.log('开始构建所有 JAR...\n');
      const results = await builder.buildAll('**/*.java', { silent });
      await builder.saveCache();

      const stats = builder.getStats();
      const failed = results.filter((r) => !r.success);

      console.log(`\n✓ 构建完成`);
      console.log(
        `  新构建: ${stats.built}, 缓存命中: ${stats.cached}, 总计: ${stats.built + stats.cached}`,
      );
      if (failed.length > 0) {
        console.log(`  失败: ${failed.length}`);
        for (const f of failed) {
          console.log(`    - ${basename(f.file)}: ${f.error?.message}`);
        }
      }
    } else if (command === 'run') {
      const name = args[1];
      if (!name) {
        console.error('错误: 请指定类名');
        process.exit(1);
      }

      const allJars = await fs.readdir(config.paths.jars);
      const jarFiles = allJars
        .filter((f) => f === `${name}.jar` || f.endsWith(`.${name}.jar`))
        .map((f) => join(config.paths.jars, f));

      if (jarFiles.length === 0) {
        console.error(`错误: 找不到 JAR 文件: *${name}.jar`);
        console.error('提示: 请先运行 build 命令');
        process.exit(1);
      }

      if (jarFiles.length > 1) {
        console.error(`错误: 找到多个匹配的 JAR 文件:`);
        jarFiles.forEach((f) => {
          console.error(`  - ${basename(f)}`);
        });
        console.error('提示: 请使用更具体的名称');
        process.exit(1);
      }

      const jarPath = jarFiles[0];

      const runner = new JarRunner(config);
      await runner.init();

      if (noCache) {
        runner.cache.cache = {};
      }

      const result = await runner.runJar(jarPath, { silent });
      await runner.saveCache();

      console.log(`\n✓ 运行完成: ${name}.jar`);
      console.log(`  输出文件: ${basename(result.outputPath)}`);
    } else if (command === 'run-all') {
      const runner = new JarRunner(config);
      await runner.init();

      if (noCache) {
        runner.cache.cache = {};
      }

      console.log('开始运行所有 JAR...\n');
      const results = await runner.runAll(null, { silent });
      await runner.saveCache();

      const stats = runner.getStats();
      const failed = results.filter((r) => !r.success);

      console.log(`\n✓ 运行完成`);
      console.log(
        `  新运行: ${stats.ran}, 缓存命中: ${stats.cached}, 跳过: ${stats.skipped || 0}`,
      );
      if (failed.length > 0) {
        console.log(`  失败: ${stats.failed}`);
        for (const f of failed) {
          console.log(`    - ${basename(f.jarPath)}: ${f.error}`);
        }
      }
    } else if (command === 'test') {
      const name = args[1];
      if (!name) {
        console.error('错误: 请指定类名');
        process.exit(1);
      }

      const javaFile = await findJavaFile(config, name);
      if (!javaFile) {
        console.error(`错误: 未找到 ${name}.java`);
        process.exit(1);
      }

      console.log(`\n1️⃣  构建 ${name}...`);
      const builder = new JarBuilder(config);
      await builder.init();
      const jarPath = await builder.buildJar(javaFile, { silent: true });
      await builder.saveCache();

      console.log(`2️⃣  运行 ${name}...`);
      const runner = new JarRunner(config);
      await runner.init();
      await runner.runJar(jarPath, { silent: true });
      await runner.saveCache();

      console.log(`3️⃣  测试 ${name}...`);
      const testFile = join(config.paths.tests, `${name}.test.ts`);
      try {
        await fs.access(testFile);
        const { spawn } = await import('node:child_process');
        await new Promise((resolve, reject) => {
          const p = spawn('pnpm', ['vitest', 'run', testFile], {
            stdio: 'inherit',
            cwd: config.paths.root,
          });
          p.on('close', (code) => {
            if (code === 0) resolve();
            else reject(new Error(`测试失败，退出码: ${code}`));
          });
          p.on('error', reject);
        });
        console.log(`\n✓ 全部完成`);
      } catch (error) {
        if (error.code === 'ENOENT') {
          console.log(`\n⚠️  未找到测试文件: ${name}.test.ts`);
          console.log(`✓ 构建和运行完成`);
        } else {
          throw error;
        }
      }
    } else if (command === 'clean') {
      const buildCache = join(config.paths.jars, config.cache.buildCacheFile);
      const runCache = join(config.paths.jars, config.cache.runCacheFile);

      try {
        await fs.unlink(buildCache);
        console.log(`✓ 已删除构建缓存`);
      } catch {}

      try {
        await fs.unlink(runCache);
        console.log(`✓ 已删除运行缓存`);
      } catch {}

      console.log(`\n✓ 缓存清理完成`);
    } else {
      console.error(`错误: 未知命令 '${command}'`);
      console.error(`运行 'jar-cli --help' 查看帮助`);
      process.exit(1);
    }
  } catch (error) {
    console.error(`\n❌ 错误: ${error.message}`);
    if (process.env.DEBUG) {
      console.error(error.stack);
    }
    process.exit(1);
  }
}

main();
