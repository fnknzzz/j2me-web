package javax.microedition.m3g;

import javax.microedition.lcdui.Image;

public class Image2D extends Object3D {
    public static final int ALPHA = 96;
    public static final int LUMINANCE = 97;
    public static final int LUMINANCE_ALPHA = 98;
    public static final int RGB = 99;
    public static final int RGBA = 100;

    public Image2D(int format, Object image) {
        super(checkAndCreate(format, image));
    }

    public Image2D(int format, int width, int height, byte[] image) {
        super(checkAndCreate(format, width, height, image));
    }

    public Image2D(int format, int width, int height, byte[] image, byte[] palette) {
        super(checkAndCreate(format, width, height, image, palette));
    }

    public Image2D(int format, int width, int height) {
        super(_ctorSize(Interface.getHandle(), format, width, height));
    }

    Image2D(int handle) {
        super(handle);
    }

    public void set(int x, int y, int width, int height, byte[] image) {
        if (image == null) {
            throw new NullPointerException();
        }
        _set(handle, x, y, width, height, image);
    }

    public boolean isMutable() {
        return _isMutable(handle);
    }

    public int getFormat() {
        return _getFormat(handle);
    }

    public int getWidth() {
        return _getWidth(handle);
    }

    public int getHeight() {
        return _getHeight(handle);
    }

    private static int checkAndCreate(int format, Object image) {
        if (image == null) {
            throw new NullPointerException();
        }
        if (image instanceof javax.microedition.lcdui.Image) {
            javax.microedition.lcdui.Image img = (javax.microedition.lcdui.Image) image;
            int width = img.getWidth();
            int height = img.getHeight();
            int[] pixels = new int[width * height];
            img.getRGB(pixels, 0, width, 0, 0, width, height);
            return _ctorSizePixels(
                    Interface.getHandle(),
                    format,
                    width,
                    height,
                    convertArgbPixels(format, pixels));
        }
        throw new IllegalArgumentException();
    }

    private static int checkAndCreate(int format, int width, int height, byte[] image) {
        if (image == null) {
            throw new NullPointerException();
        }
        checkImageDataLength(format, width, height, image.length);
        return _ctorSizePixels(Interface.getHandle(), format, width, height, image);
    }

    private static int checkAndCreate(int format, int width, int height, byte[] image, byte[] palette) {
        if (image == null || palette == null) {
            throw new NullPointerException();
        }
        if (width > 0 && height > 0) {
            int bytesPerPixel = bytesPerPixel(format);
            if (bytesPerPixel != 0) {
                if (image.length < (long) width * height) {
                    throw new IllegalArgumentException();
                }
                if (palette.length < 256 * bytesPerPixel && palette.length % bytesPerPixel != 0) {
                    throw new IllegalArgumentException();
                }
            }
        }
        return _ctorSizePixelsPalette(Interface.getHandle(), format, width, height, image, palette);
    }

    private static void checkImageDataLength(int format, int width, int height, int length) {
        if (width > 0 && height > 0) {
            int bytesPerPixel = bytesPerPixel(format);
            if (bytesPerPixel != 0 && length < (long) width * height * bytesPerPixel) {
                throw new IllegalArgumentException();
            }
        }
    }

    private static int bytesPerPixel(int format) {
        switch (format) {
            case ALPHA:
            case LUMINANCE:
                return 1;
            case LUMINANCE_ALPHA:
                return 2;
            case RGB:
                return 3;
            case RGBA:
                return 4;
            default:
                return 0;
        }
    }

    private static byte[] convertArgbPixels(int format, int[] pixels) {
        int bytesPerPixel = bytesPerPixel(format);
        if (bytesPerPixel == 0) {
            throw new IllegalArgumentException();
        }

        byte[] result = new byte[pixels.length * bytesPerPixel];
        int output = 0;
        for (int index = 0; index < pixels.length; index++) {
            int argb = pixels[index];
            int alpha = (argb >>> 24) & 0xFF;
            int red = (argb >>> 16) & 0xFF;
            int green = (argb >>> 8) & 0xFF;
            int blue = argb & 0xFF;

            switch (format) {
                case ALPHA:
                    result[output++] = (byte) alpha;
                    break;
                case LUMINANCE:
                    result[output++] = (byte) luminance(red, green, blue);
                    break;
                case LUMINANCE_ALPHA:
                    result[output++] = (byte) luminance(red, green, blue);
                    result[output++] = (byte) alpha;
                    break;
                case RGB:
                    result[output++] = (byte) red;
                    result[output++] = (byte) green;
                    result[output++] = (byte) blue;
                    break;
                case RGBA:
                    result[output++] = (byte) red;
                    result[output++] = (byte) green;
                    result[output++] = (byte) blue;
                    result[output++] = (byte) alpha;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
        return result;
    }

    private static int luminance(int red, int green, int blue) {
        return (0x4CB2 * red + 0x9691 * green + 0x1D3E * blue) >>> 16;
    }

    private native static int _ctorSizePixels(int hInterface, int format, int width, int height, byte[] image);
    private native static int _ctorSizePixelsPalette(int hInterface, int format, int width, int height, byte[] image, byte[] palette);
    private native static int _ctorSize(int hInterface, int format, int width, int height);
    private native static void _set(int handle, int x, int y, int width, int height, byte[] image);
    private native static boolean _isMutable(int handle);
    private native static int _getFormat(int handle);
    private native static int _getWidth(int handle);
    private native static int _getHeight(int handle);
}
