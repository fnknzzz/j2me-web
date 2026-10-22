package com.mascotcapsule.micro3d.v3;

/** The small subset of array helpers needed by the imported implementation. */
final class MascotArrays {
	interface Comparator { int compare(Object left, Object right); }
	private MascotArrays() {}
	static void sort(int[] values) { for (int i = 1; i < values.length; i++) { int value = values[i], j = i - 1; while (j >= 0 && values[j] > value) values[j + 1] = values[j--]; values[j + 1] = value; } }
	static int binarySearch(int[] values, int key) { int low = 0, high = values.length - 1; while (low <= high) { int mid = (low + high) >>> 1, value = values[mid]; if (value < key) low = mid + 1; else if (value > key) high = mid - 1; else return mid; } return -low - 1; }
	static void sort(Object[] values, Comparator comparator) { for (int i = 1; i < values.length; i++) { Object value = values[i]; int j = i - 1; while (j >= 0 && comparator.compare(values[j], value) > 0) values[j + 1] = values[j--]; values[j + 1] = value; } }
}
