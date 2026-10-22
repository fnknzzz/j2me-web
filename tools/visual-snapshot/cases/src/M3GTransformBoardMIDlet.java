import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.IndexBuffer;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Node;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G transform & projection board.
 * Panels (5 cols x 5 rows):
 *   Row 0: identity, postTranslate, postScale, postRotate(Z)
 *   Row 1: TRS composite, postMultiply chain, scaleBias scale=2, scaleBias with bias offset
 *   Row 2: perspective narrow FOV, perspective wide FOV, parallel small, parallel large
 *   Row 3: camera translated, camera rotated, model+camera combined, generic projection
 *   Row 4: getTransformTo self, child->parent, parent->child, sibling->sibling
 *   Row 5: align none, align Z->origin, align Z/Y axes, align(null) common reference
 */
public class M3GTransformBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);

            int cols = 5, rows = 5;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(220, 220, 220);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0: model transforms
            panelIdentity(g, 0, 0, cw, ch);
            panelTranslate(g, cw, 0, cw, ch);
            panelScale(g, cw * 2, 0, cw, ch);
            panelRotateZ(g, cw * 3, 0, cw, ch);

            // Row 1: composite transforms and scaleBias
            panelTRS(g, cw * 4, 0, cw, ch);
            panelPostMultiply(g, 0, ch, cw, ch);
            panelScaleBiasScale(g, cw, ch, cw, ch);
            panelScaleBiasOffset(g, cw * 2, ch, cw, ch);

            // Row 2: projection variants
            panelPerspectiveNarrow(g, cw * 3, ch, cw, ch);
            panelPerspectiveWide(g, cw * 4, ch, cw, ch);
            panelParallelSmall(g, 0, ch * 2, cw, ch);
            panelParallelLarge(g, cw, ch * 2, cw, ch);

            // Row 3: camera transforms
            panelCameraTranslated(g, cw * 2, ch * 2, cw, ch);
            panelCameraRotated(g, cw * 3, ch * 2, cw, ch);
            panelModelCamera(g, cw * 4, ch * 2, cw, ch);
            panelGenericProjection(g, 0, ch * 3, cw, ch);

            // Row 4: Node.getTransformTo paths
            panelGetTransformToSelf(g, cw, ch * 3, cw, ch);
            panelGetTransformToChildParent(g, cw * 2, ch * 3, cw, ch);
            panelGetTransformToParentChild(g, cw * 3, ch * 3, cw, ch);
            panelGetTransformToSiblings(g, cw * 4, ch * 3, cw, ch);

            // Row 5: Node.align orientation writeback
            panelAlignNone(g, 0, ch * 4, cw, ch);
            panelAlignZToOrigin(g, cw, ch * 4, cw, ch);
            panelAlignZYAxes(g, cw * 2, ch * 4, cw, ch);
            panelAlignCommonReference(g, cw * 3, ch * 4, cw, ch);
        }

        // --- Row 0 ---

        private static void panelIdentity(Graphics g, int vx, int vy, int vw, int vh) {
            renderTri(g, vx, vy, vw, vh, new Transform(), defaultCamera(vw, vh), defaultCameraTransform(), 0xFFFF4444);
        }

        private static void panelTranslate(Graphics g, int vx, int vy, int vw, int vh) {
            Transform t = new Transform();
            t.postTranslate(30.0f, 20.0f, 0.0f);
            renderTri(g, vx, vy, vw, vh, t, defaultCamera(vw, vh), defaultCameraTransform(), 0xFF44FF44);
        }

        private static void panelScale(Graphics g, int vx, int vy, int vw, int vh) {
            Transform t = new Transform();
            t.postScale(1.5f, 0.5f, 1.0f);
            renderTri(g, vx, vy, vw, vh, t, defaultCamera(vw, vh), defaultCameraTransform(), 0xFF4444FF);
        }

        private static void panelRotateZ(Graphics g, int vx, int vy, int vw, int vh) {
            Transform t = new Transform();
            t.postRotate(45.0f, 0.0f, 0.0f, 1.0f);
            renderTri(g, vx, vy, vw, vh, t, defaultCamera(vw, vh), defaultCameraTransform(), 0xFFFF8800);
        }

        // --- Row 1 ---

        private static void panelTRS(Graphics g, int vx, int vy, int vw, int vh) {
            // translate then rotate then scale
            Transform t = new Transform();
            t.postTranslate(10.0f, 10.0f, 0.0f);
            t.postRotate(30.0f, 0.0f, 0.0f, 1.0f);
            t.postScale(0.8f, 0.8f, 1.0f);
            renderTri(g, vx, vy, vw, vh, t, defaultCamera(vw, vh), defaultCameraTransform(), 0xFFCC44CC);
        }

        private static void panelPostMultiply(Graphics g, int vx, int vy, int vw, int vh) {
            Transform a = new Transform();
            a.postRotate(60.0f, 0.0f, 0.0f, 1.0f);
            Transform b = new Transform();
            b.postScale(0.7f, 1.3f, 1.0f);
            a.postMultiply(b);
            renderTri(g, vx, vy, vw, vh, a, defaultCamera(vw, vh), defaultCameraTransform(), 0xFF44CCCC);
        }

        private static void panelGetTransformToSelf(Graphics g, int vx, int vy, int vw, int vh) {
            Group node = new Group();
            node.setTranslation(20.0f, -15.0f, 0.0f);
            node.setOrientation(30.0f, 0.0f, 0.0f, 1.0f);

            Transform t = new Transform();
            if (node.getTransformTo(node, t)) {
                renderTri(g, vx, vy, vw, vh, t, defaultCamera(vw, vh), defaultCameraTransform(), 0xFFFF4444);
            }
        }

        private static void panelGetTransformToChildParent(Graphics g, int vx, int vy, int vw, int vh) {
            Group parent = new Group();
            Group child = new Group();
            child.setTranslation(28.0f, -18.0f, 0.0f);
            child.setOrientation(28.0f, 0.0f, 0.0f, 1.0f);
            parent.addChild(child);

            Transform t = new Transform();
            if (child.getTransformTo(parent, t)) {
                renderTri(g, vx, vy, vw, vh, t, defaultCamera(vw, vh), defaultCameraTransform(), 0xFF44FF44);
            }
        }

        private static void panelGetTransformToParentChild(Graphics g, int vx, int vy, int vw, int vh) {
            Group parent = new Group();
            Group child = new Group();
            child.setTranslation(28.0f, -18.0f, 0.0f);
            child.setOrientation(28.0f, 0.0f, 0.0f, 1.0f);
            parent.addChild(child);

            Transform t = new Transform();
            if (parent.getTransformTo(child, t)) {
                renderTri(g, vx, vy, vw, vh, t, defaultCamera(vw, vh), defaultCameraTransform(), 0xFF4444FF);
            }
        }

        private static void panelGetTransformToSiblings(Graphics g, int vx, int vy, int vw, int vh) {
            Group root = new Group();
            Group sourceParent = new Group();
            sourceParent.setTranslation(22.0f, -8.0f, 0.0f);
            sourceParent.setOrientation(25.0f, 0.0f, 0.0f, 1.0f);
            Group source = new Group();
            source.setScale(0.75f, 1.25f, 1.0f);
            sourceParent.addChild(source);

            Group target = new Group();
            target.setTranslation(-18.0f, 12.0f, 0.0f);
            target.setOrientation(-30.0f, 0.0f, 0.0f, 1.0f);

            root.addChild(sourceParent);
            root.addChild(target);

            Transform t = new Transform();
            if (source.getTransformTo(target, t)) {
                Transform shifted = new Transform();
                shifted.postTranslate(-36.0f, 0.0f, 0.0f);
                shifted.postMultiply(t);
                renderTri(g, vx, vy, vw, vh, shifted, defaultCamera(vw, vh), defaultCameraTransform(), 0xFFFF8800);
            }
        }

        private static void panelAlignNone(Graphics g, int vx, int vy, int vw, int vh) {
            Mesh mesh = buildAlignTriMesh(0xFFFF4444);
            mesh.setOrientation(45.0f, 0.0f, 0.0f, 1.0f);
            mesh.setAlignment(null, Node.NONE, null, Node.NONE);
            mesh.align(null);
            renderNode(g, vx, vy, vw, vh, mesh, new Transform(), defaultCamera(vw, vh), defaultCameraTransform());
        }

        private static void panelAlignZToOrigin(Graphics g, int vx, int vy, int vw, int vh) {
            Group root = new Group();
            Mesh mesh = buildAlignTriMesh(0xFF44FF44);
            Group target = new Group();
            target.setTranslation(70.0f, -25.0f, 160.0f);
            root.addChild(mesh);
            root.addChild(target);
            mesh.setAlignment(target, Node.ORIGIN, null, Node.NONE);
            root.align(root);
            renderNode(g, vx, vy, vw, vh, root, new Transform(), defaultCamera(vw, vh), defaultCameraTransform());
        }

        private static void panelAlignZYAxes(Graphics g, int vx, int vy, int vw, int vh) {
            Group root = new Group();
            Mesh mesh = buildAlignTriMesh(0xFF4444FF);
            Group reference = new Group();
            reference.setOrientation(90.0f, 0.0f, 0.0f, 1.0f);
            root.addChild(mesh);
            root.addChild(reference);
            mesh.setAlignment(reference, Node.Z_AXIS, reference, Node.Y_AXIS);
            root.align(root);
            renderNode(g, vx, vy, vw, vh, root, new Transform(), defaultCamera(vw, vh), defaultCameraTransform());
        }

        private static void panelAlignCommonReference(Graphics g, int vx, int vy, int vw, int vh) {
            Group root = new Group();
            Mesh mesh = buildAlignTriMesh(0xFFFF8800);
            Group reference = new Group();
            reference.setTranslation(-55.0f, 60.0f, 150.0f);
            root.addChild(mesh);
            root.addChild(reference);
            mesh.setAlignment(null, Node.ORIGIN, null, Node.NONE);
            root.align(reference);
            renderNode(g, vx, vy, vw, vh, root, new Transform(), defaultCamera(vw, vh), defaultCameraTransform());
        }

        private static void panelScaleBiasScale(Graphics g, int vx, int vy, int vw, int vh) {
            // positions stored as small values, scaled by scaleBias factor=2
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 25, 0, -20, -20, 0, 20, -20, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 2.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFF88FF00);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            render(g, vx, vy, vw, vh, vb, idx, new Transform(), defaultCamera(vw, vh), defaultCameraTransform());
        }

        private static void panelScaleBiasOffset(Graphics g, int vx, int vy, int vw, int vh) {
            // positions centered at origin, bias shifts them
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 25, 0, -20, -20, 0, 20, -20, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{15.0f, -10.0f, 0.0f});
            vb.setDefaultColor(0xFFFF0088);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            render(g, vx, vy, vw, vh, vb, idx, new Transform(), defaultCamera(vw, vh), defaultCameraTransform());
        }

        // --- Row 2 ---

        private static void panelPerspectiveNarrow(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            cam.setPerspective(30.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 380.0f);
            Transform model = new Transform();
            model.postRotate(30.0f, 1.0f, 1.0f, 0.0f);
            renderCube(g, vx, vy, vw, vh, model, cam, camT);
        }

        private static void panelPerspectiveWide(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            cam.setPerspective(90.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(30.0f, 1.0f, 1.0f, 0.0f);
            renderCube(g, vx, vy, vw, vh, model, cam, camT);
        }

        private static void panelParallelSmall(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            cam.setParallel(180.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(30.0f, 1.0f, 1.0f, 0.0f);
            renderCube(g, vx, vy, vw, vh, model, cam, camT);
        }

        private static void panelParallelLarge(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            cam.setParallel(300.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(30.0f, 1.0f, 1.0f, 0.0f);
            renderCube(g, vx, vy, vw, vh, model, cam, camT);
        }

        // --- Row 3 ---

        private static void panelCameraTranslated(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(50.0f, 30.0f, 200.0f);
            renderCube(g, vx, vy, vw, vh, new Transform(), cam, camT);
        }

        private static void panelCameraRotated(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            camT.postRotate(20.0f, 0.0f, 1.0f, 0.0f);
            renderCube(g, vx, vy, vw, vh, new Transform(), cam, camT);
        }

        private static void panelModelCamera(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 20.0f, 250.0f);
            Transform model = new Transform();
            model.postRotate(45.0f, 1.0f, 0.0f, 0.0f);
            model.postRotate(45.0f, 0.0f, 1.0f, 0.0f);
            renderCube(g, vx, vy, vw, vh, model, cam, camT);
        }

        private static void panelGenericProjection(Graphics g, int vx, int vy, int vw, int vh) {
            Camera cam = new Camera();
            // Build a custom ortho-like projection matrix
            float[] m = new float[]{
                0.01f, 0, 0, 0,
                0, 0.01f, 0, 0,
                0, 0, -0.002f, -1.0f,
                0, 0, 0, 1.0f
            };
            Transform proj = new Transform();
            proj.set(m);
            cam.setGeneric(proj);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(30.0f, 1.0f, 1.0f, 0.0f);
            renderCube(g, vx, vy, vw, vh, model, cam, camT);
        }

        // --- Utilities ---

        private static void renderTri(Graphics g, int vx, int vy, int vw, int vh,
                Transform model, Camera cam, Transform camT, int color) {
            Mesh mesh = buildTriMesh(color);
            render(g, vx, vy, vw, vh, mesh.getVertexBuffer(), mesh.getIndexBuffer(0), model, cam, camT);
        }

        private static Mesh buildTriMesh(int color) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 50, 0, -40, -40, 0, 40, -40, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            return new Mesh(vb, idx, new Appearance());
        }

        private static Mesh buildAlignTriMesh(int color) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{-28, 52, 0, -48, -34, 0, 36, -18, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            return new Mesh(vb, idx, new Appearance());
        }

        private static void renderCube(Graphics g, int vx, int vy, int vw, int vh,
                Transform model, Camera cam, Transform camT) {
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            render(g, vx, vy, vw, vh, vb, idx, model, cam, camT);
        }

        private static Mesh buildCubeMesh() {
            return new Mesh(buildColorCube(), new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4}), new Appearance());
        }

        private static void render(Graphics g, int vx, int vy, int vw, int vh,
                VertexBuffer vb, IndexBuffer idx, Transform model,
                Camera cam, Transform camT) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(vb, idx, new Appearance(), model != null ? model : new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void renderNode(Graphics g, int vx, int vy, int vw, int vh,
                javax.microedition.m3g.Node node, Transform model, Camera cam, Transform camT) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(node, model != null ? model : new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Camera defaultCamera(int width, int height) {
            Camera cam = new Camera();
            cam.setParallel(160.0f, (float) width / height, 1.0f, 1000.0f);
            return cam;
        }

        private static Transform defaultCameraTransform() {
            Transform t = new Transform();
            t.postTranslate(0.0f, 0.0f, 200.0f);
            return t;
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
    }
}
