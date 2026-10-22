import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class GraphicsTextSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Text Drawing ===");
            
            g.setColor(0, 0, 0);
            
            System.out.println("Drawing text TOP|LEFT");
            g.drawString("Hello", 10, 10, Graphics.TOP | Graphics.LEFT);
            
            System.out.println("Drawing text TOP|RIGHT");
            g.drawString("World", 100, 10, Graphics.TOP | Graphics.RIGHT);
            
            System.out.println("Drawing text BOTTOM|LEFT");
            g.drawString("Test", 10, 100, Graphics.BOTTOM | Graphics.LEFT);
            
            System.out.println("Drawing text VCENTER|HCENTER");
            g.drawString("Center", 120, 60, Graphics.VCENTER | Graphics.HCENTER);
        }
    }

    public GraphicsTextSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("GraphicsTextSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
