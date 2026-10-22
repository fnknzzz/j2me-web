import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class MIDlet04_Canvas extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    class TestCanvas extends Canvas {
        public TestCanvas() {
            System.out.println("Canvas created");
        }

        protected void paint(Graphics g) {
            System.out.println("paint called");
            System.out.print("Graphics class: ");
            System.out.println(g.getClass().getName());
        }
    }

    public MIDlet04_Canvas() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        System.out.println("Canvas set as current");
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
