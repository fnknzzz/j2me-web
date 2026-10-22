import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import com.mascotcapsule.micro3d.v3.Texture;
import com.mascotcapsule.micro3d.v3.Vector3D;
import com.mascotcapsule.micro3d.v3.Light;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/** Visual coverage for the immediate Mascot primitive material path. */
public class MascotPrimitiveMaterialBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        private static final int SCALE = 4096;
        private static final int BASE_COLOR = 0xFF18243A;
        private final Graphics3D mascot = new Graphics3D();
        private final Texture texture = new Texture(createTexture(), true);
        private final Texture sphereTexture = new Texture(createSphereTexture(), false);

        protected void paint(Graphics graphics) {
            int width = getWidth();
            int height = getHeight();
            int cellWidth = width / 3;
            int cellHeight = height / 2;
            graphics.setColor(BASE_COLOR);
            graphics.fillRect(0, 0, width, height);
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < 3; column++) {
                    panel(graphics, column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                }
            }

            try {
                mascot.bind(graphics);
                int centerY0 = cellHeight / 2;
                int centerY1 = cellHeight + cellHeight / 2;
                perFaceColors(mascot, cellWidth / 2, centerY0);
                solid(mascot, cellWidth + cellWidth / 2, centerY0, Graphics3D.PATTR_BLEND_HALF, 0xFFFFFF00);
                overlapping(mascot, cellWidth * 2 + cellWidth / 2, centerY0, Graphics3D.PATTR_BLEND_ADD);
                textured(mascot, cellWidth / 2, centerY1, 0);
                textured(mascot, cellWidth + cellWidth / 2, centerY1, Graphics3D.PATTR_COLORKEY);
                colorMaterial(mascot, cellWidth * 2 + cellWidth / 2 - 42, centerY1);
                sphereMapped(mascot, cellWidth * 2 + cellWidth / 2 + 42, centerY1);
                mascot.flush();
                mascot.release(graphics);
            } catch (Throwable error) {
                try { mascot.release(graphics); } catch (Throwable ignored) {}
                graphics.setColor(0xFF7777);
                graphics.drawString(error.toString(), 4, height - 16, Graphics.TOP | Graphics.LEFT);
            }
        }

        private static void panel(Graphics graphics, int x, int y, int width, int height) {
            graphics.setColor(0x28354F);
            graphics.fillRect(x + 3, y + 3, width - 6, height - 6);
            graphics.setColor(0x5C7199);
            graphics.drawRect(x + 3, y + 3, width - 7, height - 7);
        }

        private static FigureLayout layout(int x, int y) {
            FigureLayout layout = new FigureLayout();
            layout.setCenter(x, y);
            layout.setScale(SCALE, SCALE);
            return layout;
        }

        private static int[] triangle(int size, int z) {
            return new int[] {
                0, size, z,
                -size, -size, z,
                size, -size, z
            };
        }

        private static int[] normals() {
            return new int[] { 0, 0, 4096 };
        }

        private static void solid(
                Graphics3D mascot, int x, int y, int blendMode, int color) {
            int command = Graphics3D.PRIMITVE_TRIANGLES
                    | Graphics3D.PDATA_COLOR_PER_COMMAND
                    | blendMode;
            mascot.renderPrimitives(
                    null, 0, 0, layout(x, y), new Effect3D(), command, 1,
                    triangle(52, 256), new int[0], new int[0], new int[] { color });
        }

        private static void perFaceColors(Graphics3D mascot, int x, int y) {
            int command = Graphics3D.PRIMITVE_TRIANGLES
                    | (2 << 16)
                    | Graphics3D.PDATA_COLOR_PER_FACE;
            mascot.renderPrimitives(
                    null, 0, 0, layout(x, y), new Effect3D(), command, 2,
                    new int[] {
                        -8, 54, 256, -58, -48, 256, -8, -48, 256,
                        8, 54, 256, 8, -48, 256, 58, -48, 256
                    },
                    new int[0], new int[0], new int[] { 0xFFEF596F, 0xFF42B8FF });
        }

        private static void overlapping(Graphics3D mascot, int x, int y, int blendMode) {
            solid(mascot, x - 22, y + 8, blendMode, 0xFFFF3D7F);
            solid(mascot, x + 22, y + 8, blendMode, 0xFF42B8FF);
        }

        private void textured(Graphics3D mascot, int x, int y, int attrs) {
            int command = Graphics3D.PRIMITVE_TRIANGLES
                    | Graphics3D.PDATA_TEXURE_COORD
                    | attrs;
            mascot.renderPrimitives(
                    texture, 0, 0, layout(x, y), new Effect3D(), command, 1,
                    triangle(58, 256), new int[0],
                    new int[] { 0, 0, 7, 0, 0, 7 }, new int[0]);
        }

        private void sphereMapped(Graphics3D mascot, int x, int y) {
            Light light = new Light(new Vector3D(0, 0, -4096), 4096, 1024);
            Effect3D effect =
                    new Effect3D(light, Effect3D.NORMAL_SHADING, true, sphereTexture);
            int command = Graphics3D.PRIMITVE_TRIANGLES
                    | Graphics3D.PDATA_TEXURE_COORD
                    | Graphics3D.PDATA_NORMAL_PER_FACE
                    | Graphics3D.PATTR_LIGHTING
                    | Graphics3D.PATTR_SPHERE_MAP;
            mascot.renderPrimitives(
                    texture, 0, 0, layout(x, y), effect, command, 1,
                    triangle(36, 256), normals(), new int[] { 0, 0, 7, 0, 0, 7 },
                    new int[0]);
        }

        private static void colorMaterial(Graphics3D mascot, int x, int y) {
            Light light = new Light(new Vector3D(0, 0, -4096), 512, 2048);
            Effect3D effect = new Effect3D(light, Effect3D.NORMAL_SHADING, true, null);
            int command = Graphics3D.PRIMITVE_TRIANGLES
                    | Graphics3D.PDATA_COLOR_PER_COMMAND
                    | Graphics3D.PDATA_NORMAL_PER_FACE
                    | Graphics3D.PATTR_LIGHTING;
            mascot.renderPrimitives(
                    null, 0, 0, layout(x, y), effect, command, 1,
                    triangle(36, 256), normals(), new int[0], new int[] { 0xFF67E480 });
        }

        private static byte[] createTexture() {
            int width = 8;
            int height = 8;
            int pixelOffset = 14 + 40 + 4 * 4;
            int stride = 8;
            byte[] bmp = new byte[pixelOffset + stride * height];
            bmp[0] = 'B';
            bmp[1] = 'M';
            putInt(bmp, 2, bmp.length);
            putInt(bmp, 10, pixelOffset);
            putInt(bmp, 14, 40);
            putInt(bmp, 18, width);
            putInt(bmp, 22, height);
            putShort(bmp, 26, 1);
            putShort(bmp, 28, 8);
            putInt(bmp, 34, stride * height);
            putInt(bmp, 46, 4);
            putInt(bmp, 50, 4);
            palette(bmp, 54, 0x000000);
            palette(bmp, 58, 0xF4D34A);
            palette(bmp, 62, 0x45B8E8);
            palette(bmp, 66, 0xE85D88);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int color = ((column / 2) + (row / 2)) % 4;
                    bmp[pixelOffset + row * stride + column] = (byte) color;
                }
            }
            return bmp;
        }

        private static byte[] createSphereTexture() {
            int width = 8;
            int height = 8;
            int pixelOffset = 14 + 40 + 8;
            byte[] bmp = new byte[pixelOffset + width * height];
            bmp[0] = 'B';
            bmp[1] = 'M';
            putInt(bmp, 2, bmp.length);
            putInt(bmp, 10, pixelOffset);
            putInt(bmp, 14, 40);
            putInt(bmp, 18, width);
            putInt(bmp, 22, height);
            putShort(bmp, 26, 1);
            putShort(bmp, 28, 8);
            putInt(bmp, 34, width * height);
            putInt(bmp, 46, 2);
            putInt(bmp, 50, 2);
            palette(bmp, 54, 0x000000);
            palette(bmp, 58, 0x2CFF72);
            for (int index = pixelOffset; index < bmp.length; index++) {
                bmp[index] = 1;
            }
            return bmp;
        }

        private static void palette(byte[] data, int offset, int rgb) {
            data[offset] = (byte) rgb;
            data[offset + 1] = (byte) (rgb >> 8);
            data[offset + 2] = (byte) (rgb >> 16);
            data[offset + 3] = 0;
        }

        private static void putShort(byte[] data, int offset, int value) {
            data[offset] = (byte) value;
            data[offset + 1] = (byte) (value >> 8);
        }

        private static void putInt(byte[] data, int offset, int value) {
            data[offset] = (byte) value;
            data[offset + 1] = (byte) (value >> 8);
            data[offset + 2] = (byte) (value >> 16);
            data[offset + 3] = (byte) (value >> 24);
        }
    }
}
