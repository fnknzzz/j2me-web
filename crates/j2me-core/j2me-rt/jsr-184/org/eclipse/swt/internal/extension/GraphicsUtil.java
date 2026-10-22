package org.eclipse.swt.internal.extension;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.qt.graphics.Image;

public class GraphicsUtil {
    public static Rectangle startExternalRendering(GC gc) { return new Rectangle(0, 0, 0, 0); }
    public static Rectangle startExternalRendering(Image image) { return new Rectangle(0, 0, 0, 0); }
    public static Image getWindowSurface(GC gc) { return null; }
    public static void endExternalRendering(GC gc) {}
    public static void endExternalRendering(Image image) {}
}
