import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

public class ImageRegionTransformBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w=getWidth();g.setColor(255,255,255);g.fillRect(0,0,w,getHeight());
            int margin=16,gap=12,rw=w-margin*2,colW=(rw-gap)/2,y=12;
            Image normal=src(78,46);Image tall=src(34,82);Image wide=src(94,28);Image normalB=src(72,44);
            drawFullSet(g,normal,"full 78x46",margin,y,colW,226);
            drawFullSet(g,tall,"full 34x82",margin+colW+gap,y,colW,226);
            y+=238;
            drawFullSet(g,wide,"full 94x28",margin,y,colW,226);
            drawPartialSet(g,normalB,"partial 4x2",margin+colW+gap,y,colW,226,false);
            y+=238;
            drawPartialSet(g,normal,"partial 3x3",margin,y,rw,312,true);
        }
        private static Image src(int w,int h){Image img=Image.createImage(w,h);Graphics g=img.getGraphics();g.setColor(250,225,110);g.fillRect(0,0,w,h);g.setColor(0,0,0);g.drawRect(0,0,w-1,h-1);g.drawLine(0,0,w-1,h-1);g.drawLine(w-1,0,0,h-1);g.setColor(220,40,40);g.fillTriangle(3,3,w/2,3,3,h/2);g.setColor(40,100,220);g.fillArc(w-18,h-18,14,14,0,360);g.setColor(40,160,80);g.fillRect(w/2-5,5,10,h-10);return img;}
        private static void drawFullSet(Graphics g,Image img,String title,int x,int y,int w,int h){title(g,title,x,y,w);int top=y+20;int cw=w/4,ch=(h-22)/2,i;for(i=0;i<8;i++){int px=x+(i%4)*cw,py=top+(i/4)*ch,cx=px+cw/2,cy=py+ch/2;cell(g,px,py,cw,ch,cx,cy);g.drawRegion(img,0,0,img.getWidth(),img.getHeight(),i,cx,cy,Graphics.VCENTER|Graphics.HCENTER);}}
        private static void drawPartialSet(Graphics g,Image img,String title,int x,int y,int w,int h,boolean nine){title(g,title,x,y,w);int top=y+20;int count=nine?9:8;int cols=nine?3:4;int rows=nine?3:2;int cw=w/cols,ch=(h-22)/rows,i;int[] sx=nine?new int[]{0,39,19,0,39,19,0,39,19}:new int[]{0,36,18,0,36,18,0,36};int[] sy=nine?new int[]{0,0,11,0,0,11,0,0,11}:new int[]{0,0,11,0,0,11,0,0};int[] rw=nine?new int[]{39,39,39,39,39,39,24,24,39}:new int[]{36,36,36,36,36,36,24,24};int[] rh=nine?new int[]{46,46,24,46,46,24,46,46,24}:new int[]{44,44,22,44,44,22,44,44};int[] tr=nine?new int[]{0,0,0,5,6,3,2,4,7}:new int[]{0,0,0,5,6,3,2,4};for(i=0;i<count;i++){int px=x+(i%cols)*cw,py=top+(i/cols)*ch,cx=px+cw/2,cy=py+ch/2;cell(g,px,py,cw,ch,cx,cy);g.drawRegion(img,sx[i],sy[i],rw[i],rh[i],tr[i],cx,cy,Graphics.VCENTER|Graphics.HCENTER);}}
        private static void title(Graphics g,String s,int x,int y,int w){g.setFont(Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL));g.setColor(0,0,0);g.drawString(s,x,y-6,Graphics.TOP|Graphics.LEFT);g.setColor(220,220,220);g.drawLine(x,y+16,x+w,y+16);}
        private static void cell(Graphics g,int x,int y,int w,int h,int cx,int cy){g.setColor(235,235,235);g.drawRect(x+4,y+4,w-9,h-9);g.setColor(230,40,40);g.drawLine(cx-8,cy,cx+8,cy);g.drawLine(cx,cy-8,cx,cy+8);}
    }
}
