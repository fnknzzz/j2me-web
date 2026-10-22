package com.nokia.mid.ui;

public class DeviceControl {
    public static final int KEYMAT_ALPHANUMERIC = 2;
    public static final int KEYMAT_DEFAULT = 0;
    public static final int KEYMAT_NUMERIC = 1;
    public static final int KEYMAT_OFF = 3;

    public static void setLights(int num, int level) {
        // stub - backlight control not available in browser
    }

    public static void flashLights(long duration) {
        // stub - flash control not available in browser
    }

    public static native void startVibra(int freq, long duration);

    public static native void stopVibra();

    public static int getUserInactivityTime() {
        return 0;
    }

    public static void resetUserInactivityTime() {
        // stub
    }
}
