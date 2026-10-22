import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G depth, culling, winding and alpha composition board.
 * Panels (4 cols x 3 rows):
 *   Row 0: default winding, reversed winding, CULL_NONE reversed, CULL_FRONT default
 *   Row 1: depth near-over-far, far-rendered-last, depth test off, depth write off
 *   Row 2: alpha blend, alpha add, alpha threshold, intersecting depth/alpha
 */
public class M3GDepthCullingAlphaBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(42, 42, 46);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 3;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int x = (i % cols) * cw;
                int y = (i / cols) * ch;
                g.setColor(92, 92, 98);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }

            panelDefaultWinding(g, 0, 0, cw, ch);
            panelReversedWinding(g, cw, 0, cw, ch);
            panelCullNoneReversed(g, cw * 2, 0, cw, ch);
            panelCullFront(g, cw * 3, 0, cw, ch);

            panelDepthNearOverFar(g, 0, ch, cw, ch);
            panelDepthFarRenderedLast(g, cw, ch, cw, ch);
            panelDepthTestOff(g, cw * 2, ch, cw, ch);
            panelDepthWriteOff(g, cw * 3, ch, cw, ch);

            panelAlphaBlend(g, 0, ch * 2, cw, ch);
            panelAlphaAdd(g, cw, ch * 2, cw, ch);
            panelAlphaThreshold(g, cw * 2, ch * 2, cw, ch);
            panelIntersectingAlphaDepth(g, cw * 3, ch * 2, cw, ch);
        }

        private static void panelDefaultWinding(Graphics g, int x, int y, int w, int h) {
            renderTri(g, x, y, w, h, 0xFFE53935, false, new Appearance(), null);
        }

        private static void panelReversedWinding(Graphics g, int x, int y, int w, int h) {
            renderTri(g, x, y, w, h, 0xFF1E88E5, true, new Appearance(), null);
        }

        private static void panelCullNoneReversed(Graphics g, int x, int y, int w, int h) {
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderTri(g, x, y, w, h, 0xFF43A047, true, app, null);
        }

        private static void panelCullFront(Graphics g, int x, int y, int w, int h) {
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_FRONT);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderTri(g, x, y, w, h, 0xFFFFB300, false, app, null);
        }

        private static void panelDepthNearOverFar(Graphics g, int x, int y, int w, int h) {
            renderDepthPair(g, x, y, w, h, false, new Appearance(), new Appearance());
        }

        private static void panelDepthFarRenderedLast(Graphics g, int x, int y, int w, int h) {
            renderDepthPair(g, x, y, w, h, true, new Appearance(), new Appearance());
        }

        private static void panelDepthTestOff(Graphics g, int x, int y, int w, int h) {
            CompositingMode cm = new CompositingMode();
            cm.setDepthTestEnable(false);
            Appearance off = new Appearance();
            off.setCompositingMode(cm);
            renderDepthPair(g, x, y, w, h, true, new Appearance(), off);
        }

        private static void panelDepthWriteOff(Graphics g, int x, int y, int w, int h) {
            CompositingMode cm = new CompositingMode();
            cm.setDepthWriteEnable(false);
            Appearance noWrite = new Appearance();
            noWrite.setCompositingMode(cm);
            renderDepthTriplet(g, x, y, w, h, noWrite);
        }

        private static void panelAlphaBlend(Graphics g, int x, int y, int w, int h) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderAlphaPair(g, x, y, w, h, app, makeAlphaQuad(0xAAE53935), makeAlphaQuad(0xAA1E88E5));
        }

        private static void panelAlphaAdd(Graphics g, int x, int y, int w, int h) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA_ADD);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderAlphaPair(g, x, y, w, h, app, makeAlphaQuad(0xAA43A047), makeAlphaQuad(0xAA8E24AA));
        }

        private static void panelAlphaThreshold(Graphics g, int x, int y, int w, int h) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            cm.setAlphaThreshold(0.5f);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderAlphaPair(g, x, y, w, h, app, makeAlphaQuad(0x44FB8C00), makeAlphaQuad(0xCC00ACC1));
        }

        private static void panelIntersectingAlphaDepth(Graphics g, int x, int y, int w, int h) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderIntersectingQuads(g, x, y, w, h, app);
        }

        private static void renderDepthPair(Graphics g, int x, int y, int w, int h,
                boolean farLast, Appearance nearApp, Appearance farApp) {
            Camera cam = makeCamera(w, h);
            Transform camT = makeCameraTransform(220);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                Transform far = new Transform();
                far.postTranslate(18, 0, -45);
                Transform near = new Transform();
                near.postTranslate(-18, 0, 35);
                if (farLast) {
                    g3d.render(makeQuad(0xFFE53935), idx, nearApp, near);
                    g3d.render(makeQuad(0xFF1E88E5), idx, farApp, far);
                } else {
                    g3d.render(makeQuad(0xFF1E88E5), idx, farApp, far);
                    g3d.render(makeQuad(0xFFE53935), idx, nearApp, near);
                }
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderDepthTriplet(Graphics g, int x, int y, int w, int h, Appearance firstApp) {
            Camera cam = makeCamera(w, h);
            Transform camT = makeCameraTransform(240);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                Transform a = new Transform();
                a.postTranslate(-20, 0, 25);
                Transform b = new Transform();
                b.postTranslate(0, 0, -5);
                Transform c = new Transform();
                c.postTranslate(20, 0, 25);
                g3d.render(makeQuad(0xFF8E24AA), idx, firstApp, a);
                g3d.render(makeQuad(0xFF43A047), idx, new Appearance(), b);
                g3d.render(makeQuad(0xFFFFB300), idx, new Appearance(), c);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderAlphaPair(Graphics g, int x, int y, int w, int h,
                Appearance app, VertexBuffer left, VertexBuffer right) {
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(defaultCamera(w, h), defaultCameraTransform());
                Transform a = new Transform();
                a.postTranslate(-26, 0, 0);
                Transform b = new Transform();
                b.postTranslate(26, 0, 0);
                g3d.render(left, idx, app, a);
                g3d.render(right, idx, app, b);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderIntersectingQuads(Graphics g, int x, int y, int w, int h, Appearance app) {
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(defaultCamera(w, h), defaultCameraTransform());
                Transform a = new Transform();
                a.postRotate(35, 0, 0, 1);
                Transform b = new Transform();
                b.postRotate(-35, 0, 0, 1);
                g3d.render(makeAlphaQuad(0xAAE53935), idx, app, a);
                g3d.render(makeAlphaQuad(0xAA1E88E5), idx, app, b);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderTri(Graphics g, int x, int y, int w, int h,
                int color, boolean reversed, Appearance app, Transform model) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(defaultCamera(w, h), defaultCameraTransform());
                g3d.render(makeTriangle(color, reversed), new TriangleStripArray(0, new int[]{3}),
                        app, model == null ? new Transform() : model);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Camera makeCamera(int w, int h) {
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) w / h, 1.0f, 1000.0f);
            return cam;
        }

        private static Transform makeCameraTransform(int z) {
            Transform t = new Transform();
            t.postTranslate(0, 0, z);
            return t;
        }

        private static VertexBuffer makeTriangle(int color, boolean reversed) {
            VertexArray pos = new VertexArray(3, 3, 2);
            if (reversed) {
                pos.set(0, 3, new short[]{0, 46, 0, 42, -34, 0, -42, -34, 0});
            } else {
                pos.set(0, 3, new short[]{0, 46, 0, -42, -34, 0, 42, -34, 0});
            }
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return vb;
        }

        private static VertexBuffer makeQuad(int color) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{-48, 38, 0, -48, -38, 0, 48, 38, 0, 48, -38, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return vb;
        }

        private static VertexBuffer makeAlphaQuad(int color) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{-45, 34, 0, -45, -34, 0, 45, 34, 0, 45, -34, 0});
            VertexArray colors = new VertexArray(4, 4, 1);
            byte a = (byte) ((color >> 24) & 0xff);
            byte r = (byte) ((color >> 16) & 0xff);
            byte gg = (byte) ((color >> 8) & 0xff);
            byte b = (byte) (color & 0xff);
            colors.set(0, 4, new byte[]{r, gg, b, a, r, gg, b, a, r, gg, b, a, r, gg, b, a});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colors);
            return vb;
        }

        private static Camera defaultCamera(int w, int h) {
            Camera cam = new Camera();
            cam.setParallel(160.0f, (float) w / h, -200.0f, 200.0f);
            return cam;
        }

        private static Transform defaultCameraTransform() {
            Transform t = new Transform();
            t.postTranslate(0, 0, 200.0f);
            return t;
        }
    }
}
