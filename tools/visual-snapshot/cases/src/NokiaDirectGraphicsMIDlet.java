import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;
import com.nokia.mid.ui.DirectGraphics;
import com.nokia.mid.ui.DirectUtils;
import com.nokia.mid.ui.FullCanvas;

public class NokiaDirectGraphicsMIDlet extends MIDlet {
    protected void startApp() {
        FullCanvas c = new DirectCanvas();
        c.setFullScreenMode(true);
        Display.getDisplay(this).setCurrent(c);
    }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class DirectCanvas extends FullCanvas {
        protected void paint(Graphics g) {
            int w=getWidth(),h=getHeight();g.setColor(255,255,255);g.fillRect(0,0,w,h);
            DirectGraphics dg=DirectUtils.getDirectGraphics(g);
            pixels(g,dg,20,20);polys(dg,20,220);images(g,dg,20,430);
        }
        private static void pixels(Graphics g,DirectGraphics dg,int x,int y){int pw=72,ph=72;int[] argb=new int[pw*ph];short[] rgb=new short[pw*ph];byte[] gray=new byte[pw*ph];byte[] mask=new byte[pw*ph];int xx,yy;for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){int i=yy*pw+xx;argb[i]=(0xff<<24)|((xx*255/pw)<<16)|((yy*255/ph)<<8)|180;rgb[i]=(short)0xf800;gray[i]=(byte)((xx+yy)*255/(pw+ph));mask[i]=(byte)(xx<pw/2?255:128);}dg.drawPixels(argb,true,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_INT_8888_ARGB);dg.drawPixels(rgb,true,0,pw,x+110,y,pw,ph,0,DirectGraphics.TYPE_USHORT_565_RGB);dg.drawPixels(gray,mask,0,pw,x+220,y,pw,ph,0,DirectGraphics.TYPE_BYTE_1_GRAY);dg.drawPixels(gray,mask,0,pw,x+330,y,pw,ph,DirectGraphics.ROTATE_90,DirectGraphics.TYPE_BYTE_1_GRAY);}
        private static void polys(DirectGraphics dg,int x,int y){int[] px={x,x+80,x+110,x+46};int[] py={y+30,y,y+70,y+100};dg.setARGBColor(0x8820aa40);dg.fillPolygon(px,0,py,0,4,0);dg.setARGBColor(0xff000000);dg.drawPolygon(px,0,py,0,4,0);dg.setARGBColor(0x886040cc);dg.fillTriangle(x+160,y,x+260,y+40,x+180,y+100,0);dg.setARGBColor(0xff000000);dg.drawTriangle(x+160,y,x+260,y+40,x+180,y+100,0);}
        private static void images(Graphics g,DirectGraphics dg,int x,int y){
            // Create an asymmetric image so rotations/flips are visually distinct
            int sz=42;
            javax.microedition.lcdui.Image img=javax.microedition.lcdui.Image.createImage(sz,sz);
            Graphics ig=img.getGraphics();
            ig.setColor(0x30aa50);ig.fillRect(0,0,sz,sz);
            // Draw a red triangle in the top-left corner to show orientation
            ig.setColor(0xff0000);ig.fillTriangle(0,0,sz/2,0,0,sz/2);
            // Draw a blue dot in the bottom-right
            ig.setColor(0x0000ff);ig.fillArc(sz-12,sz-12,10,10,0,360);
            int i;for(i=0;i<6;i++){int t=0;if(i==1)t=DirectGraphics.FLIP_HORIZONTAL;else if(i==2)t=DirectGraphics.ROTATE_90;else if(i==3)t=DirectGraphics.ROTATE_180;else if(i==4)t=DirectGraphics.ROTATE_270;else if(i==5)t=DirectGraphics.FLIP_VERTICAL;dg.drawImage(img,x+i*70,y,Graphics.TOP|Graphics.LEFT,t);}
            // Gray image drawn right below
            byte[] data=new byte[sz*sz];for(i=0;i<data.length;i++)data[i]=(byte)(i%256);
            javax.microedition.lcdui.Image gray=DirectUtils.createImage(data,sz,sz);
            g.drawImage(gray,x,y+sz+10,Graphics.TOP|Graphics.LEFT);
        }
    }
}
