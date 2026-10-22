import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G texture transform and background board.
 * Panels (4 cols x 3 rows):
 *   Row 0: texture identity, translate, scale, rotate
 *   Row 1: UV scaleBias, wrap repeat, wrap clamp, texture blend color
 *   Row 2: background color clear, image repeat, image crop, color clear disabled
 */
public class M3GTextureBackgroundBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(36, 38, 42);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 3;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int x = (i % cols) * cw;
                int y = (i / cols) * ch;
                g.setColor(84, 86, 92);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }

            panelTextureIdentity(g, 0, 0, cw, ch);
            panelTextureTranslate(g, cw, 0, cw, ch);
            panelTextureScale(g, cw * 2, 0, cw, ch);
            panelTextureRotate(g, cw * 3, 0, cw, ch);

            panelUvScaleBias(g, 0, ch, cw, ch);
            panelWrapRepeat(g, cw, ch, cw, ch);
            panelWrapClamp(g, cw * 2, ch, cw, ch);
            panelTextureBlendColor(g, cw * 3, ch, cw, ch);

            panelBackgroundColor(g, 0, ch * 2, cw, ch);
            panelBackgroundImageRepeat(g, cw, ch * 2, cw, ch);
            panelBackgroundImageCrop(g, cw * 2, ch * 2, cw, ch);
            panelBackgroundColorClearOff(g, cw * 3, ch * 2, cw, ch);
        }

        private static void panelTextureIdentity(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            renderTexturedQuad(g, x, y, w, h, tex, 1.0f, new float[]{0, 0});
        }

        private static void panelTextureTranslate(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            tex.translate(0.35f, 0.15f, 0.0f);
            renderTexturedQuad(g, x, y, w, h, tex, 1.0f, new float[]{0, 0});
        }

        private static void panelTextureScale(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            tex.scale(1.8f, 1.8f, 1.0f);
            renderTexturedQuad(g, x, y, w, h, tex, 1.0f, new float[]{0, 0});
        }

        private static void panelTextureRotate(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            tex.postRotate(35.0f, 0.0f, 0.0f, 1.0f);
            renderTexturedQuad(g, x, y, w, h, tex, 1.0f, new float[]{0, 0});
        }

        private static void panelUvScaleBias(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            renderTexturedQuad(g, x, y, w, h, tex, 0.5f, new float[]{0.25f, 0.25f});
        }

        private static void panelWrapRepeat(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            tex.setWrapping(Texture2D.WRAP_REPEAT, Texture2D.WRAP_REPEAT);
            renderTexturedQuad(g, x, y, w, h, tex, 2.5f, new float[]{0, 0});
        }

        private static void panelWrapClamp(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            tex.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
            renderTexturedQuad(g, x, y, w, h, tex, 2.5f, new float[]{-0.75f, -0.75f});
        }

        private static void panelTextureBlendColor(Graphics g, int x, int y, int w, int h) {
            Texture2D tex = makeBaseTexture();
            tex.setBlending(Texture2D.FUNC_BLEND);
            tex.setBlendColor(0x00FF8800);
            renderTexturedQuad(g, x, y, w, h, tex, 1.0f, new float[]{0, 0});
        }

        private static void panelBackgroundColor(Graphics g, int x, int y, int w, int h) {
            Background bg = new Background();
            bg.setColor(0xFF224488);
            clearAndDrawWhiteTri(g, x, y, w, h, bg);
        }

        private static void panelBackgroundImageRepeat(Graphics g, int x, int y, int w, int h) {
            Background bg = new Background();
            bg.setImage(makeCheckerImage());
            bg.setImageMode(Background.REPEAT, Background.REPEAT);
            bg.setCrop(0, 0, 8, 8);
            clearAndDrawWhiteTri(g, x, y, w, h, bg);
        }

        private static void panelBackgroundImageCrop(Graphics g, int x, int y, int w, int h) {
            Background bg = new Background();
            bg.setImage(makeCheckerImage());
            bg.setImageMode(Background.BORDER, Background.BORDER);
            bg.setCrop(2, 2, 4, 4);
            clearAndDrawWhiteTri(g, x, y, w, h, bg);
        }

        private static void panelBackgroundColorClearOff(Graphics g, int x, int y, int w, int h) {
            g.setColor(80, 30, 30);
            g.fillRect(x + 10, y + 10, w - 20, h - 20);
            Background bg = new Background();
            bg.setColor(0xFF00AA00);
            bg.setColorClearEnable(false);
            clearAndDrawWhiteTri(g, x, y, w, h, bg);
        }

        private static void renderTexturedQuad(Graphics g, int x, int y, int w, int h,
                Texture2D tex, float uvScale, float[] uvBias) {
            Appearance app = new Appearance();
            app.setTexture(0, tex);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                Camera cam = new Camera();
                cam.setPerspective(60.0f, (float) w / h, 1.0f, 500.0f);
                Transform camT = new Transform();
                camT.postTranslate(0, 0, 170);
                g3d.setCamera(cam, camT);
                g3d.render(makeTexturedQuad(uvScale, uvBias), new TriangleStripArray(0, new int[]{4}),
                        app, new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void clearAndDrawWhiteTri(Graphics g, int x, int y, int w, int h, Background bg) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                g3d.clear(bg);
                g3d.setCamera(null, null);
                g3d.render(makeWhiteTriangle(), new TriangleStripArray(0, new int[]{3}),
                        new Appearance(), new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Texture2D makeBaseTexture() {
            Texture2D tex = new Texture2D(makeCheckerImage());
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            tex.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
            tex.setBlending(Texture2D.FUNC_REPLACE);
            return tex;
        }

        private static Image2D makeCheckerImage() {
            int w = 8, h = 8;
            byte[] data = new byte[w * h * 3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = (y * w + x) * 3;
                    boolean a = ((x / 2 + y / 2) & 1) == 0;
                    data[i] = (byte) (a ? 240 : 20);
                    data[i + 1] = (byte) (a ? 50 : 180);
                    data[i + 2] = (byte) (a ? 40 : 230);
                }
            }
            return new Image2D(Image2D.RGB, w, h, data);
        }

        private static VertexBuffer makeTexturedQuad(float uvScale, float[] uvBias) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{-46, 46, 0, -46, -46, 0, 46, 46, 0, 46, -46, 0});
            VertexArray uv = new VertexArray(4, 2, 2);
            uv.set(0, 4, new short[]{0, 0, 0, 100, 100, 0, 100, 100});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setTexCoords(0, uv, 0.01f * uvScale, uvBias);
            vb.setDefaultColor(0xFFFFFFFF);
            return vb;
        }

        private static VertexBuffer makeWhiteTriangle() {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 36, 0, -32, -26, 0, 32, -26, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFFFFFFFF);
            return vb;
        }
    }
}
