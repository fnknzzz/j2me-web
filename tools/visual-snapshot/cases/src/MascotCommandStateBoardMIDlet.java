import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import com.mascotcapsule.micro3d.v3.Texture;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/** Visual coverage for Mascot command-list state transitions. */
public class MascotCommandStateBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        private static final int SCALE = 4096;
        private final Graphics3D mascot = new Graphics3D();
        private final Texture yellowTexture = new Texture(createTexture(0xF0C83E), true);
        private final Texture cyanTexture = new Texture(createTexture(0x35BFEA), true);

        protected void paint(Graphics graphics) {
            int width = getWidth();
            int height = getHeight();
            int cellWidth = width / 2;
            int cellHeight = height / 2;
            graphics.setColor(0x18243A);
            graphics.fillRect(0, 0, width, height);
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < 2; column++) {
                    panel(graphics, column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                }
            }

            try {
                mascot.bind(graphics);
                drawPanel(
                        graphics, "texture-index",
                        new Texture[] { yellowTexture, cyanTexture },
                        cellWidth / 2, cellHeight / 2, textureIndexCommands(),
                        0, 0);
                drawPanel(
                        graphics, "command-light",
                        new Texture[] { yellowTexture },
                        cellWidth + cellWidth / 2, cellHeight / 2, commandLightCommands(),
                        cellWidth, 0);
                drawPanel(
                        graphics, "toon-threshold",
                        new Texture[] { cyanTexture },
                        cellWidth / 2, cellHeight + cellHeight / 2, toonCommands(),
                        0, cellHeight);
                drawPanel(
                        graphics, "point-sprite",
                        new Texture[] { yellowTexture, yellowTexture },
                        cellWidth + cellWidth / 2, cellHeight + cellHeight / 2, pointSpriteCommands(),
                        cellWidth, cellHeight);
                mascot.release(graphics);
            } catch (Throwable error) {
                error.printStackTrace();
                try { mascot.release(graphics); } catch (Throwable ignored) {}
                graphics.setColor(0xFF7777);
                graphics.drawString(error.toString(), 4, height - 16, Graphics.TOP | Graphics.LEFT);
            }
        }

        private void drawPanel(
                Graphics graphics, String stage, Texture[] textures, int centerX, int centerY,
                int[] commands, int panelX, int panelY) {
            try {
                mascot.drawCommandList(
                        textures, 0, 0, baseLayout(centerX, centerY),
                        new Effect3D(), commands);
                mascot.flush();
            } catch (Throwable error) {
                error.printStackTrace();
                graphics.setColor(0xFF7777);
                graphics.drawString(stage + ": " + error.toString(),
                        panelX + 8, panelY + 8, Graphics.TOP | Graphics.LEFT);
            }
        }

        private static FigureLayout baseLayout(int x, int y) {
            FigureLayout layout = new FigureLayout();
            layout.setCenter(x, y);
            layout.setScale(SCALE, SCALE);
            return layout;
        }

        private static int[] textureIndexCommands() {
            return new int[] {
                Graphics3D.COMMAND_LIST_VERSION_1_0,
                Graphics3D.COMMAND_TEXTURE_INDEX | 1,
                texturedTriangleCommand(),
                0, 60, 256, -58, -46, 256, 58, -46, 256,
                0, 0, 7, 0, 0, 7,
                Graphics3D.COMMAND_END
            };
        }

        private static int[] commandLightCommands() {
            return new int[] {
                Graphics3D.COMMAND_LIST_VERSION_1_0,
                Graphics3D.COMMAND_ATTRIBUTE | Graphics3D.ENV_ATTR_LIGHTING,
                Graphics3D.COMMAND_AMBIENT_LIGHT, 768,
                Graphics3D.COMMAND_DIRECTION_LIGHT, 0, 0, -4096, 4096,
                litTexturedTrianglesCommand(),
                -68, 52, 256, -126, -52, 256, -10, -52, 256,
                10, 52, 256, -48, -52, 256, 68, -52, 256,
                0, 0, 4096, 0, 0, -4096,
                0, 0, 7, 0, 0, 7, 0, 0, 7, 0, 0, 7,
                Graphics3D.COMMAND_END
            };
        }

        private static int[] toonCommands() {
            return new int[] {
                Graphics3D.COMMAND_LIST_VERSION_1_0,
                Graphics3D.COMMAND_ATTRIBUTE
                        | Graphics3D.ENV_ATTR_LIGHTING
                        | Graphics3D.ENV_ATTR_TOON_SHADING,
                Graphics3D.COMMAND_AMBIENT_LIGHT, 1024,
                Graphics3D.COMMAND_DIRECTION_LIGHT, 0, 0, -4096, 4096,
                Graphics3D.COMMAND_THRESHOLD, 128, 255, 64,
                litTexturedTrianglesCommand(),
                -68, 52, 256, -126, -52, 256, -10, -52, 256,
                10, 52, 256, -48, -52, 256, 68, -52, 256,
                0, 0, 4096, 0, 0, -4096,
                0, 0, 7, 0, 0, 7, 0, 0, 7, 0, 0, 7,
                Graphics3D.COMMAND_END
            };
        }

        private static int[] pointSpriteCommands() {
            return new int[] {
                Graphics3D.COMMAND_LIST_VERSION_1_0,
                Graphics3D.COMMAND_TEXTURE_INDEX | 1,
                Graphics3D.PRIMITVE_POINT_SPRITES
                        | (2 << 16)
                        | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_VERTEX,
                -46, 0, 256, 46, 0, 256,
                68, 46, 0, 0, 0, 7, 7,
                Graphics3D.POINT_SPRITE_LOCAL_SIZE | Graphics3D.POINT_SPRITE_NO_PERS,
                48, 68, 512, 0, 0, 7, 7,
                Graphics3D.POINT_SPRITE_PIXEL_SIZE | Graphics3D.POINT_SPRITE_NO_PERS,
                Graphics3D.COMMAND_END
            };
        }

        private static int texturedTriangleCommand() {
            return Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_TEXURE_COORD;
        }

        private static int litTexturedTrianglesCommand() {
            return Graphics3D.PRIMITVE_TRIANGLES
                    | (2 << 16)
                    | Graphics3D.PDATA_NORMAL_PER_FACE
                    | Graphics3D.PDATA_TEXURE_COORD
                    | Graphics3D.PATTR_LIGHTING;
        }

        private static void panel(Graphics graphics, int x, int y, int width, int height) {
            graphics.setColor(0x28354F);
            graphics.fillRect(x + 3, y + 3, width - 6, height - 6);
            graphics.setColor(0x5C7199);
            graphics.drawRect(x + 3, y + 3, width - 7, height - 7);
        }

        private static byte[] createTexture(int rgb) {
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
            palette(bmp, 58, rgb);
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
