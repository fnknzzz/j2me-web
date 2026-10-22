import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G viewport and Graphics state interaction board.
 * Panels (4 cols x 3 rows):
 *   Row 0: viewport origin/size variants
 *   Row 1: Graphics clip and translate affecting M3G output
 *   Row 2: camera reset, background clear clipping, repeated bind/release
 */
public class M3GViewportStateBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(245, 245, 245);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 3;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(205, 205, 205);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            panelViewportInset(g, 0, 0, cw, ch, 0xFFE53935);
            panelViewportTall(g, cw, 0, cw, ch);
            panelViewportWide(g, cw * 2, 0, cw, ch);
            panelViewportCorner(g, cw * 3, 0, cw, ch);

            panelClipLeft(g, 0, ch, cw, ch);
            panelClipBottom(g, cw, ch, cw, ch);
            panelTranslatedGraphics(g, cw * 2, ch, cw, ch);
            panelClipTranslatedGraphics(g, cw * 3, ch, cw, ch);

            panelCameraReset(g, 0, ch * 2, cw, ch);
            panelBackgroundClearClipped(g, cw, ch * 2, cw, ch);
            panelRepeatedBindRelease(g, cw * 2, ch * 2, cw, ch);
            panelViewportScissorProbe(g, cw * 3, ch * 2, cw, ch);
        }

        private static void panelViewportInset(Graphics g, int x, int y, int w, int h,
                int color) {
            int ix = ratio(w, 1, 9);
            int iy = ratio(h, 1, 10);
            int vx = x + ix;
            int vy = y + iy;
            int vw = w - ix * 2;
            int vh = h - iy * 2;
            drawGuide(g, vx, vy, vw, vh);
            renderTriangle(g, vx, vy, vw, vh, color, null, null, null);
        }

        private static void panelViewportTall(Graphics g, int x, int y, int w, int h) {
            int insetY = ratio(h, 1, 12);
            int vx = x + w / 3;
            int vy = y + insetY;
            int vw = w / 3;
            int vh = h - insetY * 2;
            drawGuide(g, vx, vy, vw, vh);
            renderTriangle(g, vx, vy, vw, vh, 0xFF43A047, null, null, null);
        }

        private static void panelViewportWide(Graphics g, int x, int y, int w, int h) {
            int insetX = ratio(w, 1, 12);
            int vx = x + insetX;
            int vy = y + h / 3;
            int vw = w - insetX * 2;
            int vh = h / 3;
            drawGuide(g, vx, vy, vw, vh);
            renderTriangle(g, vx, vy, vw, vh, 0xFF1E88E5, null, null, null);
        }

        private static void panelViewportCorner(Graphics g, int x, int y, int w, int h) {
            int insetX = ratio(w, 1, 12);
            int insetY = ratio(h, 1, 12);
            int vx = x + w / 2;
            int vy = y + h / 2;
            int vw = w / 2 - insetX;
            int vh = h / 2 - insetY;
            drawGuide(g, vx, vy, vw, vh);
            renderTriangle(g, vx, vy, vw, vh, 0xFFFF8F00, null, null, null);
        }

        private static void panelClipLeft(Graphics g, int x, int y, int w, int h) {
            int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
            int padX = ratio(w, 1, 20);
            int padY = ratio(h, 1, 18);
            g.setColor(235, 235, 235);
            g.fillRect(x + padX, y + padY, w - padX * 2, h - padY * 2);
            g.setClip(x + padX, y + padY, w / 2 - padX, h - padY * 2);
            renderQuad(g, x, y, w, h, 0xFFD32F2F, null, null, null);
            g.setClip(ox, oy, ow, oh);
        }

        private static void panelClipBottom(Graphics g, int x, int y, int w, int h) {
            int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
            int padX = ratio(w, 1, 20);
            int padY = ratio(h, 1, 18);
            g.setColor(235, 235, 235);
            g.fillRect(x + padX, y + padY, w - padX * 2, h - padY * 2);
            g.setClip(x + padX, y + h / 2, w - padX * 2, h / 2 - padY);
            renderQuad(g, x, y, w, h, 0xFF1976D2, null, null, null);
            g.setClip(ox, oy, ow, oh);
        }

        private static void panelTranslatedGraphics(Graphics g, int x, int y, int w, int h) {
            int insetX = ratio(w, 1, 9);
            int insetY = ratio(h, 1, 10);
            int tx = ratio(w, 3, 20);
            int ty = -ratio(h, 1, 12);
            g.translate(tx, ty);
            drawGuide(g, x + insetX, y + insetY, w - insetX * 2, h - insetY * 2);
            renderTriangle(g, x + insetX, y + insetY, w - insetX * 2, h - insetY * 2,
                    0xFF7B1FA2, null, null, null);
            g.translate(-tx, -ty);
        }

        private static void panelClipTranslatedGraphics(Graphics g, int x, int y, int w, int h) {
            int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
            int padX = ratio(w, 1, 20);
            int padY = ratio(h, 1, 18);
            int tx = ratio(w, 1, 9);
            int ty = ratio(h, 1, 12);
            g.setColor(235, 235, 235);
            g.fillRect(x + padX, y + padY, w - padX * 2, h - padY * 2);
            g.translate(tx, ty);
            g.setClip(x + padX + ratio(w, 1, 40), y + padY + ratio(h, 1, 36),
                    w / 2, h - padY * 3);
            renderQuad(g, x, y, w, h, 0xFF00897B, null, null, null);
            g.setClip(ox - tx, oy - ty, ow, oh);
            g.translate(-tx, -ty);
            g.setClip(ox, oy, ow, oh);
        }

        private static void panelCameraReset(Graphics g, int x, int y, int w, int h) {
            Transform model = new Transform();
            model.postTranslate(-ratio(w, 1, 5), 0, 0);
            model.postScale(0.75f, 0.75f, 1.0f);
            renderTriangle(g, x, y, w, h, 0xFF3949AB, model,
                    defaultCamera(w, h), defaultCameraTransform());

            Transform noCamera = new Transform();
            noCamera.postTranslate(ratio(w, 1, 4), -ratio(h, 1, 10), 0);
            renderTriangle(g, x, y, w, h, 0xFFE53935, noCamera, null, null);
        }

        private static void panelBackgroundClearClipped(Graphics g, int x, int y, int w, int h) {
            int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
            int insetX = ratio(w, 1, 9);
            int insetY = ratio(h, 1, 10);
            g.setClip(x + insetX, y + insetY, w - insetX * 2, h - insetY * 2);
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                Background bg = new Background();
                bg.setColor(0xFF263238);
                g3d.clear(bg);
                g3d.setCamera(defaultCamera(w, h), defaultCameraTransform());
                g3d.render(makeTriangleVB(0xFFFFFFFF, w, h), new TriangleStripArray(0, new int[]{3}),
                        new Appearance(), new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
                g.setClip(ox, oy, ow, oh);
            }
        }

        private static void panelRepeatedBindRelease(Graphics g, int x, int y, int w, int h) {
            Transform t = new Transform();
            t.postTranslate(-ratio(w, 1, 5), ratio(h, 1, 9), 0);
            renderTriangle(g, x, y, w, h, 0xFFC62828, t, null, null);
            t = new Transform();
            t.postTranslate(ratio(w, 1, 5), ratio(h, 1, 9), 0);
            renderTriangle(g, x, y, w, h, 0xFF2E7D32, t, null, null);
            t = new Transform();
            t.postTranslate(0, -ratio(h, 1, 6), 0);
            renderTriangle(g, x, y, w, h, 0xFF1565C0, t, null, null);
        }

        private static void panelViewportScissorProbe(Graphics g, int x, int y, int w, int h) {
            int vw = ratio(w, 1, 10);
            int vh = h / 3;
            int vx = x + (w - vw) / 2;
            int vy = y + (h - vh) / 2;
            drawGuide(g, vx, vy, vw, vh);
            renderQuad(g, vx, vy, vw, vh, 0xFFAD1457, null, null, null);
        }

        private static void drawGuide(Graphics g, int x, int y, int w, int h) {
            g.setColor(220, 220, 220);
            g.fillRect(x, y, w, h);
            g.setColor(120, 120, 120);
            g.drawRect(x, y, w - 1, h - 1);
        }

        private static void renderTriangle(Graphics g, int vx, int vy, int vw, int vh,
                int color, Transform model, Camera cam, Transform camT) {
            render(g, vx, vy, vw, vh, makeTriangleVB(color, vw, vh),
                    new TriangleStripArray(0, new int[]{3}), model, cam, camT);
        }

        private static void renderQuad(Graphics g, int vx, int vy, int vw, int vh,
                int color, Transform model, Camera cam, Transform camT) {
            render(g, vx, vy, vw, vh, makeQuadVB(color, vw, vh),
                    new TriangleStripArray(0, new int[]{4}), model, cam, camT);
        }

        private static void render(Graphics g, int vx, int vy, int vw, int vh,
                VertexBuffer vb, TriangleStripArray idx,
                Transform model, Camera cam, Transform camT) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                if (cam != null) {
                    g3d.setCamera(cam, camT);
                } else {
                    g3d.setCamera(defaultCamera(vw, vh), defaultCameraTransform());
                }
                g3d.render(vb, idx, new Appearance(), model == null ? new Transform() : model);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static VertexBuffer makeTriangleVB(int color, int width, int height) {
            VertexArray pos = new VertexArray(3, 3, 2);
            int halfW = ratio(width, 7, 30);
            int topY = ratio(height, 7, 30);
            int bottomY = -ratio(height, 1, 6);
            pos.set(0, 3, new short[]{
                    0, toShort(topY), 0,
                    toShort(-halfW), toShort(bottomY), 0,
                    toShort(halfW), toShort(bottomY), 0
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return vb;
        }

        private static VertexBuffer makeQuadVB(int color, int width, int height) {
            VertexArray pos = new VertexArray(4, 3, 2);
            int halfW = ratio(width, 3, 5);
            int halfH = ratio(height, 13, 36);
            pos.set(0, 4, new short[]{
                toShort(-halfW), toShort(halfH), 0,
                toShort(-halfW), toShort(-halfH), 0,
                toShort(halfW), toShort(halfH), 0,
                toShort(halfW), toShort(-halfH), 0
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return vb;
        }

        private static Camera defaultCamera(int width, int height) {
            Camera cam = new Camera();
            cam.setParallel((float) Math.max(1, height),
                    (float) Math.max(1, width) / (float) Math.max(1, height),
                    1.0f, 1000.0f);
            return cam;
        }

        private static Transform defaultCameraTransform() {
            Transform t = new Transform();
            t.postTranslate(0.0f, 0.0f, 200.0f);
            return t;
        }

        private static int ratio(int value, int numerator, int denominator) {
            int result = value * numerator / denominator;
            return result < 1 ? 1 : result;
        }

        private static short toShort(int value) {
            if (value < -32768) {
                return -32768;
            }
            if (value > 32767) {
                return 32767;
            }
            return (short) value;
        }
    }
}
