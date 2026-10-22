import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class GraphicsStateBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);

            Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(f);
            int cols = 3, rows = 5, cw = w / cols, ch = h / rows, i;
            for (i = 0; i < cols * rows; i++) {
                panel(g, (i % cols) * cw, (i / cols) * ch, cw, ch);
            }

            colorPanel(g, 0, 0, cw, ch);
            grayPanel(g, cw, 0, cw, ch);
            strokePanel(g, cw * 2, 0, cw, ch);
            translatePanel(g, 0, ch, cw, ch);
            clipPanel(g, cw, ch, cw, ch);
            fontPanel(g, cw * 2, ch, cw, ch);
            getterColor(g, 0, ch * 2, cw, ch);
            getterGray(g, cw, ch * 2, cw, ch);
            getterStroke(g, cw * 2, ch * 2, cw, ch);
            getterTranslate(g, 0, ch * 3, cw, ch);
            getterClip(g, cw, ch * 3, cw, ch);
            mixed(g, cw * 2, ch * 3, cw, ch);
            displayColorPanel(g, 0, ch * 4, cw, ch);
        }

        private static void panel(Graphics g, int x, int y, int w, int h) {
            g.setColor(235, 235, 235);
            g.drawRect(x + 4, y + 4, w - 9, h - 9);
        }

        private static void colorPanel(Graphics g, int x, int y, int w, int h) {
            g.setColor(220, 40, 40);
            g.fillRect(x + 14, y + 16, 34, 24);
            g.setColor(40, 100, 220);
            g.fillRect(x + 58, y + 16, 34, 24);
            g.setColor(40, 160, 80);
            g.fillRect(x + 102, y + 16, 34, 24);
        }

        private static void grayPanel(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 8; i++) {
                g.setGrayScale(i * 255 / 7);
                g.fillRect(x + 14 + i * 16, y + 18, 14, 44);
            }
        }

        private static void strokePanel(Graphics g, int x, int y, int w, int h) {
            int old = g.getStrokeStyle();
            int i;
            for (i = 0; i < 6; i++) {
                g.setStrokeStyle((i & 1) == 0 ? Graphics.SOLID : Graphics.DOTTED);
                g.setColor(40 + i * 25, 60, 220 - i * 18);
                g.drawRect(x + 18 + i * 10, y + 18 + i * 8, 80, 40);
            }
            g.setStrokeStyle(old);
        }

        private static void translatePanel(Graphics g, int x, int y, int w, int h) {
            g.setColor(190, 190, 190);
            g.drawRect(x + 18, y + 20, 40, 28);
            g.translate(40, 26);
            g.setColor(220, 40, 40);
            g.fillRect(x + 18, y + 20, 40, 28);
            g.translate(-40, -26);
        }

        private static void clipPanel(Graphics g, int x, int y, int w, int h) {
            int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
            g.setClip(x + 36, y + 28, 72, 46);
            fill(g, x + 18, y + 18, w - 36, h - 36);
            g.setClip(ox, oy, ow, oh);
            g.setColor(0, 0, 0);
            g.drawRect(x + 36, y + 28, 72, 46);
        }

        private static void fontPanel(Graphics g, int x, int y, int w, int h) {
            g.setColor(0, 0, 0);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.drawString("A", x + 18, y + 18, Graphics.TOP | Graphics.LEFT);
            g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
            g.drawString("B", x + 60, y + 18, Graphics.TOP | Graphics.LEFT);
            g.setFont(Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_ITALIC, Font.SIZE_LARGE));
            g.drawString("C", x + 106, y + 12, Graphics.TOP | Graphics.LEFT);
        }

        private static void getterColor(Graphics g, int x, int y, int w, int h) {
            g.setColor(0x3366cc);
            int c = g.getColor();
            g.setColor(c);
            g.fillRect(x + 18, y + 20, 60, 40);
            g.setColor((c >> 16) & 255, (c >> 8) & 255, c & 255);
            g.fillRect(x + 90, y + 20, 40, 40);
        }

        private static void getterGray(Graphics g, int x, int y, int w, int h) {
            g.setGrayScale(160);
            int v = g.getGrayScale();
            g.setGrayScale(v);
            g.fillRect(x + 18, y + 20, 60, 40);
            g.setGrayScale(255 - v);
            g.fillRect(x + 90, y + 20, 40, 40);
        }

        private static void getterStroke(Graphics g, int x, int y, int w, int h) {
            int old = g.getStrokeStyle();
            g.setStrokeStyle(Graphics.DOTTED);
            if (g.getStrokeStyle() == Graphics.DOTTED) {
                g.setColor(220, 40, 40);
                g.drawRect(x + 18, y + 20, 60, 40);
            }
            g.setStrokeStyle(Graphics.SOLID);
            if (g.getStrokeStyle() == Graphics.SOLID) {
                g.setColor(40, 100, 220);
                g.drawRect(x + 90, y + 20, 40, 40);
            }
            g.setStrokeStyle(old);
        }

        private static void getterTranslate(Graphics g, int x, int y, int w, int h) {
            g.translate(22, 18);
            int tx = g.getTranslateX(), ty = g.getTranslateY();
            g.setColor(220, 40, 40);
            g.fillRect(x + tx, y + ty, 36, 24);
            g.setColor(40, 100, 220);
            g.fillRect(x + tx + 50, y + ty + 24, 36, 24);
            g.translate(-22, -18);
        }

        private static void getterClip(Graphics g, int x, int y, int w, int h) {
            int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
            g.setClip(x + 34, y + 26, 74, 50);
            g.setColor(220, 40, 40);
            g.fillRect(g.getClipX() - 10, g.getClipY() - 10, g.getClipWidth() + 20, g.getClipHeight() + 20);
            g.setClip(ox, oy, ow, oh);
        }

        private static void mixed(Graphics g, int x, int y, int w, int h) {
            g.setColor(220, 40, 40);
            g.fillRect(x + 16, y + 18, 24, 24);
            g.setGrayScale(90);
            g.fillRect(x + 48, y + 18, 24, 24);
            g.setStrokeStyle(Graphics.DOTTED);
            g.setColor(40, 100, 220);
            g.drawRect(x + 80, y + 18, 28, 24);
            g.translate(12, 8);
            g.setColor(40, 160, 80);
            g.fillRect(x + 104, y + 42, 24, 18);
            g.translate(-12, -8);
            g.setStrokeStyle(Graphics.SOLID);
        }

        private static void displayColorPanel(Graphics g, int x, int y, int w, int h) {
            int[] colors = {0xff0000, 0x00ff00, 0x0000ff, 0x336699, 0xaabbcc};
            int i;
            for (i = 0; i < 5; i++) {
                int dc = g.getDisplayColor(colors[i]);
                g.setColor(dc);
                g.fillRect(x + 14 + i * 24, y + 18, 20, 36);
                g.setColor(colors[i]);
                g.fillRect(x + 14 + i * 24, y + 60, 20, 36);
            }
        }

        private static void fill(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 8; i++) {
                g.setColor(40 + i * 22, 60, 220 - i * 16);
                g.fillRect(x + 8 + i * 12, y + 10 + i * 6, 18, 14);
            }
        }
    }
}
