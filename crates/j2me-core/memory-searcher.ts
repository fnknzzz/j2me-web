/**
 * MemorySearcher — TypeScript wrapper for the Rust cheat engine.
 *
 * Each search scans all 6 value types simultaneously (i8/i16/i32/i64/f32/f64).
 * Candidates carry their detected type so the user can identify what kind of
 * value they found without needing to select a type upfront.
 *
 * Addresses are valid only for the current game session (heap addresses
 * change on restart). All candidate/freeze data lives here in JS.
 *
 * Search mode tags (match Rust SearchMode):
 *   0=exact  1=changed  2=unchanged  3=increased  4=decreased
 *   5=increasedBy  6=decreasedBy
 */

import type { CheatCandidate, CheatFreezeEntry, JvmInstance } from './core';

export type ValueType = 'i8' | 'i16' | 'i32' | 'i64' | 'f32' | 'f64';
export type SearchModeKind =
  | 'exact'
  | 'changed'
  | 'unchanged'
  | 'increased'
  | 'decreased'
  | 'increasedBy'
  | 'decreasedBy';

export interface SearchMode {
  kind: SearchModeKind;
  value?: number | bigint;
}

export interface SearchSession {
  candidates: CheatCandidate[];
  truncated: boolean;
  round: number;
}

export interface FreezeEntry extends CheatFreezeEntry {
  label: string;
}

const VALUE_TYPE_TAG: Record<ValueType, number> = {
  i8: 0,
  i16: 1,
  i32: 2,
  i64: 3,
  f32: 4,
  f64: 5,
};

const SEARCH_MODE_TAG: Record<SearchModeKind, number> = {
  exact: 0,
  changed: 1,
  unchanged: 2,
  increased: 3,
  decreased: 4,
  increasedBy: 5,
  decreasedBy: 6,
};

export class MemorySearcher {
  private jvm: JvmInstance;
  private session: SearchSession | null = null;
  private freezeLabels: Map<string, string> = new Map();

  constructor(jvm: JvmInstance) {
    this.jvm = jvm;
  }

  // ---- search ----

  /** Begin a new search across all value types simultaneously. */
  searchBegin(mode: SearchMode): SearchSession {
    const modeTag = SEARCH_MODE_TAG[mode.kind];
    const numValue = mode.value !== undefined ? Number(mode.value) : 0;
    this.jvm.cheatSearchBegin(modeTag, numValue);
    const raw = this.jvm.cheatGetResults();
    this.session = {
      candidates: raw.addrs,
      truncated: raw.truncated,
      round: 1,
    };
    return { ...this.session };
  }

  /** Refine the candidate list. Each candidate keeps its individual type. */
  searchRefine(mode: SearchMode): SearchSession {
    if (!this.session)
      throw new Error('No active search session. Call searchBegin first.');
    const modeTag = SEARCH_MODE_TAG[mode.kind];
    const numValue = mode.value !== undefined ? Number(mode.value) : 0;
    this.jvm.cheatSearchRefine(modeTag, numValue);
    const raw = this.jvm.cheatGetResults();
    this.session = {
      ...this.session,
      candidates: raw.addrs,
      truncated: raw.truncated,
      round: this.session.round + 1,
    };
    return { ...this.session };
  }

  getSession(): SearchSession | null {
    return this.session ? { ...this.session } : null;
  }

  searchClear(): void {
    this.jvm.cheatSearchClear();
    this.session = null;
  }

  // ---- direct read / write ----

  read(address: number, valueType: ValueType): number | bigint | null {
    return this.jvm.cheatRead(address, VALUE_TYPE_TAG[valueType]);
  }

  write(address: number, valueType: ValueType, value: number | bigint): void {
    this.jvm.cheatWrite(address, VALUE_TYPE_TAG[valueType], value);
  }

  // ---- freeze ----

  freezeAdd(
    address: number,
    valueType: ValueType,
    value: number | bigint,
    label = '',
  ): void {
    this.jvm.cheatFreezeAdd(address, VALUE_TYPE_TAG[valueType], value);
    this.freezeLabels.set(`${address}:${valueType}`, label);
  }

  freezeRemove(address: number, valueType: ValueType): void {
    this.jvm.cheatFreezeRemove(address, VALUE_TYPE_TAG[valueType]);
    this.freezeLabels.delete(`${address}:${valueType}`);
  }

  freezeClear(): void {
    this.jvm.cheatFreezeClear();
    this.freezeLabels.clear();
  }

  freezeSetEnabled(
    address: number,
    valueType: ValueType,
    enabled: boolean,
  ): void {
    this.jvm.cheatFreezeSetEnabled(address, VALUE_TYPE_TAG[valueType], enabled);
  }

  freezeList(): FreezeEntry[] {
    return this.jvm.cheatFreezeList().map((e) => ({
      ...e,
      label: this.freezeLabels.get(`${e.addr}:${e.type}`) ?? '',
    }));
  }

  /** Call after save_state / session restart. Clears search state; freeze entries are marked stale by Rust. */
  onSessionInvalidated(): void {
    this.session = null;
  }
}
