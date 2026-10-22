import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import com.mascotcapsule.micro3d.v3.Texture;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class MascotProjectionClipMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Scene()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Scene extends Canvas {
        private static final int SCALE = 4096;
        private final Graphics3D mascot = new Graphics3D();

        protected void paint(Graphics g) {
            int width = getWidth();
            int height = getHeight();
            int split = height * 5 / 12;
            g.setColor(20, 24, 34);
            g.fillRect(0, 0, width, height);
            g.setColor(52, 63, 88);
            g.drawLine(width / 2, 28, width / 2, split - 18);
            g.drawRect(95, split + 20, width - 191, height - split - 45);

            try {
                mascot.bind(g);
                mascot.drawCommandList((Texture[]) null, width / 4, split / 2,
                        new FigureLayout(), new Effect3D(), fovCommands());
                mascot.drawCommandList((Texture[]) null, width * 3 / 4, split / 2,
                        new FigureLayout(), new Effect3D(), widthHeightCommands());
                mascot.drawCommandList((Texture[]) null, 0, 0,
                        new FigureLayout(), new Effect3D(), clipCommands(width, height, split));
                mascot.flush();
                mascot.release(g);
            } catch (Throwable error) {
                try { mascot.release(g); } catch (Throwable ignored) {}
                g.setColor(255, 110, 110);
                g.drawString(error.toString(), 4, height - 16, Graphics.TOP | Graphics.LEFT);
            }
        }

        private static int[] fovCommands() {
            return new int[] {
                Graphics3D.COMMAND_LIST_VERSION_1_0,
                Graphics3D.COMMAND_PERSPECTIVE_FOV, 256, 4096, 682,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                -16, 60, 512, -86, -46, 512, 54, -46, 512, 0xFFEC6C57,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                220, 170, 1536, 120, 90, 1536, 320, 90, 1536, 0xFF55C9EE,
                Graphics3D.COMMAND_FLUSH,
                Graphics3D.COMMAND_END
            };
        }

        private static int[] widthHeightCommands() {
            return new int[] {
                Graphics3D.COMMAND_LIST_VERSION_1_0,
                Graphics3D.COMMAND_PERSPECTIVE_WH, 256, 4096, 4096 * 320, 4096 * 360,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                -16, 60, 512, -86, -46, 512, 54, -46, 512, 0xFFB578F2,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                135, 170, 1536, 45, 90, 1536, 225, 90, 1536, 0xFF73D58C,
                Graphics3D.COMMAND_FLUSH,
                Graphics3D.COMMAND_END
            };
        }

        private static int[] clipCommands(int width, int height, int split) {
            return new int[] {
                Graphics3D.COMMAND_LIST_VERSION_1_0,
                Graphics3D.COMMAND_CLIP, 96, split + 21, width - 95, height - 23,
                Graphics3D.COMMAND_CENTER, width / 2, split + (height - split) / 2,
                Graphics3D.COMMAND_PARALLEL_SCALE, SCALE, SCALE,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                0, 20, 0, -250, -150, 0, 250, -150, 0, 0xFF12BCEB,
                Graphics3D.PRIMITVE_TRIANGLES | (1 << 16) | Graphics3D.PDATA_COLOR_PER_COMMAND,
                0, 80, 0, -100, 200, 0, 100, 200, 0, 0xFFFF9F43,
                Graphics3D.COMMAND_END
            };
        }
    }
}
