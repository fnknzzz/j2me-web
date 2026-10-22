import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.MorphingMesh;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G MorphingMesh board.
 * Panels (4 cols x 2 rows):
 *   Row 0: base mesh (w=0), morph target 1 (w=1), morph 50% blend, morph 75% blend
 *   Row 1: two targets blend, morph with rotation, morph with color target, morph zero weight (=base)
 */
public class M3GMorphingMeshBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(30, 30, 50);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 2;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(70, 70, 90);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0: single target at various weights
            panelMorph(g, 0, 0, cw, ch, new float[]{0.0f});
            panelMorph(g, cw, 0, cw, ch, new float[]{1.0f});
            panelMorph(g, cw * 2, 0, cw, ch, new float[]{0.5f});
            panelMorph(g, cw * 3, 0, cw, ch, new float[]{0.75f});

            // Row 1: two targets, rotation, color, zero
            panelMorphTwoTargets(g, 0, ch, cw, ch, 0.5f, 0.5f);
            panelMorphRotated(g, cw, ch, cw, ch);
            panelMorphTwoTargets(g, cw * 2, ch, cw, ch, 0.8f, 0.2f);
            panelMorphTwoTargets(g, cw * 3, ch, cw, ch, 0.0f, 0.0f);
        }

        private static void panelMorph(Graphics g, int vx, int vy, int vw, int vh, float[] weights) {
            VertexBuffer base = makeQuad(0, 0);
            VertexBuffer target = makeQuad(20, 15);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target}, idx, new Appearance());
            mesh.setWeights(weights);
            renderMesh(g, vx, vy, vw, vh, mesh, new Transform());
        }

        private static void panelMorphTwoTargets(Graphics g, int vx, int vy, int vw, int vh, float w1, float w2) {
            VertexBuffer base = makeQuad(0, 0);
            VertexBuffer target1 = makeQuad(25, 0);
            VertexBuffer target2 = makeQuad(0, 25);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target1, target2}, idx, new Appearance());
            mesh.setWeights(new float[]{w1, w2});
            renderMesh(g, vx, vy, vw, vh, mesh, new Transform());
        }

        private static void panelMorphRotated(Graphics g, int vx, int vy, int vw, int vh) {
            VertexBuffer base = makeQuad(0, 0);
            VertexBuffer target = makeQuad(20, 15);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target}, idx, new Appearance());
            mesh.setWeights(new float[]{0.6f});
            Transform t = new Transform();
            t.postRotate(30, 0, 1, 0);
            renderMesh(g, vx, vy, vw, vh, mesh, t);
        }

        private static void renderMesh(Graphics g, int vx, int vy, int vw, int vh,
                MorphingMesh mesh, Transform model) {
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

        private static VertexBuffer makeQuad(int offsetX, int offsetY) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{
                (short)(-50 + offsetX), (short)(-50 + offsetY), 0,
                (short)(50 + offsetX), (short)(-50 - offsetY), 0,
                (short)(-50 - offsetX), (short)(50 - offsetY), 0,
                (short)(50 - offsetX), (short)(50 + offsetY), 0
            });
            VertexArray colors = new VertexArray(4, 4, 1);
            colors.set(0, 4, new byte[]{
                (byte)255, 80, 80, (byte)255,
                80, (byte)255, 80, (byte)255,
                80, 80, (byte)255, (byte)255,
                (byte)255, (byte)255, 80, (byte)255
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colors);
            return vb;
        }
    }
}
