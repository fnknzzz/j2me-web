package com.jsjvm.midp;

public final class MidletSuiteLauncher {
    private static int[] entryIndices = new int[0];
    private static String[] entryNames = new String[0];
    private static String[] entryClassNames = new String[0];
    private static String[] entryIcons = new String[0];
    private static int pendingLaunchIndex;

    private MidletSuiteLauncher() {}

    public static int entryCount() {
        return entryIndices.length;
    }

    public static int entryIndex(int position) {
        int arrayIndex = position - 1;
        if (arrayIndex < 0 || arrayIndex >= entryIndices.length) {
            return 0;
        }
        return entryIndices[arrayIndex];
    }

    public static String entryName(int position) {
        return valueAt(entryNames, position);
    }

    public static String entryClassName(int position) {
        return valueAt(entryClassNames, position);
    }

    public static String entryIcon(int position) {
        return valueAt(entryIcons, position);
    }

    public static void launch(int position) {
        pendingLaunchIndex = entryIndex(position);
    }

    private static String valueAt(String[] values, int position) {
        int arrayIndex = position - 1;
        if (arrayIndex < 0 || arrayIndex >= values.length) {
            return "";
        }
        String value = values[arrayIndex];
        return value == null ? "" : value;
    }
}
