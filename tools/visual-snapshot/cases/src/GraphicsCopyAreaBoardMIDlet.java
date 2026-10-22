import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class GraphicsCopyAreaBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w=getWidth(), h=getHeight();
            g.setColor(255,255,255); g.fillRect(0,0,w,h);
            int row=h/3;
            overlap(g,0,0,w,row);
            clipTranslate(g,0,row,w,row);
            anchors(g,0,row*2,w,row);
        }
        private static void overlap(Graphics g,int x,int y,int w,int h){
            int sw=80,sh=80;
            int sx=x+20,sy=y+20;
            g.setColor(240,240,240);g.fillRect(sx,sy,sw,sh);
            g.setColor(220,40,40);g.fillRect(sx+10,sy+10,30,30);
            g.setColor(40,100,220);g.fillRect(sx+40,sy+40,30,30);
            g.setColor(0,0,0);g.drawRect(sx,sy,sw,sh);
            g.copyArea(sx,sy,sw,sh,sx+20,sy,Graphics.TOP|Graphics.LEFT);
            g.setColor(0,0,0);g.drawRect(sx+20,sy,sw,sh);
            int sx2=x+200,sy2=y+20;
            g.setColor(240,240,240);g.fillRect(sx2,sy2,sw,sh);
            g.setColor(40,160,80);g.fillTriangle(sx2+10,sy2+10,sx2+70,sy2+10,sx2+40,sy2+70);
            g.setColor(0,0,0);g.drawRect(sx2,sy2,sw,sh);
            g.copyArea(sx2,sy2,sw,sh,sx2,sy2+20,Graphics.TOP|Graphics.LEFT);
            g.setColor(0,0,0);g.drawRect(sx2,sy2+20,sw,sh);
            int sx3=x+400,sy3=y+40;
            g.setColor(240,240,240);g.fillRect(sx3,sy3,sw,sh);
            g.setColor(130,40,160);g.fillArc(sx3+15,sy3+15,50,50,0,360);
            g.setColor(0,0,0);g.drawRect(sx3,sy3,sw,sh);
            g.copyArea(sx3,sy3,sw,sh,sx3-20,sy3-20,Graphics.TOP|Graphics.LEFT);
            g.setColor(0,0,0);g.drawRect(sx3-20,sy3-20,sw,sh);
        }
        private static void clipTranslate(Graphics g,int x,int y,int w,int h){
            int sw=80,sh=80;
            int sx=x+20,sy=y+20;
            g.setColor(240,240,240);g.fillRect(sx,sy,sw,sh);
            g.setColor(220,40,40);g.fillRect(sx+10,sy+10,60,60);
            g.setColor(0,0,0);g.drawRect(sx,sy,sw,sh);
            int ox=g.getClipX(),oy=g.getClipY(),ow=g.getClipWidth(),oh=g.getClipHeight();
            int dx=x+140,dy=y+20;
            g.setClip(dx,dy,40,sh);
            g.copyArea(sx,sy,sw,sh,dx,dy,Graphics.TOP|Graphics.LEFT);
            g.setClip(ox,oy,ow,oh);
            g.setColor(0,0,0);g.drawRect(dx,dy,sw,sh);
            g.setColor(255,0,0);g.drawRect(dx,dy,40,sh);
            int sx2=x+280,sy2=y+20;
            g.setColor(240,240,240);g.fillRect(sx2,sy2,sw,sh);
            g.setColor(40,100,220);g.fillTriangle(sx2+10,sy2+10,sx2+70,sy2+10,sx2+40,sy2+70);
            g.setColor(0,0,0);g.drawRect(sx2,sy2,sw,sh);
            g.translate(30,20);
            g.copyArea(sx2-30,sy2-20,sw,sh,sx2+80-30,sy2-20,Graphics.TOP|Graphics.LEFT);
            g.translate(-30,-20);
            g.setColor(0,0,0);g.drawRect(sx2+80,sy2,sw,sh);
            int sx3=x+480,sy3=y+20;
            g.setColor(240,240,240);g.fillRect(sx3,sy3,60,60);
            g.setColor(40,160,80);g.fillRect(sx3+10,sy3+10,40,40);
            g.setColor(0,0,0);g.drawRect(sx3,sy3,60,60);
            g.translate(10,10);
            int ox2=g.getClipX(),oy2=g.getClipY(),ow2=g.getClipWidth(),oh2=g.getClipHeight();
            g.setClip(sx3+50-10,sy3+70-10,60,40);
            g.copyArea(sx3-10,sy3-10,60,60,sx3+50-10,sy3+70-10,Graphics.TOP|Graphics.LEFT);
            g.setClip(ox2,oy2,ow2,oh2);
            g.translate(-10,-10);
            g.setColor(0,0,0);g.drawRect(sx3+50,sy3+70,60,60);
        }
        private static void anchors(Graphics g,int x,int y,int w,int h){
            int sw=60,sh=60;
            int sx=x+20,sy=y+20;
            g.setColor(240,240,240);g.fillRect(sx,sy,sw,sh);
            g.setColor(220,40,40);g.fillRect(sx+5,sy+5,20,20);
            g.setColor(40,100,220);g.fillRect(sx+35,sy+35,20,20);
            g.setColor(0,0,0);g.drawRect(sx,sy,sw,sh);
            int[] anc={Graphics.TOP|Graphics.LEFT,Graphics.TOP|Graphics.HCENTER,Graphics.TOP|Graphics.RIGHT,Graphics.VCENTER|Graphics.LEFT,Graphics.VCENTER|Graphics.HCENTER,Graphics.VCENTER|Graphics.RIGHT,Graphics.BOTTOM|Graphics.LEFT,Graphics.BOTTOM|Graphics.HCENTER,Graphics.BOTTOM|Graphics.RIGHT};
            int i;for(i=0;i<9;i++){int dx2=x+125+(i%3)*160,dy2=y+36+(i/3)*48;g.setColor(255,0,0);g.drawLine(dx2-6,dy2,dx2+6,dy2);g.drawLine(dx2,dy2-6,dx2,dy2+6);g.copyArea(sx,sy,sw,sh,dx2,dy2,anc[i]);}
        }
    }
}
