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
 * M3G immediate-mode rendering board.
 * Panels (4 cols x 4 rows):
 *   Row 0: flat triangle, vertex-colored tri, scaled tri, translated tri
 *   Row 1: perspective cube front, cube rotated 45Y, cube rotated 45X+45Y, cube rotated 30X+60Y
 *   Row 2: parallel projection cube, explicit indices tri, multi-strip, byte positions
 *   Row 3: background clear colors (4 quadrants with different bg colors)
 */
public class M3GImmediateModeBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 4;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(220, 220, 220);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0: basic triangles with no camera / identity transform variants
            panelFlatTriangle(g, 0, 0, cw, ch);
            panelVertexColorTriangle(g, cw, 0, cw, ch);
            panelScaledTriangle(g, cw * 2, 0, cw, ch);
            panelTranslatedTriangle(g, cw * 3, 0, cw, ch);

            // Row 1: perspective cube at various rotations
            panelPerspectiveCube(g, 0, ch, cw, ch, 0, 0);
            panelPerspectiveCube(g, cw, ch, cw, ch, 0, 45);
            panelPerspectiveCube(g, cw * 2, ch, cw, ch, 45, 45);
            panelPerspectiveCube(g, cw * 3, ch, cw, ch, 30, 60);

            // Row 2: parallel projection, explicit indices, multi-strip, byte positions
            panelParallelCube(g, 0, ch * 2, cw, ch);
            panelExplicitIndicesTriangle(g, cw, ch * 2, cw, ch);
            panelMultiStrip(g, cw * 2, ch * 2, cw, ch);
            panelBytePositions(g, cw * 3, ch * 2, cw, ch);

            // Row 3: background clear with different colors
            panelBackgroundClear(g, 0, ch * 3, cw, ch, 0xFF2244AA);
            panelBackgroundClear(g, cw, ch * 3, cw, ch, 0xFF44AA22);
            panelBackgroundClear(g, cw * 2, ch * 3, cw, ch, 0xFFAA2244);
            panelBackgroundClear(g, cw * 3, ch * 3, cw, ch, 0xFF888800);
        }

        // --- Row 0: Basic triangle variants ---

        private static void panelFlatTriangle(Graphics g, int vx, int vy, int vw, int vh) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 50, 0, -40, -40, 0, 40, -40, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFFFF0000);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            renderImmediate(g, vx, vy, vw, vh, vb, idx, panelCamera(vw, vh), panelCameraTransform(), null);
        }

        private static void panelVertexColorTriangle(Graphics g, int vx, int vy, int vw, int vh) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 50, 0, -40, -40, 0, 40, -40, 0});
            VertexArray colors = new VertexArray(3, 4, 1);
            colors.set(0, 3, new byte[]{
                (byte)255, 0, 0, (byte)255,
                0, (byte)255, 0, (byte)255,
                0, 0, (byte)255, (byte)255
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colors);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            renderImmediate(g, vx, vy, vw, vh, vb, idx, panelCamera(vw, vh), panelCameraTransform(), null);
        }

        private static void panelScaledTriangle(Graphics g, int vx, int vy, int vw, int vh) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 50, 0, -40, -40, 0, 40, -40, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFF00CC00);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            Transform t = new Transform();
            t.postScale(0.6f, 0.6f, 1.0f);
            renderImmediate(g, vx, vy, vw, vh, vb, idx, panelCamera(vw, vh), panelCameraTransform(), t);
        }

        private static void panelTranslatedTriangle(Graphics g, int vx, int vy, int vw, int vh) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 50, 0, -40, -40, 0, 40, -40, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFF0000FF);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            Transform t = new Transform();
            t.postTranslate(20.0f, -15.0f, 0.0f);
            renderImmediate(g, vx, vy, vw, vh, vb, idx, panelCamera(vw, vh), panelCameraTransform(), t);
        }

        // --- Row 1: Perspective cube at various rotations ---

        private static void panelPerspectiveCube(Graphics g, int vx, int vy, int vw, int vh, float ax, float ay) {
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            if (ax != 0) model.postRotate(ax, 1.0f, 0.0f, 0.0f);
            if (ay != 0) model.postRotate(ay, 0.0f, 1.0f, 0.0f);
            renderImmediate(g, vx, vy, vw, vh, vb, idx, cam, camT, model);
        }

        // --- Row 2: Parallel, explicit indices, multi-strip, byte positions ---

        private static void panelParallelCube(Graphics g, int vx, int vy, int vw, int vh) {
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setParallel(200.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(30.0f, 1.0f, 0.0f, 0.0f);
            model.postRotate(30.0f, 0.0f, 1.0f, 0.0f);
            renderImmediate(g, vx, vy, vw, vh, vb, idx, cam, camT, model);
        }

        private static void panelExplicitIndicesTriangle(Graphics g, int vx, int vy, int vw, int vh) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{
                0, 60, 0,
                -50, -40, 0,
                50, -40, 0,
                0, -60, 0
            });
            VertexArray colors = new VertexArray(4, 4, 1);
            colors.set(0, 4, new byte[]{
                (byte)255, (byte)255, 0, (byte)255,
                (byte)255, 0, (byte)255, (byte)255,
                0, (byte)255, (byte)255, (byte)255,
                (byte)255, (byte)128, 0, (byte)255
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colors);
            // Use explicit indices: two triangles sharing vertices
            int[] indices = new int[]{0, 1, 2, 1, 3, 2};
            TriangleStripArray idx = new TriangleStripArray(indices, new int[]{3, 3});
            renderImmediate(g, vx, vy, vw, vh, vb, idx, panelCamera(vw, vh), panelCameraTransform(), null);
        }

        private static void panelMultiStrip(Graphics g, int vx, int vy, int vw, int vh) {
            // Two separate triangle strips
            VertexArray pos = new VertexArray(7, 3, 2);
            pos.set(0, 7, new short[]{
                // Strip 1: 4 vertices (2 triangles)
                -50, 50, 0,  -50, -10, 0,  0, 50, 0,  0, -10, 0,
                // Strip 2: 3 vertices (1 triangle)
                10, 40, 0,  30, -40, 0,  50, 40, 0
            });
            VertexArray colors = new VertexArray(7, 4, 1);
            colors.set(0, 7, new byte[]{
                (byte)200, 50, 50, (byte)255,
                (byte)200, 50, 50, (byte)255,
                (byte)200, 50, 50, (byte)255,
                (byte)200, 50, 50, (byte)255,
                50, 50, (byte)200, (byte)255,
                50, 50, (byte)200, (byte)255,
                50, 50, (byte)200, (byte)255
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colors);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 3});
            renderImmediate(g, vx, vy, vw, vh, vb, idx, panelCamera(vw, vh), panelCameraTransform(), null);
        }

        private static void panelBytePositions(Graphics g, int vx, int vy, int vw, int vh) {
            // Using byte component type with scaleBias
            VertexArray pos = new VertexArray(3, 3, 1);
            pos.set(0, 3, new byte[]{
                0, 50, 0,
                -40, -40, 0,
                40, -40, 0
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFFFF8800);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            renderImmediate(g, vx, vy, vw, vh, vb, idx, panelCamera(vw, vh), panelCameraTransform(), null);
        }

        // --- Row 3: Background clear ---

        private static void panelBackgroundClear(Graphics g, int vx, int vy, int vw, int vh, int color) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                Background bg = new Background();
                bg.setColor(color);
                g3d.clear(bg);
                g3d.setCamera(panelCamera(vw, vh), panelCameraTransform());
                // Render a small white triangle on top
                VertexArray pos = new VertexArray(3, 3, 2);
                pos.set(0, 3, new short[]{0, 30, 0, -25, -20, 0, 25, -20, 0});
                VertexBuffer vb = new VertexBuffer();
                vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
                vb.setDefaultColor(0xFFFFFFFF);
                TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
                g3d.render(vb, idx, new Appearance(), new Transform());
            } catch (Throwable t) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        // --- Shared utilities ---

        private static void renderImmediate(Graphics g, int vx, int vy, int vw, int vh,
                VertexBuffer vb, TriangleStripArray idx,
                Camera cam, Transform camT, Transform model) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                if (cam != null) {
                    g3d.setCamera(cam, camT);
                } else {
                    g3d.setCamera(null, null);
                }
                Transform t = model != null ? model : new Transform();
                g3d.render(vb, idx, new Appearance(), t);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Camera panelCamera(int width, int height) {
            Camera cam = new Camera();
            cam.setParallel(160.0f, (float) width / height, 1.0f, 1000.0f);
            return cam;
        }

        private static Transform panelCameraTransform() {
            Transform t = new Transform();
            t.postTranslate(0.0f, 0.0f, 200.0f);
            return t;
        }

        private static VertexBuffer buildColorCube() {
            short[] positions = new short[]{
                // Front face
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                // Back face
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                // Top face
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                // Bottom face
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                // Right face
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                // Left face
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
            };
            byte[] colorData = new byte[]{
                // Front - red
                (byte)230, 60, 60, (byte)255, (byte)230, 60, 60, (byte)255,
                (byte)230, 60, 60, (byte)255, (byte)230, 60, 60, (byte)255,
                // Back - green
                60, (byte)200, 60, (byte)255, 60, (byte)200, 60, (byte)255,
                60, (byte)200, 60, (byte)255, 60, (byte)200, 60, (byte)255,
                // Top - blue
                60, 100, (byte)230, (byte)255, 60, 100, (byte)230, (byte)255,
                60, 100, (byte)230, (byte)255, 60, 100, (byte)230, (byte)255,
                // Bottom - yellow
                (byte)230, (byte)220, 50, (byte)255, (byte)230, (byte)220, 50, (byte)255,
                (byte)230, (byte)220, 50, (byte)255, (byte)230, (byte)220, 50, (byte)255,
                // Right - magenta
                (byte)200, 60, (byte)200, (byte)255, (byte)200, 60, (byte)200, (byte)255,
                (byte)200, 60, (byte)200, (byte)255, (byte)200, 60, (byte)200, (byte)255,
                // Left - cyan
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
    }
}
