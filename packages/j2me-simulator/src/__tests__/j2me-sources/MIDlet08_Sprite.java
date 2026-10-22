import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.midlet.*;

public class MIDlet08_Sprite extends MIDlet {
    private Display display;
    private TestGameCanvas canvas;

    class TestGameCanvas extends GameCanvas {
        public TestGameCanvas() {
            super(false);
            System.out.println("GameCanvas created");
        }

        public void run() {
            System.out.println("=== Sprite Test ===");
            
            System.out.println("Creating sprite image...");
            Image spriteImage = Image.createImage(64, 32);
            System.out.println("Sprite image created");
            
            System.out.println("Creating sprite...");
            Sprite sprite = new Sprite(spriteImage, 32, 32);
            System.out.println("Sprite created");
            
            System.out.print("Frame count: ");
            System.out.println(sprite.getRawFrameCount());
            
            System.out.print("Initial frame: ");
            System.out.println(sprite.getFrame());
            
            sprite.setFrame(1);
            System.out.print("After setFrame(1): ");
            System.out.println(sprite.getFrame());
            
            System.out.println("Sprite test complete");
        }
    }

    public MIDlet08_Sprite() {
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
