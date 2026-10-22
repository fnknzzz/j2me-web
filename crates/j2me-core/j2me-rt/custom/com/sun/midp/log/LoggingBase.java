package com.sun.midp.log;

import com.sun.midp.configurator.Constants;

public class LoggingBase {

    public static final int INFORMATION = Constants.LOG_INFORMATION;
    public static final int WARNING = Constants.LOG_WARNING;
    public static final int ERROR = Constants.LOG_ERROR;
    public static final int CRITICAL = Constants.LOG_CRITICAL;
    public static final int DISABLED = Constants.LOG_DISABLED;

    public static void report(int severity, int channelID, String message) {
    }

    public static void trace(Throwable exc, String message) {
    }
}
