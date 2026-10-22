package com.nokia.mid.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Displayable;

public class DirectUtils {
    public static DirectGraphics getDirectGraphics(Graphics g) {
        return new DirectGraphicsImp(g);
    }

    public static Image createImage(int width, int height, int argb) {
        // Image::createImage(int width, int height)
        // in turn calls
        // ImageDataFactory::createMutableImageData(ImageData data, int width, int height)
        Image image = Image.createImage(width, height);
        setPixels(image, argb);
        return image;
    }

    private static native void setPixels(Image image, int argb);
    private static native void makeMutable(Image image);

    public static Image createImage(byte imageData[], int width, int height) {
        int len = width * height;
        int[] argb = new int[len];
        for (int i = 0; i < len && i < imageData.length; i++) {
            int g = imageData[i] & 0xFF;
            argb[i] = 0xFF000000 | (g << 16) | (g << 8) | g;
        }
        return Image.createRGBImage(argb, width, height, false);
    }

    public static Font getFont(int face, int style, int height) {
        throw new RuntimeException("DirectUtils::getFont(int,int,int) not implemented");
    }

    public static boolean setHeader(Displayable displayable, String headerText, Image headerImage, int headerTextColor, int headerBgColor, int headerDividerColor) {
        throw new RuntimeException("DirectUtils::setHeader(Displayable,String,Image,int,int,int) not implemented");
    }
}
