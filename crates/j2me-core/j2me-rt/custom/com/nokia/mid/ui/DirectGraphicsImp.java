package com.nokia.mid.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class DirectGraphicsImp implements DirectGraphics {
    private Graphics graphics;
    private int argbColor = 0xFF000000;

    public DirectGraphicsImp(Graphics g) {
        graphics = g;
        int baseColor = g.getColor();
        argbColor = 0xFF000000 | (baseColor & 0x00FFFFFF);
    }

    public Graphics getGraphics() {
        return graphics;
    }

    public void setARGBColor(int argbColor) {
        this.argbColor = argbColor;
        graphics.setColor(argbColor & 0x00FFFFFF);
    }

    public int getAlphaComponent() {
        return (argbColor >>> 24) & 0xFF;
    }

    public int getNativePixelFormat() {
        return TYPE_INT_8888_ARGB;
    }

    public void drawElementBorder(int x, int y, int w, int h, int borderType, boolean withFocus) {
        // stub
    }

    public native void drawImage(Image image, int x, int y, int anchor, int manipulation);

    public native void drawPixels(int pixels[], boolean transparency, int offset, int scanlength,
                                  int x, int y, int width, int height, int manipulation, int format);

    public native void drawPixels(short pixels[], boolean transparency, int offset, int scanlength,
                                  int x, int y, int width, int height, int manipulation, int format);

    public native void drawPixels(byte pixels[], byte transparencyMask[], int offset, int scanlength,
                                  int x, int y, int width, int height, int manipulation, int format);

    public native void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor);

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        int saved = graphics.getColor();
        int color = argbColor != 0 ? (argbColor & 0x00FFFFFF) : (this.argbColor & 0x00FFFFFF);
        graphics.setColor(color);
        graphics.fillTriangle(x1, y1, x2, y2, x3, y3);
        graphics.setColor(saved);
    }

    public native void drawPolygon(int xPoints[], int xOffset, int yPoints[], int yOffset, int nPoints, int argbColor);

    public native void fillPolygon(int xPoints[], int xOffset, int yPoints[], int yOffset, int nPoints, int argbColor);

    public native void getPixels(int pixels[], int offset, int scanlength, int x, int y, int width,
                                 int height, int format);

    public native void getPixels(short pixels[], int offset, int scanlength, int x, int y, int width,
                                 int height, int format);

    public native void getPixels(byte pixels[], byte transparencyMask[], int offset, int scanlength,
                                 int x, int y, int width, int height, int format);
}
