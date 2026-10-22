import { setWasmLoader } from './core';

// 顶层声明，确保 rspack 能静态分析到 wasm 资源
const wasmUrl = new URL('j2me_core.wasm', import.meta.url).href;

if (typeof process !== 'undefined' && process.versions?.node) {
  setWasmLoader(async () => {
    const { readFileSync } = await import(/* webpackIgnore: true */ 'node:fs');
    const { resolve, dirname } = await import(
      /* webpackIgnore: true */ 'node:path'
    );
    const { fileURLToPath } = await import(
      /* webpackIgnore: true */ 'node:url'
    );
    const dir = dirname(fileURLToPath(import.meta.url));
    return readFileSync(resolve(dir, 'j2me_core.wasm'));
  });
} else {
  setWasmLoader(async () => {
    const resp = await fetch(wasmUrl);
    if (!resp.ok)
      throw new Error(
        `Failed to fetch WASM: ${resp.status} ${resp.statusText}`,
      );
    return resp.arrayBuffer();
  });
}

export type {
  AsyncRequest,
  CheatObjectCandidate,
  CheatObjectField,
  CheatObjectSearchResult,
  CreateSurfaceCallback,
  DerivedImageRestore,
  ImageSurfaceSnapshotTarget,
  ResourceImageRestore,
  RmsKeysCallback,
  RmsLoadCallback,
  RmsRemoveCallback,
  RmsSaveCallback,
} from './core';
export { getWasmBytes, JvmInstance, JvmRewindSnapshot } from './core';
