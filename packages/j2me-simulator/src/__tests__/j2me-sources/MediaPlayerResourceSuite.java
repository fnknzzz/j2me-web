import javax.microedition.midlet.*;
import javax.microedition.media.*;

public class MediaPlayerResourceSuite extends MIDlet {
    public MediaPlayerResourceSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Player Resource Test ===");
        
        testPlayerFromResource();
        
        System.out.println("Media player resource test completed");
    }
    
    private void testPlayerFromResource() {
        System.out.println("Test: Create player from resource");
        
        try {
            Player player = Manager.createPlayer("/sound.mid");
            if (player != null) {
                System.out.println("Player created from resource");
                System.out.print("Initial state: ");
                System.out.println(player.getState());
                System.out.print("Content type: ");
                System.out.println(player.getContentType());
                
                player.close();
                System.out.print("State after close: ");
                System.out.println(player.getState());
            } else {
                System.out.println("Player is null");
            }
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
