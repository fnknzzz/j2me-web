import type { J2MEWeb } from 'j2me-web';
import { J2MEKeyCode } from 'j2me-web';
import { J2MEPhone } from '@/j2me-phone';

// --- IndexedDB Save State Storage ---
const DB_NAME = 'j2me-savestates';
const DB_VERSION = 1;
const STORE_NAME = 'snapshots';
const JAR_STORE_NAME = 'jars';

interface StoredJar {
  id: string;
  name: string;
  data: ArrayBuffer;
  importedAt: number;
}

const openDB = (): Promise<IDBDatabase> =>
  new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
      if (!db.objectStoreNames.contains(JAR_STORE_NAME)) {
        db.createObjectStore(JAR_STORE_NAME, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });

const saveSnapshot = async (
  jarName: string,
  slot: number,
  data: Uint8Array,
): Promise<void> => {
  const db = await openDB();
  const key = `${jarName}:slot${slot}`;
  const tx = db.transaction(STORE_NAME, 'readwrite');
  tx.objectStore(STORE_NAME).put(data, key);
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
};

const loadSnapshot = async (
  jarName: string,
  slot: number,
): Promise<Uint8Array | null> => {
  const db = await openDB();
  const key = `${jarName}:slot${slot}`;
  const tx = db.transaction(STORE_NAME, 'readonly');
  const store = tx.objectStore(STORE_NAME);
  const req = store.get(key);
  return new Promise((resolve, reject) => {
    req.onsuccess = () => {
      resolve(req.result ? new Uint8Array(req.result) : null);
    };
    req.onerror = () => reject(req.error);
  });
};

/** Read save timestamp and size from the 24-byte header without decompressing. */
const getSlotInfo = async (
  jarName: string,
  slot: number,
): Promise<{ timestamp: number; size: number } | null> => {
  const db = await openDB();
  const key = `${jarName}:slot${slot}`;
  const tx = db.transaction(STORE_NAME, 'readonly');
  const store = tx.objectStore(STORE_NAME);
  const req = store.get(key);
  return new Promise((resolve, reject) => {
    req.onsuccess = () => {
      if (!req.result) {
        resolve(null);
        return;
      }
      const raw = new Uint8Array(req.result);
      if (raw.byteLength < 24) {
        resolve(null);
        return;
      }
      const view = new DataView(raw.buffer, raw.byteOffset, raw.byteLength);
      // JSSV header: magic(4) + version(4) + timestamp(8)
      const lo = view.getUint32(8, true);
      const hi = view.getUint32(12, true);
      resolve({ timestamp: lo + hi * 0x100000000, size: raw.byteLength });
    };
    req.onerror = () => reject(req.error);
  });
};

type StatusType = 'loading' | 'success' | 'error';

type J2MEWebWithMusicSpeed = J2MEWeb & {
  setMusicFollowSpeed?: (enabled: boolean) => void;
};

const setMusicFollowSpeed = (j2me: J2MEWeb, enabled: boolean): void => {
  (j2me as J2MEWebWithMusicSpeed).setMusicFollowSpeed?.(enabled);
};

const request = <T>(operation: IDBRequest<T>): Promise<T> =>
  new Promise((resolve, reject) => {
    operation.onsuccess = () => resolve(operation.result);
    operation.onerror = () => reject(operation.error);
  });

const createJarId = (): string => {
  const bytes = new Uint8Array(16);
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }
  return `${Date.now().toString(36)}-${Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')}`;
};

const listStoredJars = async (): Promise<StoredJar[]> => {
  const db = await openDB();
  const jars = await request(db.transaction(JAR_STORE_NAME).objectStore(JAR_STORE_NAME).getAll());
  return jars.sort((a, b) => b.importedAt - a.importedAt);
};

const getStoredJar = async (id: string): Promise<StoredJar | undefined> => {
  const db = await openDB();
  return request(db.transaction(JAR_STORE_NAME).objectStore(JAR_STORE_NAME).get(id));
};

const storeJar = async (file: File): Promise<StoredJar> => {
  const jar: StoredJar = {
    id: createJarId(),
    name: file.name.replace(/\.jar$/i, ''),
    data: await file.arrayBuffer(),
    importedAt: Date.now(),
  };
  const db = await openDB();
  const transaction = db.transaction(JAR_STORE_NAME, 'readwrite');
  transaction.objectStore(JAR_STORE_NAME).put(jar);
  await new Promise<void>((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
  });
  return jar;
};

const deleteStoredJar = async (id: string): Promise<void> => {
  const db = await openDB();
  const transaction = db.transaction(JAR_STORE_NAME, 'readwrite');
  transaction.objectStore(JAR_STORE_NAME).delete(id);
  await new Promise<void>((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
  });
};

const selectJar = (id: string) => {
  const next = new URL(window.location.href);
  next.search = '';
  next.searchParams.set('jar', id);
  window.location.href = next.toString();
};

const createJarSelectorCard = async (activeJarId?: string): Promise<HTMLElement> => {
  const selectorCard = document.createElement('div');
  selectorCard.className = 'info-card';
  selectorCard.innerHTML = '<h3>📦 我的 JAR</h3><p style="font-size:13px;color:#666;margin-bottom:12px;">选择本地 JAR 文件导入。文件只保存在当前浏览器中。</p>';

  const importButton = document.createElement('button');
  importButton.textContent = '➕ 导入 JAR 文件';
  const fileInput = document.createElement('input');
  fileInput.type = 'file';
  fileInput.accept = '.jar,application/java-archive,application/java-archive';
  fileInput.style.display = 'none';
  importButton.addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', async () => {
    const file = fileInput.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.jar')) {
      alert('请选择 .jar 文件。');
      return;
    }
    importButton.disabled = true;
    importButton.textContent = '正在导入…';
    try {
      selectJar((await storeJar(file)).id);
    } catch (error) {
      console.error('Failed to import JAR:', error);
      alert('导入失败，请检查浏览器存储空间。');
      importButton.disabled = false;
      importButton.textContent = '➕ 导入 JAR 文件';
    }
  });
  selectorCard.append(importButton, fileInput);

  const jars = await listStoredJars();
  if (jars.length === 0) return selectorCard;

  const list = document.createElement('div');
  list.style.cssText = 'display:flex;flex-direction:column;gap:8px;margin-top:14px;max-height:320px;overflow-y:auto;';
  for (const jar of jars) {
    const row = document.createElement('div');
    row.style.cssText = `display:flex;align-items:center;gap:8px;padding:9px;border:1px solid ${jar.id === activeJarId ? '#667eea' : '#e9ecef'};border-radius:8px;`;
    const launch = document.createElement('button');
    launch.textContent = `▶ ${jar.name}`;
    launch.style.cssText = 'flex:1;min-width:0;text-align:left;padding:7px 9px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';
    launch.title = jar.name;
    launch.addEventListener('click', () => selectJar(jar.id));
    const remove = document.createElement('button');
    remove.textContent = '删除';
    remove.style.background = '#dc3545';
    remove.addEventListener('click', async () => {
      if (!confirm(`删除“${jar.name}”及其本地 JAR 文件？`)) return;
      await deleteStoredJar(jar.id);
      if (jar.id === activeJarId) {
        window.location.href = new URL(window.location.href).pathname;
      } else {
        row.remove();
      }
    });
    row.append(launch, remove);
    list.appendChild(row);
  }
  selectorCard.appendChild(list);
  return selectorCard;
};

interface FreezeEntry {
  addr: number;
  typeTag: number;
  value: number;
  label: string;
  enabled: boolean;
  stale: boolean;
}

const setupCheatPanel = (j2me: J2MEWeb) => {
  const toggleBtn = document.getElementById(
    'cheat-toggle-btn',
  ) as HTMLButtonElement | null;
  const panel = document.getElementById('cheat-panel') as HTMLElement | null;
  if (!toggleBtn || !panel) return;

  toggleBtn.disabled = false;
  let panelOpen = false;
  toggleBtn.addEventListener('click', () => {
    panelOpen = !panelOpen;
    panel.style.display = panelOpen ? '' : 'none';
    toggleBtn.style.background = panelOpen ? '#28a745' : '';
  });

  const searchBtn = document.getElementById(
    'cheat-search-btn',
  ) as HTMLButtonElement;
  const refineBtn = document.getElementById(
    'cheat-refine-btn',
  ) as HTMLButtonElement;
  const clearBtn = document.getElementById(
    'cheat-clear-btn',
  ) as HTMLButtonElement;
  const modeSelect = document.getElementById('cheat-mode') as HTMLSelectElement;
  const valueInput = document.getElementById('cheat-value') as HTMLInputElement;
  const statusEl = document.getElementById('cheat-status') as HTMLElement;
  const resultsEl = document.getElementById('cheat-results') as HTMLElement;
  const freezeListEl = document.getElementById(
    'cheat-freeze-list',
  ) as HTMLElement;

  let sessionRound = 0;
  const freezes = new Map<string, FreezeEntry>();

  const getJvm = () => (j2me as any).getJvm?.() ?? null;

  const setStatus = (msg: string) => {
    statusEl.textContent = msg;
  };

  const TYPE_COLORS: Record<string, string> = {
    i8: '#6c757d',
    i16: '#0d6efd',
    i32: '#198754',
    i64: '#6f42c1',
    f32: '#fd7e14',
    f64: '#dc3545',
  };

  const TYPE_TAGS: Record<string, number> = {
    i8: 0,
    i16: 1,
    i32: 2,
    i64: 3,
    f32: 4,
    f64: 5,
  };

  const renderResults = (
    candidates: Array<{
      addr: number;
      types?: string[];
      type?: string;
      value: number;
      confirmed_type?: string;
      field?: string;
    }>,
    truncated: boolean,
  ) => {
    if (candidates.length === 0) {
      resultsEl.innerHTML =
        '<div style="padding:8px 12px;color:#aaa;font-size:12px;">无候选地址</div>';
      return;
    }
    const shown = candidates.slice(0, 50);
    const rows = shown.map((c) => {
      const addr = c.addr;
      // Support both new format (types[]) and old format (type string)
      const typeList: string[] = c.types ?? (c.type ? [c.type] : ['i32']);
      const primaryType = c.confirmed_type || typeList[0] || 'i32';
      const typeTag = TYPE_TAGS[primaryType] ?? 2;
      const val = c.value;
      const confirmed = c.confirmed_type;
      const typeBadges = confirmed
        ? `<span style="background:${TYPE_COLORS[confirmed] || '#333'};color:#fff;border-radius:3px;padding:1px 5px;font-size:10px;">✓ ${confirmed}</span>`
        : typeList
            .map(
              (t) =>
                `<span style="background:${TYPE_COLORS[t] || '#333'};color:#fff;border-radius:3px;padding:1px 4px;font-size:10px;">${t}</span>`,
            )
            .join('');

      const fieldHint = c.field
        ? `<span style="color:#999;font-size:10px;max-width:100px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${c.field}">${c.field}</span>`
        : '';

      return `<div style="display:flex;align-items:center;gap:4px;padding:4px 8px;border-bottom:1px solid #f0f0f0;font-size:12px;">
        <span style="font-family:monospace;color:#888;flex:0 0 80px;">0x${addr.toString(16).padStart(8, '0')}</span>
        <span style="display:flex;gap:2px;flex:0 0 auto;">${typeBadges}</span>
        <span style="font-weight:600;color:#333;flex:1;">${val}</span>
        ${fieldHint}
        <button onclick="cheatWriteAddr(${addr},${typeTag})" style="padding:2px 6px;font-size:11px;min-width:auto;background:#667eea;">改值</button>
        <button onclick="cheatFreezeAddr(${addr},${typeTag},'${primaryType}',${val})" style="padding:2px 6px;font-size:11px;min-width:auto;background:#e67e22;">冻结</button>
        ${confirmed ? `<button onclick="cheatCodePin(${addr},${typeTag})" style="padding:2px 6px;font-size:11px;min-width:auto;background:#20c997;" title="生成稳定金手指">📌</button>` : ''}
      </div>`;
    });
    const extra =
      truncated || candidates.length > 50
        ? `<div style="padding:6px 12px;color:#e67e22;font-size:11px;">显示 ${shown.length} / ${candidates.length} 条${truncated ? '（已截断，请继续过滤）' : ''}</div>`
        : '';
    resultsEl.innerHTML = rows.join('') + extra;
  };

  const renderFreezes = () => {
    if (freezes.size === 0) {
      freezeListEl.innerHTML =
        '<div style="padding:8px 12px;color:#aaa;font-size:12px;">暂无冻结项</div>';
      return;
    }
    const rows = [...freezes.values()].map((e) => {
      const staleTag = e.stale
        ? '<span style="color:#e74c3c;margin-left:4px;">[失效]</span>'
        : '';
      const enabledColor = e.enabled ? '#28a745' : '#868e96';
      const typeName =
        ['i8', 'i16', 'i32', 'i64', 'f32', 'f64'][e.typeTag] ?? 'i32';
      const color = TYPE_COLORS[typeName] || '#333';
      const fk = `${e.addr}:${typeName}`;
      return `<div style="display:flex;align-items:center;gap:6px;padding:4px 8px;border-bottom:1px solid #f0f0f0;font-size:12px;">
        <span style="font-family:monospace;color:#888;flex:0 0 80px;">0x${e.addr.toString(16).padStart(8, '0')}</span>
        <span style="background:${color};color:#fff;border-radius:3px;padding:1px 5px;font-size:10px;flex:0 0 auto;">${typeName}</span>
        <span style="flex:1;color:#333;">${e.value}${staleTag}</span>
        <span style="color:#999;font-size:11px;">${e.label || ''}</span>
        <button onclick="cheatToggleFreeze('${fk}')" style="padding:2px 6px;font-size:11px;min-width:auto;background:${enabledColor};">${e.enabled ? '启用' : '关闭'}</button>
        <button onclick="cheatRemoveFreeze('${fk}')" style="padding:2px 6px;font-size:11px;min-width:auto;background:#e74c3c;">删除</button>
      </div>`;
    });
    freezeListEl.innerHTML = rows.join('');
  };

  const refreshFreezes = () => {
    const jvm = getJvm();
    if (!jvm) return;
    const list = jvm.cheatFreezeList();
    for (const entry of list) {
      const key = `${entry.addr}:${entry.type}`;
      const existing = freezes.get(key);
      if (existing) {
        existing.stale = entry.stale;
        existing.enabled = entry.enabled;
      }
    }
    renderFreezes();
  };

  // Expose helpers to onclick handlers
  (window as any).cheatWriteAddr = (addr: number, typeTag: number) => {
    const val = Number(prompt(`写入新值到 0x${addr.toString(16)}:`, '0'));
    if (Number.isNaN(val)) return;
    const jvm = getJvm();
    if (!jvm) return;
    jvm.cheatWrite(addr, typeTag, val);
    setStatus(`已写入 ${val} → 0x${addr.toString(16)}`);
  };

  (window as any).cheatFreezeAddr = (
    addr: number,
    typeTag: number,
    typeName: string,
    curVal: number,
  ) => {
    const val = Number(prompt(`冻结值（当前 ${curVal}）:`, String(curVal)));
    if (Number.isNaN(val)) return;
    const label = prompt('备注标签（可选）:', '') ?? '';
    const jvm = getJvm();
    if (!jvm) return;
    jvm.cheatFreezeAdd(addr, typeTag, val);
    const key = `${addr}:${typeName}`;
    freezes.set(key, {
      addr,
      typeTag,
      value: val,
      label,
      enabled: true,
      stale: false,
    });
    renderFreezes();
    setStatus(`已冻结 0x${addr.toString(16)} [${typeName}] = ${val}`);
  };

  (window as any).cheatToggleFreeze = (key: string) => {
    const entry = freezes.get(key);
    if (!entry) return;
    entry.enabled = !entry.enabled;
    getJvm()?.cheatFreezeSetEnabled(entry.addr, entry.typeTag, entry.enabled);
    renderFreezes();
  };

  (window as any).cheatRemoveFreeze = (key: string) => {
    const entry = freezes.get(key);
    if (!entry) return;
    freezes.delete(key);
    getJvm()?.cheatFreezeRemove(entry.addr, entry.typeTag);
    renderFreezes();
  };

  searchBtn.addEventListener('click', () => {
    const jvm = getJvm();
    if (!jvm) {
      setStatus('JVM 未就绪');
      return;
    }
    const modeTag = Number(modeSelect.value);
    const val = Number(valueInput.value);
    const needsValue = [0, 5, 6].includes(modeTag);
    if (needsValue && valueInput.value.trim() === '') {
      setStatus('请先输入数值');
      return;
    }
    jvm.cheatSearchBegin(modeTag, needsValue ? val : undefined);
    const res = jvm.cheatGetResults();
    sessionRound = 1;
    refineBtn.disabled = false;
    setStatus(
      `第 ${sessionRound} 轮：找到 ${res.count} 个候选${res.truncated ? '（已截断，继续过滤）' : ''}`,
    );
    renderResults(res.addrs as any, res.truncated);
  });

  refineBtn.addEventListener('click', () => {
    const jvm = getJvm();
    if (!jvm) return;
    const modeTag = Number(modeSelect.value);
    const val = Number(valueInput.value);
    const needsValue = [0, 5, 6].includes(modeTag);
    jvm.cheatSearchRefine(modeTag, needsValue ? val : undefined);
    const res = jvm.cheatGetResults();
    sessionRound++;
    setStatus(
      `第 ${sessionRound} 轮：找到 ${res.count} 个候选${res.truncated ? '（已截断，继续过滤）' : ''}`,
    );
    renderResults(res.addrs as any, res.truncated);
  });

  clearBtn.addEventListener('click', () => {
    getJvm()?.cheatSearchClear();
    sessionRound = 0;
    refineBtn.disabled = true;
    setStatus('已清空搜索');
    resultsEl.innerHTML = '';
  });

  // Refresh freeze stale status after save-state
  const origSaveState = (j2me as any).saveState?.bind(j2me);
  if (origSaveState) {
    (j2me as any).saveState = async (...args: unknown[]) => {
      const result = await origSaveState(...args);
      refreshFreezes();
      return result;
    };
  }

  renderFreezes();

  // ---- 稳定金手指面板 (方案 B) ----

  interface StableEntry {
    id: string;
    label: string;
    kind: string;
    class_name: string;
    field_name: string;
    descriptor: string;
    value_type: string;
    action: 'none' | 'write' | 'freeze';
    value?: number;
    // runtime state (not persisted)
    resolved?: boolean;
    addr?: number;
    error?: string;
  }

  const STABLE_CODE_KEY = () =>
    `cheat_stable_${new URLSearchParams(window.location.search).get('jar') || 'default'}`;

  const loadStableEntries = (): StableEntry[] => {
    try {
      return JSON.parse(localStorage.getItem(STABLE_CODE_KEY()) || '[]');
    } catch {
      return [];
    }
  };
  const saveStableEntries = (entries: StableEntry[]) => {
    localStorage.setItem(
      STABLE_CODE_KEY(),
      JSON.stringify(
        entries.map(({ resolved: _r, addr: _a, error: _e, ...rest }) => rest),
      ),
    );
  };

  let stableEntries: StableEntry[] = loadStableEntries();

  const codeListEl = document.getElementById('cheat-code-list') as HTMLElement;

  const renderStableCodes = () => {
    if (stableEntries.length === 0) {
      codeListEl.innerHTML =
        '<div style="padding:8px 12px;color:#aaa;font-size:12px;">暂无稳定金手指，从搜索结果中点击 📌 添加</div>';
      return;
    }
    const rows = stableEntries.map((e, i) => {
      const statusColor =
        e.resolved === true
          ? '#20c997'
          : e.resolved === false
            ? '#e74c3c'
            : '#aaa';
      const statusText =
        e.resolved === true
          ? `✓ 0x${(e.addr ?? 0).toString(16)}`
          : e.resolved === false
            ? `✗ ${e.error}`
            : '待应用';
      const actionLabel =
        e.action === 'freeze'
          ? '冻结'
          : e.action === 'write'
            ? '写入'
            : '仅定位';
      return `<div style="display:flex;align-items:center;gap:4px;padding:4px 8px;border-bottom:1px solid #f0f0f0;font-size:12px;">
        <span style="flex:1;color:#333;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${e.class_name}.${e.field_name}">${e.label || e.id}</span>
        <span style="background:#f0f0f0;border-radius:3px;padding:1px 4px;font-size:10px;color:#555;">${e.value_type}</span>
        <span style="background:#f0f0f0;border-radius:3px;padding:1px 4px;font-size:10px;color:#555;">${actionLabel}${e.value !== undefined ? `=${e.value}` : ''}</span>
        <span style="color:${statusColor};font-size:10px;flex:0 0 auto;">${statusText}</span>
        <button onclick="cheatCodeEdit(${i})" style="padding:2px 6px;font-size:11px;min-width:auto;background:#667eea;">编辑</button>
        <button onclick="cheatCodeDelete(${i})" style="padding:2px 6px;font-size:11px;min-width:auto;background:#e74c3c;">删除</button>
      </div>`;
    });
    codeListEl.innerHTML = rows.join('');
  };

  const applyStableCodes = () => {
    const jvm = getJvm();
    if (!jvm) {
      setStatus('JVM 未就绪');
      return;
    }
    const results = jvm.cheatCodeResolve(stableEntries as any);
    for (const r of results as Array<{
      id: string;
      ok: boolean;
      addr?: number;
      error?: string;
    }>) {
      const entry = stableEntries.find((e) => e.id === r.id);
      if (!entry) continue;
      entry.resolved = r.ok;
      entry.addr = r.addr;
      entry.error = r.error;
    }
    renderStableCodes();
    const ok = (results as Array<{ ok: boolean }>).filter((r) => r.ok).length;
    setStatus(`稳定金手指：${ok}/${results.length} 条成功应用`);
  };

  (window as any).cheatCodePin = (addr: number, typeTag: number) => {
    const jvm = getJvm();
    if (!jvm) return;
    const desc = jvm.cheatCodeFromCandidate(addr, typeTag);
    if ('error' in desc) {
      alert(`无法生成稳定码：${desc.error}`);
      return;
    }
    const label = prompt('备注标签（如：金币）:', desc.id) ?? '';
    if (label === null) return;
    const actionStr =
      prompt('动作（none/write/freeze）:', 'freeze') ?? 'freeze';
    const action = (
      ['none', 'write', 'freeze'].includes(actionStr) ? actionStr : 'freeze'
    ) as StableEntry['action'];
    let value: number | undefined;
    if (action !== 'none') {
      const v = prompt('冻结/写入值:', '9999');
      if (v !== null && v.trim() !== '') value = Number(v);
    }
    const entry: StableEntry = {
      ...desc,
      label: label || desc.id,
      action,
      value,
    };
    stableEntries.push(entry);
    saveStableEntries(stableEntries);
    renderStableCodes();
    setStatus(`已添加稳定金手指：${entry.label}`);
  };

  (window as any).cheatCodeEdit = (index: number) => {
    const entry = stableEntries[index];
    if (!entry) return;
    const label = prompt('备注标签:', entry.label) ?? entry.label;
    const actionStr =
      prompt('动作（none/write/freeze）:', entry.action) ?? entry.action;
    const action = (
      ['none', 'write', 'freeze'].includes(actionStr) ? actionStr : entry.action
    ) as StableEntry['action'];
    let value = entry.value;
    if (action !== 'none') {
      const v = prompt('值:', String(value ?? ''));
      if (v !== null && v.trim() !== '') value = Number(v);
    }
    stableEntries[index] = {
      ...entry,
      label,
      action,
      value,
      resolved: undefined,
      addr: undefined,
      error: undefined,
    };
    saveStableEntries(stableEntries);
    renderStableCodes();
  };

  (window as any).cheatCodeDelete = (index: number) => {
    stableEntries.splice(index, 1);
    saveStableEntries(stableEntries);
    renderStableCodes();
  };

  document
    .getElementById('cheat-code-apply-btn')
    ?.addEventListener('click', applyStableCodes);

  document
    .getElementById('cheat-code-export-btn')
    ?.addEventListener('click', () => {
      const json = JSON.stringify(
        stableEntries.map(
          ({ resolved: _r, addr: _a, error: _e, ...rest }) => rest,
        ),
        null,
        2,
      );
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `cheat-${STABLE_CODE_KEY()}.json`;
      a.click();
      URL.revokeObjectURL(url);
    });

  document
    .getElementById('cheat-code-import-btn')
    ?.addEventListener('click', () => {
      const text = prompt('粘贴金手指 JSON:');
      if (!text) return;
      try {
        const imported: StableEntry[] = JSON.parse(text);
        if (!Array.isArray(imported)) throw new Error('not an array');
        stableEntries.push(...imported);
        saveStableEntries(stableEntries);
        renderStableCodes();
        setStatus(`已导入 ${imported.length} 条稳定金手指`);
      } catch (e) {
        alert(`导入失败：${e}`);
      }
    });

  document
    .getElementById('cheat-code-clear-btn')
    ?.addEventListener('click', () => {
      if (!confirm('确认清空所有稳定金手指？')) return;
      stableEntries = [];
      saveStableEntries(stableEntries);
      renderStableCodes();
    });

  renderStableCodes();
};

const setupControlButtons = (j2me: J2MEWeb, phone: J2MEPhone) => {
  const pauseBtn = document.getElementById('pause-btn') as HTMLButtonElement;
  const screenshotBtn = document.getElementById(
    'screenshot-btn',
  ) as HTMLButtonElement;
  const fullscreenBtn = document.getElementById(
    'fullscreen-btn',
  ) as HTMLButtonElement;
  const speedSlider = document.getElementById(
    'speed-slider',
  ) as HTMLInputElement;
  const speedLabel = document.getElementById('speed-label');
  const musicFollowSpeedToggle = document.getElementById(
    'music-follow-speed-toggle',
  ) as HTMLInputElement;
  const statusDiv = document.getElementById('status');
  const vmStatusSpan = document.getElementById('vm-status');
  let paused = false;

  const updateStatus = (message: string, type: StatusType) => {
    if (statusDiv) {
      statusDiv.textContent = message;
      statusDiv.className = type;
    }
  };

  const updateVMStatus = (status: string) => {
    if (vmStatusSpan) {
      vmStatusSpan.textContent = status;
    }
  };

  const setPaused = (nextPaused: boolean) => {
    paused = nextPaused;
    if (pauseBtn) {
      pauseBtn.textContent = paused ? '▶️ 继续' : '⏸️ 暂停';
    }
  };

  if (pauseBtn) {
    pauseBtn.addEventListener('click', async () => {
      try {
        if (paused) {
          await j2me.resume();
          setPaused(false);
          updateStatus('已恢复运行', 'success');
          updateVMStatus('运行中');
        } else {
          await j2me.pause();
          setPaused(true);
          updateStatus('已暂停', 'success');
          updateVMStatus('已暂停');
        }
      } catch (error) {
        updateStatus(`${paused ? '恢复' : '暂停'}失败: ${error}`, 'error');
      }
    });
  }

  if (screenshotBtn) {
    screenshotBtn.disabled = false;
    screenshotBtn.addEventListener('click', () => {
      j2me.downloadScreenshot();
      updateStatus('截图已保存', 'success');
    });
  }

  if (fullscreenBtn) {
    fullscreenBtn.disabled = false;
    fullscreenBtn.addEventListener('click', async () => {
      await j2me.toggleFullscreen();
      fullscreenBtn.textContent = j2me.isFullscreen() ? '🗗 退出全屏' : '⛶ 全屏';
    });

    document.addEventListener('fullscreenchange', () => {
      fullscreenBtn.textContent = j2me.isFullscreen() ? '🗗 退出全屏' : '⛶ 全屏';
    });
  }

  const SPEED_STORAGE_KEY = 'j2me-game-speed';
  const MUSIC_FOLLOW_SPEED_STORAGE_KEY = 'j2me-music-follow-speed';

  if (speedSlider) {
    const savedSpeed = localStorage.getItem(SPEED_STORAGE_KEY);
    if (savedSpeed) {
      const speed = Number.parseFloat(savedSpeed);
      if (speed >= 0.5 && speed <= 10) {
        speedSlider.value = String(speed);
        j2me.setSpeed(speed);
        if (speedLabel) speedLabel.textContent = `${speed}x`;
      }
    }

    speedSlider.disabled = false;
    speedSlider.addEventListener('input', () => {
      const speed = Number.parseFloat(speedSlider.value);
      j2me.setSpeed(speed);
      if (speedLabel) speedLabel.textContent = `${speed}x`;
      localStorage.setItem(SPEED_STORAGE_KEY, String(speed));
      updateStatus(`速度已设置为 ${speed}x`, 'success');
    });
  }

  if (musicFollowSpeedToggle) {
    const savedFollowSpeed =
      localStorage.getItem(MUSIC_FOLLOW_SPEED_STORAGE_KEY) === 'true';
    musicFollowSpeedToggle.checked = savedFollowSpeed;
    musicFollowSpeedToggle.disabled = false;
    setMusicFollowSpeed(j2me, savedFollowSpeed);
    musicFollowSpeedToggle.addEventListener('change', () => {
      setMusicFollowSpeed(j2me, musicFollowSpeedToggle.checked);
      localStorage.setItem(
        MUSIC_FOLLOW_SPEED_STORAGE_KEY,
        String(musicFollowSpeedToggle.checked),
      );
      updateStatus(
        musicFollowSpeedToggle.checked ? '声音已跟随变速' : '声音保持原速',
        'success',
      );
    });
  }

  const fpsBtn = document.getElementById('fps-btn') as HTMLButtonElement;
  if (fpsBtn) {
    fpsBtn.disabled = false;
    const updateFpsButtonStyle = () => {
      fpsBtn.style.background = phone.isFpsVisible() ? '#28a745' : '';
    };
    updateFpsButtonStyle();
    fpsBtn.addEventListener('click', () => {
      phone.toggleFps();
      updateFpsButtonStyle();
    });
  }

  // --- Save/Load State Slots ---
  const currentJarName =
    new URLSearchParams(window.location.search).get('jar') || 'unknown';

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes}B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)}KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
  };

  const updateSlotButtons = async (
    slot: number,
    _saveBtn: HTMLButtonElement,
    loadBtn: HTMLButtonElement,
  ) => {
    const info = await getSlotInfo(currentJarName, slot);
    if (info) {
      loadBtn.textContent = `📂 读${slot} (${formatSize(info.size)})`;
    } else {
      loadBtn.textContent = `📂 读${slot}`;
    }
  };

  const slotBusy = new Map<number, boolean>();
  const slotButtons = new Map<
    number,
    { saveBtn: HTMLButtonElement; loadBtn: HTMLButtonElement }
  >();
  const quickSlots = document.createElement('div');
  quickSlots.id = 'fullscreen-quick-slots';
  quickSlots.className = 'fullscreen-quick-slots';
  quickSlots.setAttribute('aria-label', '全屏快速存读档');
  quickSlots.innerHTML = `
    <button data-quick-save-slot="1" title="快速保存 1">存1</button>
    <button data-quick-load-slot="1" title="快速读取 1">读1</button>
    <button data-quick-save-slot="2" title="快速保存 2">存2</button>
    <button data-quick-load-slot="2" title="快速读取 2">读2</button>
    <button data-quick-save-slot="3" title="快速保存 3">存3</button>
    <button data-quick-load-slot="3" title="快速读取 3">读3</button>
  `;
  j2me.getCanvas().parentElement?.appendChild(quickSlots);

  const saveSlot = async (slot: number) => {
    if (slotBusy.get(slot)) return;
    slotBusy.set(slot, true);
    try {
      updateStatus(`存档${slot} 保存中...`, 'loading');
      const data = await j2me.saveState();
      if (!data) {
        updateStatus('存档失败：等待异步任务完成超时', 'error');
        return;
      }
      await saveSnapshot(currentJarName, slot, data);
      updateStatus(
        `存档${slot} 保存成功 (${formatSize(data.byteLength)})`,
        'success',
      );
      const buttons = slotButtons.get(slot);
      if (buttons) {
        updateSlotButtons(slot, buttons.saveBtn, buttons.loadBtn);
      }
    } finally {
      slotBusy.set(slot, false);
    }
  };

  const loadSlot = async (slot: number) => {
    if (slotBusy.get(slot)) return;
    slotBusy.set(slot, true);
    try {
      const data = await loadSnapshot(currentJarName, slot);
      if (!data) {
        updateStatus(`存档${slot} 为空`, 'error');
        return;
      }
      j2me.loadState(data);
      updateStatus(`存档${slot} 加载成功`, 'success');
    } finally {
      slotBusy.set(slot, false);
    }
  };
  (window as any).quickSaveSlot = saveSlot;
  (window as any).quickLoadSlot = loadSlot;

  for (let slot = 1; slot <= 3; slot++) {
    const saveBtn = document.getElementById(
      `save-slot-${slot}`,
    ) as HTMLButtonElement;
    const loadBtn = document.getElementById(
      `load-slot-${slot}`,
    ) as HTMLButtonElement;
    if (!saveBtn || !loadBtn) continue;
    saveBtn.disabled = false;
    loadBtn.disabled = false;
    slotButtons.set(slot, { saveBtn, loadBtn });
    updateSlotButtons(slot, saveBtn, loadBtn);

    saveBtn.addEventListener('click', () => void saveSlot(slot));
    loadBtn.addEventListener('click', () => void loadSlot(slot));
  }

  quickSlots
    .querySelectorAll<HTMLButtonElement>('[data-quick-save-slot]')
    .forEach((button) => {
      button.addEventListener('click', () => {
        const slot = Number(button.dataset.quickSaveSlot);
        if (Number.isFinite(slot)) void saveSlot(slot);
      });
    });

  quickSlots
    .querySelectorAll<HTMLButtonElement>('[data-quick-load-slot]')
    .forEach((button) => {
      button.addEventListener('click', () => {
        const slot = Number(button.dataset.quickLoadSlot);
        if (Number.isFinite(slot)) void loadSlot(slot);
      });
    });

  // 快捷键 ' 读取第一个存档
  document.addEventListener('keydown', async (e) => {
    if (e.key !== "'") return;
    const loadBtn = document.getElementById('load-slot-1') as HTMLButtonElement;
    if (loadBtn) loadBtn.click();
  });

  // --- Cheat Panel ---
  setupCheatPanel(j2me);
};

const setupConsoleKeyHelpers = (j2me: J2MEWeb) => {
  const tap = (keyCode: number, duration = 100) => {
    j2me.input.triggerKeyPressed(keyCode);
    setTimeout(() => {
      j2me.input.triggerKeyReleased(keyCode);
    }, duration);
  };

  const up = () => tap(J2MEKeyCode.KEY_UP);
  const down = () => tap(J2MEKeyCode.KEY_DOWN);
  const left = () => tap(J2MEKeyCode.KEY_LEFT);
  const right = () => tap(J2MEKeyCode.KEY_RIGHT);
  const ok = () => tap(J2MEKeyCode.KEY_FIRE);
  const fire = () => tap(J2MEKeyCode.KEY_FIRE);
  const menu = () => tap(J2MEKeyCode.KEY_SOFT_LEFT);
  const back = () => tap(J2MEKeyCode.KEY_SOFT_RIGHT);

  const n0 = () => tap(J2MEKeyCode.KEY_NUM0);
  const n1 = () => tap(J2MEKeyCode.KEY_NUM1);
  const n2 = () => tap(J2MEKeyCode.KEY_NUM2);
  const n3 = () => tap(J2MEKeyCode.KEY_NUM3);
  const n4 = () => tap(J2MEKeyCode.KEY_NUM4);
  const n5 = () => tap(J2MEKeyCode.KEY_NUM5);
  const n6 = () => tap(J2MEKeyCode.KEY_NUM6);
  const n7 = () => tap(J2MEKeyCode.KEY_NUM7);
  const n8 = () => tap(J2MEKeyCode.KEY_NUM8);
  const n9 = () => tap(J2MEKeyCode.KEY_NUM9);
  const star = () => tap(J2MEKeyCode.KEY_STAR);
  const pound = () => tap(J2MEKeyCode.KEY_POUND);

  (window as any).up = up;
  (window as any).down = down;
  (window as any).left = left;
  (window as any).right = right;
  (window as any).ok = ok;
  (window as any).fire = fire;
  (window as any).menu = menu;
  (window as any).back = back;
  (window as any).n0 = n0;
  (window as any).n1 = n1;
  (window as any).n2 = n2;
  (window as any).n3 = n3;
  (window as any).n4 = n4;
  (window as any).n5 = n5;
  (window as any).n6 = n6;
  (window as any).n7 = n7;
  (window as any).n8 = n8;
  (window as any).n9 = n9;
  (window as any).star = star;
  (window as any).pound = pound;
  (window as any).tap = tap;
  (window as any).k = J2MEKeyCode;

  console.log(
    '%c🎮 J2ME 按键控制台已就绪！',
    'color: #667eea; font-size: 16px; font-weight: bold;',
  );
  console.log('%c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━', 'color: #cbd5e0;');
  console.log(
    '%c快捷函数（直接输入即可）：',
    'color: #48bb78; font-weight: bold; font-size: 13px;',
  );
  console.log('');
  console.log(
    '%c  方向键：%cup()  down()  left()  right()',
    'color: #ed8936; font-weight: bold;',
    'color: #4299e1;',
  );
  console.log(
    '%c  功能键：%cok()  fire()  menu()  back()',
    'color: #ed8936; font-weight: bold;',
    'color: #4299e1;',
  );
  console.log(
    '%c  数字键：%cn0() ~ n9()  star()  pound()',
    'color: #ed8936; font-weight: bold;',
    'color: #4299e1;',
  );
  console.log('');
  console.log(
    '%c高级函数：',
    'color: #48bb78; font-weight: bold; font-size: 13px;',
  );
  console.log(
    '%c  tap(k.KEY_*)    %c- 点击任意按键',
    'color: #4299e1;',
    'color: #718096;',
  );
  console.log(
    '%c  k               %c- 按键码对象（如 k.KEY_UP）',
    'color: #4299e1;',
    'color: #718096;',
  );
  console.log('');
  console.log('%c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━', 'color: #cbd5e0;');
  console.log('%c示例：', 'color: #f6ad55; font-weight: bold;');
  console.log(
    '%c  up()            %c→ 按上键',
    'color: #9f7aea; font-weight: bold;',
    'color: #a0aec0;',
  );
  console.log(
    '%c  ok()            %c→ 按确认键',
    'color: #9f7aea; font-weight: bold;',
    'color: #a0aec0;',
  );
  console.log(
    '%c  n5()            %c→ 按数字5',
    'color: #9f7aea; font-weight: bold;',
    'color: #a0aec0;',
  );
  console.log(
    '%c  tap(k.KEY_UP)   %c→ 使用按键码',
    'color: #9f7aea; font-weight: bold;',
    'color: #a0aec0;',
  );
  console.log('%c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━', 'color: #cbd5e0;');
};

async function main() {
  const phoneSection = document.querySelector('.phone-section') as HTMLElement;
  if (!phoneSection) {
    console.error('Phone section not found');
    return;
  }

  const statusDiv = document.getElementById('status');
  const vmStatusSpan = document.getElementById('vm-status');
  const pauseBtn = document.getElementById('pause-btn') as HTMLButtonElement;

  const updateStatus = (
    message: string,
    type: 'loading' | 'success' | 'error',
  ) => {
    if (statusDiv) {
      statusDiv.textContent = message;
      statusDiv.className = type;
    }
  };

  const updateVMStatus = (status: string) => {
    if (vmStatusSpan) {
      vmStatusSpan.textContent = status;
    }
  };

  const urlParams = new URLSearchParams(window.location.search);
  const midletNumber = urlParams.get('midlet')
    ? Number.parseInt(urlParams.get('midlet')!, 10)
    : undefined;
  const jarId = urlParams.get('jar') || undefined;

  // 获取 info-section 用于显示选择器
  const infoSection = document.querySelector('.info-section') as HTMLElement;

  if (infoSection) {
    const firstCard = infoSection.querySelector('.info-card');
    if (firstCard) {
      const selectorCard = await createJarSelectorCard(jarId);
      infoSection.insertBefore(selectorCard, firstCard);
    }
  }

  if (!jarId) {
    if (vmStatusSpan) {
      vmStatusSpan.textContent = '等待选择';
    }
    return;
  }

  try {
    updateStatus('正在加载 JAR 文件...', 'loading');
    updateVMStatus('加载中');

    const storedJar = await getStoredJar(jarId);
    if (!storedJar) throw new Error('找不到本地 JAR 文件。请重新导入。');
    const jarData = storedJar.data;
    console.log(`Loading local JAR: ${storedJar.name} (${jarData.byteLength} bytes)`);

    updateStatus('正在启动 J2ME...', 'loading');
    console.log('Starting J2ME...');

    const urlParams = new URLSearchParams(window.location.search);
    const fontSizeRaw = urlParams.get('fontSize');
    const fontSize = fontSizeRaw ? Number(fontSizeRaw) : NaN;
    const SPEED_STORAGE_KEY = 'j2me-game-speed';
    const MUSIC_FOLLOW_SPEED_STORAGE_KEY = 'j2me-music-follow-speed';
    const savedSpeed = localStorage.getItem(SPEED_STORAGE_KEY);
    const initialSpeed = savedSpeed ? Number.parseFloat(savedSpeed) : 1;
    const initialMusicFollowSpeed =
      localStorage.getItem(MUSIC_FOLLOW_SPEED_STORAGE_KEY) === 'true';

    const phone = new J2MEPhone({
      container: phoneSection,
      jar: jarData,
      midletId: jarId,
      midletNumber,
      controlButtonHandlers: {
        gamepad: {
          quickSave: (pressed: boolean) => {
            if (!pressed) return;
            const save = (window as any).quickSaveSlot as
              | ((slot: number) => Promise<void>)
              | undefined;
            void save?.(1);
          },
          quickLoad: (pressed: boolean) => {
            if (!pressed) return;
            const load = (window as any).quickLoadSlot as
              | ((slot: number) => Promise<void>)
              | undefined;
            void load?.(1);
          },
        },
      },
      font:
        Number.isFinite(fontSize) && fontSize > 0 ? { fontSize } : undefined,
      onReady: (j2me) => {
        console.log('J2ME started successfully');
        updateStatus('启动成功', 'success');
        updateVMStatus('运行中');

        if ([0.5, 1, 2, 4].includes(initialSpeed)) {
          j2me.setSpeed(initialSpeed);
        }
        setMusicFollowSpeed(j2me, initialMusicFollowSpeed);

        if (pauseBtn) pauseBtn.disabled = false;

        setupControlButtons(j2me, phone);
        (window as any).j2me = j2me;
        (window as any).phone = phone;
        setupConsoleKeyHelpers(j2me);
      },
      onError: (error) => {
        console.error('Failed to start J2ME:', error);
        updateStatus(`启动失败: ${error}`, 'error');
        updateVMStatus('错误');
      },
    });
  } catch (error) {
    console.error('Failed to start J2ME:', error);
    updateStatus(`启动失败: ${error}`, 'error');
    updateVMStatus('错误');
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', main);
} else {
  main();
}
