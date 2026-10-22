package com.mascotcapsule.micro3d.v3;

/** Small CLDC-safe primitive map used by the MTRA animation parser. */
final class MascotIntMap {
	private int[] keys;
	private int[] values;
	private int size;

	MascotIntMap(int capacity) {
		int actual = capacity < 4 ? 4 : capacity;
		keys = new int[actual];
		values = new int[actual];
	}

	int size() { return size; }
	int keyAt(int index) { return keys[index]; }
	int valueAt(int index) { return values[index]; }
	int get(int key) { return get(key, 0); }
	int get(int key, int defaultValue) {
		for (int i = 0; i < size; i++) if (keys[i] == key) return values[i];
		return defaultValue;
	}
	void put(int key, int value) {
		for (int i = 0; i < size; i++) {
			if (keys[i] == key) { values[i] = value; return; }
		}
		if (size == keys.length) {
			int[] newKeys = new int[size * 2];
			int[] newValues = new int[size * 2];
			System.arraycopy(keys, 0, newKeys, 0, size);
			System.arraycopy(values, 0, newValues, 0, size);
			keys = newKeys;
			values = newValues;
		}
		keys[size] = key;
		values[size++] = value;
	}
}
