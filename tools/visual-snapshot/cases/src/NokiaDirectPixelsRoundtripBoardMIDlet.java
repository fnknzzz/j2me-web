import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;
import com.nokia.mid.ui.DirectGraphics;
import com.nokia.mid.ui.DirectUtils;
import com.nokia.mid.ui.FullCanvas;

public class NokiaDirectPixelsRoundtripBoardMIDlet extends MIDlet {
    protected void startApp() { FullCanvas c=new Board(); c.setFullScreenMode(true); Display.getDisplay(this).setCurrent(c); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends FullCanvas {
        protected void paint(Graphics g) {
            int w=getWidth();g.setColor(255,255,255);g.fillRect(0,0,w,getHeight());DirectGraphics dg=DirectUtils.getDirectGraphics(g);
            roundtripInt(g,dg,16,20);roundtrip565(g,dg,220,20);roundtripByte(g,dg,424,20);
            transformReadback(g,dg,16,160);
            draw4444(g,dg,16,350);draw1555(g,dg,330,350);
            byteRotate(g,dg,16,540);
        }
        private static void title(Graphics g,String s,int x,int y){g.setFont(Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL));g.setColor(0,0,0);g.drawString(s,x,y,Graphics.TOP|Graphics.LEFT);}
        private static void frame(Graphics g,int x,int y,int w,int h){g.setColor(220,220,220);g.drawRect(x,y,w,h);}
        private static void roundtripInt(Graphics g,DirectGraphics dg,int x,int y){title(g,"int 8888 draw/get",x,y-20);int pw=64,ph=64;int[] p=new int[pw*ph];int xx,yy;for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++)p[yy*pw+xx]=0xff000000|((xx*255/pw)<<16)|((yy*255/ph)<<8)|180;dg.drawPixels(p,true,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_INT_8888_ARGB);int[] out=new int[pw*ph];dg.getPixels(out,0,pw,x,y,pw,ph,0);previewInt(g,out,x+84,y);frame(g,x,y,pw,ph);frame(g,x+84,y,64,64);}
        private static void roundtrip565(Graphics g,DirectGraphics dg,int x,int y){title(g,"ushort 565 draw/get",x,y-20);int pw=64,ph=64;short[] p=new short[pw*ph];int i;for(i=0;i<p.length;i++)p[i]=(short)(i%2==0?0xf800:0x07e0);dg.drawPixels(p,true,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_USHORT_565_RGB);short[] out=new short[pw*ph];dg.getPixels(out,0,pw,x,y,pw,ph,0);previewShort(g,out,x+84,y);frame(g,x,y,pw,ph);frame(g,x+84,y,64,64);}
        private static void roundtripByte(Graphics g,DirectGraphics dg,int x,int y){title(g,"byte gray draw/get",x,y-20);int pw=64,ph=64;byte[] p=new byte[pw*ph];byte[] m=new byte[pw*ph];int i;for(i=0;i<p.length;i++){p[i]=(byte)(i%256);m[i]=(byte)(i%2==0?255:128);}dg.drawPixels(p,m,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_BYTE_1_GRAY);byte[] out=new byte[pw*ph];byte[] mout=new byte[pw*ph];dg.getPixels(out,mout,0,pw,x,y,pw,ph,0);previewByte(g,out,mout,x+84,y);frame(g,x,y,pw,ph);frame(g,x+84,y,64,64);}
        private static void transformReadback(Graphics g,DirectGraphics dg,int x,int y){title(g,"transform readback",x,y-20);int pw=64,ph=64;int[] p=new int[pw*ph];int xx,yy;for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++)p[yy*pw+xx]=0xff000000|((xx*255/pw)<<16)|((yy*255/ph)<<8)|100;dg.drawPixels(p,true,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_INT_8888_ARGB);dg.drawPixels(p,true,0,pw,x+100,y,pw,ph,DirectGraphics.ROTATE_90,DirectGraphics.TYPE_INT_8888_ARGB);int[] out=new int[pw*ph];dg.getPixels(out,0,pw,x+100,y,pw,ph,DirectGraphics.ROTATE_90);previewInt(g,out,x+200,y);dg.drawPixels(p,true,0,pw,x+330,y,pw,ph,DirectGraphics.ROTATE_180,DirectGraphics.TYPE_INT_8888_ARGB);int[] out2=new int[pw*ph];dg.getPixels(out2,0,pw,x+330,y,pw,ph,DirectGraphics.ROTATE_180);previewInt(g,out2,x+430,y);frame(g,x,y,pw,ph);frame(g,x+100,y,pw,ph);frame(g,x+200,y,64,64);frame(g,x+330,y,pw,ph);frame(g,x+430,y,64,64);}
        private static void draw4444(Graphics g,DirectGraphics dg,int x,int y){title(g,"ushort 4444 alpha",x,y-20);int pw=120,ph=90;short[] p=new short[pw*ph];int xx,yy;for(yy=0;yy<ph;yy+=8)for(xx=0;xx<pw*2+30;xx+=8){g.setColor(((xx+yy)&8)==0?200:160,200,200);g.fillRect(x+xx,y+yy,8,8);}for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){int a=xx*15/pw;int r=yy*15/ph;int gg=8;int b=15-xx*15/pw;p[yy*pw+xx]=(short)((a<<12)|(r<<8)|(gg<<4)|b);}dg.drawPixels(p,true,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_USHORT_4444_ARGB);short[] p2=new short[pw*ph];for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++)p2[yy*pw+xx]=(short)(0xf000|((xx*15/pw)<<8)|((yy*15/ph)<<4)|12);dg.drawPixels(p2,true,0,pw,x+150,y,pw,ph,0,DirectGraphics.TYPE_USHORT_4444_ARGB);frame(g,x,y,pw,ph);frame(g,x+150,y,pw,ph);}
        private static void draw1555(Graphics g,DirectGraphics dg,int x,int y){title(g,"ushort 1555 alpha",x,y-20);int pw=120,ph=90;short[] p=new short[pw*ph];int xx,yy;for(yy=0;yy<ph;yy+=8)for(xx=0;xx<pw*2+30;xx+=8){g.setColor(((xx+yy)&8)==0?200:160,200,200);g.fillRect(x+xx,y+yy,8,8);}for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){int a=xx<pw/2?1:0;int r=xx*31/pw;int gg=yy*31/ph;int b=16;p[yy*pw+xx]=(short)((a<<15)|(r<<10)|(gg<<5)|b);}dg.drawPixels(p,true,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_USHORT_1555_ARGB);short[] p2=new short[pw*ph];for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++)p2[yy*pw+xx]=(short)((1<<15)|((xx*31/pw)<<10)|((yy*31/ph)<<5)|(31-xx*31/pw));dg.drawPixels(p2,true,0,pw,x+150,y,pw,ph,0,DirectGraphics.TYPE_USHORT_1555_ARGB);frame(g,x,y,pw,ph);frame(g,x+150,y,pw,ph);}
        private static void byteRotate(Graphics g,DirectGraphics dg,int x,int y){title(g,"byte gray normal / rotate90",x,y-20);int pw=120,ph=90;byte[] p=new byte[pw*ph];byte[] m=new byte[pw*ph];int xx,yy;for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){int i=yy*pw+xx;p[i]=(byte)((xx+yy)*255/(pw+ph));m[i]=(byte)(xx<pw/2?255:128);}dg.drawPixels(p,m,0,pw,x,y,pw,ph,0,DirectGraphics.TYPE_BYTE_1_GRAY);dg.drawPixels(p,m,0,pw,x+150,y,pw,ph,DirectGraphics.ROTATE_90,DirectGraphics.TYPE_BYTE_1_GRAY);frame(g,x,y,pw,ph);frame(g,x+150,y,pw,ph);}
        private static void previewInt(Graphics g,int[] p,int x,int y){int xx,yy;for(yy=0;yy<32;yy++)for(xx=0;xx<32;xx++){g.setColor(p[(yy*2)*64+(xx*2)]);g.fillRect(x+xx*2,y+yy*2,2,2);}}
        private static void previewShort(Graphics g,short[] p,int x,int y){int xx,yy;for(yy=0;yy<32;yy++)for(xx=0;xx<32;xx++){int v=p[(yy*2)*64+(xx*2)]&0xffff;int r=((v>>11)&31)<<3;int gg=((v>>5)&63)<<2;int b=(v&31)<<3;g.setColor(r,gg,b);g.fillRect(x+xx*2,y+yy*2,2,2);}}
        private static void previewByte(Graphics g,byte[] p,byte[] m,int x,int y){int xx,yy;for(yy=0;yy<32;yy++)for(xx=0;xx<32;xx++){int idx=(yy*2)*64+(xx*2);int v=p[idx]&255;int a=m[idx]&255;g.setColor((v*a)/255,(v*a)/255,(v*a)/255);g.fillRect(x+xx*2,y+yy*2,2,2);}}
    }
}
