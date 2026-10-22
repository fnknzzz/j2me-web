import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.io.*;

public class ResourcePackageSuite extends MIDlet {
    private Display display;

    public ResourcePackageSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Resource Package Test ===");
        
        testTextResource();
        testBinaryResource();
        testImageResource();
        testNestedResource();
        
        System.out.println("Resource package test completed");
    }
    
    private void testTextResource() {
        System.out.println("Test 1: Load text resource");
        
        try {
            InputStream is = getClass().getResourceAsStream("/data.txt");
            if (is == null) {
                System.out.println("Resource not found: /data.txt");
                return;
            }
            
            System.out.print("Resource found, available bytes: ");
            System.out.println(is.available());
            
            byte[] buffer = new byte[100];
            int bytesRead = is.read(buffer);
            System.out.print("Bytes read: ");
            System.out.println(bytesRead);
            
            if (bytesRead > 0) {
                System.out.print("First byte value: ");
                System.out.println(buffer[0]);
                System.out.print("Second byte value: ");
                System.out.println(buffer[1]);
            }
            
            is.close();
            System.out.println("Text resource test passed");
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testBinaryResource() {
        System.out.println("Test 2: Load binary resource");
        
        try {
            InputStream is = getClass().getResourceAsStream("/config.bin");
            if (is == null) {
                System.out.println("Resource not found: /config.bin");
                return;
            }
            
            System.out.print("Binary resource available bytes: ");
            System.out.println(is.available());
            
            DataInputStream dis = new DataInputStream(is);
            
            int value1 = dis.readInt();
            System.out.print("Read int value: ");
            System.out.println(value1);
            
            short value2 = dis.readShort();
            System.out.print("Read short value: ");
            System.out.println(value2);
            
            byte value3 = dis.readByte();
            System.out.print("Read byte value: ");
            System.out.println(value3);
            
            dis.close();
            System.out.println("Binary resource test passed");
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testImageResource() {
        System.out.println("Test 3: Load image resource");
        
        try {
            Image img = Image.createImage("/icon.png");
            if (img != null) {
                System.out.println("Image loaded successfully");
                System.out.print("Image width: ");
                System.out.println(img.getWidth());
                System.out.print("Image height: ");
                System.out.println(img.getHeight());
                System.out.print("Is mutable: ");
                System.out.println(img.isMutable());
            } else {
                System.out.println("Image is null");
            }
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testNestedResource() {
        System.out.println("Test 4: Load nested directory resource");
        
        try {
            InputStream is = getClass().getResourceAsStream("/res/level1.dat");
            if (is == null) {
                System.out.println("Resource not found: /res/level1.dat");
                return;
            }
            
            System.out.print("Nested resource available bytes: ");
            System.out.println(is.available());
            
            byte[] buffer = new byte[10];
            int bytesRead = is.read(buffer);
            System.out.print("Bytes read from nested resource: ");
            System.out.println(bytesRead);
            
            is.close();
            System.out.println("Nested resource test passed");
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
