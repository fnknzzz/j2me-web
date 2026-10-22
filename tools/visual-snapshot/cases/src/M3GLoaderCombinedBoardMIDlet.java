import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.IndexBuffer;
import javax.microedition.m3g.Loader;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.m3g.World;
import javax.microedition.midlet.MIDlet;

/**
 * Combined/edge-case M3G Loader test board.
 * Each panel loads a .m3g file exercising a different combination or boundary scenario.
 *
 * Layout (4 cols x 2 rows):
 *   [0,0] textured_mesh.m3g   - Image2D + Texture2D chain
 *   [1,0] multi_submesh.m3g   - single Mesh with 2 submeshes (different materials)
 *   [2,0] nested_groups.m3g   - World > Group > 2 Meshes hierarchy
 *   [3,0] full_appearance.m3g - CompositingMode + PolygonMode + Material in Appearance
 *   [0,1] compressed.m3g      - zlib compressed section
 *   [1,1] empty_world.m3g     - World with no mesh (boundary: only background clear)
 *   [2,1] scale_bias.m3g      - VertexBuffer positionScale/positionBias
 *   [3,1] offset_bytes.m3g    - Loader.load(byte[], offset)
 */
public class M3GLoaderCombinedBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 2;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(180, 180, 180);
                g.drawRect(cx + 1, cy + 1, cw - 3, ch - 3);
            }

            // Row 0
            panelRender(g, 0, 0, cw, ch, "/textured_mesh.m3g", true);
            panelRender(g, cw, 0, cw, ch, "/multi_submesh.m3g", true);
            panelRender(g, cw * 2, 0, cw, ch, "/nested_groups.m3g", true);
            panelRender(g, cw * 3, 0, cw, ch, "/full_appearance.m3g", true);

            // Row 1
            panelRender(g, 0, ch, cw, ch, "/compressed.m3g", false);
            panelRender(g, cw, ch, cw, ch, "/empty_world.m3g", true);
            panelRender(g, cw * 2, ch, cw, ch, "/scale_bias.m3g", false);
            panelLoadFromOffset(g, cw * 3, ch, cw, ch, "/offset_bytes.m3g", 7);
        }

        private static void panelRender(Graphics g, int vx, int vy, int vw, int vh, String file, boolean tryWorld) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                Object3D[] roots = Loader.load(file);
                if (roots == null || roots.length == 0) {
                    drawError(g, vx, vy, vw, vh, "no roots");
                    return;
                }

                World world = tryWorld ? findWorld(roots) : null;
                if (world != null) {
                    g3d.bindTarget(g);
                    g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                    g3d.render(world);
                } else {
                    Mesh mesh = findMesh(roots);
                    if (mesh == null) {
                        drawError(g, vx, vy, vw, vh, "no mesh/world");
                        return;
                    }
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

                    VertexBuffer vb = mesh.getVertexBuffer();
                    IndexBuffer ib = mesh.getIndexBuffer(0);
                    Appearance app = mesh.getAppearance(0);
                    if (app == null) app = new Appearance();
                    g3d.render(vb, ib, app, new Transform());
                }
            } catch (Throwable t) {
                drawError(g, vx, vy, vw, vh, t.getMessage());
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
        }

        private static void panelLoadFromOffset(Graphics g, int vx, int vy, int vw, int vh,
                String file, int offset) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                Object3D[] roots = Loader.load(readResource(file), offset);
                Mesh mesh = findMesh(roots);
                if (mesh == null) {
                    drawError(g, vx, vy, vw, vh, "no mesh");
                    return;
                }

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

                VertexBuffer vb = mesh.getVertexBuffer();
                IndexBuffer ib = mesh.getIndexBuffer(0);
                Appearance app = mesh.getAppearance(0);
                if (app == null) app = new Appearance();
                g3d.render(vb, ib, app, new Transform());
            } catch (Throwable t) {
                drawError(g, vx, vy, vw, vh, t.getMessage());
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
        }

        private static byte[] readResource(String file) throws Exception {
            InputStream is = Board.class.getResourceAsStream(file);
            if (is == null) throw new Exception("resource");
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[128];
                int n;
                while ((n = is.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } finally {
                is.close();
            }
        }

        private static World findWorld(Object3D[] roots) {
            for (int i = 0; i < roots.length; i++) {
                if (roots[i] instanceof World) return (World) roots[i];
            }
            return null;
        }

        private static Mesh findMesh(Object3D[] roots) {
            for (int i = 0; i < roots.length; i++) {
                if (roots[i] instanceof Mesh) return (Mesh) roots[i];
            }
            return null;
        }

        private static void drawError(Graphics g, int vx, int vy, int vw, int vh, String msg) {
            g.setColor(80, 0, 0);
            g.fillRect(vx + 2, vy + 2, vw - 4, vh - 4);
            g.setColor(255, 100, 100);
            if (msg == null) msg = "null";
            if (msg.length() > 24) msg = msg.substring(0, 24);
            g.drawString("ERR:" + msg, vx + 4, vy + vh / 2 - 6, Graphics.TOP | Graphics.LEFT);
        }
    }
}
