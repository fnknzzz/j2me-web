import javax.microedition.midlet.*;
import javax.microedition.media.*;

public class MediaPlayerListenerSuite extends MIDlet implements PlayerListener {
    public MediaPlayerListenerSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Player Listener Test ===");
        
        testPlayerListener();
        
        System.out.println("Media player listener test completed");
    }
    
    private void testPlayerListener() {
        System.out.println("Test: Player listener");
        
        try {
            Player player = Manager.createPlayer("/music.mid");
            if (player == null) {
                System.out.println("Player is null");
                return;
            }
            
            player.addPlayerListener(this);
            System.out.println("Player listener added");
            
            player.realize();
            System.out.println("Player realized");
            
            player.removePlayerListener(this);
            System.out.println("Player listener removed");
            
            player.close();
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }
    
    public void playerUpdate(Player player, String event, Object eventData) {
        System.out.print("Player event: ");
        System.out.println(event);
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
