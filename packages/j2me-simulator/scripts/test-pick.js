#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { promises as fs } from 'node:fs';
import { join, resolve } from 'node:path';

const log = (s) => console.log(s);
const errorLog = (s) => console.error(s);

const JARS_DIR = resolve(process.cwd(), 'src/__tests__/j2me-jars');

const runVitest = async (testName) => {
  return new Promise((resolve, reject) => {
    const args = testName
      ? ['run', '--reporter=verbose', `src/__tests__/${testName}.test.ts`]
      : ['run', '--reporter=verbose'];

    const child = spawn('npx', ['vitest', ...args], {
      cwd: process.cwd(),
      stdio: 'inherit',
      shell: true,
    });

    child.on('close', (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`测试失败，退出码: ${code}`));
      }
    });

    child.on('error', (err) => {
      reject(err);
    });
  });
};

const main = async () => {
  const args = process.argv.slice(2);

  try {
    if (args.length === 0) {
      log('运行所有测试...');
      await runVitest();
      log('\n✅ 所有测试完成');
    } else {
      const testName = args[0];
      const jarPath = join(JARS_DIR, `${testName}.jar`);

      try {
        await fs.access(jarPath);
      } catch {
        errorLog(`❌ 错误: JAR 文件不存在: ${jarPath}`);
        errorLog('');
        errorLog('可用的 JAR 文件:');
        const files = await fs.readdir(JARS_DIR);
        const jarFiles = files.filter((f) => f.endsWith('.jar'));
        for (const file of jarFiles) {
          errorLog(`  - ${file.replace('.jar', '')}`);
        }
        process.exit(1);
      }

      log(`运行测试: ${testName}`);
      await runVitest(testName);
      log(`\n✅ ${testName} 测试完成`);
    }
  } catch (error) {
    errorLog(`\n❌ 测试失败: ${error.message}`);
    process.exit(1);
  }
};

main();
