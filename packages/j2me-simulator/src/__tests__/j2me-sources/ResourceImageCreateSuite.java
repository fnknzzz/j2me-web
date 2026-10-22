import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.io.*;

public class ResourceImageCreateSuite extends MIDlet {
    public ResourceImageCreateSuite() {
    }

    protected void startApp() {
        System.out.println("=== Image.createImage Test ===");
        
        try {
            Image img = Image.createImage("/icon.png");
            if (img != null) {
                System.out.println("Image loaded successfully");
                System.out.print("Image width: ");
                System.out.println(img.getWidth());
                System.out.print("Image height: ");
                System.out.println(img.getHeight());
            } else {
                System.out.println("Image is null");
            }
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
        
        System.out.println("Image test completed");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
