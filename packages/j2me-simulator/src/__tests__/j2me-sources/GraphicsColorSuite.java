import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class GraphicsColorSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Color Operations ===");
            
            g.setColor(255, 0, 0);
            System.out.print("Set RGB(255,0,0), get: ");
            System.out.println(g.getColor());
            
            g.setColor(0, 255, 0);
            System.out.print("Set RGB(0,255,0), get: ");
            System.out.println(g.getColor());
            
            g.setColor(0, 0, 255);
            System.out.print("Set RGB(0,0,255), get: ");
            System.out.println(g.getColor());
            
            g.setColor(0xFFFFFF);
            System.out.print("Set 0xFFFFFF, get: ");
            System.out.println(g.getColor());
            
            g.setGrayScale(0);
            System.out.print("Set gray 0, get: ");
            System.out.println(g.getGrayScale());
            
            g.setGrayScale(128);
            System.out.print("Set gray 128, get: ");
            System.out.println(g.getGrayScale());
            
            g.setGrayScale(255);
            System.out.print("Set gray 255, get: ");
            System.out.println(g.getGrayScale());
        }
    }

    public GraphicsColorSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("GraphicsColorSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
