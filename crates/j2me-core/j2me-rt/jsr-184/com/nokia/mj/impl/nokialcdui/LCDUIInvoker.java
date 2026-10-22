package com.nokia.mj.impl.nokialcdui;

import javax.microedition.lcdui.Graphics;
import org.eclipse.swt.graphics.Rectangle;

public class LCDUIInvoker {
    public static Object getDisplay() { return null; }
    public static org.eclipse.swt.graphics.Image getEswtImage(javax.microedition.lcdui.Image image) { return null; }
    public static Rectangle startExternalRendering(Graphics g) { return new Rectangle(0, 0, 0, 0); }
    public static void endExternalRendering(Graphics g) {}
    public static org.eclipse.swt.internal.qt.graphics.Image getWindowSurface(Graphics g) { return null; }
}
