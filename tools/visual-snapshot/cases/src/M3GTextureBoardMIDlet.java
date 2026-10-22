import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G texture rendering board.
 * Panels (4 cols x 3 rows):
 *   Row 0: checkerboard RGB, RGBA with alpha, luminance texture, luminance_alpha
 *   Row 1: FUNC_REPLACE, FUNC_MODULATE, FUNC_DECAL, FUNC_BLEND
 *   Row 2: wrap REPEAT, wrap CLAMP, filter NEAREST, filter LINEAR
 */
public class M3GTextureBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(60, 60, 60);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 3;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(100, 100, 100);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0: image formats
            panelRGB(g, 0, 0, cw, ch);
            panelRGBA(g, cw, 0, cw, ch);
            panelLuminance(g, cw * 2, 0, cw, ch);
            panelLuminanceAlpha(g, cw * 3, 0, cw, ch);

            // Row 1: blend functions
            panelBlendFunc(g, 0, ch, cw, ch, Texture2D.FUNC_REPLACE);
            panelBlendFunc(g, cw, ch, cw, ch, Texture2D.FUNC_MODULATE);
            panelBlendFunc(g, cw * 2, ch, cw, ch, Texture2D.FUNC_DECAL);
            panelBlendFunc(g, cw * 3, ch, cw, ch, Texture2D.FUNC_BLEND);

            // Row 2: wrapping and filtering
            panelWrapRepeat(g, 0, ch * 2, cw, ch);
            panelWrapClamp(g, cw, ch * 2, cw, ch);
            panelFilterNearest(g, cw * 2, ch * 2, cw, ch);
            panelFilterLinear(g, cw * 3, ch * 2, cw, ch);
        }

        // --- Row 0: Image format variants ---

        private static void panelRGB(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerRGB(4, 4);
            Texture2D tex = new Texture2D(img);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            renderTexturedQuad(g, vx, vy, vw, vh, tex);
        }

        private static void panelRGBA(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerRGBA(4, 4);
            Texture2D tex = new Texture2D(img);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            renderTexturedQuad(g, vx, vy, vw, vh, tex);
        }

        private static void panelLuminance(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeGradientLuminance(4, 4);
            Texture2D tex = new Texture2D(img);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            renderTexturedQuad(g, vx, vy, vw, vh, tex);
        }

        private static void panelLuminanceAlpha(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeGradientLuminanceAlpha(4, 4);
            Texture2D tex = new Texture2D(img);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            renderTexturedQuad(g, vx, vy, vw, vh, tex);
        }

        // --- Row 1: Blend functions ---

        private static void panelBlendFunc(Graphics g, int vx, int vy, int vw, int vh, int func) {
            Image2D img = makeCheckerRGB(4, 4);
            Texture2D tex = new Texture2D(img);
            tex.setBlending(func);
            if (func == Texture2D.FUNC_BLEND) {
                tex.setBlendColor(0x00FF8800);
            }
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            renderTexturedQuad(g, vx, vy, vw, vh, tex);
        }

        // --- Row 2: Wrap / filter ---

        private static void panelWrapRepeat(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerRGB(2, 2);
            Texture2D tex = new Texture2D(img);
            tex.setWrapping(Texture2D.WRAP_REPEAT, Texture2D.WRAP_REPEAT);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            // Use texcoords that go beyond [0,1]
            renderTexturedQuadScaledUV(g, vx, vy, vw, vh, tex, 3.0f);
        }

        private static void panelWrapClamp(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerRGB(2, 2);
            Texture2D tex = new Texture2D(img);
            tex.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            renderTexturedQuadScaledUV(g, vx, vy, vw, vh, tex, 3.0f);
        }

        private static void panelFilterNearest(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerRGB(2, 2);
            Texture2D tex = new Texture2D(img);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            renderTexturedQuad(g, vx, vy, vw, vh, tex);
        }

        private static void panelFilterLinear(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D img = makeCheckerRGB(2, 2);
            Texture2D tex = new Texture2D(img);
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_LINEAR);
            renderTexturedQuad(g, vx, vy, vw, vh, tex);
        }

        // --- Texture creation ---

        private static Image2D makeCheckerRGB(int w, int h) {
            byte[] data = new byte[w * h * 3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 3;
                    if (((x + y) & 1) == 0) {
                        data[off] = (byte)255; data[off + 1] = (byte)255; data[off + 2] = (byte)255;
                    } else {
                        data[off] = (byte)0; data[off + 1] = (byte)0; data[off + 2] = (byte)0;
                    }
                }
            }
            return new Image2D(Image2D.RGB, w, h, data);
        }

        private static Image2D makeCheckerRGBA(int w, int h) {
            byte[] data = new byte[w * h * 4];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 4;
                    if (((x + y) & 1) == 0) {
                        data[off] = (byte)255; data[off + 1] = 0; data[off + 2] = 0;
                        data[off + 3] = (byte)255;
                    } else {
                        data[off] = 0; data[off + 1] = 0; data[off + 2] = (byte)255;
                        data[off + 3] = (byte)128;
                    }
                }
            }
            return new Image2D(Image2D.RGBA, w, h, data);
        }

        private static Image2D makeGradientLuminance(int w, int h) {
            byte[] data = new byte[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    data[y * w + x] = (byte)((x + y) * 255 / (w + h - 2));
                }
            }
            return new Image2D(Image2D.LUMINANCE, w, h, data);
        }

        private static Image2D makeGradientLuminanceAlpha(int w, int h) {
            byte[] data = new byte[w * h * 2];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 2;
                    data[off] = (byte)((x) * 255 / (w - 1));
                    data[off + 1] = (byte)((y) * 255 / (h - 1));
                }
            }
            return new Image2D(Image2D.LUMINANCE_ALPHA, w, h, data);
        }

        // --- Rendering ---

        private static void renderTexturedQuad(Graphics g, int vx, int vy, int vw, int vh, Texture2D tex) {
            renderTexturedQuadScaledUV(g, vx, vy, vw, vh, tex, 1.0f);
        }

        private static void renderTexturedQuadScaledUV(Graphics g, int vx, int vy, int vw, int vh,
                Texture2D tex, float uvScale) {
            // A quad: two triangles as a strip of 4 vertices
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{
                -60, -60, 0,
                 60, -60, 0,
                -60,  60, 0,
                 60,  60, 0
            });

            // Tex coords: short values scaled by scaleBias
            short uvMax = (short)(100 * uvScale);
            VertexArray texCoords = new VertexArray(4, 2, 2);
            texCoords.set(0, 4, new short[]{
                0, uvMax,
                uvMax, uvMax,
                0, 0,
                uvMax, 0
            });

            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setTexCoords(0, texCoords, 0.01f, new float[]{0, 0});
            vb.setDefaultColor(0xFFCCCCCC);

            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});

            Appearance app = new Appearance();
            app.setTexture(0, tex);

            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 500.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 150.0f);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(vb, idx, app, new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
