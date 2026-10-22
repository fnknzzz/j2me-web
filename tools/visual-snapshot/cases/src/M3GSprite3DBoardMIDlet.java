import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Sprite3D;
import javax.microedition.m3g.Transform;
import javax.microedition.midlet.MIDlet;

/**
 * M3G Sprite3D rendering board.
 * Panels (4 cols x 2 rows):
 *   Row 0: scaled sprite, unscaled sprite, sprite with crop, sprite translated
 *   Row 1: sprite with appearance, sprite rotated, sprite RGBA, multiple sprites
 */
public class M3GSprite3DBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(40, 40, 60);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 2;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(80, 80, 100);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0
            panelScaledSprite(g, 0, 0, cw, ch);
            panelUnscaledSprite(g, cw, 0, cw, ch);
            panelCroppedSprite(g, cw * 2, 0, cw, ch);
            panelTranslatedSprite(g, cw * 3, 0, cw, ch);

            // Row 1
            panelSpriteWithAppearance(g, 0, ch, cw, ch);
            panelRotatedSprite(g, cw, ch, cw, ch);
            panelRGBASprite(g, cw * 2, ch, cw, ch);
            panelMultipleSprites(g, cw * 3, ch, cw, ch);
        }

        // --- Row 0 ---

        private static void panelScaledSprite(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerImage(8, 8);
            Sprite3D sprite = new Sprite3D(true, img, new Appearance());
            sprite.setScale(4.0f, 4.0f, 1.0f);
            sprite.setTranslation(40.0f, 0.0f, 0.0f);
            renderSprite(g, vx, vy, vw, vh, sprite, scaleTransform(36.0f));
        }

        private static void panelUnscaledSprite(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerImage(8, 8);
            Sprite3D sprite = new Sprite3D(false, img, new Appearance());
            renderSprite(g, vx, vy, vw, vh, sprite, new Transform());
        }

        private static void panelCroppedSprite(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerImage(8, 8);
            Sprite3D sprite = new Sprite3D(true, img, new Appearance());
            sprite.setCrop(2, 2, 4, 4);
            renderSprite(g, vx, vy, vw, vh, sprite, scaleTransform(36.0f));
        }

        private static void panelTranslatedSprite(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerImage(8, 8);
            Sprite3D sprite = new Sprite3D(true, img, new Appearance());
            renderSprite(g, vx, vy, vw, vh, sprite, translatedScaleTransform(16, -10, 36.0f));
        }

        // --- Row 1 ---

        private static void panelSpriteWithAppearance(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeColorImage(8, 8, 0xFF4488CC);
            Sprite3D sprite = new Sprite3D(true, img, new Appearance());
            renderSprite(g, vx, vy, vw, vh, sprite, scaleTransform(36.0f));
        }

        private static void panelRotatedSprite(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerImage(8, 8);
            Sprite3D sprite = new Sprite3D(true, img, new Appearance());
            // Billboard sprites should face camera regardless of rotation
            Transform t = scaleTransform(36.0f);
            t.postRotate(45, 0, 1, 0);
            renderSprite(g, vx, vy, vw, vh, sprite, t);
        }

        private static void panelRGBASprite(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeRGBAImage(8, 8);
            Sprite3D sprite = new Sprite3D(true, img, new Appearance());
            renderSprite(g, vx, vy, vw, vh, sprite, scaleTransform(36.0f));
        }

        private static void panelMultipleSprites(Graphics g, int vx, int vy, int vw, int vh) {
            Group group = new Group();
            group.setScale(3.0f, 3.0f, 1.0f);
            group.setTranslation(40, 0, 0);
            Image2D img1 = makeColorImage(4, 4, 0xFFFF4444);
            Sprite3D s1 = makeScaledSprite(img1, 24.0f);
            s1.setTranslation(-30, 20, 0);
            group.addChild(s1);

            Image2D img2 = makeColorImage(4, 4, 0xFF44FF44);
            Sprite3D s2 = makeScaledSprite(img2, 24.0f);
            s2.setTranslation(30, 20, 0);
            group.addChild(s2);

            Image2D img3 = makeColorImage(4, 4, 0xFF4444FF);
            Sprite3D s3 = makeScaledSprite(img3, 24.0f);
            s3.setTranslation(0, -20, 0);
            group.addChild(s3);

            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 500.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 100);
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(group, new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        // --- Utilities ---

        private static Transform scaleTransform(float size) {
            Transform transform = new Transform();
            transform.postScale(size, size, 1.0f);
            return transform;
        }

        private static Transform translatedScaleTransform(float tx, float ty, float size) {
            Transform transform = new Transform();
            transform.postTranslate(tx, ty, 0.0f);
            transform.postScale(size, size, 1.0f);
            return transform;
        }

        private static Sprite3D makeScaledSprite(Image2D img, float size) {
            Sprite3D sprite = new Sprite3D(true, img, new Appearance());
            sprite.setScale(size, size, 1.0f);
            return sprite;
        }

        private static void renderSprite(Graphics g, int vx, int vy, int vw, int vh,
                Sprite3D sprite, Transform transform) {
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 500.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 100);
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(sprite, transform);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Image2D makeCheckerImage(int w, int h) {
            byte[] data = new byte[w * h * 3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 3;
                    if (((x + y) & 1) == 0) {
                        data[off] = (byte)255; data[off+1] = (byte)200; data[off+2] = 0;
                    } else {
                        data[off] = 0; data[off+1] = 0; data[off+2] = (byte)180;
                    }
                }
            }
            return new Image2D(Image2D.RGB, w, h, data);
        }

        private static Image2D makeColorImage(int w, int h, int argb) {
            byte[] data = new byte[w * h * 3];
            byte r = (byte)((argb >> 16) & 0xFF);
            byte gb = (byte)((argb >> 8) & 0xFF);
            byte b = (byte)(argb & 0xFF);
            for (int i = 0; i < w * h; i++) {
                data[i*3] = r; data[i*3+1] = gb; data[i*3+2] = b;
            }
            return new Image2D(Image2D.RGB, w, h, data);
        }

        private static Image2D makeRGBAImage(int w, int h) {
            byte[] data = new byte[w * h * 4];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 4;
                    data[off] = (byte)255;
                    data[off+1] = (byte)(x * 255 / (w-1));
                    data[off+2] = (byte)(y * 255 / (h-1));
                    data[off+3] = (byte)(128 + (x + y) * 127 / (w + h - 2));
                }
            }
            return new Image2D(Image2D.RGBA, w, h, data);
        }
    }
}
