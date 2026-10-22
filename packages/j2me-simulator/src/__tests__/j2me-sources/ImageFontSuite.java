import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.midlet.*;
import java.io.IOException;

public class ImageFontSuite extends MIDlet {
    private Display display;
    private TestCanvas canvas;

    static class TestCanvas extends Canvas {
        protected void paint(Graphics g) {
            testFontOperations(g);
            testImageCreation(g);
            testImageOperations(g);
        }
        
        private void testFontOperations(Graphics g) {
            Font defaultFont = Font.getDefaultFont();
            System.out.print("Default font face: ");
            System.out.println(defaultFont.getFace());
            System.out.print("Default font style: ");
            System.out.println(defaultFont.getStyle());
            System.out.print("Default font size: ");
            System.out.println(defaultFont.getSize());
            
            Font smallPlain = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            System.out.print("Small plain height: ");
            System.out.println(smallPlain.getHeight());
            System.out.print("Small plain baseline: ");
            System.out.println(smallPlain.getBaselinePosition());
            
            Font mediumBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            System.out.print("Medium bold height: ");
            System.out.println(mediumBold.getHeight());
            
            Font largeItalic = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_ITALIC, Font.SIZE_LARGE);
            System.out.print("Large italic height: ");
            System.out.println(largeItalic.getHeight());
            
            Font monospace = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
            System.out.print("Monospace width 'A': ");
            System.out.println(monospace.charWidth('A'));
            System.out.print("Monospace width 'i': ");
            System.out.println(monospace.charWidth('i'));
            
            Font proportional = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
            System.out.print("Proportional width 'A': ");
            System.out.println(proportional.charWidth('A'));
            System.out.print("Proportional width 'i': ");
            System.out.println(proportional.charWidth('i'));
            
            String testStr = "Hello";
            System.out.print("String width 'Hello': ");
            System.out.println(defaultFont.stringWidth(testStr));
            System.out.print("Substring width 'ell': ");
            System.out.println(defaultFont.substringWidth(testStr, 1, 3));
            
            char[] chars = {'H', 'e', 'l', 'l', 'o'};
            System.out.print("Chars width: ");
            System.out.println(defaultFont.charsWidth(chars, 0, 5));
            System.out.print("Chars width (partial): ");
            System.out.println(defaultFont.charsWidth(chars, 1, 3));
            
            System.out.print("Is plain: ");
            System.out.println(defaultFont.isPlain());
            System.out.print("Is bold: ");
            System.out.println(defaultFont.isBold());
            System.out.print("Is italic: ");
            System.out.println(defaultFont.isItalic());
            System.out.print("Is underlined: ");
            System.out.println(defaultFont.isUnderlined());
            
            g.setFont(mediumBold);
            Font currentFont = g.getFont();
            System.out.print("Current font face: ");
            System.out.println(currentFont.getFace());
            System.out.print("Current font style: ");
            System.out.println(currentFont.getStyle());
        }
        
        private void testImageCreation(Graphics g) {
            try {
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
                
                int[] rgbData = new int[100 * 100];
                for (int i = 0; i < rgbData.length; i++) {
                    rgbData[i] = 0xFF0000FF;
                }
                Image img2 = Image.createRGBImage(rgbData, 100, 100, true);
                System.out.print("Created RGB image: ");
                System.out.print(img2.getWidth());
                System.out.print("x");
                System.out.println(img2.getHeight());
                System.out.print("Is mutable: ");
                System.out.println(img2.isMutable());
                
                Image img3 = Image.createImage(img1);
                System.out.print("Created immutable copy: ");
                System.out.print(img3.getWidth());
                System.out.print("x");
                System.out.println(img3.getHeight());
                System.out.print("Is mutable: ");
                System.out.println(img3.isMutable());
                
                Image img4 = Image.createImage(img1, 10, 10, 50, 50, Sprite.TRANS_NONE);
                System.out.print("Created region image: ");
                System.out.print(img4.getWidth());
                System.out.print("x");
                System.out.println(img4.getHeight());
                
                Image img5 = Image.createImage(img1, 10, 10, 50, 50, Sprite.TRANS_MIRROR);
                System.out.print("Created transformed image: ");
                System.out.print(img5.getWidth());
                System.out.print("x");
                System.out.println(img5.getHeight());
                
            } catch (Exception e) {
                System.out.print("Image creation error: ");
                System.out.println(e.getMessage());
            }
        }
        
        private void testImageOperations(Graphics g) {
            try {
                Image img = Image.createImage(50, 50);
                Graphics imgG = img.getGraphics();
                
                imgG.setColor(255, 0, 0);
                imgG.fillRect(0, 0, 25, 25);
                
                imgG.setColor(0, 255, 0);
                imgG.fillRect(25, 0, 25, 25);
                
                imgG.setColor(0, 0, 255);
                imgG.fillRect(0, 25, 25, 25);
                
                imgG.setColor(255, 255, 0);
                imgG.fillRect(25, 25, 25, 25);
                
                g.drawImage(img, 10, 10, Graphics.TOP | Graphics.LEFT);
                g.drawImage(img, 70, 10, Graphics.TOP | Graphics.RIGHT);
                g.drawImage(img, 130, 10, Graphics.TOP | Graphics.HCENTER);
                
                g.drawImage(img, 10, 70, Graphics.VCENTER | Graphics.LEFT);
                g.drawImage(img, 70, 70, Graphics.VCENTER | Graphics.RIGHT);
                g.drawImage(img, 130, 70, Graphics.VCENTER | Graphics.HCENTER);
                
                g.drawImage(img, 10, 130, Graphics.BOTTOM | Graphics.LEFT);
                g.drawImage(img, 70, 130, Graphics.BOTTOM | Graphics.RIGHT);
                g.drawImage(img, 130, 130, Graphics.BOTTOM | Graphics.HCENTER);
                
                g.drawRegion(img, 0, 0, 25, 25, Sprite.TRANS_NONE, 10, 150, Graphics.TOP | Graphics.LEFT);
                g.drawRegion(img, 25, 0, 25, 25, Sprite.TRANS_MIRROR, 40, 150, Graphics.TOP | Graphics.LEFT);
                g.drawRegion(img, 0, 25, 25, 25, Sprite.TRANS_MIRROR_ROT180, 70, 150, Graphics.TOP | Graphics.LEFT);
                g.drawRegion(img, 25, 25, 25, 25, Sprite.TRANS_ROT90, 100, 150, Graphics.TOP | Graphics.LEFT);
                
                int[] rgbData = new int[50 * 50];
                img.getRGB(rgbData, 0, 50, 0, 0, 50, 50);
                System.out.print("Got RGB data, first pixel: ");
                System.out.println(Integer.toHexString(rgbData[0]));
                System.out.print("Got RGB data, last pixel: ");
                System.out.println(Integer.toHexString(rgbData[rgbData.length - 1]));
                
            } catch (Exception e) {
                System.out.print("Image operation error: ");
                System.out.println(e.getMessage());
            }
        }
    }

    public ImageFontSuite() {
        display = Display.getDisplay(this);
        canvas = new TestCanvas();
    }

    protected void startApp() {
        display.setCurrent(canvas);
        canvas.repaint();
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}
}
