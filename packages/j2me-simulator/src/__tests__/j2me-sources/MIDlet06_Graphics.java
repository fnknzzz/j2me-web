import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class MIDlet06_Graphics extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    class TestCanvas extends Canvas {
        private Image image;

        public TestCanvas() {
            image = Image.createImage(64, 32);
        }

        protected void paint(Graphics g) {
            System.out.println("=== Graphics Test ===");
            Graphics imageG = image.getGraphics();
            
            System.out.println("Setting color...");
            imageG.setColor(255, 0, 0);
            System.out.println("Color set");
            
            System.out.println("Drawing rect...");
            imageG.fillRect(0, 0, 32, 32);
            System.out.println("Rect drawn");
            
            System.out.println("Graphics test complete");
        }
    }

    public MIDlet06_Graphics() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
