import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Texture2D;

public class M3GLcduiImage2DTest {
    public static void main(String[] args) {
        Image source = Image.createRGBImage(
                new int[] { 0x40112233, 0xFF445566 },
                2,
                1,
                true);
        Image2D image = new Image2D(Image2D.RGBA, source);
        Texture2D texture = new Texture2D(image);

        if (image.getWidth() != 2 || image.getHeight() != 1
                || texture.getImage() != image) {
            throw new RuntimeException("LCDUI Image2D conversion failed");
        }

        System.out.println("PASS: LCDUI Image2D conversion");
    }
}
