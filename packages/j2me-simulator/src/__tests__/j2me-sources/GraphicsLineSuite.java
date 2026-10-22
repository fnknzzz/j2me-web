import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class GraphicsLineSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Line Drawing ===");
            
            g.setColor(0, 0, 0);
            
            System.out.println("Drawing horizontal line");
            g.drawLine(0, 50, 100, 50);
            
            System.out.println("Drawing vertical line");
            g.drawLine(50, 0, 50, 100);
            
            System.out.println("Drawing diagonal line");
            g.drawLine(0, 0, 100, 100);
            
            System.out.println("Drawing reverse diagonal");
            g.drawLine(100, 0, 0, 100);
        }
    }

    public GraphicsLineSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("GraphicsLineSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
