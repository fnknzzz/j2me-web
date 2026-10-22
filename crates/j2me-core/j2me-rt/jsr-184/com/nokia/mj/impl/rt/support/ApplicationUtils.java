package com.nokia.mj.impl.rt.support;

public class ApplicationUtils {
    private static ApplicationUtils instance = new ApplicationUtils();
    public static ApplicationUtils getInstance() { return instance; }
    public void addShutdownListener(ShutdownListener listener) {}
}
