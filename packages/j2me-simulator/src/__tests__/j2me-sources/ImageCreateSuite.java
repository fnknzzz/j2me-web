import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class ImageCreateSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Image Creation ===");
            
            Image img1 = Image.createImage(100, 100);
            System.out.print("Created mutable image: ");
            System.out.print(img1.getWidth());
            System.out.print("x");
            System.out.println(img1.getHeight());
            System.out.print("Is mutable: ");
            System.out.println(img1.isMutable());
            
            Graphics imgG = img1.getGraphics();
            imgG.setColor(255, 0, 0);
            imgG.fillRect(0, 0, 100, 100);
            System.out.println("Drew on mutable image");
            
            Image img2 = Image.createImage(50, 75);
            System.out.print("Created another image: ");
            System.out.print(img2.getWidth());
            System.out.print("x");
            System.out.println(img2.getHeight());
        }
    }

    public ImageCreateSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("ImageCreateSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
