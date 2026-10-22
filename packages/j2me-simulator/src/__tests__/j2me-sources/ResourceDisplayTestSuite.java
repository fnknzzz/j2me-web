import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class ResourceDisplayTestSuite extends MIDlet {
    private Display display;

    public ResourceDisplayTestSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Display Test ===");
        
        if (display != null) {
            System.out.println("Display initialized successfully");
        } else {
            System.out.println("Display is null");
        }
        
        System.out.println("Display test completed");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
