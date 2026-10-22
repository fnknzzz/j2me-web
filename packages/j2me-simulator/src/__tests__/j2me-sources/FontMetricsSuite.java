import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class FontMetricsSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            System.out.println("=== Font Metrics ===");
            
            Font monoFont = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
            System.out.print("Monospace 'A' width: ");
            System.out.println(monoFont.charWidth('A'));
            System.out.print("Monospace 'i' width: ");
            System.out.println(monoFont.charWidth('i'));
            
            Font propFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
            System.out.print("Proportional 'A' width: ");
            System.out.println(propFont.charWidth('A'));
            System.out.print("Proportional 'i' width: ");
            System.out.println(propFont.charWidth('i'));
            
            Font defaultFont = Font.getDefaultFont();
            String testStr = "Hello";
            System.out.print("String 'Hello' width: ");
            System.out.println(defaultFont.stringWidth(testStr));
            System.out.print("Substring 'ell' width: ");
            System.out.println(defaultFont.substringWidth(testStr, 1, 3));
            
            char[] chars = {'H', 'e', 'l', 'l', 'o'};
            System.out.print("Chars full width: ");
            System.out.println(defaultFont.charsWidth(chars, 0, 5));
            System.out.print("Chars partial width: ");
            System.out.println(defaultFont.charsWidth(chars, 1, 3));
        }
    }

    public FontMetricsSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("FontMetricsSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
