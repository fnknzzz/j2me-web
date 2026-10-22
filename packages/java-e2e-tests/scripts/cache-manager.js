#!/usr/bin/env node

import crypto from 'node:crypto';
import { accessSync, promises as fs } from 'node:fs';
import { join } from 'node:path';

const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

export class CacheManager {
  constructor(outputDir, cacheFileName) {
    this.cacheFile = join(outputDir, cacheFileName);
    this.cache = null;
    this.fileHashCache = new Map();
  }

  async load() {
    try {
      const content = await fs.readFile(this.cacheFile, 'utf8');
      this.cache = JSON.parse(content);
    } catch {
      this.cache = {};
    }
    return this.cache;
  }

  async save() {
    if (!this.cache) return;
    await fs.writeFile(this.cacheFile, JSON.stringify(this.cache, null, 2));
  }

  get(key) {
    return this.cache?.[key];
  }

  set(key, value) {
    if (!this.cache) this.cache = {};
    this.cache[key] = value;
  }

  has(key) {
    return this.cache != null && key in this.cache;
  }

  async computeFileHash(filePath) {
    try {
      const content = await fs.readFile(filePath);
      return sha256(content);
    } catch {
      return null;
    }
  }

  async computeFilesHash(filePaths) {
    const fileHashes = {};
    for (const path of filePaths) {
      try {
        if (this.fileHashCache.has(path)) {
          fileHashes[path] = this.fileHashCache.get(path);
          continue;
        }

        const stat = await fs.stat(path);
        const content = await fs.readFile(path);
        const hashInfo = {
          hash: sha256(content),
          mtime: stat.mtimeMs,
          size: stat.size,
        };

        this.fileHashCache.set(path, hashInfo);
        fileHashes[path] = hashInfo;
      } catch {}
    }
    return fileHashes;
  }

  clearFileHashCache() {
    this.fileHashCache.clear();
  }

  async quickCheckFiles(key, filePaths) {
    const cached = this.get(key);
    if (!cached || !cached.fileHashes) return null;

    const cachedFiles = Object.keys(cached.fileHashes);
    if (cachedFiles.length !== filePaths.length) return null;

    const fileHashes = {};
    for (const path of filePaths) {
      try {
        const stat = await fs.stat(path);
        const cachedInfo = cached.fileHashes[path];

        if (!cachedInfo) return null;

        if (
          cachedInfo.mtime === stat.mtimeMs &&
          cachedInfo.size === stat.size
        ) {
          fileHashes[path] = cachedInfo;
        } else {
          return null;
        }
      } catch {
        return null;
      }
    }

    return fileHashes;
  }

  isCacheValidWithHashes(key, fileHashes, requiredFiles = []) {
    const cached = this.get(key);
    if (!cached || !cached.fileHashes) return false;

    const cachedFiles = Object.keys(cached.fileHashes);
    const currentFiles = Object.keys(fileHashes);
    if (cachedFiles.length !== currentFiles.length) return false;

    for (const [file, info] of Object.entries(fileHashes)) {
      const cachedInfo = cached.fileHashes[file];
      if (!cachedInfo || cachedInfo.hash !== info.hash) return false;
    }

    for (const file of requiredFiles) {
      try {
        accessSync(file);
      } catch {
        return false;
      }
    }

    return true;
  }
}
