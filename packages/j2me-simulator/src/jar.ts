import JSZip from 'jszip';

export interface JarEntry {
  name: string;
  data: ArrayBuffer;
  isClass: boolean;
}

export async function parseJarBuffer(buffer: Uint8Array): Promise<JarEntry[]> {
  const zip = await JSZip.loadAsync(buffer);
  const entries: JarEntry[] = [];

  for (const [name, file] of Object.entries(zip.files)) {
    if (file.dir) continue;
    entries.push({
      name,
      data: await file.async('arraybuffer'),
      isClass: name.endsWith('.class'),
    });
  }

  return entries;
}

export async function parseJar(
  jar: Uint8Array | JarEntry[],
): Promise<JarEntry[]> {
  return Array.isArray(jar) ? jar : parseJarBuffer(jar);
}
