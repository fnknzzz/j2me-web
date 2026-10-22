import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class MIDlet05_Image extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    class TestCanvas extends Canvas {
        private Image image;

        public TestCanvas() {
            System.out.println("Creating image...");
            image = Image.createImage(64, 32);
            System.out.println("Image created");
            System.out.print("Image size: ");
            System.out.print(image.getWidth());
            System.out.print("x");
            System.out.println(image.getHeight());
            System.out.print("Is mutable: ");
            System.out.println(image.isMutable());
        }

        protected void paint(Graphics g) {
            System.out.println("Getting image graphics...");
            Graphics imageG = image.getGraphics();
            System.out.println("Image graphics obtained");
            System.out.print("Image graphics class: ");
            System.out.println(imageG.getClass().getName());
        }
    }

    public MIDlet05_Image() {
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
