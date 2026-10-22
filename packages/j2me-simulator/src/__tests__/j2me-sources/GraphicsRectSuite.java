import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class GraphicsRectSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Rectangle Drawing ===");
            
            g.setColor(0, 0, 0);
            
            System.out.println("Drawing rect outline");
            g.drawRect(10, 10, 50, 50);
            
            System.out.println("Drawing filled rect");
            g.fillRect(70, 10, 50, 50);
            
            System.out.println("Drawing small rect");
            g.drawRect(10, 70, 20, 20);
            
            System.out.println("Drawing large filled rect");
            g.fillRect(40, 70, 80, 40);
        }
    }

    public GraphicsRectSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("GraphicsRectSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
