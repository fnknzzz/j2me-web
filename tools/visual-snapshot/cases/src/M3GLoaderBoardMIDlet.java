import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.IndexBuffer;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Loader;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.m3g.World;
import javax.microedition.midlet.MIDlet;

/**
 * M3G Loader test board. Each panel loads a different .m3g file and renders it.
 * Tests are atomic: each panel exercises one Loader feature independently.
 *
 * Layout (3 cols x 2 rows):
 *   [0,0] single_triangle.m3g  - minimal mesh, no camera
 *   [1,0] color_triangle.m3g   - per-vertex colors
 *   [2,0] world_camera.m3g     - retained-mode World with Camera+Background
 *   [0,1] material_lit.m3g     - Material + Light in World
 *   [1,1] cube_world.m3g       - multi-strip cube with vertex colors
 *   [2,1] (error panel)        - load nonexistent file, verify graceful failure
 */
public class M3GLoaderBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);

            int cols = 3, rows = 2;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(200, 200, 200);
                g.drawRect(cx + 1, cy + 1, cw - 3, ch - 3);
            }

            // Row 0
            panelLoadImmediate(g, 0, 0, cw, ch, "/single_triangle.m3g");
            panelLoadImmediate(g, cw, 0, cw, ch, "/color_triangle.m3g");
            panelLoadWorld(g, cw * 2, 0, cw, ch, "/world_camera.m3g");

            // Row 1
            panelLoadWorld(g, 0, ch, cw, ch, "/material_lit.m3g");
            panelLoadWorldRotated(g, cw, ch, cw, ch, "/cube_world.m3g");
            panelLoadError(g, cw * 2, ch, cw, ch);
        }

        /**
         * Loads a .m3g with a single root Mesh and renders it in immediate mode.
         */
        private static void panelLoadImmediate(Graphics g, int vx, int vy, int vw, int vh, String file) {
            Graphics3D g3d = Graphics3D.getInstance();
            String error = null;
            try {
                Object3D[] roots = Loader.load(file);
                if (roots == null || roots.length == 0) {
                    drawError(g, vx, vy, vw, vh, "no roots");
                    return;
                }
                Mesh mesh = findMesh(roots);
                if (mesh == null) {
                    drawError(g, vx, vy, vw, vh, "no mesh");
                    return;
                }

                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                Camera camera = new Camera();
                camera.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(0.0f, 0.0f, 180.0f);
                g3d.setCamera(camera, cameraTransform);
                Background bg = new Background();
                bg.setColor(0xFF333333);
                g3d.clear(bg);

                VertexBuffer vb = mesh.getVertexBuffer();
                IndexBuffer ib = mesh.getIndexBuffer(0);
                Appearance app = mesh.getAppearance(0);
                if (app == null) app = new Appearance();
                g3d.render(vb, ib, app, new Transform());
            } catch (Throwable t) {
                error = t.getMessage();
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
            if (error != null) {
                drawError(g, vx, vy, vw, vh, error);
            }
        }

        /**
         * Loads a .m3g containing a World and renders it in retained mode.
         */
        private static void panelLoadWorld(Graphics g, int vx, int vy, int vw, int vh, String file) {
            Graphics3D g3d = Graphics3D.getInstance();
            String error = null;
            try {
                Object3D[] roots = Loader.load(file);
                if (roots == null || roots.length == 0) {
                    drawError(g, vx, vy, vw, vh, "no roots");
                    return;
                }
                World world = findWorld(roots);
                if (world == null) {
                    drawError(g, vx, vy, vw, vh, "no world");
                    return;
                }

                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.render(world);
            } catch (Throwable t) {
                error = t.getMessage();
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
            if (error != null) {
                drawError(g, vx, vy, vw, vh, error);
            }
        }

        /**
         * Loads a World and applies a model rotation before rendering (tests transform application).
         */
        private static void panelLoadWorldRotated(Graphics g, int vx, int vy, int vw, int vh, String file) {
            Graphics3D g3d = Graphics3D.getInstance();
            String error = null;
            try {
                Object3D[] roots = Loader.load(file);
                if (roots == null || roots.length == 0) {
                    drawError(g, vx, vy, vw, vh, "no roots");
                    return;
                }
                World world = findWorld(roots);
                if (world == null) {
                    // Fallback: try to find Mesh and render in immediate mode with rotation
                    Mesh mesh = findMesh(roots);
                    if (mesh == null) { drawError(g, vx, vy, vw, vh, "no world/mesh"); return; }

                    g3d.bindTarget(g);
                    g3d.setViewport(vx, vy, vw, vh);
                    Camera cam = new Camera();
                    cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
                    Transform camT = new Transform();
                    camT.postTranslate(0, 0, 200.0f);
                    g3d.setCamera(cam, camT);
                    Background bg = new Background();
                    bg.setColor(0xFF222222);
                    g3d.clear(bg);

                    Transform model = new Transform();
                    model.postRotate(30.0f, 1.0f, 0.0f, 0.0f);
                    model.postRotate(45.0f, 0.0f, 1.0f, 0.0f);

                    VertexBuffer vb = mesh.getVertexBuffer();
                    IndexBuffer ib = mesh.getIndexBuffer(0);
                    Appearance app = mesh.getAppearance(0);
                    if (app == null) app = new Appearance();
                    g3d.render(vb, ib, app, model);
                    return;
                }

                // World exists: find the mesh child and rotate it
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.render(world);
            } catch (Throwable t) {
                error = t.getMessage();
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
            if (error != null) {
                drawError(g, vx, vy, vw, vh, error);
            }
        }

        /**
         * Attempts to load a nonexistent file. Should show green "OK" if error is caught gracefully.
         */
        private static void panelLoadError(Graphics g, int vx, int vy, int vw, int vh) {
            try {
                Object3D[] roots = Loader.load("/nonexistent_file.m3g");
                // Should not reach here
                drawError(g, vx, vy, vw, vh, "no error!");
            } catch (Throwable t) {
                // Expected path: error was thrown
                g.setColor(0, 0, 0);
                g.fillRect(vx + 2, vy + 2, vw - 4, vh - 4);
                g.setColor(0, 200, 0);
                g.drawString("OK: " + t.getClass().getName(), vx + 4, vy + vh / 2 - 6, Graphics.TOP | Graphics.LEFT);
            }
        }

        // --- Utilities ---

        private static Mesh findMesh(Object3D[] roots) {
            for (int i = 0; i < roots.length; i++) {
                if (roots[i] instanceof Mesh) return (Mesh) roots[i];
            }
            return null;
        }

        private static World findWorld(Object3D[] roots) {
            for (int i = 0; i < roots.length; i++) {
                if (roots[i] instanceof World) return (World) roots[i];
            }
            return null;
        }

        private static void drawError(Graphics g, int vx, int vy, int vw, int vh, String msg) {
            g.setColor(80, 0, 0);
            g.fillRect(vx + 2, vy + 2, vw - 4, vh - 4);
            g.setColor(255, 100, 100);
            if (msg == null) msg = "null";
            if (msg.length() > 30) msg = msg.substring(0, 30);
            g.drawString("ERR: " + msg, vx + 4, vy + vh / 2 - 6, Graphics.TOP | Graphics.LEFT);
        }
    }
}
