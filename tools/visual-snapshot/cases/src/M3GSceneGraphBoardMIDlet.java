import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.m3g.World;
import javax.microedition.midlet.MIDlet;

/**
 * M3G scene graph rendering board.
 * Panels (4 cols x 3 rows):
 *   Row 0: render(World) single mesh, render(World) multi mesh, render(Node) mesh, render(Node) group
 *   Row 1: nested groups, node translate, node scale, node rotate
 *   Row 2: renderingEnable=false, scope filter, world background, multi-submesh mesh
 */
public class M3GSceneGraphBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 3;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(220, 220, 220);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0: basic scene graph
            panelWorldSingleMesh(g, 0, 0, cw, ch);
            panelWorldMultiMesh(g, cw, 0, cw, ch);
            panelRenderNodeMesh(g, cw * 2, 0, cw, ch);
            panelRenderNodeGroup(g, cw * 3, 0, cw, ch);

            // Row 1: node transforms
            panelNestedGroups(g, 0, ch, cw, ch);
            panelNodeTranslate(g, cw, ch, cw, ch);
            panelNodeScale(g, cw * 2, ch, cw, ch);
            panelNodeRotate(g, cw * 3, ch, cw, ch);

            // Row 2: visibility, scope, background, submesh
            panelRenderingDisabled(g, 0, ch * 2, cw, ch);
            panelScopeFilter(g, cw, ch * 2, cw, ch);
            panelWorldBackground(g, cw * 2, ch * 2, cw, ch);
            panelMultiSubmesh(g, cw * 3, ch * 2, cw, ch);
        }

        // --- Row 0 ---

        private static void panelWorldSingleMesh(Graphics g, int vx, int vy, int vw, int vh) {
            World world = new World();
            Background bg = new Background();
            bg.setColor(0xFFFFFFFF);
            world.setBackground(bg);
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            cam.setTranslation(0, 0, 200);
            world.addChild(cam);
            world.setActiveCamera(cam);
            Mesh mesh = buildColorCubeMesh();
            mesh.postRotate(30, 1, 0, 0);
            mesh.postRotate(30, 0, 1, 0);
            world.addChild(mesh);
            renderWorld(g, vx, vy, vw, vh, world);
        }

        private static void panelWorldMultiMesh(Graphics g, int vx, int vy, int vw, int vh) {
            World world = new World();
            Background bg = new Background();
            bg.setColor(0xFFFFFFFF);
            world.setBackground(bg);
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            cam.setTranslation(0, 0, 300);
            world.addChild(cam);
            world.setActiveCamera(cam);

            Mesh m1 = buildSmallCubeMesh(0xFFFF4444);
            m1.setTranslation(-50, 0, 0);
            m1.postRotate(20, 1, 1, 0);
            world.addChild(m1);

            Mesh m2 = buildSmallCubeMesh(0xFF4444FF);
            m2.setTranslation(50, 0, 0);
            m2.postRotate(-20, 1, 1, 0);
            world.addChild(m2);

            renderWorld(g, vx, vy, vw, vh, world);
        }

        private static void panelRenderNodeMesh(Graphics g, int vx, int vy, int vw, int vh) {
            Mesh mesh = buildColorCubeMesh();
            Transform t = new Transform();
            t.postRotate(45, 1, 0, 0);
            t.postRotate(30, 0, 1, 0);
            renderNode(g, vx, vy, vw, vh, mesh, t);
        }

        private static void panelRenderNodeGroup(Graphics g, int vx, int vy, int vw, int vh) {
            Group group = new Group();
            Mesh m1 = buildSmallCubeMesh(0xFF44CC44);
            m1.setTranslation(-30, 0, 0);
            group.addChild(m1);
            Mesh m2 = buildSmallCubeMesh(0xFFCC4444);
            m2.setTranslation(30, 0, 0);
            group.addChild(m2);
            Transform t = new Transform();
            t.postRotate(25, 1, 1, 0);
            renderNode(g, vx, vy, vw, vh, group, t);
        }

        // --- Row 1 ---

        private static void panelNestedGroups(Graphics g, int vx, int vy, int vw, int vh) {
            Group outer = new Group();
            outer.postRotate(15, 0, 0, 1);
            Group inner = new Group();
            inner.setTranslation(0, 20, 0);
            Mesh mesh = buildSmallCubeMesh(0xFFFFAA00);
            mesh.postRotate(30, 1, 1, 0);
            inner.addChild(mesh);
            outer.addChild(inner);
            Transform t = new Transform();
            renderNode(g, vx, vy, vw, vh, outer, t);
        }

        private static void panelNodeTranslate(Graphics g, int vx, int vy, int vw, int vh) {
            Group group = new Group();
            Mesh mesh = buildSmallCubeMesh(0xFF00AAFF);
            mesh.setTranslation(30, -20, 0);
            mesh.postRotate(20, 1, 1, 0);
            group.addChild(mesh);
            renderNode(g, vx, vy, vw, vh, group, new Transform());
        }

        private static void panelNodeScale(Graphics g, int vx, int vy, int vw, int vh) {
            Group group = new Group();
            Mesh mesh = buildSmallCubeMesh(0xFFAA00FF);
            mesh.setScale(1.5f, 0.5f, 1.0f);
            mesh.postRotate(30, 1, 0, 0);
            group.addChild(mesh);
            renderNode(g, vx, vy, vw, vh, group, new Transform());
        }

        private static void panelNodeRotate(Graphics g, int vx, int vy, int vw, int vh) {
            Group group = new Group();
            Mesh mesh = buildSmallCubeMesh(0xFF00FFAA);
            mesh.setOrientation(60, 1, 1, 0);
            group.addChild(mesh);
            renderNode(g, vx, vy, vw, vh, group, new Transform());
        }

        // --- Row 2 ---

        private static void panelRenderingDisabled(Graphics g, int vx, int vy, int vw, int vh) {
            World world = new World();
            Background bg = new Background();
            bg.setColor(0xFFFFFFFF);
            world.setBackground(bg);
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            cam.setTranslation(0, 0, 300);
            world.addChild(cam);
            world.setActiveCamera(cam);
            // Visible mesh
            Mesh m1 = buildSmallCubeMesh(0xFF44FF44);
            m1.setTranslation(-40, 0, 0);
            m1.postRotate(30, 1, 1, 0);
            world.addChild(m1);
            // Invisible mesh
            Mesh m2 = buildSmallCubeMesh(0xFFFF4444);
            m2.setTranslation(40, 0, 0);
            m2.setRenderingEnable(false);
            world.addChild(m2);
            renderWorld(g, vx, vy, vw, vh, world);
        }

        private static void panelScopeFilter(Graphics g, int vx, int vy, int vw, int vh) {
            // Only render scope=1 nodes, skip scope=2
            World world = new World();
            Background bg = new Background();
            bg.setColor(0xFFFFFFFF);
            world.setBackground(bg);
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            cam.setTranslation(0, 0, 300);
            cam.setScope(1);
            world.addChild(cam);
            world.setActiveCamera(cam);
            Mesh m1 = buildSmallCubeMesh(0xFF4488FF);
            m1.setTranslation(-40, 0, 0);
            m1.setScope(1);
            m1.postRotate(25, 1, 1, 0);
            world.addChild(m1);
            Mesh m2 = buildSmallCubeMesh(0xFFFF8844);
            m2.setTranslation(40, 0, 0);
            m2.setScope(2);
            m2.postRotate(25, 1, 1, 0);
            world.addChild(m2);
            renderWorld(g, vx, vy, vw, vh, world);
        }

        private static void panelWorldBackground(Graphics g, int vx, int vy, int vw, int vh) {
            World world = new World();
            Background bg = new Background();
            bg.setColor(0xFF224488);
            world.setBackground(bg);
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            cam.setTranslation(0, 0, 200);
            world.addChild(cam);
            world.setActiveCamera(cam);
            Mesh mesh = buildSmallCubeMesh(0xFFFFFFFF);
            mesh.postRotate(30, 1, 1, 0);
            world.addChild(mesh);
            renderWorld(g, vx, vy, vw, vh, world);
        }

        private static void panelMultiSubmesh(Graphics g, int vx, int vy, int vw, int vh) {
            // A mesh with two submeshes (different appearances)
            VertexBuffer vb = buildColorCubeVB();
            // Split the 6 faces into 2 submeshes of 3 faces each
            TriangleStripArray sub1 = new TriangleStripArray(0, new int[]{4, 4, 4});
            TriangleStripArray sub2 = new TriangleStripArray(12, new int[]{4, 4, 4});
            Appearance app1 = new Appearance();
            Appearance app2 = new Appearance();
            Mesh mesh = new Mesh(vb,
                new TriangleStripArray[]{sub1, sub2},
                new Appearance[]{app1, app2});
            mesh.postRotate(30, 1, 0, 0);
            mesh.postRotate(30, 0, 1, 0);
            renderNode(g, vx, vy, vw, vh, mesh, new Transform());
        }

        // --- Utilities ---

        private static void renderWorld(Graphics g, int vx, int vy, int vw, int vh, World world) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.render(world);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderNode(Graphics g, int vx, int vy, int vw, int vh,
                javax.microedition.m3g.Node node, Transform transform) {
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 200);
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(node, transform);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Mesh buildColorCubeMesh() {
            VertexBuffer vb = buildColorCubeVB();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            return new Mesh(vb, idx, new Appearance());
        }

        private static Mesh buildSmallCubeMesh(int color) {
            short[] positions = new short[]{
                -30, -30, 30,  30, -30, 30,  -30, 30, 30,  30, 30, 30,
                30, -30, -30,  -30, -30, -30,  30, 30, -30,  -30, 30, -30,
                -30, 30, 30,  30, 30, 30,  -30, 30, -30,  30, 30, -30,
                -30, -30, -30,  30, -30, -30,  -30, -30, 30,  30, -30, 30,
                30, -30, 30,  30, -30, -30,  30, 30, 30,  30, 30, -30,
                -30, -30, -30,  -30, -30, 30,  -30, 30, -30,  -30, 30, 30
            };
            VertexArray posArr = new VertexArray(24, 3, 2);
            posArr.set(0, 24, positions);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            return new Mesh(vb, idx, new Appearance());
        }

        private static VertexBuffer buildColorCubeVB() {
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
    }
}
