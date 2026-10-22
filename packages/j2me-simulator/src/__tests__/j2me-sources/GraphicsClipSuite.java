import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class GraphicsClipSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Clipping Operations ===");
            
            g.setClip(10, 20, 100, 150);
            System.out.print("Clip X: ");
            System.out.println(g.getClipX());
            System.out.print("Clip Y: ");
            System.out.println(g.getClipY());
            System.out.print("Clip Width: ");
            System.out.println(g.getClipWidth());
            System.out.print("Clip Height: ");
            System.out.println(g.getClipHeight());
            
            g.clipRect(20, 30, 50, 80);
            System.out.print("After clipRect - X: ");
            System.out.println(g.getClipX());
            System.out.print("After clipRect - Y: ");
            System.out.println(g.getClipY());
            System.out.print("After clipRect - Width: ");
            System.out.println(g.getClipWidth());
            System.out.print("After clipRect - Height: ");
            System.out.println(g.getClipHeight());
        }
    }

    public GraphicsClipSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("GraphicsClipSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
