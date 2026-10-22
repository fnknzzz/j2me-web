export interface StoredFontMetricsEntry {
  height: number;
  baseline: number;
}

export interface StoredFontMetricsState {
  version: number;
  dpr: number;
  probeText: string;
  alphaThreshold: number;
  entries: Record<string, StoredFontMetricsEntry>;
}

export const loadFontMetricsState = (
  storageKey: string,
): StoredFontMetricsState | null => {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<StoredFontMetricsState> | null;
    if (!parsed || typeof parsed !== 'object') {
      return null;
    }
    if (
      typeof parsed.version !== 'number' ||
      typeof parsed.dpr !== 'number' ||
      typeof parsed.probeText !== 'string' ||
      typeof parsed.alphaThreshold !== 'number' ||
      !parsed.entries ||
      typeof parsed.entries !== 'object'
    ) {
      return null;
    }
    return parsed as StoredFontMetricsState;
  } catch {
    return null;
  }
};

export const saveFontMetricsState = (
  storageKey: string,
  state: StoredFontMetricsState,
): void => {
  if (typeof localStorage === 'undefined') {
    return;
  }
  try {
    localStorage.setItem(storageKey, JSON.stringify(state));
  } catch {
    return;
  }
};

export const createEmptyFontMetricsState = (options: {
  version: number;
  dpr: number;
  probeText: string;
  alphaThreshold: number;
}): StoredFontMetricsState => {
  return {
    version: options.version,
    dpr: options.dpr,
    probeText: options.probeText,
    alphaThreshold: options.alphaThreshold,
    entries: {},
  };
};

export const isCompatibleFontMetricsState = (
  state: StoredFontMetricsState,
  options: {
    version: number;
    dpr: number;
    probeText: string;
    alphaThreshold: number;
  },
): boolean => {
  const dprMatches = Math.abs(state.dpr - options.dpr) < 0.01;
  return (
    state.version === options.version &&
    dprMatches &&
    state.probeText === options.probeText &&
    state.alphaThreshold === options.alphaThreshold
  );
};
