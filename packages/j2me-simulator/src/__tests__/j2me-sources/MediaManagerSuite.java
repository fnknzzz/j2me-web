import javax.microedition.midlet.*;
import javax.microedition.media.*;

public class MediaManagerSuite extends MIDlet {
    public MediaManagerSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Manager Test ===");
        
        testManagerBasics();
        
        System.out.println("Media manager test completed");
    }
    
    private void testManagerBasics() {
        System.out.println("Test: Manager basics");
        
        String[] contentTypes = Manager.getSupportedContentTypes("capture");
        System.out.print("Supported content types count: ");
        if (contentTypes != null) {
            System.out.println(contentTypes.length);
            for (int i = 0; i < contentTypes.length && i < 5; i++) {
                System.out.print("Content type: ");
                System.out.println(contentTypes[i]);
            }
        } else {
            System.out.println(0);
        }
        
        String[] protocols = Manager.getSupportedProtocols("audio/midi");
        System.out.print("Supported protocols count: ");
        if (protocols != null) {
            System.out.println(protocols.length);
            for (int i = 0; i < protocols.length && i < 5; i++) {
                System.out.print("Protocol: ");
                System.out.println(protocols[i]);
            }
        } else {
            System.out.println(0);
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
