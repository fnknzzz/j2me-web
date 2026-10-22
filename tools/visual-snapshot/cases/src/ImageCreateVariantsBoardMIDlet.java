import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.midlet.MIDlet;
import java.io.InputStream;

public class ImageCreateVariantsBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w=getWidth(), h=getHeight();
            g.setColor(255,255,255); g.fillRect(0,0,w,h);
            fromBytes(g,0,0,w,h/3);
            fromRegionTransform(g,0,h/3,w,h/3);
            fromTransparentPng(g,0,h*2/3,w,h/3);
        }
        private static void panel(Graphics g,int x,int y,int w,int h){g.setColor(235,235,235);g.drawRect(x+4,y+4,w-9,h-9);}
        private static void fromBytes(Graphics g,int x,int y,int w,int h){
            panel(g,x,y,w,h);
            try{
                InputStream is=Board.class.getResourceAsStream("/tiny.png");
                byte[] buf=new byte[4096];int len=0,r;
                while((r=is.read(buf,len,buf.length-len))>0)len+=r;
                is.close();
                Image img1=Image.createImage(buf,0,len);
                g.drawImage(img1,x+w/4,y+h/2,Graphics.VCENTER|Graphics.HCENTER);
                g.setColor(0,0,0);g.drawRect(x+w/4-img1.getWidth()/2,y+h/2-img1.getHeight()/2,img1.getWidth(),img1.getHeight());
                byte[] padded=new byte[len+32];
                int i;for(i=0;i<32;i++)padded[i]=(byte)0xAB;
                System.arraycopy(buf,0,padded,32,len);
                Image img2=Image.createImage(padded,32,len);
                g.drawImage(img2,x+w*3/4,y+h/2,Graphics.VCENTER|Graphics.HCENTER);
                g.setColor(0,0,0);g.drawRect(x+w*3/4-img2.getWidth()/2,y+h/2-img2.getHeight()/2,img2.getWidth(),img2.getHeight());
            }catch(Exception e){}
        }
        private static void fromRegionTransform(Graphics g,int x,int y,int w,int h){
            Image src=makeSrc(80,60);
            g.drawImage(src,x+20,y+20,Graphics.TOP|Graphics.LEFT);
            g.setColor(0,0,0);g.drawRect(x+20,y+20,80,60);
            int[] sx={0,40,0,40,0,0,20,0};
            int[] sy={0,0,30,30,0,0,15,0};
            int[] rw={40,40,40,40,80,80,40,80};
            int[] rh={30,30,30,30,60,60,30,60};
            int[] tr={Sprite.TRANS_NONE,Sprite.TRANS_MIRROR_ROT180,Sprite.TRANS_MIRROR,Sprite.TRANS_ROT180,Sprite.TRANS_ROT90,Sprite.TRANS_MIRROR_ROT90,Sprite.TRANS_ROT270,Sprite.TRANS_MIRROR_ROT270};
            int cols=4,rows=2,cw=(w-140)/cols,ch=h/rows;
            int i;for(i=0;i<8;i++){
                int px=x+140+(i%cols)*cw,py=y+(i/cols)*ch;
                int cx=px+cw/2,cy=py+ch/2;
                panel(g,px,py,cw,ch);
                g.setColor(255,0,0);g.drawLine(cx-6,cy,cx+6,cy);g.drawLine(cx,cy-6,cx,cy+6);
                Image sub=Image.createImage(src,sx[i],sy[i],rw[i],rh[i],tr[i]);
                g.drawImage(sub,cx,cy,Graphics.VCENTER|Graphics.HCENTER);
            }
        }
        private static Image makeSrc(int w,int h){Image img=Image.createImage(w,h);Graphics g=img.getGraphics();g.setColor(250,225,110);g.fillRect(0,0,w,h);g.setColor(0,0,0);g.drawRect(0,0,w-1,h-1);g.drawLine(0,0,w-1,h-1);g.setColor(220,40,40);g.fillTriangle(3,3,w/2,3,3,h/2);g.setColor(40,100,220);g.fillArc(w-18,h-18,14,14,0,360);g.setColor(40,160,80);g.fillRect(w/2-5,5,10,h-10);return img;}
        private static void fromTransparentPng(Graphics g,int x,int y,int w,int h){
            panel(g,x,y,w,h);
            // Checker background to show transparency (same style as ImageResourceDisplayMIDlet)
            int stripW=230,stripH=36;
            int sx=x+(w-stripW)/2,sy=y+(h-stripH)/2;
            int xx,yy;for(yy=0;yy<stripH;yy+=12)for(xx=0;xx<stripW;xx+=12){g.setColor(((xx+yy)/12)%2==0?230:190,230,230);g.fillRect(sx+xx,sy+yy,12,12);}
            try{
                // Path 1: createImage(Image,sx,sy,w,h,transform) sub-region
                Image sheet=Image.createImage("/alpha_icons.png");
                Image ic0=Image.createImage(sheet,0,0,24,24,Sprite.TRANS_NONE);
                Image ic1=Image.createImage(sheet,24,0,24,24,Sprite.TRANS_NONE);
                Image ic2=Image.createImage(sheet,48,0,24,24,Sprite.TRANS_NONE);
                Image ic1m=Image.createImage(sheet,24,0,24,24,Sprite.TRANS_MIRROR);
                g.drawImage(ic0,sx+18,sy+stripH/2,Graphics.HCENTER|Graphics.VCENTER);
                g.drawImage(ic1,sx+54,sy+stripH/2,Graphics.HCENTER|Graphics.VCENTER);
                g.drawImage(ic2,sx+90,sy+stripH/2,Graphics.HCENTER|Graphics.VCENTER);
                g.drawImage(ic1m,sx+126,sy+stripH/2,Graphics.HCENTER|Graphics.VCENTER);
                // Path 2: createImage(byte[],off,len) from raw PNG bytes
                InputStream is=Board.class.getResourceAsStream("/alpha_icons.png");
                byte[] buf=new byte[4096];int len=0,r;
                while((r=is.read(buf,len,buf.length-len))>0)len+=r;
                is.close();
                Image fromBuf=Image.createImage(buf,0,len);
                Image ic3=Image.createImage(fromBuf,0,0,24,24,Sprite.TRANS_NONE);
                Image ic4=Image.createImage(fromBuf,24,0,24,24,Sprite.TRANS_ROT90);
                g.drawImage(ic3,sx+174,sy+stripH/2,Graphics.HCENTER|Graphics.VCENTER);
                g.drawImage(ic4,sx+210,sy+stripH/2,Graphics.HCENTER|Graphics.VCENTER);
            }catch(Exception e){}
        }
    }
}
