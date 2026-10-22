import fs from 'node:fs/promises';
import { inflateRawSync } from 'node:zlib';

export async function readMidletClassFromJar(
  jarPath: string,
  midletIndex = 1,
): Promise<string> {
  const jar = await fs.readFile(jarPath);
  const manifest = readJarEntry(jar, 'META-INF/MANIFEST.MF');
  if (!manifest) {
    throw new Error(`JAR does not contain META-INF/MANIFEST.MF: ${jarPath}`);
  }

  const attributes = parseManifest(manifest.toString('utf8'));
  const midlet = attributes.get(`MIDlet-${midletIndex}`);
  if (!midlet) {
    throw new Error(
      `JAR manifest does not contain MIDlet-${midletIndex}: ${jarPath}`,
    );
  }

  const parts = midlet.split(',');
  const className = parts[parts.length - 1]?.trim();
  if (!className) {
    throw new Error(`Invalid MIDlet-${midletIndex} manifest value: ${midlet}`);
  }
  return className;
}

function readJarEntry(jar: Buffer, entryName: string): Buffer | null {
  const eocdOffset = findEndOfCentralDirectory(jar);
  if (eocdOffset < 0) {
    throw new Error('Invalid JAR: missing end of central directory');
  }

  const centralDirectorySize = jar.readUInt32LE(eocdOffset + 12);
  const centralDirectoryOffset = jar.readUInt32LE(eocdOffset + 16);
  const centralDirectoryEnd = centralDirectoryOffset + centralDirectorySize;
  let offset = centralDirectoryOffset;

  while (offset < centralDirectoryEnd) {
    if (jar.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error('Invalid JAR: bad central directory header');
    }

    const compressionMethod = jar.readUInt16LE(offset + 10);
    const compressedSize = jar.readUInt32LE(offset + 20);
    const uncompressedSize = jar.readUInt32LE(offset + 24);
    const nameLength = jar.readUInt16LE(offset + 28);
    const extraLength = jar.readUInt16LE(offset + 30);
    const commentLength = jar.readUInt16LE(offset + 32);
    const localHeaderOffset = jar.readUInt32LE(offset + 42);
    const name = jar.toString('utf8', offset + 46, offset + 46 + nameLength);

    if (name.toUpperCase() === entryName.toUpperCase()) {
      return readLocalFileData(
        jar,
        localHeaderOffset,
        compressionMethod,
        compressedSize,
        uncompressedSize,
      );
    }

    offset += 46 + nameLength + extraLength + commentLength;
  }

  return null;
}

function readLocalFileData(
  jar: Buffer,
  localHeaderOffset: number,
  compressionMethod: number,
  compressedSize: number,
  uncompressedSize: number,
): Buffer {
  if (jar.readUInt32LE(localHeaderOffset) !== 0x04034b50) {
    throw new Error('Invalid JAR: bad local file header');
  }

  const nameLength = jar.readUInt16LE(localHeaderOffset + 26);
  const extraLength = jar.readUInt16LE(localHeaderOffset + 28);
  const dataStart = localHeaderOffset + 30 + nameLength + extraLength;
  const compressed = jar.subarray(dataStart, dataStart + compressedSize);

  if (compressionMethod === 0) {
    return compressed;
  }
  if (compressionMethod === 8) {
    const inflated = inflateRawSync(compressed);
    if (inflated.length !== uncompressedSize) {
      throw new Error('Invalid JAR: inflated entry size mismatch');
    }
    return inflated;
  }

  throw new Error(`Unsupported JAR compression method: ${compressionMethod}`);
}

function findEndOfCentralDirectory(jar: Buffer): number {
  for (let offset = jar.length - 22; offset >= 0; offset--) {
    if (jar.readUInt32LE(offset) === 0x06054b50) {
      return offset;
    }
  }
  return -1;
}

function parseManifest(text: string): Map<string, string> {
  const attributes = new Map<string, string>();
  let currentKey: string | null = null;

  for (const line of text.replace(/\r\n/g, '\n').split('\n')) {
    if (line.length === 0) {
      currentKey = null;
      continue;
    }
    if (line.startsWith(' ') && currentKey) {
      attributes.set(
        currentKey,
        `${attributes.get(currentKey) ?? ''}${line.slice(1)}`,
      );
      continue;
    }

    const separator = line.indexOf(':');
    if (separator < 0) {
      continue;
    }
    currentKey = line.slice(0, separator);
    attributes.set(currentKey, line.slice(separator + 1).trimStart());
  }

  return attributes;
}
