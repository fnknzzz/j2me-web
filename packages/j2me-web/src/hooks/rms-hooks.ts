const RMS_DB_PREFIX = 'j2me-rms-';
const RMS_STORE_NAME = 'rms';
const RMS_DB_VERSION = 2;

const getDB = (midletId: string): Promise<IDBDatabase> => {
  return new Promise((resolve, reject) => {
    const dbName = `${RMS_DB_PREFIX}${midletId}`;
    const request = indexedDB.open(dbName, RMS_DB_VERSION);

    request.onerror = () => reject(request.error);

    request.onsuccess = () => resolve(request.result);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(RMS_STORE_NAME)) {
        db.createObjectStore(RMS_STORE_NAME);
      }
    };
  });
};

export const createRMSHooks = (midletId?: string) => {
  const appId = midletId || 'default';

  return {
    rms: {
      load: async (key: string): Promise<ArrayBuffer | null> => {
        try {
          const db = await getDB(appId);
          return new Promise((resolve, reject) => {
            const tx = db.transaction(RMS_STORE_NAME, 'readonly');
            const store = tx.objectStore(RMS_STORE_NAME);
            const request = store.get(key);

            request.onsuccess = () => {
              resolve(request.result ?? null);
            };
            request.onerror = () => reject(request.error);
          });
        } catch (e) {
          console.error(`[RMS] Failed to load "${key}":`, e);
          return null;
        }
      },

      save: async (key: string, value: ArrayBuffer): Promise<void> => {
        try {
          const db = await getDB(appId);
          return new Promise((resolve, reject) => {
            const tx = db.transaction(RMS_STORE_NAME, 'readwrite');
            const store = tx.objectStore(RMS_STORE_NAME);
            const request = store.put(value, key);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
          });
        } catch (e) {
          console.error(`[RMS] Failed to save "${key}":`, e);
        }
      },

      remove: async (key: string): Promise<void> => {
        try {
          const db = await getDB(appId);
          return new Promise((resolve, reject) => {
            const tx = db.transaction(RMS_STORE_NAME, 'readwrite');
            const store = tx.objectStore(RMS_STORE_NAME);
            const request = store.delete(key);

            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
          });
        } catch (e) {
          console.error(`[RMS] Failed to remove "${key}":`, e);
        }
      },

      keys: async (): Promise<string[]> => {
        try {
          const db = await getDB(appId);
          return new Promise((resolve, reject) => {
            const tx = db.transaction(RMS_STORE_NAME, 'readonly');
            const store = tx.objectStore(RMS_STORE_NAME);
            const request = store.getAllKeys();

            request.onsuccess = () => {
              resolve(request.result as string[]);
            };
            request.onerror = () => reject(request.error);
          });
        } catch (e) {
          console.error('[RMS] Failed to list keys:', e);
          return [];
        }
      },
    },
  };
};
