import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

public class ImageResourceDisplayMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w=getWidth(),h=getHeight();g.setColor(255,255,255);g.fillRect(0,0,w,h);
            Image[] imgs=new Image[4];String[] names={"/icon.png","/logo.png","/splash.png","/transparent.gif"};int i;
            for(i=0;i<4;i++){try{imgs[i]=Image.createImage(names[i]);}catch(Exception e){imgs[i]=fallback(80,50,i);}}
            int cellW=w/2,cellH=h/2;
            for(i=0;i<4;i++){int x=(i%2)*cellW,y=(i/2)*cellH;panel(g,x,y,cellW,cellH);checker(g,x+18,y+18,cellW-36,cellH-36);g.drawImage(imgs[i],x+cellW/2,y+cellH/2,Graphics.HCENTER|Graphics.VCENTER);g.setColor(0,0,0);g.drawRect(x+cellW/2-imgs[i].getWidth()/2,y+cellH/2-imgs[i].getHeight()/2,imgs[i].getWidth(),imgs[i].getHeight());}
        }
        private static void panel(Graphics g,int x,int y,int w,int h){g.setColor(235,235,235);g.drawRect(x+5,y+5,w-11,h-11);}
        private static void checker(Graphics g,int x,int y,int w,int h){int xx,yy;for(yy=0;yy<h;yy+=12)for(xx=0;xx<w;xx+=12){g.setColor(((xx+yy)/12)%2==0?230:190,230,230);g.fillRect(x+xx,y+yy,12,12);}}
        private static Image fallback(int w,int h,int n){Image img=Image.createImage(w,h);Graphics g=img.getGraphics();g.setColor(240,220,220);g.fillRect(0,0,w,h);g.setColor(220,40,40);g.fillTriangle(4,4,w-4,h/2,4,h-4);g.setColor(0,0,0);g.drawRect(0,0,w-1,h-1);return img;}
    }
}
