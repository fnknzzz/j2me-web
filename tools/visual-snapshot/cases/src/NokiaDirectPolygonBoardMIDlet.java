import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;
import com.nokia.mid.ui.DirectGraphics;
import com.nokia.mid.ui.DirectUtils;
import com.nokia.mid.ui.FullCanvas;

public class NokiaDirectPolygonBoardMIDlet extends MIDlet {
    protected void startApp() { FullCanvas c=new Board(); c.setFullScreenMode(true); Display.getDisplay(this).setCurrent(c); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends FullCanvas {
        protected void paint(Graphics g) {
            int w=getWidth(),h=getHeight();g.setColor(255,255,255);g.fillRect(0,0,w,h);
            DirectGraphics dg=DirectUtils.getDirectGraphics(g);
            int row=h/4;
            fillVariousPolygons(dg,0,0,w,row);
            drawPolygonsWithOffset(dg,0,row,w,row);
            concavePolygons(dg,0,row*2,w,row);
            transparentOverlap(g,dg,0,row*3,w,row);
        }
        private static void fillVariousPolygons(DirectGraphics dg,int x,int y,int w,int h){
            int cx,cy,r,i;
            cx=x+60;cy=y+h/2;r=60;
            int[] tx={cx,cx-52,cx+52};int[] ty={cy-60,cy+40,cy+40};
            dg.fillPolygon(tx,0,ty,0,3,0xffff4040);

            cx=x+180;cy=y+h/2;
            int[] sx={cx-50,cx+50,cx+50,cx-50};int[] sy={cy-50,cy-50,cy+50,cy+50};
            dg.fillPolygon(sx,0,sy,0,4,0xff40cc40);

            cx=x+310;cy=y+h/2;r=55;
            int[] px=new int[5];int[] py=new int[5];
            for(i=0;i<5;i++){double a=Math.PI*2*i/5-Math.PI/2;px[i]=cx+(int)(r*Math.cos(a));py[i]=cy+(int)(r*Math.sin(a));}
            dg.fillPolygon(px,0,py,0,5,0xff4040cc);

            cx=x+440;cy=y+h/2;r=50;
            int[] hx=new int[6];int[] hy=new int[6];
            for(i=0;i<6;i++){double a=Math.PI*2*i/6-Math.PI/2;hx[i]=cx+(int)(r*Math.cos(a));hy[i]=cy+(int)(r*Math.sin(a));}
            dg.fillPolygon(hx,0,hy,0,6,0xffcc8040);

            cx=x+570;cy=y+h/2;r=50;
            int[] ox=new int[8];int[] oy=new int[8];
            for(i=0;i<8;i++){double a=Math.PI*2*i/8-Math.PI/2;ox[i]=cx+(int)(r*Math.cos(a));oy[i]=cy+(int)(r*Math.sin(a));}
            dg.fillPolygon(ox,0,oy,0,8,0xff8040cc);
        }
        private static void drawPolygonsWithOffset(DirectGraphics dg,int x,int y,int w,int h){
            int cx,cy,r,i;
            cx=x+80;cy=y+h/2;r=55;
            int[] px=new int[7];int[] py=new int[7];
            px[0]=9999;py[0]=9999;
            for(i=0;i<5;i++){double a=Math.PI*2*i/5-Math.PI/2;px[i+1]=cx+(int)(r*Math.cos(a));py[i+1]=cy+(int)(r*Math.sin(a));}
            px[6]=0;py[6]=0;
            dg.drawPolygon(px,1,py,1,5,0xff000000);

            cx=x+220;cy=y+h/2;r=55;
            int[] qx=new int[7];int[] qy=new int[7];
            qx[0]=9999;qy[0]=9999;
            for(i=0;i<5;i++){double a=Math.PI*2*i/5-Math.PI/2;qx[i+1]=cx+(int)(r*Math.cos(a));qy[i+1]=cy+(int)(r*Math.sin(a));}
            qx[6]=0;qy[6]=0;
            dg.fillPolygon(qx,1,qy,1,5,0xff6060dd);

            cx=x+370;cy=y+h/2;r=50;
            int[] hx=new int[6];int[] hy=new int[6];
            for(i=0;i<6;i++){double a=Math.PI*2*i/6-Math.PI/2;hx[i]=cx+(int)(r*Math.cos(a));hy[i]=cy+(int)(r*Math.sin(a));}
            dg.drawPolygon(hx,0,hy,0,6,0xff0000ff);

            cx=x+520;cy=y+h/2;r=50;
            int[] ox=new int[8];int[] oy=new int[8];
            for(i=0;i<8;i++){double a=Math.PI*2*i/8-Math.PI/2;ox[i]=cx+(int)(r*Math.cos(a));oy[i]=cy+(int)(r*Math.sin(a));}
            dg.drawPolygon(ox,0,oy,0,8,0xffff8800);
        }
        private static void concavePolygons(DirectGraphics dg,int x,int y,int w,int h){
            int[] ax={x+10,x+60,x+60,x+110,x+60,x+60};
            int[] ay={y+h/2-20,y+h/2-20,y+20,y+h/2,y+h-20,y+h/2+20};
            dg.fillPolygon(ax,0,ay,0,6,0xff40aa60);
            dg.drawPolygon(ax,0,ay,0,6,0xff000000);

            int cx=x+220,cy=y+h/2,ro=65,ri=26,i;
            int[] stx=new int[10];int[] sty=new int[10];
            for(i=0;i<10;i++){int rr=(i%2==0)?ro:ri;double a=Math.PI*2*i/10-Math.PI/2;stx[i]=cx+(int)(rr*Math.cos(a));sty[i]=cy+(int)(rr*Math.sin(a));}
            dg.fillPolygon(stx,0,sty,0,10,0xffddaa20);
            dg.drawPolygon(stx,0,sty,0,10,0xff000000);

            int[] bx={x+340,x+420,x+340,x+420};
            int[] by={y+20,y+h-20,y+h-20,y+20};
            dg.fillPolygon(bx,0,by,0,4,0x88cc4040);
            dg.drawPolygon(bx,0,by,0,4,0xff000000);

            int cx2=x+520,cy2=y+h/2;
            int[] lx={cx2-50,cx2,cx2+50,cx2+20,cx2+50,cx2,cx2-50,cx2-20};
            int[] ly={cy2-60,cy2-20,cy2-60,cy2,cy2+60,cy2+20,cy2+60,cy2};
            dg.fillPolygon(lx,0,ly,0,8,0xff6060cc);
            dg.drawPolygon(lx,0,ly,0,8,0xff000000);
        }
        private static void transparentOverlap(Graphics g,DirectGraphics dg,int x,int y,int w,int h){
            int cx=x+100,cy=y+h/2,r=60,i;
            int[] hx=new int[6];int[] hy=new int[6];
            for(i=0;i<6;i++){double a=Math.PI*2*i/6-Math.PI/2;hx[i]=cx+(int)(r*Math.cos(a));hy[i]=cy+(int)(r*Math.sin(a));}
            dg.fillPolygon(hx,0,hy,0,6,0x80ff0000);

            cx=x+160;
            int[] px=new int[5];int[] py=new int[5];
            for(i=0;i<5;i++){double a=Math.PI*2*i/5-Math.PI/2;px[i]=cx+(int)(r*Math.cos(a));py[i]=cy+(int)(r*Math.sin(a));}
            dg.fillPolygon(px,0,py,0,5,0x8000ff00);

            int[] sx={x+130,x+230,x+230,x+130};int[] sy={cy-50,cy-50,cy+50,cy+50};
            dg.fillPolygon(sx,0,sy,0,4,0x800000ff);

            dg.fillTriangle(x+300,y+20,x+380,y+h-20,x+460,y+20,0xffff6600);
            dg.drawTriangle(x+300,y+20,x+380,y+h-20,x+460,y+20,0xff000000);
            dg.fillTriangle(x+400,y+40,x+480,y+h-10,x+560,y+40,0xff006688);
            dg.drawTriangle(x+400,y+40,x+480,y+h-10,x+560,y+40,0xff000000);
            dg.fillTriangle(x+340,y+h/2,x+420,y+h-10,x+500,y+h/2,0x8800cc66);
        }
    }
}
