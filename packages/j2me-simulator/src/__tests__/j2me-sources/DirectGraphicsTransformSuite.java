import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class DirectGraphicsTransformSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        private Image testImage;

        public TestCanvas() {
            testImage = Image.createImage(20, 10);
            Graphics ig = testImage.getGraphics();
            ig.setColor(255, 0, 0);
            ig.fillRect(0, 0, 10, 10);
            ig.setColor(0, 0, 255);
            ig.fillRect(10, 0, 10, 10);
        }

        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);
            g.setColor(0, 0, 0);
            g.drawString("DirectGraphics Transform Test", 2, 2, Graphics.TOP | Graphics.LEFT);

            com.nokia.mid.ui.DirectGraphics dg = com.nokia.mid.ui.DirectUtils.getDirectGraphics(g);

            int y = 30;
            int gap = 30;

            dg.drawImage(testImage, 10, y, Graphics.TOP | Graphics.LEFT, 0);
            System.out.println("NONE: x=10, y=" + y);

            y += gap;
            dg.drawImage(testImage, 10, y, Graphics.TOP | Graphics.LEFT, com.nokia.mid.ui.DirectGraphics.FLIP_HORIZONTAL);
            System.out.println("FLIP_H: x=10, y=" + y);

            y += gap;
            dg.drawImage(testImage, 10, y, Graphics.TOP | Graphics.LEFT, com.nokia.mid.ui.DirectGraphics.FLIP_VERTICAL);
            System.out.println("FLIP_V: x=10, y=" + y);

            y += gap;
            dg.drawImage(testImage, 10, y, Graphics.TOP | Graphics.LEFT, com.nokia.mid.ui.DirectGraphics.ROTATE_90);
            System.out.println("ROT_90: x=10, y=" + y);

            y += gap;
            dg.drawImage(testImage, 10, y, Graphics.TOP | Graphics.LEFT, com.nokia.mid.ui.DirectGraphics.ROTATE_180);
            System.out.println("ROT_180: x=10, y=" + y);

            y += gap;
            dg.drawImage(testImage, 10, y, Graphics.TOP | Graphics.LEFT, com.nokia.mid.ui.DirectGraphics.ROTATE_270);
            System.out.println("ROT_270: x=10, y=" + y);

            y += gap;
            dg.drawImage(testImage, 10, y, Graphics.TOP | Graphics.LEFT, 
                com.nokia.mid.ui.DirectGraphics.FLIP_HORIZONTAL | com.nokia.mid.ui.DirectGraphics.ROTATE_90);
            System.out.println("FLIP_H+ROT_90: x=10, y=" + y);

            System.out.println("DirectGraphicsTransformSuite paint done");
        }
    }

    public DirectGraphicsTransformSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("DirectGraphicsTransformSuite started");
    }

    protected void pauseApp() {
        System.out.println("DirectGraphicsTransformSuite paused");
    }

    protected void destroyApp(boolean unconditional) {
        System.out.println("DirectGraphicsTransformSuite destroyed");
    }
}
