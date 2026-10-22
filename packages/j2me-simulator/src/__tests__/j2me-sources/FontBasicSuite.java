import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class FontBasicSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Font Basic Operations ===");
            
            Font defaultFont = Font.getDefaultFont();
            System.out.print("Default font face: ");
            System.out.println(defaultFont.getFace());
            System.out.print("Default font style: ");
            System.out.println(defaultFont.getStyle());
            System.out.print("Default font size: ");
            System.out.println(defaultFont.getSize());
            
            Font smallFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            System.out.print("Small font height: ");
            System.out.println(smallFont.getHeight());
            System.out.print("Small font baseline: ");
            System.out.println(smallFont.getBaselinePosition());
            
            Font mediumFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            System.out.print("Medium bold height: ");
            System.out.println(mediumFont.getHeight());
            
            Font largeFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_ITALIC, Font.SIZE_LARGE);
            System.out.print("Large italic height: ");
            System.out.println(largeFont.getHeight());
        }
    }

    public FontBasicSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("FontBasicSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
