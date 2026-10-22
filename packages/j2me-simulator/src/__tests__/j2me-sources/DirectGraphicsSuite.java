import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class DirectGraphicsSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth(); int h = getHeight();
            g.setColor(255,255,255); g.fillRect(0,0,w,h);
            g.setColor(0,120,215); g.fillRect(0,0,w,28); g.setColor(0,0,0);
            g.drawString("DirectGraphicsSuite", w/2, 2, Graphics.TOP | Graphics.HCENTER);

            int pxW=16, pxH=16; int[] argb = new int[pxW*pxH];
            for (int yy=0; yy<pxH; yy++) {
                for (int xx=0; xx<pxW; xx++) {
                    int a=0xFF; int r=(xx*15)&0xFF; int gc=(yy*15)&0xFF; int b=((xx+yy)*7)&0xFF;
                    argb[yy*pxW+xx]=(a<<24)|(r<<16)|(gc<<8)|b;
                }
            }

            short[] rgb565 = new short[pxW*pxH];
            for (int i=0;i<rgb565.length;i++){ rgb565[i]=(short)0xF800; }

            byte[] gray = new byte[pxW*pxH]; byte[] gmask = new byte[pxW*pxH];
            for (int i=0;i<gray.length;i++){ int xx=i%pxW; int yy=i/pxW; gray[i]=(byte)(((yy*12)+(xx*6))&0xFF); gmask[i]=(byte)((xx<pxW/2)?0xFF:0x80); }

            com.nokia.mid.ui.DirectGraphics dg = com.nokia.mid.ui.DirectUtils.getDirectGraphics(g);
            int x0=8; int y0=40; int gap=6;
            int fmt = dg.getNativePixelFormat();
            System.out.println("fmt="+fmt);

            dg.drawPixels(argb, true, 0, pxW, x0, y0, pxW, pxH, 0, com.nokia.mid.ui.DirectGraphics.TYPE_INT_8888_ARGB);
            dg.drawPixels(rgb565, true, 0, pxW, x0+pxW+gap, y0, pxW, pxH, 0, com.nokia.mid.ui.DirectGraphics.TYPE_USHORT_565_RGB);
            dg.drawPixels(gray, gmask, 0, pxW, x0+(pxW+gap)*2, y0, pxW, pxH, 0, com.nokia.mid.ui.DirectGraphics.TYPE_BYTE_1_GRAY);

            dg.drawPixels(gray, gmask, 0, pxW, x0, y0+pxH+gap, pxW, pxH, com.nokia.mid.ui.DirectGraphics.FLIP_HORIZONTAL, com.nokia.mid.ui.DirectGraphics.TYPE_BYTE_1_GRAY);
            dg.drawPixels(gray, gmask, 0, pxW, x0+pxW+gap, y0+pxH+gap, pxW, pxH, com.nokia.mid.ui.DirectGraphics.ROTATE_90, com.nokia.mid.ui.DirectGraphics.TYPE_BYTE_1_GRAY);
            dg.drawPixels(gray, gmask, 0, pxW, x0+(pxW+gap)*2, y0+pxH+gap, pxW, pxH, com.nokia.mid.ui.DirectGraphics.ROTATE_270, com.nokia.mid.ui.DirectGraphics.TYPE_BYTE_1_GRAY);

            byte[] gout = new byte[pxW*pxH]; byte[] mout = new byte[pxW*pxH];
            dg.getPixels(gout, mout, 0, pxW, x0, y0, pxW, pxH, com.nokia.mid.ui.DirectGraphics.TYPE_BYTE_1_GRAY);
            System.out.println("pixels="+gout[0]+","+gout[pxW*pxH-1]+" alpha="+mout[0]+","+mout[pxW*pxH-1]);

            int[] px = new int[]{x0, x0+30, x0+40, x0+20};
            int[] py = new int[]{y0+pxH*2+gap+12, y0+pxH*2+gap+24, y0+pxH*2+gap+54, y0+pxH*2+gap+36};
            dg.setARGBColor(0x8000FF00);
            dg.fillPolygon(px, 0, py, 0, 4, 0);
            dg.setARGBColor(0xFF000000);
            dg.drawPolygon(px, 0, py, 0, 4, 0);
        }
    }

    public DirectGraphicsSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("DirectGraphicsSuite started");
    }

    protected void pauseApp() {
        System.out.println("DirectGraphicsSuite paused");
    }

    protected void destroyApp(boolean unconditional) {
        System.out.println("DirectGraphicsSuite destroyed");
    }
}
