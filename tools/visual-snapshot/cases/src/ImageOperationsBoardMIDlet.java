import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

public class ImageOperationsBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w=getWidth(), h=getHeight();
            g.setColor(255,255,255);g.fillRect(0,0,w,h);
            int cols=4, rows=6, cw=w/cols, ch=h/rows;
            int i;for(i=0;i<cols*rows;i++)panel(g,(i%cols)*cw,(i/cols)*ch,cw,ch);
            Image a=tile(64,48,0); Image b=tile(48,64,1); Image c=tile(55,55,2);
            creations(g,0,0,w,ch,a,b,c);
            rgb(g,0,ch,cw,ch,false); rgb(g,cw,ch,cw,ch,true); copies(g,cw*2,ch,cw*2,ch,a,b,c);
            anchors(g,0,ch*2,cw*2,ch,a); layered(g,cw*2,ch*2,cw*2,ch,a,b,c);
            offscreenPrimitives(g,0,ch*3,w,ch);
            offscreenText(g,0,ch*4,cw*2,ch); offscreenClipTranslate(g,cw*2,ch*4,cw*2,ch);
            offscreenPipeline(g,0,ch*5,cw*2,ch); regionAndSizes(g,cw*2,ch*5,cw*2,ch);
        }
        private static void panel(Graphics g,int x,int y,int w,int h){g.setColor(235,235,235);g.drawRect(x+4,y+4,w-9,h-9);}
        private static Image tile(int w,int h,int mode){Image img=Image.createImage(w,h);Graphics g=img.getGraphics();g.setColor(245,230,120);g.fillRect(0,0,w,h);g.setColor(0,0,0);g.drawRect(0,0,w-1,h-1);g.drawLine(0,0,w-1,h-1);g.drawLine(w-1,0,0,h-1);g.setColor(220,40,40);g.fillTriangle(3,3,w/2,3,3,h/2);g.setColor(40,100,220);g.fillArc(w-18,h-18,14,14,0,360);if(mode==1){g.setColor(40,160,80);g.fillRect(w/2-6,4,12,h-8);}else if(mode==2){g.setColor(130,40,160);g.fillRoundRect(10,10,w-20,h-20,16,16);}return img;}
        private static void creations(Graphics g,int x,int y,int w,int h,Image a,Image b,Image c){g.drawImage(a,x+w/8,y+h/2,Graphics.HCENTER|Graphics.VCENTER);g.drawImage(b,x+w*3/8,y+h/2,Graphics.HCENTER|Graphics.VCENTER);g.drawImage(c,x+w*5/8,y+h/2,Graphics.HCENTER|Graphics.VCENTER);g.drawImage(Image.createImage(a),x+w*7/8,y+h/2,Graphics.HCENTER|Graphics.VCENTER);}
        private static void rgb(Graphics g,int x,int y,int w,int h,boolean alpha){int ww=96,hh=74;int[] p=new int[ww*hh];int xx,yy;for(yy=0;yy<hh;yy++)for(xx=0;xx<ww;xx++){int a=alpha?(80+xx*160/ww):255;p[yy*ww+xx]=(a<<24)|((xx*255/ww)<<16)|((yy*255/hh)<<8)|180;}if(alpha)checker(g,x+20,y+20,ww,hh);g.drawRGB(p,0,ww,x+20,y+20,ww,hh,alpha);}
        private static void checker(Graphics g,int x,int y,int w,int h){int xx,yy;for(yy=0;yy<h;yy+=8)for(xx=0;xx<w;xx+=8){g.setColor(((xx+yy)&8)==0?220:170,220,220);g.fillRect(x+xx,y+yy,8,8);}}
        private static void anchors(Graphics g,int x,int y,int w,int h,Image img){int[] a={Graphics.TOP|Graphics.LEFT,Graphics.TOP|Graphics.HCENTER,Graphics.TOP|Graphics.RIGHT,Graphics.VCENTER|Graphics.LEFT,Graphics.VCENTER|Graphics.HCENTER,Graphics.VCENTER|Graphics.RIGHT,Graphics.BOTTOM|Graphics.LEFT,Graphics.BOTTOM|Graphics.HCENTER,Graphics.BOTTOM|Graphics.RIGHT};int i;for(i=0;i<9;i++){int cx=x+36+(i%3)*84;int cy=y+26+(i/3)*38;g.setColor(255,0,0);g.drawLine(cx-6,cy,cx+6,cy);g.drawLine(cx,cy-6,cx,cy+6);g.drawImage(img,cx,cy,a[i]);}}
        private static void copies(Graphics g,int x,int y,int w,int h,Image a,Image b,Image c){g.drawImage(a,x+25,y+22,Graphics.TOP|Graphics.LEFT);g.drawImage(Image.createImage(a),x+120,y+22,Graphics.TOP|Graphics.LEFT);g.drawImage(b,x+36,y+86,Graphics.BOTTOM|Graphics.LEFT);g.drawImage(c,x+w-24,y+h-22,Graphics.BOTTOM|Graphics.RIGHT);}
        private static void layered(Graphics g,int x,int y,int w,int h,Image a,Image b,Image c){int i;for(i=0;i<5;i++){g.drawImage(a,x+20+i*42,y+20+i*26,Graphics.TOP|Graphics.LEFT);g.drawImage(b,x+50+i*32,y+35+i*22,Graphics.TOP|Graphics.LEFT);g.drawImage(c,x+85+i*24,y+18+i*18,Graphics.TOP|Graphics.LEFT);}}
        private static void offscreenPrimitives(Graphics g,int x,int y,int w,int h){Image img=Image.createImage(580,110);Graphics ig=img.getGraphics();ig.setColor(255,255,255);ig.fillRect(0,0,580,110);ig.setColor(200,60,60);ig.drawLine(10,10,90,60);ig.setColor(60,60,200);ig.drawRect(100,10,60,40);ig.setColor(60,160,60);ig.fillRect(170,10,60,40);ig.setColor(200,120,40);ig.drawRoundRect(240,10,60,40,12,12);ig.setColor(120,40,200);ig.fillRoundRect(310,10,60,40,12,12);ig.setColor(40,160,160);ig.drawArc(380,10,50,40,0,270);ig.setColor(160,40,120);ig.fillArc(440,10,50,40,45,180);ig.setColor(80,80,200);ig.fillTriangle(510,50,530,10,570,50);ig.setColor(0,0,0);ig.drawRect(0,0,579,109);g.drawImage(img,x+30,y+18,Graphics.TOP|Graphics.LEFT);}
        private static void offscreenText(Graphics g,int x,int y,int w,int h){Image img=Image.createImage(280,110);Graphics ig=img.getGraphics();ig.setColor(255,250,230);ig.fillRect(0,0,280,110);ig.setFont(Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL));ig.setColor(0,0,0);ig.drawString("small plain",10,10,Graphics.TOP|Graphics.LEFT);ig.setFont(Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,Font.SIZE_MEDIUM));ig.setColor(60,60,180);ig.drawString("bold medium",10,35,Graphics.TOP|Graphics.LEFT);ig.setFont(Font.getFont(Font.FACE_MONOSPACE,Font.STYLE_ITALIC,Font.SIZE_LARGE));ig.setColor(180,40,40);ig.drawString("mono italic",10,65,Graphics.TOP|Graphics.LEFT);ig.setColor(0,0,0);ig.drawRect(0,0,279,109);g.drawImage(img,x+20,y+18,Graphics.TOP|Graphics.LEFT);}
        private static void offscreenClipTranslate(Graphics g,int x,int y,int w,int h){Image img=Image.createImage(280,110);Graphics ig=img.getGraphics();ig.setColor(255,255,255);ig.fillRect(0,0,280,110);ig.setClip(20,20,110,70);ig.setColor(60,120,200);ig.fillArc(-10,-10,150,120,0,360);ig.setColor(200,60,60);ig.drawLine(0,0,140,100);ig.setClip(0,0,280,110);ig.translate(150,20);ig.setColor(60,180,60);ig.fillRect(0,0,70,45);ig.setColor(0,0,0);ig.drawRect(0,0,69,44);ig.translate(-150,-20);ig.setColor(0,0,0);ig.drawRect(0,0,279,109);g.drawImage(img,x+20,y+18,Graphics.TOP|Graphics.LEFT);}
        private static void offscreenPipeline(Graphics g,int x,int y,int w,int h){Image a=Image.createImage(80,70);Graphics ga=a.getGraphics();ga.setColor(230,240,255);ga.fillRect(0,0,80,70);ga.setColor(220,40,40);ga.fillTriangle(40,8,10,62,70,62);ga.setColor(0,0,0);ga.drawRect(0,0,79,69);Image b=Image.createImage(180,80);Graphics gb=b.getGraphics();gb.setColor(245,245,245);gb.fillRect(0,0,180,80);gb.drawImage(a,8,5,Graphics.TOP|Graphics.LEFT);gb.drawImage(Image.createImage(a),92,5,Graphics.TOP|Graphics.LEFT);gb.setColor(0,0,0);gb.drawRect(0,0,179,79);g.drawImage(b,x+20,y+24,Graphics.TOP|Graphics.LEFT);}
        private static void regionAndSizes(Graphics g,int x,int y,int w,int h){Image tiny=Image.createImage(16,16);Graphics gt=tiny.getGraphics();gt.setColor(255,200,100);gt.fillRect(0,0,16,16);gt.setColor(200,40,40);gt.fillRect(2,2,6,6);gt.setColor(0,0,0);gt.drawRect(0,0,15,15);Image src=Image.createImage(80,80);Graphics gs=src.getGraphics();gs.setColor(255,60,60);gs.fillRect(0,0,40,40);gs.setColor(60,255,60);gs.fillRect(40,0,40,40);gs.setColor(60,60,255);gs.fillRect(0,40,40,40);gs.setColor(255,255,60);gs.fillRect(40,40,40,40);gs.setColor(0,0,0);gs.drawRect(0,0,79,79);g.drawImage(tiny,x+20,y+h/2,Graphics.VCENTER|Graphics.LEFT);int i;for(i=0;i<3;i++)g.drawRegion(src,0,0,80,80,i,x+70+i*70,y+26,Graphics.TOP|Graphics.LEFT);}
    }
}
