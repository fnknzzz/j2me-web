import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.SkinnedMesh;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G SkinnedMesh board.
 * Panels (4 cols x 2 rows):
 *   Row 0: skeleton identity, skeleton rotated bone, skeleton translated bone, two-bone skeleton
 *   Row 1: skeleton with mesh rotation, skeleton scaled, deep hierarchy, skeleton with vertex colors
 */
public class M3GSkinnedMeshBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(30, 40, 30);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 2;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(70, 90, 70);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0
            panelIdentitySkeleton(g, 0, 0, cw, ch);
            panelRotatedBone(g, cw, 0, cw, ch);
            panelTranslatedBone(g, cw * 2, 0, cw, ch);
            panelTwoBones(g, cw * 3, 0, cw, ch);

            // Row 1
            panelMeshRotation(g, 0, ch, cw, ch);
            panelScaledBone(g, cw, ch, cw, ch);
            panelDeepHierarchy(g, cw * 2, ch, cw, ch);
            panelVertexColors(g, cw * 3, ch, cw, ch);
        }

        // --- Row 0 ---

        private static void panelIdentitySkeleton(Graphics g, int vx, int vy, int vw, int vh) {
            SkinnedMesh mesh = buildSkinnedQuad(new Group());
            renderSkinned(g, vx, vy, vw, vh, mesh, new Transform());
        }

        private static void panelRotatedBone(Graphics g, int vx, int vy, int vw, int vh) {
            Group skeleton = new Group();
            Group bone = new Group();
            skeleton.addChild(bone);
            SkinnedMesh mesh = buildSkinnedQuad(skeleton);
            bindAll(mesh, bone);
            bone.postRotate(30, 0, 0, 1);
            renderSkinned(g, vx, vy, vw, vh, mesh, new Transform());
        }

        private static void panelTranslatedBone(Graphics g, int vx, int vy, int vw, int vh) {
            Group skeleton = new Group();
            Group bone = new Group();
            skeleton.addChild(bone);
            SkinnedMesh mesh = buildSkinnedQuad(skeleton);
            bindAll(mesh, bone);
            bone.setTranslation(20, 10, 0);
            renderSkinned(g, vx, vy, vw, vh, mesh, new Transform());
        }

        private static void panelTwoBones(Graphics g, int vx, int vy, int vw, int vh) {
            Group skeleton = new Group();
            Group bone1 = new Group();
            skeleton.addChild(bone1);
            Group bone2 = new Group();
            skeleton.addChild(bone2);
            SkinnedMesh mesh = buildSkinnedQuad(skeleton);
            bindLeftRight(mesh, bone1, bone2);
            bone1.postRotate(15, 0, 0, 1);
            bone2.postRotate(-15, 0, 0, 1);
            renderSkinned(g, vx, vy, vw, vh, mesh, new Transform());
        }

        // --- Row 1 ---

        private static void panelMeshRotation(Graphics g, int vx, int vy, int vw, int vh) {
            SkinnedMesh mesh = buildSkinnedQuad(new Group());
            Transform t = new Transform();
            t.postRotate(45, 0, 1, 0);
            renderSkinned(g, vx, vy, vw, vh, mesh, t);
        }

        private static void panelScaledBone(Graphics g, int vx, int vy, int vw, int vh) {
            Group skeleton = new Group();
            Group bone = new Group();
            skeleton.addChild(bone);
            SkinnedMesh mesh = buildSkinnedQuad(skeleton);
            bindAll(mesh, bone);
            bone.setScale(1.2f, 0.8f, 1.0f);
            renderSkinned(g, vx, vy, vw, vh, mesh, new Transform());
        }

        private static void panelDeepHierarchy(Graphics g, int vx, int vy, int vw, int vh) {
            Group skeleton = new Group();
            Group level1 = new Group();
            Group level2 = new Group();
            level1.addChild(level2);
            skeleton.addChild(level1);
            SkinnedMesh mesh = buildSkinnedQuad(skeleton);
            bindAll(mesh, level2);
            level1.setTranslation(10, 0, 0);
            level2.postRotate(20, 0, 0, 1);
            renderSkinned(g, vx, vy, vw, vh, mesh, new Transform());
        }

        private static void panelVertexColors(Graphics g, int vx, int vy, int vw, int vh) {
            Group skeleton = new Group();
            Group bone = new Group();
            skeleton.addChild(bone);
            SkinnedMesh mesh = buildSkinnedQuadColored(skeleton);
            bindAll(mesh, bone);
            bone.postRotate(20, 0, 0, 1);
            renderSkinned(g, vx, vy, vw, vh, mesh, new Transform());
        }

        // --- Utilities ---

        private static void renderSkinned(Graphics g, int vx, int vy, int vw, int vh,
                SkinnedMesh mesh, Transform model) {
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 500.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 200);
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(mesh, model);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static SkinnedMesh buildSkinnedQuad(Group skeleton) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{
                -50, -60, 0,
                 50, -60, 0,
                -50,  60, 0,
                 50,  60, 0
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFFCC8844);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            return new SkinnedMesh(vb, idx, new Appearance(), skeleton);
        }

        private static SkinnedMesh buildSkinnedQuadColored(Group skeleton) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{
                -50, -60, 0,
                 50, -60, 0,
                -50,  60, 0,
                 50,  60, 0
            });
            VertexArray colors = new VertexArray(4, 4, 1);
            colors.set(0, 4, new byte[]{
                (byte)255, 0, 0, (byte)255,
                0, (byte)255, 0, (byte)255,
                0, 0, (byte)255, (byte)255,
                (byte)255, (byte)255, 0, (byte)255
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colors);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            return new SkinnedMesh(vb, idx, new Appearance(), skeleton);
        }

        private static void bindAll(SkinnedMesh mesh, Group bone) {
            mesh.addTransform(bone, 256, 0, 4);
        }

        private static void bindLeftRight(SkinnedMesh mesh, Group leftBone, Group rightBone) {
            mesh.addTransform(leftBone, 256, 0, 1);
            mesh.addTransform(rightBone, 256, 1, 1);
            mesh.addTransform(leftBone, 256, 2, 1);
            mesh.addTransform(rightBone, 256, 3, 1);
        }
    }
}
