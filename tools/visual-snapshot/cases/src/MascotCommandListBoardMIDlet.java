import com.mascotcapsule.micro3d.v3.AffineTrans;
import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import com.mascotcapsule.micro3d.v3.Light;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/** A 3 x 3 board of independent Mascot Capsule render entry points. */
public class MascotCommandListBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        private final Graphics3D mascot = new Graphics3D();
        private static final int FULL_SCALE = 4096;

        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cols = 3, rows = 3;
            int cw = w / cols, ch = h / rows;
            g.setColor(20, 24, 34);
            g.fillRect(0, 0, w, h);
            try {
                mascot.bind(g);
                for (int i = 0; i < cols * rows; i++) {
                    int x = (i % cols) * cw;
                    int y = (i / cols) * ch;
                    panel(g, x, y, cw, ch);
                    renderPanel(i, x + cw / 2, y + ch / 2 + 8);
                }
                mascot.flush();
                mascot.release(g);
            } catch (Throwable error) {
                try { mascot.release(g); } catch (Throwable ignored) {}
                status(g, "Mascot error: " + error.toString(), 5, h - 16, 0xFF7777);
            }
        }

        private void renderPanel(int panel, int x, int y) {
            FigureLayout layout = new FigureLayout();
            Effect3D effect = new Effect3D();
            switch (panel) {
                case 0: triangle(x, y, layout, effect, 0xFFFF6655, false); break;
                case 1: triangle(x, y, layout, effect, 0xFF55CCFF, true); break;
                case 2: quad(x, y, layout, effect); break;
                case 3: points(x, y, layout, effect); break;
                case 4: lines(x, y, layout, effect); break;
                case 5: perspectiveTriangle(x, y, layout, effect); break;
                case 6: rotatedTriangle(x, y, layout, effect); break;
                case 7: litTriangle(x, y, layout, effect); break;
                default: flushedTriangle(x, y, layout, effect); break;
            }
        }

        private void triangle(int x, int y, FigureLayout l, Effect3D e, int color, boolean parallelSize) {
            int projection = parallelSize
                    ? Graphics3D.COMMAND_PARALLEL_SIZE
                    : Graphics3D.COMMAND_PARALLEL_SCALE;
            int scale = parallelSize ? 512 : FULL_SCALE;
            int[] commands = {
                Graphics3D.COMMAND_LIST_VERSION_1_0, projection, scale, scale,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                0, 58, 0, -54, -42, 0, 54, -42, 0, color,
                Graphics3D.COMMAND_END
            };
            mascot.drawCommandList((com.mascotcapsule.micro3d.v3.Texture[]) null, x, y, l, e, commands);
        }

        private void quad(int x, int y, FigureLayout l, Effect3D e) {
            int[] commands = {
                Graphics3D.COMMAND_LIST_VERSION_1_0, Graphics3D.COMMAND_PARALLEL_SCALE, FULL_SCALE, FULL_SCALE,
                Graphics3D.PRIMITVE_QUADS | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                -48, 48, 0, 48, 48, 0, 48, -48, 0, -48, -48, 0, 0xFFCC66FF,
                Graphics3D.COMMAND_END
            };
            mascot.drawCommandList((com.mascotcapsule.micro3d.v3.Texture[]) null, x, y, l, e, commands);
        }

        private void points(int x, int y, FigureLayout l, Effect3D e) {
            l.setScale(FULL_SCALE, FULL_SCALE);
            int command = Graphics3D.PRIMITVE_POINTS | (5 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND;
            int[] vertices = { -48, 25, 0, -24, -20, 0, 0, 30, 0, 24, -20, 0, 48, 25, 0 };
            mascot.renderPrimitives(null, x, y, l, e, command, 5, vertices, new int[0], new int[0], new int[] { 0xFF55DD99 });
        }

        private void lines(int x, int y, FigureLayout l, Effect3D e) {
            l.setScale(FULL_SCALE, FULL_SCALE);
            int command = Graphics3D.PRIMITVE_LINES | (2 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND;
            int[] vertices = { -58, -38, 0, 58, 38, 0, -58, 38, 0, 58, -38, 0 };
            mascot.renderPrimitives(null, x, y, l, e, command, 2, vertices, new int[0], new int[0], new int[] { 0xFFFFCC55 });
        }

        private void perspectiveTriangle(int x, int y, FigureLayout l, Effect3D e) {
            int[] commands = {
                Graphics3D.COMMAND_LIST_VERSION_1_0, Graphics3D.COMMAND_PERSPECTIVE_FOV, 16, 2048, 512,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                0, 58, 512, -54, -42, 512, 54, -42, 512, 0xFF6699FF,
                Graphics3D.COMMAND_END
            };
            mascot.drawCommandList((com.mascotcapsule.micro3d.v3.Texture[]) null, x, y, l, e, commands);
        }

        private void rotatedTriangle(int x, int y, FigureLayout l, Effect3D e) {
            AffineTrans t = new AffineTrans();
            t.setIdentity();
            t.setRotationZ(256);
            l.setAffineTrans(t);
            triangle(x, y, l, e, 0xFFFF88AA, false);
        }

        private void litTriangle(int x, int y, FigureLayout l, Effect3D e) {
            Light light = new Light();
            light.setAmbientIntensity(1024);
            light.setParallelLightIntensity(4096);
            e.setLight(light);
            int[] commands = {
                Graphics3D.COMMAND_LIST_VERSION_1_0, Graphics3D.COMMAND_PARALLEL_SCALE, FULL_SCALE, FULL_SCALE,
                Graphics3D.COMMAND_ATTRIBUTE | Graphics3D.ENV_ATTR_LIGHTING,
                Graphics3D.COMMAND_AMBIENT_LIGHT, 1024,
                Graphics3D.COMMAND_DIRECTION_LIGHT, 0, 0, 4096, 4096,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                0, 58, 0, -54, -42, 0, 54, -42, 0, 0xFF77DD77,
                Graphics3D.COMMAND_END
            };
            mascot.drawCommandList((com.mascotcapsule.micro3d.v3.Texture[]) null, x, y, l, e, commands);
        }

        private void flushedTriangle(int x, int y, FigureLayout l, Effect3D e) {
            int[] commands = {
                Graphics3D.COMMAND_LIST_VERSION_1_0, Graphics3D.COMMAND_PARALLEL_SCALE, FULL_SCALE, FULL_SCALE,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                0, 58, 0, -54, -42, 0, 54, -42, 0, 0xFFFFAA44,
                Graphics3D.COMMAND_FLUSH, Graphics3D.COMMAND_END
            };
            mascot.drawCommandList((com.mascotcapsule.micro3d.v3.Texture[]) null, x, y, l, e, commands);
        }

        private static void panel(Graphics g, int x, int y, int w, int h) {
            g.setColor(40, 48, 66);
            g.fillRect(x + 3, y + 3, w - 6, h - 6);
            g.setColor(92, 110, 146);
            g.drawRect(x + 3, y + 3, w - 7, h - 7);
        }

        private static void status(Graphics g, String text, int x, int y, int rgb) {
            g.setColor(rgb);
            g.drawString(text, x, y, Graphics.TOP | Graphics.LEFT);
        }
    }
}
