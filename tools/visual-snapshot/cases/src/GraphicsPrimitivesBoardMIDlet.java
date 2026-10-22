import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class GraphicsPrimitivesBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 6, cw = w / cols, ch = h / rows;
            int i;
            for (i = 0; i < cols * rows; i++) {
                panel(g, (i % cols) * cw, (i / cols) * ch, cw, ch);
            }

            lineDirections(g, 0, 0, cw, ch);
            lineDensity(g, cw, 0, cw, ch);
            rectOutlines(g, cw * 2, 0, cw, ch);
            rectFills(g, cw * 3, 0, cw, ch);

            triangleMatrix(g, 0, ch, cw, ch);
            triangleEdges(g, cw, ch, cw, ch);
            arcMatrix(g, cw * 2, ch, cw, ch, false);
            arcMatrix(g, cw * 3, ch, cw, ch, true);

            roundRectMatrix(g, 0, ch * 2, cw, ch, false);
            roundRectMatrix(g, cw, ch * 2, cw, ch, true);
            strokes(g, cw * 2, ch * 2, cw * 2, ch);

            colorGrid(g, 0, ch * 3, cw, ch);
            grayscale(g, cw, ch * 3, cw, ch);
            drawRgb(g, cw * 2, ch * 3, cw, ch, false);
            drawRgb(g, cw * 3, ch * 3, cw, ch, true);

            pixelEdges(g, 0, ch * 4, cw, ch);
            overlaps(g, cw, ch * 4, cw, ch);
            mixed(g, cw * 2, ch * 4, cw, ch);
            checker(g, cw * 3, ch * 4, cw, ch);

            arcSizeStress(g, 0, ch * 5, cw * 2, ch);
            roundRectSizeStress(g, cw * 2, ch * 5, cw * 2, ch);
        }

        private static void panel(Graphics g, int x, int y, int w, int h) {
            g.setColor(235, 235, 235);
            g.drawRect(x + 4, y + 4, w - 9, h - 9);
        }

        private static void lineDirections(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 7; i++) {
                g.setColor(20 + i * 28, 70, 220 - i * 20);
                g.drawLine(x + 12, y + 14 + i * 11, x + w - 12, y + 14 + i * 11);
            }
            for (i = 0; i < 7; i++) {
                g.setColor(220 - i * 20, 50 + i * 18, 80);
                g.drawLine(x + 14 + i * 14, y + 12, x + 14 + i * 14, y + h - 18);
            }
            g.setColor(0, 0, 0);
            g.drawLine(x + 14, y + h - 18, x + w - 14, y + 14);
            g.drawLine(x + 14, y + 14, x + w - 14, y + h - 18);
            g.drawLine(x + w / 2, y + 10, x + w / 2, y + h - 10);
        }

        private static void lineDensity(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 32; i++) {
                g.setColor((i * 19) % 255, 60 + (i * 7) % 150, 210 - (i * 5) % 130);
                g.drawLine(x + 10 + (i % 8) * 17, y + 12 + (i / 8) * 22,
                    x + 24 + (i % 8) * 17, y + 22 + (i / 8) * 22);
            }
            g.setColor(0, 0, 0);
            for (i = 0; i < 12; i++) {
                g.drawLine(x + w / 2, y + h / 2, x + 12 + i * (w - 24) / 11, y + 12);
            }
        }

        private static void rectOutlines(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 10; i++) {
                g.setColor(40 + i * 20, 80 + i * 9, 220 - i * 16);
                g.drawRect(x + 12 + (i % 5) * 25, y + 16 + (i / 5) * 38, 20 + (i % 3) * 3, 20 + (i % 2) * 9);
            }
            g.setColor(0, 0, 0);
            g.drawRect(x + w - 48, y + h - 30, 0, 0);
            g.drawRect(x + w - 34, y + h - 30, 1, 1);
            g.drawRect(x + w - 18, y + h - 30, 2, 2);
        }

        private static void rectFills(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 10; i++) {
                g.setColor(220 - i * 18, 40 + i * 18, 80 + i * 12);
                g.fillRect(x + 12 + (i % 5) * 25, y + 16 + (i / 5) * 38, 20 + (i % 3) * 3, 20 + (i % 2) * 9);
            }
            g.setColor(0, 0, 0);
            g.fillRect(x + w - 48, y + h - 30, 1, 1);
            g.fillRect(x + w - 34, y + h - 30, 2, 2);
            g.fillRect(x + w - 18, y + h - 30, 3, 3);
        }

        private static void colorGrid(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 24; i++) {
                g.setColor((i * 37) % 256, (220 - i * 9) & 255, (40 + i * 17) % 256);
                g.fillRect(x + 10 + (i % 4) * 34, y + 12 + (i / 4) * 18, 28, 14);
            }
        }

        private static void triangleMatrix(Graphics g, int x, int y, int w, int h) {
            int[][] p = {
                {50, 10, 12, 62, 82, 62},
                {12, 12, 86, 18, 42, 68},
                {12, 68, 88, 52, 58, 8},
                {18, 14, 30, 74, 88, 34},
                {8, 42, 88, 40, 46, 52},
                {48, 8, 42, 82, 54, 82}
            };
            int i;
            for (i = 0; i < 6; i++) {
                int ox = x + 10 + (i % 3) * 44;
                int oy = y + 14 + (i / 3) * 54;
                g.setColor(45 + i * 42, 70 + i * 28, 205 - i * 30);
                g.fillTriangle(ox + p[i][0] / 2, oy + p[i][1] / 2,
                    ox + p[i][2] / 2, oy + p[i][3] / 2,
                    ox + p[i][4] / 2, oy + p[i][5] / 2);
            }
        }

        private static void triangleEdges(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 8; i++) {
                int ox = x + 12 + (i % 4) * 32;
                int oy = y + 16 + (i / 4) * 48;
                g.setColor(220 - i * 18, 50 + i * 18, 90 + i * 12);
                g.fillTriangle(ox + 16, oy + 4, ox + (i % 2 == 0 ? 2 : 28), oy + 34, ox + 30, oy + 20 + (i % 3) * 4);
                g.setColor(0, 0, 0);
                g.drawLine(ox + 16, oy + 4, ox + (i % 2 == 0 ? 2 : 28), oy + 34);
                g.drawLine(ox + (i % 2 == 0 ? 2 : 28), oy + 34, ox + 30, oy + 20 + (i % 3) * 4);
                g.drawLine(ox + 30, oy + 20 + (i % 3) * 4, ox + 16, oy + 4);
            }
        }

        private static void arcMatrix(Graphics g, int x, int y, int w, int h, boolean fill) {
            int[] s = {0, 45, 90, 180, 270, -45, 30, 120};
            int[] a = {360, 180, 90, -135, 135, 60, 450, -270};
            int[] ww = {28, 36, 44, 52, 30, 42, 58, 66};
            int[] hh = {24, 30, 36, 42, 46, 28, 34, 50};
            int i;
            for (i = 0; i < 8; i++) {
                g.setColor(fill ? 220 - i * 20 : 40 + i * 24, fill ? 45 + i * 18 : 70, fill ? 90 + i * 12 : 220 - i * 20);
                if (fill) {
                    g.fillArc(x + 10 + (i % 4) * 35, y + 14 + (i / 4) * 52, ww[i], hh[i], s[i], a[i]);
                } else {
                    g.drawArc(x + 10 + (i % 4) * 35, y + 14 + (i / 4) * 52, ww[i], hh[i], s[i], a[i]);
                }
            }
        }

        private static void roundRectMatrix(Graphics g, int x, int y, int w, int h, boolean fill) {
            int[] aw = {0, 4, 12, 24, 48, 80, 100, 140};
            int[] ah = {0, 18, 8, 36, 12, 64, 100, 20};
            int[] ww = {28, 34, 42, 50, 58, 66, 36, 46};
            int[] hh = {24, 30, 38, 46, 28, 36, 50, 56};
            int i;
            for (i = 0; i < 8; i++) {
                g.setColor(fill ? 220 - i * 18 : 45 + i * 22, fill ? 55 + i * 14 : 120, fill ? 100 + i * 12 : 220 - i * 18);
                if (fill) {
                    g.fillRoundRect(x + 8 + (i % 4) * 36, y + 14 + (i / 4) * 52, ww[i], hh[i], aw[i], ah[i]);
                } else {
                    g.drawRoundRect(x + 8 + (i % 4) * 36, y + 14 + (i / 4) * 52, ww[i], hh[i], aw[i], ah[i]);
                }
            }
        }

        private static void arcSizeStress(Graphics g, int x, int y, int w, int h) {
            int i;
            int cell = w / 8;
            for (i = 0; i < 8; i++) {
                int px = x + i * cell;
                panel(g, px, y, cell, h);
                g.setColor(40 + i * 22, 80, 220 - i * 16);
                g.drawArc(px + 8, y + 16, cell - 16, h - 36, i * 45, 180);
                g.setColor(220 - i * 18, 50 + i * 14, 90);
                g.fillArc(px + 14, y + h / 2, cell - 28, h / 2 - 16, i * 45 - 90, i == 3 ? -135 : 90 + i * 45);
            }
        }

        private static void roundRectSizeStress(Graphics g, int x, int y, int w, int h) {
            int i;
            int cell = w / 8;
            int[] aw = {0, 4, 16, 48, 8, 24, 72, 120};
            int[] ah = {0, 20, 8, 36, 64, 12, 80, 24};
            for (i = 0; i < 8; i++) {
                int px = x + i * cell;
                panel(g, px, y, cell, h);
                g.setColor(45 + i * 22, 120, 220 - i * 18);
                g.drawRoundRect(px + 7, y + 18, cell - 14, h / 2 - 24, aw[i], ah[i]);
                g.setColor(220 - i * 18, 55 + i * 14, 100 + i * 12);
                g.fillRoundRect(px + 9, y + h / 2 + 4, cell - 18, h / 2 - 22, aw[7 - i], ah[i]);
            }
        }

        private static void strokes(Graphics g, int x, int y, int w, int h) {
            int old = g.getStrokeStyle();
            int i;
            for (i = 0; i < 2; i++) {
                int s = i == 0 ? Graphics.SOLID : Graphics.DOTTED;
                int ox = x + i * (w / 2);
                g.setStrokeStyle(s);
                g.setColor(30, 90, 220);
                g.drawLine(ox + 14, y + 18, ox + w / 2 - 18, y + 18);
                g.setColor(220, 55, 65);
                g.drawRect(ox + 18, y + 34, 48, 34);
                g.setColor(45, 150, 80);
                g.drawArc(ox + 76, y + 30, 54, 42, 30, 270);
                g.setColor(130, 60, 190);
                g.drawRoundRect(ox + 24, y + 82, 88, 34, 20, 14);
                g.setColor(0, 0, 0);
                g.drawLine(ox + 112, y + 18, ox + 142, y + 54);
                g.drawArc(ox + 116, y + 68, 34, 30, 0, 360);
            }
            g.setStrokeStyle(old);
        }

        private static void grayscale(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 16; i++) {
                g.setGrayScale(i * 255 / 15);
                g.fillRect(x + 12 + i * (w - 24) / 16, y + 18, (w - 24) / 16 + 1, h - 36);
            }
        }

        private static void drawRgb(Graphics g, int x, int y, int w, int h, boolean alpha) {
            int ww = 96, hh = 72, xx, yy;
            int[] p = new int[ww * hh];
            if (alpha) {
                for (yy = 0; yy < hh; yy += 8) {
                    for (xx = 0; xx < ww; xx += 8) {
                        g.setColor(((xx + yy) & 8) == 0 ? 220 : 170, 220, 220);
                        g.fillRect(x + 18 + xx, y + 22 + yy, 8, 8);
                    }
                }
            }
            for (yy = 0; yy < hh; yy++) {
                for (xx = 0; xx < ww; xx++) {
                    int a = alpha ? 80 + xx * 160 / ww : 255;
                    p[yy * ww + xx] = (a << 24) | ((xx * 255 / ww) << 16) | ((yy * 255 / hh) << 8) | 150;
                }
            }
            g.drawRGB(p, 0, ww, x + 18, y + 22, ww, hh, alpha);
        }

        private static void pixelEdges(Graphics g, int x, int y, int w, int h) {
            g.setColor(0, 0, 0);
            g.drawLine(x + 8, y + 8, x + 8, y + h - 8);
            g.drawLine(x + 8, y + 8, x + w - 8, y + 8);
            g.setColor(220, 40, 40);
            g.fillRect(x + 24, y + 24, 1, 1);
            g.setColor(40, 100, 220);
            g.fillRect(x + 34, y + 24, 2, 2);
            g.setColor(40, 160, 80);
            g.drawRect(x + 48, y + 24, 0, 0);
            g.setColor(160, 40, 160);
            g.drawLine(x + 64, y + 24, x + 64, y + 24);
            g.setColor(0, 0, 0);
            g.drawRect(x + 84, y + 18, 40, 40);
            g.fillRect(x + 85, y + 19, 1, 38);
            g.fillRect(x + 86, y + 20, 2, 36);
        }

        private static void overlaps(Graphics g, int x, int y, int w, int h) {
            int i;
            for (i = 0; i < 6; i++) {
                g.setColor(220 - i * 18, 45 + i * 22, 120);
                g.fillRect(x + 18 + i * 14, y + 18 + i * 10, 42, 30);
            }
            g.setColor(0, 0, 0);
            for (i = 0; i < 5; i++) {
                g.drawRect(x + 20 + i * 10, y + 72 + i * 6, 70, 34);
            }
        }

        private static void mixed(Graphics g, int x, int y, int w, int h) {
            g.setColor(235, 240, 255);
            g.fillRect(x + 20, y + 20, w - 40, h - 40);
            g.setColor(220, 40, 40);
            g.fillArc(x + 30, y + 30, 48, 48, 45, 270);
            g.setColor(40, 160, 80);
            g.fillTriangle(x + w - 24, y + 28, x + w - 76, y + h - 24, x + w - 24, y + h - 24);
            g.setColor(0, 0, 0);
            g.drawRoundRect(x + 30, y + 30, w - 60, h - 60, 24, 24);
        }

        private static void checker(Graphics g, int x, int y, int w, int h) {
            int xx, yy;
            for (yy = 0; yy < 4; yy++) {
                for (xx = 0; xx < 24; xx++) {
                    g.setColor(((xx + yy) & 1) == 0 ? 230 : 180, 180 + yy * 15, 220 - yy * 20);
                    g.fillRect(x + 12 + xx * (w - 24) / 24, y + 18 + yy * 24, (w - 24) / 24 + 1, 22);
                }
            }
        }
    }
}
