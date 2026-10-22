import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;
import com.nokia.mid.ui.DirectGraphics;
import com.nokia.mid.ui.DirectUtils;
import com.nokia.mid.ui.FullCanvas;

public class NokiaDirectPixelsByteBoardMIDlet extends MIDlet {
    protected void startApp() { FullCanvas c=new Board(); c.setFullScreenMode(true); Display.getDisplay(this).setCurrent(c); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends FullCanvas {
        protected void paint(Graphics g) {
            int w=getWidth(),h=getHeight();g.setColor(255,255,255);g.fillRect(0,0,w,h);
            DirectGraphics dg=DirectUtils.getDirectGraphics(g);
            byte1GrayVert(g,dg,16,20);
            byte2Gray(g,dg,330,20);
            byte4Gray(g,dg,16,250);
            byte8Gray(g,dg,330,250);
            byte332Rgb(g,dg,16,480);
            byteManipulations(g,dg,330,480);
        }
        private static void byte1GrayVert(Graphics g,DirectGraphics dg,int x,int y){
            int pw=64,ph=64;
            int cols=pw;int rows=(ph+7)/8;
            byte[] pix=new byte[cols*rows];
            byte[] mask=new byte[cols*rows];
            int c,r;
            for(c=0;c<cols;c++)for(r=0;r<rows;r++){int idx=r*cols+c;pix[idx]=(byte)((c%2==0)?0xAA:0x55);mask[idx]=(byte)0xFF;}
            dg.drawPixels(pix,mask,0,cols,x,y,pw,ph,0,-1);
            g.setColor(0,0,0);g.drawRect(x-1,y-1,pw+1,ph+1);
            byte[] out=new byte[cols*rows];byte[] mout=new byte[cols*rows];
            dg.getPixels(out,mout,0,cols,x,y,pw,ph,-1);
            previewGray1V(g,out,pw,ph,x+80,y);
            label(g,"1_GRAY_V",x,y+ph+4);
        }
        private static void byte2Gray(Graphics g,DirectGraphics dg,int x,int y){
            int pw=64,ph=64;
            int bpl=(pw+3)/4;
            byte[] pix=new byte[bpl*ph];
            byte[] mask=new byte[bpl*ph];
            int xx,yy;
            for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){
                int val=(xx*3)/pw;
                int byteIdx=yy*bpl+xx/4;
                int shift=6-(xx%4)*2;
                pix[byteIdx]=(byte)(pix[byteIdx]|(val<<shift));
                mask[byteIdx]=(byte)(mask[byteIdx]|(3<<shift));
            }
            dg.drawPixels(pix,mask,0,bpl,x,y,pw,ph,0,2);
            g.setColor(0,0,0);g.drawRect(x-1,y-1,pw+1,ph+1);
            byte[] out=new byte[bpl*ph];byte[] mout=new byte[bpl*ph];
            dg.getPixels(out,mout,0,bpl,x,y,pw,ph,2);
            previewGrayN(g,out,pw,ph,bpl,2,x+80,y);
            label(g,"2_GRAY",x,y+ph+4);
        }
        private static void byte4Gray(Graphics g,DirectGraphics dg,int x,int y){
            int pw=64,ph=64;
            int bpl=(pw+1)/2;
            byte[] pix=new byte[bpl*ph];
            byte[] mask=new byte[bpl*ph];
            int xx,yy;
            for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){
                int val=((xx+yy)*15)/126;
                int byteIdx=yy*bpl+xx/2;
                int shift=(xx%2==0)?4:0;
                pix[byteIdx]=(byte)(pix[byteIdx]|(val<<shift));
                mask[byteIdx]=(byte)(mask[byteIdx]|(0xF<<shift));
            }
            dg.drawPixels(pix,mask,0,bpl,x,y,pw,ph,0,4);
            g.setColor(0,0,0);g.drawRect(x-1,y-1,pw+1,ph+1);
            byte[] out=new byte[bpl*ph];byte[] mout=new byte[bpl*ph];
            dg.getPixels(out,mout,0,bpl,x,y,pw,ph,4);
            previewGrayN(g,out,pw,ph,bpl,4,x+80,y);
            label(g,"4_GRAY",x,y+ph+4);
        }
        private static void byte8Gray(Graphics g,DirectGraphics dg,int x,int y){
            int pw=64,ph=64;
            byte[] pix=new byte[pw*ph];
            byte[] mask=new byte[pw*ph];
            int xx,yy;
            for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){
                int dx=xx-32,dy=yy-32;
                int dist=(int)Math.sqrt(dx*dx+dy*dy);
                int val=dist*255/45;if(val>255)val=255;
                int idx=yy*pw+xx;
                pix[idx]=(byte)val;
                mask[idx]=(byte)0xFF;
            }
            dg.drawPixels(pix,mask,0,pw,x,y,pw,ph,0,8);
            g.setColor(0,0,0);g.drawRect(x-1,y-1,pw+1,ph+1);
            byte[] out=new byte[pw*ph];byte[] mout=new byte[pw*ph];
            dg.getPixels(out,mout,0,pw,x,y,pw,ph,8);
            previewGray8(g,out,pw,ph,x+80,y);
            label(g,"8_GRAY",x,y+ph+4);
        }
        private static void byte332Rgb(Graphics g,DirectGraphics dg,int x,int y){
            int pw=64,ph=64;
            byte[] pix=new byte[pw*ph];
            byte[] mask=new byte[pw*ph];
            int xx,yy;
            for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){
                int r=(xx*7)/63;
                int gr=(yy*7)/63;
                int b=2;
                int idx=yy*pw+xx;
                pix[idx]=(byte)((r<<5)|(gr<<2)|b);
                mask[idx]=(byte)0xFF;
            }
            dg.drawPixels(pix,mask,0,pw,x,y,pw,ph,0,332);
            g.setColor(0,0,0);g.drawRect(x-1,y-1,pw+1,ph+1);
            byte[] out=new byte[pw*ph];byte[] mout=new byte[pw*ph];
            dg.getPixels(out,mout,0,pw,x,y,pw,ph,332);
            previewRgb332(g,out,pw,ph,x+80,y);
            label(g,"332_RGB",x,y+ph+4);
        }
        private static void byteManipulations(Graphics g,DirectGraphics dg,int x,int y){
            int pw=48,ph=48;
            byte[] pix=new byte[pw*ph];
            byte[] mask=new byte[pw*ph];
            int xx,yy;
            for(yy=0;yy<ph;yy++)for(xx=0;xx<pw;xx++){
                int idx=yy*pw+xx;
                int val=0;
                if(yy<12)val=220;
                else if(xx<12)val=180;
                else val=40+(xx*yy)%80;
                pix[idx]=(byte)val;mask[idx]=(byte)0xFF;
            }
            dg.drawPixels(pix,mask,0,pw,x,y,pw,ph,0,8);
            g.setColor(0,0,0);g.drawRect(x-1,y-1,pw+1,ph+1);
            dg.drawPixels(pix,mask,0,pw,x+60,y,pw,ph,DirectGraphics.ROTATE_90,8);
            g.setColor(0,0,0);g.drawRect(x+59,y-1,pw+1,ph+1);
            dg.drawPixels(pix,mask,0,pw,x+120,y,pw,ph,DirectGraphics.ROTATE_180,8);
            g.setColor(0,0,0);g.drawRect(x+119,y-1,pw+1,ph+1);
            dg.drawPixels(pix,mask,0,pw,x+180,y,pw,ph,DirectGraphics.FLIP_HORIZONTAL,8);
            g.setColor(0,0,0);g.drawRect(x+179,y-1,pw+1,ph+1);
            label(g,"Manipulations",x,y+ph+4);
        }
        private static void previewGray1V(Graphics g,byte[] data,int pw,int ph,int x,int y){
            int cols=pw;int rows=(ph+7)/8;
            int xx,yy;
            for(yy=0;yy<32;yy++)for(xx=0;xx<32;xx++){
                int srcX=xx*2,srcY=yy*2;
                int row=srcY/8;int bit=srcY%8;
                int byteVal=data[row*cols+srcX]&0xFF;
                int on=(byteVal>>bit)&1;
                int gray=on==1?255:0;
                g.setColor(gray,gray,gray);g.fillRect(x+xx*2,y+yy*2,2,2);
            }
            g.setColor(0,0,0);g.drawRect(x-1,y-1,65,65);
        }
        private static void previewGrayN(Graphics g,byte[] data,int pw,int ph,int bpl,int bitsPerPixel,int x,int y){
            int pixPerByte=8/bitsPerPixel;int maxVal=(1<<bitsPerPixel)-1;
            int xx,yy;
            for(yy=0;yy<32;yy++)for(xx=0;xx<32;xx++){
                int srcX=xx*2,srcY=yy*2;
                int byteIdx=srcY*bpl+srcX/pixPerByte;
                int pixInByte=srcX%pixPerByte;
                int shift=(8-bitsPerPixel)-pixInByte*bitsPerPixel;
                int val=(data[byteIdx]>>shift)&maxVal;
                int gray=val*255/maxVal;
                g.setColor(gray,gray,gray);g.fillRect(x+xx*2,y+yy*2,2,2);
            }
            g.setColor(0,0,0);g.drawRect(x-1,y-1,65,65);
        }
        private static void previewGray8(Graphics g,byte[] data,int pw,int ph,int x,int y){
            int xx,yy;
            for(yy=0;yy<32;yy++)for(xx=0;xx<32;xx++){
                int val=data[(yy*2)*pw+(xx*2)]&0xFF;
                g.setColor(val,val,val);g.fillRect(x+xx*2,y+yy*2,2,2);
            }
            g.setColor(0,0,0);g.drawRect(x-1,y-1,65,65);
        }
        private static void previewRgb332(Graphics g,byte[] data,int pw,int ph,int x,int y){
            int xx,yy;
            for(yy=0;yy<32;yy++)for(xx=0;xx<32;xx++){
                int v=data[(yy*2)*pw+(xx*2)]&0xFF;
                int r=((v>>5)&7)*255/7;
                int gr=((v>>2)&7)*255/7;
                int b=(v&3)*255/3;
                g.setColor(r,gr,b);g.fillRect(x+xx*2,y+yy*2,2,2);
            }
            g.setColor(0,0,0);g.drawRect(x-1,y-1,65,65);
        }
        private static void label(Graphics g,String s,int x,int y){
            g.setColor(0,0,0);g.drawString(s,x,y,Graphics.TOP|Graphics.LEFT);
        }
    }
}
