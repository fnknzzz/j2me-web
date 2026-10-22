import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

public class ImageGetRgbBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w=getWidth(),h=getHeight();g.setColor(255,255,255);g.fillRect(0,0,w,h);
            Image a=make(96,72); Image b=make(64,64); int[] outA=new int[96*72]; int[] outB=new int[64*64];
            a.getRGB(outA,0,96,0,0,96,72); b.getRGB(outB,0,64,0,0,64,64);
            g.drawImage(a,20,20,Graphics.TOP|Graphics.LEFT); drawRgb(g,outA,96,72,180,20);
            g.drawImage(b,20,140,Graphics.TOP|Graphics.LEFT); drawRgb(g,outB,64,64,180,140);
            Image c=Image.createRGBImage(outA,96,72,true); g.drawImage(c,360,20,Graphics.TOP|Graphics.LEFT);
            int[] sub=new int[40*28]; a.getRGB(sub,0,40,20,20,40,28); drawRgb(g,sub,40,28,360,140);
        }
        private static Image make(int w,int h){Image img=Image.createImage(w,h);Graphics g=img.getGraphics();g.setColor(250,225,110);g.fillRect(0,0,w,h);g.setColor(0,0,0);g.drawRect(0,0,w-1,h-1);g.drawLine(0,0,w-1,h-1);g.setColor(220,40,40);g.fillTriangle(3,3,w/2,3,3,h/2);g.setColor(40,100,220);g.fillArc(w-18,h-18,14,14,0,360);g.setColor(40,160,80);g.fillRect(w/2-6,6,12,h-12);return img;}
        private static void drawRgb(Graphics g,int[] p,int w,int h,int x,int y){g.drawRGB(p,0,w,x,y,w,h,true);g.setColor(0,0,0);g.drawRect(x,y,w,h);}
    }
}
