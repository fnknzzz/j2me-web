import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.midlet.*;

public class MIDlet07_GameCanvas extends MIDlet {
    private Display display;
    private TestGameCanvas canvas;

    class TestGameCanvas extends GameCanvas {
        public TestGameCanvas() {
            super(false);
            System.out.println("GameCanvas created");
        }

        public void run() {
            System.out.println("Getting graphics...");
            Graphics g = getGraphics();
            System.out.println("Graphics obtained");
            System.out.print("Graphics class: ");
            System.out.println(g.getClass().getName());
            
            System.out.println("Flushing graphics...");
            flushGraphics();
            System.out.println("Graphics flushed");
        }
    }

    public MIDlet07_GameCanvas() {
        display = Display.getDisplay(this);
        canvas = new TestGameCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.run();
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
