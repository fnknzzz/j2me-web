import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormImageItemSuite extends MIDlet {
    private Display display;
    private Form form;

    public FormImageItemSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form ImageItem Test ===");
        
        form = new Form("ImageItem Test");
        testImageItem();
        
        System.out.println("Form ImageItem test completed");
    }
    
    private void testImageItem() {
        System.out.println("Test: ImageItem");
        
        try {
            Image img = Image.createImage(32, 32);
            ImageItem imageItem = new ImageItem("Icon:", img, ImageItem.LAYOUT_CENTER, "Alt text");
            form.append(imageItem);
            
            System.out.print("ImageItem label: ");
            System.out.println(imageItem.getLabel());
            System.out.print("Alt text: ");
            System.out.println(imageItem.getAltText());
            System.out.print("Layout: ");
            System.out.println(imageItem.getLayout());
            
            Image newImg = Image.createImage(48, 48);
            imageItem.setImage(newImg);
            System.out.println("Image updated");
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
