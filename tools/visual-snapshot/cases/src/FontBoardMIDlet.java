import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class FontBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private static final class Board extends Canvas {
        private final char[] chars = new char[]{'H','e','l','l','o','\u4e2d','\u6587','!','?'};
        protected void paint(Graphics g) {
            int w=getWidth();g.setColor(255,255,255);g.fillRect(0,0,w,getHeight());
            Font med=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_MEDIUM);
            int margin=18;int gap=12;int rw=w-margin*2;int colW=(rw-gap)/2;
            int y=drawAnchors(g,med,w,0);
            int leftY=drawTextApis(g,y+12,margin,colW);
            drawFacesStyles(g,leftY+12,margin,colW);
            drawMetrics(g,y+12,margin+colW+gap,colW);
        }
        private static int drawAnchors(Graphics g,Font f,int w,int startY){
            String[] s={"Abg","\u4e2d\u6587","Hello","\u6d4bA"};
            int[] a={Graphics.LEFT,Graphics.HCENTER,Graphics.RIGHT,Graphics.VCENTER|Graphics.LEFT,Graphics.VCENTER|Graphics.HCENTER,Graphics.VCENTER|Graphics.RIGHT,Graphics.BOTTOM|Graphics.LEFT,Graphics.BOTTOM|Graphics.HCENTER,Graphics.BOTTOM|Graphics.RIGHT,Graphics.BASELINE|Graphics.LEFT,Graphics.BASELINE|Graphics.HCENTER,Graphics.BASELINE|Graphics.RIGHT};
            int cols=3,rows=4,cw=w/cols,ch=82,i;
            for(i=0;i<12;i++){int px=(i%cols)*cw;int py=startY+(i/cols)*ch;int cx=px+cw/2;int cy=py+ch/2;g.setColor(235,235,235);g.drawRect(px+4,py+4,cw-9,ch-9);g.setColor(255,0,0);g.drawLine(cx-10,cy,cx+10,cy);g.drawLine(cx,cy-10,cx,cy+10);g.setColor(230,40,40);g.drawLine(px+8,cy+f.getBaselinePosition(),px+cw-8,cy+f.getBaselinePosition());g.setFont(f);g.setColor(0,0,0);g.drawString(s[i%4],cx,cy,a[i]);}
            return startY+rows*ch;
        }
        private int drawTextApis(Graphics g,int y,int x,int rw){
            Font m=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_MEDIUM);
            Font l=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_LARGE);
            y=sample(g,m,"Hello\u4e16\u754c",x,y,rw,42,Graphics.VCENTER|Graphics.LEFT);
            y=sample(g,l,"\u4e2d\u6587\u6d4b\u8bd5\uff0c\u3002",x,y+4,rw,48,Graphics.VCENTER|Graphics.LEFT);
            int rowH=m.getHeight()+12;
            g.setColor(245,245,255);g.fillRect(x,y+4,rw,rowH*3+8);g.setColor(210,210,225);g.drawRect(x,y+4,rw,rowH*3+8);g.setFont(m);
            int top=y+10;g.setColor(230,40,40);g.drawLine(x+8,top+m.getBaselinePosition(),x+rw-8,top+m.getBaselinePosition());
            g.setColor(0,0,0);g.drawString("Hello\u4e2d\u6587!?",x+8,top,Graphics.TOP|Graphics.LEFT);
            g.drawSubstring("Hello\u4e2d\u6587!?",2,5,x+8,top+rowH,Graphics.TOP|Graphics.LEFT);
            g.drawChars(chars,1,5,x+8,top+rowH*2,Graphics.TOP|Graphics.LEFT);
            y+=rowH*3+16;
            y=charCells(g,m,"iW\u4e2d\u6d4b\uff0c\u3002!?",x,y+4,rw);
            return y;
        }
        private static int sample(Graphics g,Font f,String text,int x,int y,int w,int h,int anchor){
            g.setColor(245,245,245);g.fillRect(x,y,w,h);g.setColor(205,205,205);g.drawRect(x,y,w,h);g.setFont(f);int baseline=y+(h-f.getHeight())/2+f.getBaselinePosition();g.setColor(230,40,40);g.drawLine(x,baseline,x+w,baseline);g.setColor(0,0,0);g.drawString(text,x+8,y+h/2,anchor);return y+h;
        }
        private int charCells(Graphics g,Font f,String s,int x,int y,int w){
            int cx=x;int i;g.setFont(f);
            for(i=0;i<s.length();i++){char ch=s.charAt(i);int cw=f.charWidth(ch)+12;if(cx+cw>x+w)break;g.setColor(240,240,255);g.fillRect(cx,y,cw,f.getHeight()+10);g.setColor(190,190,210);g.drawRect(cx,y,cw,f.getHeight()+10);g.setColor(230,40,40);g.drawLine(cx,y+5+f.getBaselinePosition(),cx+cw,y+5+f.getBaselinePosition());g.setColor(0,0,0);g.drawChar(ch,cx+6,y+5,Graphics.TOP|Graphics.LEFT);cx+=cw+4;}
            return y+f.getHeight()+10;
        }
        private int drawMetrics(Graphics g,int y,int x,int rw){
            Font p=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_MEDIUM);
            Font b=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,Font.SIZE_MEDIUM);
            Font it=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_ITALIC,Font.SIZE_MEDIUM);
            Font u=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_UNDERLINED,Font.SIZE_MEDIUM);
            Font sm=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
            Font lg=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_LARGE);
            y=metricRow(g,p,"Abg",x,y,rw);y=metricRow(g,b,"Abg",x,y+4,rw);y=metricRow(g,it,"Abg",x,y+4,rw);y=metricRow(g,u,"Abg",x,y+4,rw);y=metricRow(g,sm,"Hello\u4e2d\u6587",x,y+4,rw);y=metricRow(g,lg,"\u4e2d\u6587\u6e2c\u8a66",x,y+4,rw);
            y=widthRows(g,p,x,y+8,rw);
            return subBlock(g,p,"Hello\u4e2d\u6587!?",x,y+8,rw);
        }
        private static int metricRow(Graphics g,Font f,String text,int x,int y,int w){
            int fh=f.getHeight();int rowH=fh+8;int top=y+4;int baseline=top+f.getBaselinePosition();g.setColor(242,242,255);g.fillRect(x,y,w,rowH);g.setColor(195,195,215);g.drawRect(x,y,w,rowH);g.setColor(40,170,40);g.drawLine(x,top,x+w,top);g.drawLine(x,top+fh,x+w,top+fh);g.setColor(230,40,40);g.drawLine(x,baseline,x+w,baseline);g.setFont(f);g.setColor(0,0,0);g.drawString(text,x+8,top,Graphics.TOP|Graphics.LEFT);return y+rowH;
        }
        private static int widthRows(Graphics g,Font f,int x,int y,int rw){
            String[] ss={"iiiiiiii","WWWWWW","Hello \u4e16\u754c","space  gap","1234567890"};int i;
            for(i=0;i<ss.length;i++){int tw=f.stringWidth(ss[i]);int row=f.getHeight()+8;g.setColor(245,245,255);g.fillRect(x,y,rw,row);g.setColor(215,215,230);g.drawRect(x,y,rw,row);g.setColor(40,170,40);g.drawRect(x+6,y+4,tw,f.getHeight());g.setColor(230,40,40);g.drawLine(x+6,y+4+f.getBaselinePosition(),x+6+tw,y+4+f.getBaselinePosition());g.setFont(f);g.setColor(0,0,0);g.drawString(ss[i],x+6,y+4,Graphics.TOP|Graphics.LEFT);y+=row+2;}
            return y;
        }
        private int subBlock(Graphics g,Font f,String s,int x,int y,int w){
            int tw=f.stringWidth(s);int rowH=f.getHeight()+10;g.setColor(245,245,255);g.fillRect(x,y,w,rowH);g.setColor(210,210,225);g.drawRect(x,y,w,rowH);g.setColor(40,170,40);g.drawRect(x+8,y+4,tw,f.getHeight());g.setColor(230,40,40);g.drawLine(x+8,y+4+f.getBaselinePosition(),x+8+tw,y+4+f.getBaselinePosition());g.setFont(f);g.setColor(0,0,0);g.drawString(s,x+8,y+4,Graphics.TOP|Graphics.LEFT);
            int y2=y+rowH+3;String sub=s.substring(2,6);int sw=f.substringWidth(s,2,4);g.setColor(245,255,245);g.fillRect(x,y2,w,rowH);g.setColor(210,225,210);g.drawRect(x,y2,w,rowH);g.setColor(40,170,40);g.drawRect(x+8,y2+4,sw,f.getHeight());g.setColor(230,40,40);g.drawLine(x+8,y2+4+f.getBaselinePosition(),x+8+sw,y2+4+f.getBaselinePosition());g.setFont(f);g.setColor(0,0,0);g.drawString(sub,x+8,y2+4,Graphics.TOP|Graphics.LEFT);
            int y3=y2+rowH+3;int cw=f.charsWidth(chars,1,5);g.setColor(255,245,245);g.fillRect(x,y3,w,rowH);g.setColor(225,210,210);g.drawRect(x,y3,w,rowH);g.setColor(40,170,40);g.drawRect(x+8,y3+4,cw,f.getHeight());g.setColor(230,40,40);g.drawLine(x+8,y3+4+f.getBaselinePosition(),x+8+cw,y3+4+f.getBaselinePosition());g.setColor(0,0,0);g.drawChars(chars,1,5,x+8,y3+4,Graphics.TOP|Graphics.LEFT);return y3+rowH;
        }
        private static void drawFacesStyles(Graphics g,int y,int x,int rw){
            int[] faces={Font.FACE_SYSTEM,Font.FACE_MONOSPACE,Font.FACE_PROPORTIONAL};
            int[] styles={Font.STYLE_PLAIN,Font.STYLE_BOLD,Font.STYLE_ITALIC,Font.STYLE_UNDERLINED,Font.STYLE_BOLD|Font.STYLE_ITALIC,Font.STYLE_BOLD|Font.STYLE_UNDERLINED};
            int colW=rw/3;int cellH=44;int rowStep=48;int i,j;
            for(j=0;j<styles.length;j++){for(i=0;i<faces.length;i++){Font f=Font.getFont(faces[i],styles[j],Font.SIZE_MEDIUM);drawFontCell(g,f,"A\u4e2d",x+i*colW,y+j*rowStep,colW-4,cellH);}}
            y+=styles.length*rowStep+12;
            drawFontCell(g,Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL),"small",x,y,rw,44);
            drawFontCell(g,Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_MEDIUM),"medium",x,y+48,rw,48);
            drawFontCell(g,Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_LARGE),"large",x,y+100,rw,58);
        }
        private static void drawFontCell(Graphics g,Font f,String s,int x,int y,int w,int h){g.setColor(245,245,255);g.fillRect(x,y,w,h);g.setColor(200,200,220);g.drawRect(x,y,w,h);g.setColor(230,40,40);g.drawLine(x,y+4+f.getBaselinePosition(),x+w,y+4+f.getBaselinePosition());g.setFont(f);g.setColor(0,0,0);g.drawString(s,x+4,y+4,Graphics.TOP|Graphics.LEFT);}
    }
}
