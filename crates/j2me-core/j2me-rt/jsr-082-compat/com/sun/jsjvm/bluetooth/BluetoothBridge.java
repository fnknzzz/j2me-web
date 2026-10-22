package com.sun.jsjvm.bluetooth;

/** Single coarse-grained native boundary for the JSR-82 compatibility layer. */
public final class BluetoothBridge {
    private BluetoothBridge() {}

    public static native String request(String operation, String payload);
}
