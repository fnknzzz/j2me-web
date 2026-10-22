import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SNAPSHOTS_DIR = path.join(__dirname, 'snapshots');
const JARS_DIR = path.join(__dirname, 'jars');

const entriesCache = new Map();

const loadEntriesFromFile = (entriesPath) => {
  const cached = entriesCache.get(entriesPath);
  if (cached) return cached;

  const code = fs.readFileSync(entriesPath, 'utf-8').replace(/^\uFEFF/, '');
  const transformed = code.replace(
    /export\s+const\s+entries\s*=\s*/,
    'const entries = ',
  );
  const atobImpl =
    globalThis.atob ||
    ((base64) => Buffer.from(base64, 'base64').toString('binary'));
  const getEntries = new Function('atob', `${transformed}\nreturn entries;`);
  const entries = getEntries(atobImpl);
  entriesCache.set(entriesPath, entries);
  return entries;
};

const scanJarFiles = (dir) => {
  const results = [];
  if (!fs.existsSync(dir)) return results;
  const items = fs.readdirSync(dir, { withFileTypes: true });
  for (const item of items) {
    const fullPath = path.join(dir, item.name);
    if (item.isDirectory()) {
      results.push(...scanJarFiles(fullPath));
    } else if (item.name.endsWith('.jar')) {
      results.push(item.name.slice(0, -4));
    }
  }
  return results;
};

const createTestCases = (category) => {
  const names = scanJarFiles(path.join(JARS_DIR, category))
    .filter((name) => name.endsWith('Suite'))
    .sort();

  return names.map((name) => ({
    name,
    get entries() {
      const entriesPath = path.join(JARS_DIR, category, `${name}-entries.js`);
      return loadEntriesFromFile(entriesPath);
    },
    get jar() {
      const jarPath = path.join(JARS_DIR, category, `${name}.jar`);
      const buf = fs.readFileSync(jarPath);
      return buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);
    },
    get output() {
      return fs.readFileSync(
        path.join(SNAPSHOTS_DIR, category, `${name}.txt`),
        'utf-8',
      );
    },
  }));
};

export const cldcTestCases = createTestCases('cldc');

export { cldcValidators } from './cldc-validators.js';
