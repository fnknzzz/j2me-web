import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Sprite3D;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * Visual regression for Image2D reference switches through M3G objects.
 *
 * Rows:
 *   0: Texture2D.setImage(newImage) on the same Texture2D
 *   1: Sprite3D.setImage(newImage) on the same Sprite3D
 *   2: Background.setImage(newImage) on the same Background
 */
public class M3GImage2DSetImageBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 2;
            int ch = h / 3;

            g.setColor(30, 34, 38);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 6; i++) {
                int x = (i % 2) * cw;
                int y = (i / 2) * ch;
                g.setColor(86, 90, 98);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }

            renderTextureSetImage(g, 0, 0, cw, ch);
            renderSpriteSetImage(g, 0, ch, cw, ch);
            renderBackgroundSetImage(g, 0, ch * 2, cw, ch);
        }

        private static void renderTextureSetImage(Graphics g, int x, int y, int w, int h) {
            Texture2D texture = makeTexture(makeQuadrantsA(4, 4));
            renderTexturedQuad(g, x, y, w, h, texture);

            texture.setImage(makeQuadrantsB(4, 4));
            renderTexturedQuad(g, x + w, y, w, h, texture);
        }

        private static void renderSpriteSetImage(Graphics g, int x, int y, int w, int h) {
            Sprite3D sprite = new Sprite3D(true, makeQuadrantsA(8, 8), new Appearance());
            renderSprite(g, x, y, w, h, sprite);

            sprite.setImage(makeQuadrantsB(8, 8));
            renderSprite(g, x + w, y, w, h, sprite);
        }

        private static void renderBackgroundSetImage(Graphics g, int x, int y, int w, int h) {
            Background background = new Background();
            background.setImage(makeQuadrantsA(8, 8));
            background.setImageMode(Background.REPEAT, Background.REPEAT);
            background.setCrop(0, 0, 8, 8);
            clearBackground(g, x, y, w, h, background);

            background.setImage(makeQuadrantsB(8, 8));
            clearBackground(g, x + w, y, w, h, background);
        }

        private static Texture2D makeTexture(Image2D image) {
            Texture2D texture = new Texture2D(image);
            texture.setBlending(Texture2D.FUNC_REPLACE);
            texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            return texture;
        }

        private static void renderTexturedQuad(Graphics g, int x, int y, int w, int h, Texture2D texture) {
            Appearance appearance = new Appearance();
            appearance.setTexture(0, texture);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                Camera camera = new Camera();
                camera.setPerspective(60.0f, (float) w / (float) h, 1.0f, 500.0f);
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(0.0f, 0.0f, 170.0f);
                g3d.setCamera(camera, cameraTransform);
                g3d.render(makeTexturedQuad(), new TriangleStripArray(0, new int[]{4}),
                        appearance, new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderSprite(Graphics g, int x, int y, int w, int h, Sprite3D sprite) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                Camera camera = new Camera();
                camera.setPerspective(60.0f, (float) w / (float) h, 1.0f, 500.0f);
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(0.0f, 0.0f, 100.0f);
                g3d.setCamera(camera, cameraTransform);
                Transform transform = new Transform();
                transform.postScale(32.0f, 32.0f, 1.0f);
                g3d.render(sprite, transform);
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void clearBackground(Graphics g, int x, int y, int w, int h, Background background) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                g3d.clear(background);
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static VertexBuffer makeTexturedQuad() {
            VertexArray positions = new VertexArray(4, 3, 2);
            positions.set(0, 4, new short[]{
                -70, -70, 0,
                 70, -70, 0,
                -70,  70, 0,
                 70,  70, 0
            });

            VertexArray texCoords = new VertexArray(4, 2, 2);
            texCoords.set(0, 4, new short[]{
                0, 100,
                100, 100,
                0, 0,
                100, 0
            });

            VertexBuffer vertices = new VertexBuffer();
            vertices.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vertices.setTexCoords(0, texCoords, 0.01f, new float[]{0, 0});
            vertices.setDefaultColor(0xFFFFFFFF);
            return vertices;
        }

        private static Image2D makeQuadrantsA(int w, int h) {
            return new Image2D(Image2D.RGB, w, h, makeQuadrants(w, h,
                    230, 45, 35,
                    35, 180, 70,
                    245, 220, 45,
                    40, 120, 230));
        }

        private static Image2D makeQuadrantsB(int w, int h) {
            return new Image2D(Image2D.RGB, w, h, makeQuadrants(w, h,
                    35, 170, 220,
                    235, 85, 170,
                    245, 245, 245,
                    25, 25, 25));
        }

        private static byte[] makeQuadrants(int w, int h,
                int r0, int g0, int b0,
                int r1, int g1, int b1,
                int r2, int g2, int b2,
                int r3, int g3, int b3) {
            byte[] data = new byte[w * h * 3];
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int quad = (yy < h / 2 ? 0 : 2) + (xx < w / 2 ? 0 : 1);
                    int off = (yy * w + xx) * 3;
                    if (quad == 0) {
                        data[off] = (byte) r0;
                        data[off + 1] = (byte) g0;
                        data[off + 2] = (byte) b0;
                    } else if (quad == 1) {
                        data[off] = (byte) r1;
                        data[off + 1] = (byte) g1;
                        data[off + 2] = (byte) b1;
                    } else if (quad == 2) {
                        data[off] = (byte) r2;
                        data[off + 1] = (byte) g2;
                        data[off + 2] = (byte) b2;
                    } else {
                        data[off] = (byte) r3;
                        data[off + 1] = (byte) g3;
                        data[off + 2] = (byte) b3;
                    }
                }
            }
            return data;
        }
    }
}
