import javax.microedition.midlet.*;
import java.io.*;

public class ResourceLoadSuite extends MIDlet {
    public ResourceLoadSuite() {
    }

    protected void startApp() {
        System.out.println("=== Resource Loading Test ===");
        
        testClassResourceAsStream();
        testAbsolutePath();
        testRelativePath();
        testNonExistentResource();
        
        System.out.println("Resource loading test completed");
    }
    
    private void testClassResourceAsStream() {
        System.out.println("Test 1: Class.getResourceAsStream");
        
        try {
            InputStream is = getClass().getResourceAsStream("/test.txt");
            if (is == null) {
                System.out.println("Resource not found: /test.txt");
            } else {
                System.out.println("Resource found: /test.txt");
                System.out.print("Available bytes: ");
                System.out.println(is.available());
                
                int firstByte = is.read();
                System.out.print("First byte: ");
                System.out.println(firstByte);
                
                is.close();
                System.out.println("Stream closed");
            }
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testAbsolutePath() {
        System.out.println("Test 2: Absolute path resource");
        
        try {
            InputStream is = getClass().getResourceAsStream("/images/icon.png");
            if (is == null) {
                System.out.println("Resource not found: /images/icon.png");
            } else {
                System.out.println("Resource found: /images/icon.png");
                System.out.print("Available bytes: ");
                System.out.println(is.available());
                is.close();
            }
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testRelativePath() {
        System.out.println("Test 3: Relative path resource");
        
        try {
            InputStream is = getClass().getResourceAsStream("data.bin");
            if (is == null) {
                System.out.println("Resource not found: data.bin");
            } else {
                System.out.println("Resource found: data.bin");
                System.out.print("Available bytes: ");
                System.out.println(is.available());
                is.close();
            }
        } catch (IOException e) {
            System.out.print("IOException: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testNonExistentResource() {
        System.out.println("Test 4: Non-existent resource");
        
        InputStream is = getClass().getResourceAsStream("/nonexistent.txt");
        if (is == null) {
            System.out.println("Correctly returned null for non-existent resource");
        } else {
            System.out.println("ERROR: Should return null for non-existent resource");
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
