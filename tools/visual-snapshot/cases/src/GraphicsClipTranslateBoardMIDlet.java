import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class GraphicsClipTranslateBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w=getWidth(),h=getHeight();g.setColor(255,255,255);g.fillRect(0,0,w,h);int cols=4,rows=4,cw=w/cols,ch=h/rows;int i;for(i=0;i<16;i++)panel(g,(i%cols)*cw,(i/cols)*ch,cw,ch);
            clipA(g,0,0,cw,ch);clipB(g,cw,0,cw,ch);clipC(g,cw*2,0,cw,ch);clipD(g,cw*3,0,cw,ch);
            transA(g,0,ch,cw,ch);transB(g,cw,ch,cw,ch);transC(g,cw*2,ch,cw,ch);transD(g,cw*3,ch,cw,ch);
            mixA(g,0,ch*2,cw,ch);mixB(g,cw,ch*2,cw,ch);mixC(g,cw*2,ch*2,cw,ch);mixD(g,cw*3,ch*2,cw,ch);
            restoreA(g,0,ch*3,cw,ch);restoreB(g,cw,ch*3,cw,ch);restoreC(g,cw*2,ch*3,cw,ch);restoreD(g,cw*3,ch*3,cw,ch);
        }
        private static void panel(Graphics g,int x,int y,int w,int h){g.setColor(235,235,235);g.drawRect(x+4,y+4,w-9,h-9);}
        private static void clipA(Graphics g,int x,int y,int w,int h){int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();g.setColor(240,240,240);g.fillRect(x+14,y+18,w-28,h-36);g.setClip(x+36,y+30,w-72,h-60);fillPattern(g,x+14,y+18,w-28,h-36);g.setClip(ox,oy,ow,oh);g.setColor(0,0,0);g.drawRect(x+36,y+30,w-72,h-60);}
        private static void clipB(Graphics g,int x,int y,int w,int h){int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();g.setClip(x+20,y+20,w-40,h-40);g.clipRect(x+40,y+34,w-80,h-68);fillPattern(g,x+12,y+12,w-24,h-24);g.setClip(ox,oy,ow,oh);g.setColor(0,0,0);g.drawRect(x+40,y+34,w-80,h-68);}
        private static void clipC(Graphics g,int x,int y,int w,int h){int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();g.setClip(x+20,y+20,40,40);g.setColor(220,40,40);g.fillRect(x+12,y+12,w-24,h-24);g.setClip(x+70,y+50,50,40);g.setColor(40,100,220);g.fillRect(x+12,y+12,w-24,h-24);g.setClip(ox,oy,ow,oh);}
        private static void clipD(Graphics g,int x,int y,int w,int h){int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();g.setClip(x+24,y+24,w-48,h-48);g.setColor(40,160,80);g.fillTriangle(x+12,y+h-12,x+w/2,y+12,x+w-12,y+h-12);g.setClip(ox,oy,ow,oh);}
        private static void transA(Graphics g,int x,int y,int w,int h){g.setColor(190,190,190);g.drawRect(x+18,y+18,44,34);g.translate(36,28);g.setColor(220,40,40);g.fillRect(x+18,y+18,44,34);g.translate(-36,-28);}
        private static void transB(Graphics g,int x,int y,int w,int h){g.translate(20,20);g.setColor(40,100,220);g.drawArc(x+18,y+18,60,40,45,270);g.translate(18,22);g.setColor(40,160,80);g.fillArc(x+18,y+18,40,28,0,180);g.translate(-38,-42);}
        private static void transC(Graphics g,int x,int y,int w,int h){g.translate(28,30);g.setColor(220,40,40);g.fillTriangle(x+16,y+16,x+54,y+84,x+88,y+28);g.translate(-28,-30);g.setColor(0,0,0);g.drawRect(x+16,y+16,90,70);}
        private static void transD(Graphics g,int x,int y,int w,int h){g.translate(30,20);fillPattern(g,x+10,y+10,70,60);g.translate(-30,-20);}
        private static void mixA(Graphics g,int x,int y,int w,int h){int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();g.setClip(x+34,y+28,w-68,h-56);g.translate(18,12);fillPattern(g,x+12,y+12,w-24,h-24);g.translate(-18,-12);g.setClip(ox,oy,ow,oh);}
        private static void mixB(Graphics g,int x,int y,int w,int h){int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();g.translate(24,20);g.setClip(x+24,y+24,46,46);g.setColor(220,40,40);g.fillRect(x+12,y+12,90,74);g.translate(-24,-20);g.setClip(ox,oy,ow,oh);}
        private static void mixC(Graphics g,int x,int y,int w,int h){g.translate(24,20);g.setColor(40,100,220);g.drawLine(x+12,y+12,x+110,y+90);g.drawLine(x+12,y+90,x+110,y+12);g.translate(-24,-20);}
        private static void mixD(Graphics g,int x,int y,int w,int h){int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();g.setClip(x+20,y+20,w-40,h-40);g.translate(24,20);g.setColor(40,160,80);g.fillArc(x+12,y+12,80,60,0,360);g.translate(-24,-20);g.setClip(ox,oy,ow,oh);}
        private static void restoreA(Graphics g,int x,int y,int w,int h){g.setColor(0,0,0);g.drawRect(x+18,y+18,90,54);g.drawLine(x+18,y+72,x+108,y+18);}
        private static void restoreB(Graphics g,int x,int y,int w,int h){g.setColor(220,40,40);g.fillRect(x+24,y+24,36,36);g.setColor(40,100,220);g.fillRect(x+72,y+24,36,36);g.setColor(40,160,80);g.fillRect(x+48,y+70,36,20);}
        private static void restoreC(Graphics g,int x,int y,int w,int h){g.setColor(220,40,40);g.drawArc(x+20,y+20,40,28,0,180);g.setColor(40,100,220);g.drawArc(x+70,y+20,40,28,180,180);g.setColor(40,160,80);g.drawArc(x+45,y+62,40,28,45,270);}
        private static void restoreD(Graphics g,int x,int y,int w,int h){fillPattern(g,x+14,y+18,w-28,h-36);}
        private static void fillPattern(Graphics g,int x,int y,int w,int h){int i;for(i=0;i<8;i++){g.setColor(40+i*22,60+i*10,220-i*14);g.fillRect(x+6+i*12,y+6+i*8,18,12);g.drawLine(x+4,y+6+i*10,x+w-4,y+6+i*10);}}
    }
}
