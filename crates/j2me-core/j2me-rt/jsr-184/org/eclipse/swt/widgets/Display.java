package org.eclipse.swt.widgets;

public class Display {
    public static Display getCurrent() { return null; }
    public static Display getDefault() { return null; }
    public boolean isDisposed() { return false; }
    public Thread getThread() { return Thread.currentThread(); }
    public void syncExec(Runnable runnable) { if (runnable != null) runnable.run(); }
}
