import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class GraphicsCompleteSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            testColorOperations(g);
            testDrawingPrimitives(g);
            testShapes(g);
            testText(g);
            testClipping(g);
            testTransform(g);
            testStrokeStyle(g);
        }
        
        private void testColorOperations(Graphics g) {
            g.setColor(255, 0, 0);
            g.getColor();
            
            g.setColor(0, 255, 0);
            g.getColor();
            
            g.setColor(0, 0, 255);
            g.getColor();
            
            g.setColor(0xFFFFFF);
            g.getColor();
            
            g.setGrayScale(0);
            g.getGrayScale();
            
            g.setGrayScale(128);
            g.getGrayScale();
            
            g.setGrayScale(255);
            g.getGrayScale();
        }
        
        private void testDrawingPrimitives(Graphics g) {
            g.setColor(0, 0, 0);
            
            g.drawLine(0, 0, 100, 100);
            g.drawLine(100, 0, 0, 100);
            g.drawLine(50, 0, 50, 100);
            g.drawLine(0, 50, 100, 50);
            
            g.drawRect(10, 10, 80, 80);
            g.drawRect(20, 20, 60, 60);
            
            g.fillRect(30, 30, 40, 40);
            g.fillRect(35, 35, 30, 30);
        }
        
        private void testShapes(Graphics g) {
            g.setColor(255, 0, 0);
            
            g.drawRoundRect(10, 10, 50, 50, 10, 10);
            g.drawRoundRect(70, 10, 50, 50, 20, 20);
            
            g.fillRoundRect(10, 70, 50, 50, 10, 10);
            g.fillRoundRect(70, 70, 50, 50, 20, 20);
            
            g.drawArc(10, 130, 50, 50, 0, 90);
            g.drawArc(70, 130, 50, 50, 90, 90);
            g.drawArc(10, 190, 50, 50, 180, 90);
            g.drawArc(70, 190, 50, 50, 270, 90);
            
            g.fillArc(130, 10, 50, 50, 0, 180);
            g.fillArc(130, 70, 50, 50, 180, 180);
            g.fillArc(130, 130, 50, 50, 0, 360);
        }
        
        private void testText(Graphics g) {
            g.setColor(0, 0, 0);
            
            g.drawString("TOP|LEFT", 10, 10, Graphics.TOP | Graphics.LEFT);
            g.drawString("TOP|RIGHT", 230, 10, Graphics.TOP | Graphics.RIGHT);
            g.drawString("TOP|HCENTER", 120, 10, Graphics.TOP | Graphics.HCENTER);
            
            g.drawString("BOTTOM|LEFT", 10, 310, Graphics.BOTTOM | Graphics.LEFT);
            g.drawString("BOTTOM|RIGHT", 230, 310, Graphics.BOTTOM | Graphics.RIGHT);
            g.drawString("BOTTOM|HCENTER", 120, 310, Graphics.BOTTOM | Graphics.HCENTER);
            
            g.drawString("VCENTER|LEFT", 10, 160, Graphics.VCENTER | Graphics.LEFT);
            g.drawString("VCENTER|RIGHT", 230, 160, Graphics.VCENTER | Graphics.RIGHT);
            g.drawString("VCENTER|HCENTER", 120, 160, Graphics.VCENTER | Graphics.HCENTER);
            
            char[] chars = {'H', 'e', 'l', 'l', 'o'};
            g.drawChars(chars, 0, 5, 10, 200, Graphics.TOP | Graphics.LEFT);
            g.drawChars(chars, 0, 3, 10, 220, Graphics.TOP | Graphics.LEFT);
            g.drawChars(chars, 2, 3, 10, 240, Graphics.TOP | Graphics.LEFT);
            
            g.drawSubstring("Hello World", 0, 5, 10, 260, Graphics.TOP | Graphics.LEFT);
            g.drawSubstring("Hello World", 6, 5, 10, 280, Graphics.TOP | Graphics.LEFT);
        }
        
        private void testClipping(Graphics g) {
            g.setClip(0, 0, 240, 320);
            g.getClipX();
            g.getClipY();
            g.getClipWidth();
            g.getClipHeight();
            
            g.setClip(10, 10, 100, 100);
            g.getClipX();
            g.getClipY();
            g.getClipWidth();
            g.getClipHeight();
            
            g.clipRect(20, 20, 80, 80);
            g.getClipX();
            g.getClipY();
            g.getClipWidth();
            g.getClipHeight();
            
            g.clipRect(30, 30, 60, 60);
            g.getClipX();
            g.getClipY();
            g.getClipWidth();
            g.getClipHeight();
        }
        
        private void testTransform(Graphics g) {
            g.translate(0, 0);
            g.getTranslateX();
            g.getTranslateY();
            
            g.translate(50, 30);
            g.getTranslateX();
            g.getTranslateY();
            
            g.translate(20, 10);
            g.getTranslateX();
            g.getTranslateY();
            
            g.translate(-30, -20);
            g.getTranslateX();
            g.getTranslateY();
        }
        
        private void testStrokeStyle(Graphics g) {
            g.setStrokeStyle(Graphics.SOLID);
            g.getStrokeStyle();
            
            g.drawLine(10, 10, 100, 10);
            g.drawRect(10, 20, 90, 40);
            
            g.setStrokeStyle(Graphics.DOTTED);
            g.getStrokeStyle();
            
            g.drawLine(10, 70, 100, 70);
            g.drawRect(10, 80, 90, 40);
        }
    }

    public GraphicsCompleteSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}
}
