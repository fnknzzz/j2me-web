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
 * M3G polygon mode and compositing mode board.
 * Panels (4 cols x 3 rows):
 *   Row 0: cull back (default), cull front, cull none, winding CW
 *   Row 1: shade flat, shade smooth, cull none two-sided, depth test off (overlap)
 *   Row 2: blend ALPHA, blend ALPHA_ADD, blend MODULATE, alpha threshold
 */
public class M3GAppearanceModeBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(50, 50, 50);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 3;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(90, 90, 90);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0: culling and winding
            panelCullBack(g, 0, 0, cw, ch);
            panelCullFront(g, cw, 0, cw, ch);
            panelCullNone(g, cw * 2, 0, cw, ch);
            panelWindingCW(g, cw * 3, 0, cw, ch);

            // Row 1: shading and depth
            panelShadeFlat(g, 0, ch, cw, ch);
            panelShadeSmooth(g, cw, ch, cw, ch);
            panelCullNoneTwoSided(g, cw * 2, ch, cw, ch);
            panelDepthTestOff(g, cw * 3, ch, cw, ch);

            // Row 2: compositing modes
            panelBlendAlpha(g, 0, ch * 2, cw, ch);
            panelBlendAlphaAdd(g, cw, ch * 2, cw, ch);
            panelBlendModulate(g, cw * 2, ch * 2, cw, ch);
            panelAlphaThreshold(g, cw * 3, ch * 2, cw, ch);
        }

        // --- Row 0: Culling ---

        private static void panelCullBack(Graphics g, int vx, int vy, int vw, int vh) {
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_BACK);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderCube(g, vx, vy, vw, vh, app, 30, 45);
        }

        private static void panelCullFront(Graphics g, int vx, int vy, int vw, int vh) {
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_FRONT);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderCube(g, vx, vy, vw, vh, app, 30, 45);
        }

        private static void panelCullNone(Graphics g, int vx, int vy, int vw, int vh) {
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderCube(g, vx, vy, vw, vh, app, 30, 45);
        }

        private static void panelWindingCW(Graphics g, int vx, int vy, int vw, int vh) {
            PolygonMode pm = new PolygonMode();
            pm.setWinding(PolygonMode.WINDING_CW);
            pm.setCulling(PolygonMode.CULL_BACK);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderCube(g, vx, vy, vw, vh, app, 30, 45);
        }

        // --- Row 1: Shading and depth ---

        private static void panelShadeFlat(Graphics g, int vx, int vy, int vw, int vh) {
            PolygonMode pm = new PolygonMode();
            pm.setShading(PolygonMode.SHADE_FLAT);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderGradientCube(g, vx, vy, vw, vh, app);
        }

        private static void panelShadeSmooth(Graphics g, int vx, int vy, int vw, int vh) {
            PolygonMode pm = new PolygonMode();
            pm.setShading(PolygonMode.SHADE_SMOOTH);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderGradientCube(g, vx, vy, vw, vh, app);
        }

        private static void panelCullNoneTwoSided(Graphics g, int vx, int vy, int vw, int vh) {
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);
            Appearance app = new Appearance();
            app.setPolygonMode(pm);
            renderCube(g, vx, vy, vw, vh, app, 30, 45);
        }

        private static void panelDepthTestOff(Graphics g, int vx, int vy, int vw, int vh) {
            // Render two overlapping cubes with depth test off - back one should show
            CompositingMode cm = new CompositingMode();
            cm.setDepthTestEnable(false);
            cm.setDepthWriteEnable(false);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderOverlappingCubes(g, vx, vy, vw, vh, app);
        }

        // --- Row 2: Compositing ---

        private static void panelBlendAlpha(Graphics g, int vx, int vy, int vw, int vh) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderSemiTransparent(g, vx, vy, vw, vh, app);
        }

        private static void panelBlendAlphaAdd(Graphics g, int vx, int vy, int vw, int vh) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA_ADD);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderSemiTransparent(g, vx, vy, vw, vh, app);
        }

        private static void panelBlendModulate(Graphics g, int vx, int vy, int vw, int vh) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.MODULATE);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderSemiTransparent(g, vx, vy, vw, vh, app);
        }

        private static void panelAlphaThreshold(Graphics g, int vx, int vy, int vw, int vh) {
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            cm.setAlphaThreshold(0.5f);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);
            renderSemiTransparent(g, vx, vy, vw, vh, app);
        }

        // --- Rendering utilities ---

        private static void renderCube(Graphics g, int vx, int vy, int vw, int vh,
                Appearance app, float rx, float ry) {
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(rx, 1.0f, 0.0f, 0.0f);
            model.postRotate(ry, 0.0f, 1.0f, 0.0f);
            render(g, vx, vy, vw, vh, vb, idx, app, model, cam, camT);
        }

        private static void renderGradientCube(Graphics g, int vx, int vy, int vw, int vh, Appearance app) {
            VertexBuffer vb = buildGradientCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(25.0f, 1.0f, 0.0f, 0.0f);
            model.postRotate(35.0f, 0.0f, 1.0f, 0.0f);
            render(g, vx, vy, vw, vh, vb, idx, app, model, cam, camT);
        }

        private static void renderOverlappingCubes(Graphics g, int vx, int vy, int vw, int vh, Appearance app) {
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 250.0f);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                // Back cube
                Transform t1 = new Transform();
                t1.postTranslate(0, 0, -30);
                t1.postRotate(20, 1, 1, 0);
                g3d.render(vb, idx, app, t1);
                // Front cube
                Transform t2 = new Transform();
                t2.postTranslate(20, 10, 30);
                t2.postRotate(-20, 1, 1, 0);
                g3d.render(vb, idx, app, t2);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderSemiTransparent(Graphics g, int vx, int vy, int vw, int vh, Appearance app) {
            VertexBuffer vb = buildAlphaCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(30, 1, 0, 0);
            model.postRotate(30, 0, 1, 0);
            render(g, vx, vy, vw, vh, vb, idx, app, model, cam, camT);
        }

        private static void render(Graphics g, int vx, int vy, int vw, int vh,
                VertexBuffer vb, TriangleStripArray idx, Appearance app,
                Transform model, Camera cam, Transform camT) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(vb, idx, app, model);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static VertexBuffer buildColorCube() {
            short[] positions = new short[]{
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
            };
            byte[] colorData = new byte[]{
                (byte)230, 60, 60, (byte)255, (byte)230, 60, 60, (byte)255,
                (byte)230, 60, 60, (byte)255, (byte)230, 60, 60, (byte)255,
                60, (byte)200, 60, (byte)255, 60, (byte)200, 60, (byte)255,
                60, (byte)200, 60, (byte)255, 60, (byte)200, 60, (byte)255,
                60, 100, (byte)230, (byte)255, 60, 100, (byte)230, (byte)255,
                60, 100, (byte)230, (byte)255, 60, 100, (byte)230, (byte)255,
                (byte)230, (byte)220, 50, (byte)255, (byte)230, (byte)220, 50, (byte)255,
                (byte)230, (byte)220, 50, (byte)255, (byte)230, (byte)220, 50, (byte)255,
                (byte)200, 60, (byte)200, (byte)255, (byte)200, 60, (byte)200, (byte)255,
                (byte)200, 60, (byte)200, (byte)255, (byte)200, 60, (byte)200, (byte)255,
                60, (byte)210, (byte)210, (byte)255, 60, (byte)210, (byte)210, (byte)255,
                60, (byte)210, (byte)210, (byte)255, 60, (byte)210, (byte)210, (byte)255
            };
            VertexArray posArr = new VertexArray(24, 3, 2);
            posArr.set(0, 24, positions);
            VertexArray colArr = new VertexArray(24, 4, 1);
            colArr.set(0, 24, colorData);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colArr);
            return vb;
        }

        private static VertexBuffer buildGradientCube() {
            short[] positions = new short[]{
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
            };
            // Each vertex gets a unique color to show interpolation
            byte[] colorData = new byte[24 * 4];
            for (int i = 0; i < 24; i++) {
                colorData[i * 4]     = (byte)(50 + i * 8);     // R
                colorData[i * 4 + 1] = (byte)(200 - i * 7);    // G
                colorData[i * 4 + 2] = (byte)(100 + i * 5);    // B
                colorData[i * 4 + 3] = (byte)255;              // A
            }
            VertexArray posArr = new VertexArray(24, 3, 2);
            posArr.set(0, 24, positions);
            VertexArray colArr = new VertexArray(24, 4, 1);
            colArr.set(0, 24, colorData);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colArr);
            return vb;
        }

        private static VertexBuffer buildAlphaCube() {
            short[] positions = new short[]{
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
            };
            // Semi-transparent colors
            byte[] colorData = new byte[]{
                (byte)230, 60, 60, (byte)128, (byte)230, 60, 60, (byte)128,
                (byte)230, 60, 60, (byte)128, (byte)230, 60, 60, (byte)128,
                60, (byte)200, 60, (byte)128, 60, (byte)200, 60, (byte)128,
                60, (byte)200, 60, (byte)128, 60, (byte)200, 60, (byte)128,
                60, 100, (byte)230, (byte)128, 60, 100, (byte)230, (byte)128,
                60, 100, (byte)230, (byte)128, 60, 100, (byte)230, (byte)128,
                (byte)230, (byte)220, 50, (byte)128, (byte)230, (byte)220, 50, (byte)128,
                (byte)230, (byte)220, 50, (byte)128, (byte)230, (byte)220, 50, (byte)128,
                (byte)200, 60, (byte)200, (byte)128, (byte)200, 60, (byte)200, (byte)128,
                (byte)200, 60, (byte)200, (byte)128, (byte)200, 60, (byte)200, (byte)128,
                60, (byte)210, (byte)210, (byte)128, 60, (byte)210, (byte)210, (byte)128,
                60, (byte)210, (byte)210, (byte)128, 60, (byte)210, (byte)210, (byte)128
            };
            VertexArray posArr = new VertexArray(24, 3, 2);
            posArr.set(0, 24, positions);
            VertexArray colArr = new VertexArray(24, 4, 1);
            colArr.set(0, 24, colorData);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colArr);
            return vb;
        }
    }
}
