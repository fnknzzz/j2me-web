import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class GraphicsSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            g.setColor(255, 0, 0);
            g.getColor();
            
            g.setColor(0x00FF00);
            g.getColor();
            
            g.setGrayScale(128);
            g.getGrayScale();
            
            g.setClip(10, 20, 100, 150);
            g.getClipX();
            g.getClipY();
            g.getClipWidth();
            g.getClipHeight();
            
            g.translate(50, 30);
            g.getTranslateX();
            g.getTranslateY();
            
            g.setStrokeStyle(Graphics.SOLID);
            g.getStrokeStyle();
            
            g.setStrokeStyle(Graphics.DOTTED);
            g.getStrokeStyle();
            
            Font font = g.getFont();
            font.getFace();
            font.getStyle();
            font.getSize();
            
            Font customFont = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_LARGE);
            g.setFont(customFont);
            Font currentFont = g.getFont();
            currentFont.getFace();
            currentFont.getStyle();
            currentFont.getSize();
            
            g.setColor(0, 0, 0);
            g.drawLine(0, 0, 100, 100);
            g.drawRect(10, 10, 50, 50);
            g.fillRect(70, 10, 50, 50);
            g.drawRoundRect(10, 70, 50, 50, 10, 10);
            g.fillRoundRect(70, 70, 50, 50, 10, 10);
            g.drawArc(10, 130, 50, 50, 0, 90);
            g.fillArc(70, 130, 50, 50, 0, 90);
            g.drawString("Test", 10, 190, Graphics.TOP | Graphics.LEFT);
        }
    }

    public GraphicsSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("GraphicsSuite started");
    }

    protected void pauseApp() {
        System.out.println("GraphicsSuite paused");
    }

    protected void destroyApp(boolean unconditional) {
        System.out.println("GraphicsSuite destroyed");
    }
}
