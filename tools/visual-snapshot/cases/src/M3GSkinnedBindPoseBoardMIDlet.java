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

/** Visual regression for SkinnedMesh bind-pose-relative bone transforms. */
public class M3GSkinnedBindPoseBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight(), cw = w / 3, ch = h / 2;
            g.setColor(31, 39, 33);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 6; i++) {
                int x = (i % 3) * cw, y = (i / 3) * ch;
                g.setColor(73, 91, 78);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }
            panel(g, 0, 0, cw, ch, 0);
            panel(g, cw, 0, cw, ch, 1);
            panel(g, cw * 2, 0, cw, ch, 2);
            panel(g, 0, ch, cw, ch, 3);
            panel(g, cw, ch, cw, ch, 4);
            panel(g, cw * 2, ch, cw, ch, 5);
        }

        private static void panel(Graphics g, int x, int y, int w, int h, int mode) {
            Group skeleton = new Group();
            Group parent = null;
            Group bone = new Group();
            if (mode == 1) bone.setTranslation(28, 16, 0);
            if (mode == 2) bone.setOrientation(35, 0, 0, 1);
            if (mode == 5) {
                parent = new Group();
                parent.setTranslation(20, 0, 0);
                bone.setTranslation(0, 12, 0);
                parent.addChild(bone);
                skeleton.addChild(parent);
            } else {
                skeleton.addChild(bone);
            }

            SkinnedMesh mesh = makeMesh(skeleton);
            mesh.addTransform(bone, 256, 0, 4);
            if (mode == 3) bone.setTranslation(28, 16, 0);
            if (mode == 4) bone.setOrientation(35, 0, 0, 1);
            if (mode == 5) bone.postRotate(30, 0, 0, 1);
            render(g, x, y, w, h, mesh);
        }

        private static SkinnedMesh makeMesh(Group skeleton) {
            VertexArray positions = new VertexArray(4, 3, 2);
            positions.set(0, 4, new short[]{
                -58, -42, 0, 58, -42, 0, -58, 42, 0, 58, 42, 0
            });
            VertexArray colors = new VertexArray(4, 4, 1);
            colors.set(0, 4, new byte[]{
                (byte)235, 75, 35, (byte)255, 30, (byte)180, (byte)235, (byte)255,
                45, (byte)205, 75, (byte)255, (byte)240, (byte)185, 30, (byte)255
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(positions, 1, new float[]{0, 0, 0});
            vb.setColors(colors);
            return new SkinnedMesh(vb, new TriangleStripArray(0, new int[]{4}),
                    new Appearance(), skeleton);
        }

        private static void render(Graphics g, int x, int y, int w, int h, SkinnedMesh mesh) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                Camera camera = new Camera();
                camera.setPerspective(60, (float)w / h, 1, 500);
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(0, 0, 180);
                g3d.setCamera(camera, cameraTransform);
                g3d.render(mesh, new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
