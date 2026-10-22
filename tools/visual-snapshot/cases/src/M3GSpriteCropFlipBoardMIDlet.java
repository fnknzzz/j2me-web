import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Sprite3D;
import javax.microedition.m3g.Transform;
import javax.microedition.midlet.MIDlet;

/**
 * Sprite3D crop, flip and out-of-bounds transparency board.
 *
 * Panels (3 cols x 2 rows):
 *   0: normal full crop
 *   1: negative crop width mirrors horizontally
 *   2: negative crop height mirrors vertically
 *   3: negative crop width and height mirrors both axes
 *   4: crop extends beyond top-left; out-of-bounds area is transparent
 *   5: crop extends beyond bottom-right; out-of-bounds area is transparent
 */
public class M3GSpriteCropFlipBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 3;
            int ch = h / 2;
            g.setColor(28, 32, 38);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 6; i++) {
                int x = (i % 3) * cw;
                int y = (i / 3) * ch;
                g.setColor(88, 92, 104);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }

            renderPanel(g, 0, 0, cw, ch, 0);
            renderPanel(g, cw, 0, cw, ch, 1);
            renderPanel(g, cw * 2, 0, cw, ch, 2);
            renderPanel(g, 0, ch, cw, ch, 3);
            renderPanel(g, cw, ch, cw, ch, 4);
            renderPanel(g, cw * 2, ch, cw, ch, 5);
        }

        private static void renderPanel(Graphics g, int x, int y, int w, int h, int mode) {
            Sprite3D sprite = new Sprite3D(false, makeAsymmetricImage(), new Appearance());
            if (mode == 1) {
                sprite.setCrop(0, 0, -64, 64);
            } else if (mode == 2) {
                sprite.setCrop(0, 0, 64, -64);
            } else if (mode == 3) {
                sprite.setCrop(0, 0, -64, -64);
            } else if (mode == 4) {
                sprite.setCrop(-24, -24, 64, 64);
            } else if (mode == 5) {
                sprite.setCrop(24, 24, 64, 64);
            }

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                Camera camera = new Camera();
                camera.setPerspective(60.0f, (float) w / (float) h, 1.0f, 500.0f);
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(0.0f, 0.0f, 180.0f);
                g3d.setCamera(camera, cameraTransform);
                g3d.render(sprite, new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Image2D makeAsymmetricImage() {
            int size = 64;
            byte[] pixels = new byte[size * size * 3];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int offset = (y * size + x) * 3;
                    if (x < size / 2 && y < size / 2) {
                        pixels[offset] = (byte) 240;
                        pixels[offset + 1] = 35;
                        pixels[offset + 2] = 35;
                    } else if (x >= size / 2 && y < size / 2) {
                        pixels[offset] = 20;
                        pixels[offset + 1] = (byte) 190;
                        pixels[offset + 2] = (byte) 235;
                    } else if (x < size / 2) {
                        pixels[offset] = 35;
                        pixels[offset + 1] = (byte) 210;
                        pixels[offset + 2] = 70;
                    } else {
                        pixels[offset] = (byte) 245;
                        pixels[offset + 1] = (byte) 190;
                        pixels[offset + 2] = 25;
                    }
                }
            }
            return new Image2D(Image2D.RGB, size, size, pixels);
        }
    }
}
