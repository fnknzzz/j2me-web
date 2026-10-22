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
 * Probes Graphics3D releaseTarget flush ordering against LCDUI drawing.
 * Panels (2 cols x 2 rows):
 *   0: 2D before bind, then M3G clear
 *   1: M3G clear, 2D draw while target is bound, then release
 *   2: M3G triangle, 2D draw while target is bound, then release
 *   3: M3G clear/release, then 2D draw after release
 */
public class M3GReleaseTargetFlushBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 2;
            int ch = h / 2;

            g.setColor(245, 245, 245);
            g.fillRect(0, 0, w, h);

            panel2DBeforeBind(g, 0, 0, cw, ch);
            panel2DDuringBindAfterClear(g, cw, 0, cw, ch);
            panel2DDuringBindAfterTriangle(g, 0, ch, cw, ch);
            panel2DAfterRelease(g, cw, ch, cw, ch);

            for (int i = 0; i < 4; i++) {
                int x = (i % 2) * cw;
                int y = (i / 2) * ch;
                g.setColor(80, 84, 94);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }
        }

        private static void panel2DBeforeBind(Graphics g, int x, int y, int w, int h) {
            g.setColor(20, 80, 210);
            g.fillRect(x + 14, y + 14, w - 28, h - 28);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x + 14, y + 14, w - 28, h - 28);
                clear3D(g3d, 0xFF2E7D32);
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
        }

        private static void panel2DDuringBindAfterClear(Graphics g, int x, int y, int w, int h) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x + 14, y + 14, w - 28, h - 28);
                clear3D(g3d, 0xFF2E7D32);
                draw2DProbe(g, x, y, w, h, 0xFFD32F2F);
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
        }

        private static void panel2DDuringBindAfterTriangle(Graphics g, int x, int y, int w, int h) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x + 14, y + 14, w - 28, h - 28);
                clear3D(g3d, 0xFF263238);
                renderTriangle3D(g3d, w, h, 0xFFFFD54F);
                draw2DProbe(g, x, y, w, h, 0xFFD32F2F);
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
        }

        private static void panel2DAfterRelease(Graphics g, int x, int y, int w, int h) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x + 14, y + 14, w - 28, h - 28);
                clear3D(g3d, 0xFF2E7D32);
            } finally {
                try { g3d.releaseTarget(); } catch (Throwable ignored) {}
            }
            draw2DProbe(g, x, y, w, h, 0xFFD32F2F);
        }

        private static void clear3D(Graphics3D g3d, int color) {
            Background bg = new Background();
            bg.setColor(color);
            g3d.clear(bg);
        }

        private static void renderTriangle3D(Graphics3D g3d, int panelW, int panelH, int color) {
            Camera camera = new Camera();
            camera.setPerspective(60.0f, (float) panelW / panelH, 1.0f, 1000.0f);
            Transform cameraTransform = new Transform();
            cameraTransform.postTranslate(0.0f, 0.0f, 180.0f);
            g3d.setCamera(camera, cameraTransform);
            g3d.render(makeTriangle(color), new TriangleStripArray(0, new int[]{3}),
                    new Appearance(), new Transform());
        }

        private static VertexBuffer makeTriangle(int color) {
            VertexArray positions = new VertexArray(3, 3, 2);
            positions.set(0, 3, new short[]{0, 58, 0, -56, -44, 0, 56, -44, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return vb;
        }

        private static void draw2DProbe(Graphics g, int x, int y, int w, int h, int color) {
            g.setColor((color >> 16) & 0xff, (color >> 8) & 0xff, color & 0xff);
            g.fillRect(x + w / 2 - 34, y + h / 2 - 34, 68, 68);
        }
    }
}
