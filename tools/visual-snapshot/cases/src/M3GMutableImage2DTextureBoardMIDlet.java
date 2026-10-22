import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * Visual regression for mutable Image2D texture cache invalidation.
 *
 * Panels:
 *   0: same mutable texture before a full Image2D.set()
 *   1: same mutable texture after the full Image2D.set()
 *   2: same mutable texture before a partial Image2D.set()
 *   3: same mutable texture after the partial Image2D.set()
 *   4: mutable Image2D after offscreen Background clear
 *   5: mutable Image2D after offscreen Background clear + mesh render
 */
public class M3GMutableImage2DTextureBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 2;
            int ch = h / 3;

            g.setColor(32, 34, 38);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 6; i++) {
                int x = (i % 2) * cw;
                int y = (i / 2) * ch;
                g.setColor(94, 98, 106);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }

            renderFullSetCase(g, 0, 0, cw, ch);
            renderPartialSetCase(g, 0, ch, cw, ch);
            renderImageTargetCase(g, 0, ch * 2, cw, ch);
        }

        private static void renderFullSetCase(Graphics g, int x, int y, int w, int h) {
            Image2D image = new Image2D(Image2D.RGB, 4, 4);
            image.set(0, 0, 4, 4, makeQuadrants(4, 4,
                    230, 45, 35,
                    35, 180, 70,
                    245, 220, 45,
                    40, 120, 230));

            Texture2D texture = makeTexture(image);
            render(g, x, y, w, h, texture);

            image.set(0, 0, 4, 4, makeQuadrants(4, 4,
                    35, 170, 220,
                    235, 85, 170,
                    245, 245, 245,
                    25, 25, 25));
            render(g, x + w, y, w, h, texture);
        }

        private static void renderImageTargetCase(Graphics g, int x, int y, int w, int h) {
            Image2D cleared = new Image2D(Image2D.RGB, 32, 32);
            renderToImage(cleared, false);
            render(g, x, y, w, h, makeTexture(cleared));

            Image2D rendered = new Image2D(Image2D.RGB, 32, 32);
            renderToImage(rendered, true);
            render(g, x + w, y, w, h, makeTexture(rendered));
        }

        private static void renderPartialSetCase(Graphics g, int x, int y, int w, int h) {
            Image2D image = new Image2D(Image2D.RGB, 4, 4);
            image.set(0, 0, 4, 4, makeChecker(4, 4,
                    238, 238, 238,
                    85, 85, 85));

            Texture2D texture = makeTexture(image);
            render(g, x, y, w, h, texture);

            image.set(1, 1, 2, 2, makeQuadrants(2, 2,
                    250, 60, 200,
                    40, 220, 230,
                    255, 130, 30,
                    80, 80, 255));
            render(g, x + w, y, w, h, texture);
        }

        private static Texture2D makeTexture(Image2D image) {
            Texture2D texture = new Texture2D(image);
            texture.setBlending(Texture2D.FUNC_REPLACE);
            texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            return texture;
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

        private static byte[] makeChecker(int w, int h,
                int r0, int g0, int b0,
                int r1, int g1, int b1) {
            byte[] data = new byte[w * h * 3];
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int off = (yy * w + xx) * 3;
                    if (((xx + yy) & 1) == 0) {
                        data[off] = (byte) r0;
                        data[off + 1] = (byte) g0;
                        data[off + 2] = (byte) b0;
                    } else {
                        data[off] = (byte) r1;
                        data[off + 1] = (byte) g1;
                        data[off + 2] = (byte) b1;
                    }
                }
            }
            return data;
        }

        private static void render(Graphics g, int x, int y, int w, int h, Texture2D texture) {
            VertexArray positions = new VertexArray(4, 3, 2);
            positions.set(0, 4, new short[]{
                -80, -80, 0,
                 80, -80, 0,
                -80,  80, 0,
                 80,  80, 0
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

            TriangleStripArray indices = new TriangleStripArray(0, new int[]{4});
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
                cameraTransform.postTranslate(0.0f, 0.0f, 180.0f);
                g3d.setCamera(camera, cameraTransform);
                g3d.render(vertices, indices, appearance, new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderToImage(Image2D image, boolean drawTriangle) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(image);
                g3d.setViewport(0, 0, image.getWidth(), image.getHeight());
                Background background = new Background();
                background.setColor(0x203858);
                background.setColorClearEnable(true);
                background.setDepthClearEnable(true);
                g3d.clear(background);

                if (drawTriangle) {
                    Camera camera = new Camera();
                    camera.setPerspective(60.0f, 1.0f, 1.0f, 500.0f);
                    Transform cameraTransform = new Transform();
                    cameraTransform.postTranslate(0.0f, 0.0f, 150.0f);
                    g3d.setCamera(camera, cameraTransform);
                    Appearance appearance = new Appearance();
                    PolygonMode polygonMode = new PolygonMode();
                    polygonMode.setCulling(PolygonMode.CULL_NONE);
                    appearance.setPolygonMode(polygonMode);
                    g3d.render(makeTriangleVertices(), new TriangleStripArray(0, new int[]{3}),
                            appearance, new Transform());
                }
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static VertexBuffer makeTriangleVertices() {
            VertexArray positions = new VertexArray(3, 3, 2);
            positions.set(0, 3, new short[]{
                  0, -74, 0,
                -76,  64, 0,
                 76,  64, 0
            });

            VertexArray colors = new VertexArray(3, 3, 1);
            colors.set(0, 3, new byte[]{
                (byte) 240, (byte) 70,  (byte) 55,
                (byte) 55,  (byte) 210, (byte) 95,
                (byte) 65,  (byte) 130, (byte) 245
            });

            VertexBuffer vertices = new VertexBuffer();
            vertices.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vertices.setColors(colors);
            return vertices;
        }
    }
}
