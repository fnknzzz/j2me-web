#!/usr/bin/env node

import { resolve } from 'node:path';

export const createConfig = (rootDir, options = {}) => {
  const tests = options.testsDir || `${rootDir}/src/__tests__`;
  const resolvePath = (p, fallback) =>
    p ? resolve(rootDir, p) : resolve(rootDir, fallback);

  return {
    paths: {
      root: rootDir,
      tests,
      javaSources: resolvePath(options.javaSources, `${tests}/java-sources`),
      jars: resolvePath(options.jars, `${tests}/compiled`),
      jarOutputs: resolvePath(options.jarOutputs, `${tests}/jar-outputs`),
      runtimeJar: resolvePath(
        options.runtimeJar,
        '../../crates/j2me-core/j2me-rt/classes.jar',
      ),
    },
    cache: {
      buildCacheFile: '.jar-build-cache.json',
      runCacheFile: '.jar-run-cache.json',
    },
    java: {
      source: options.javaSource || '1.8',
      target: options.javaTarget || '1.8',
      encoding: options.javaEncoding || 'UTF-8',
    },
    concurrency: {
      maxParallel: 4,
    },
    skipEntries: options.skipEntries || false,
  };
};
