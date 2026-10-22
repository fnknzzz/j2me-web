import javax.microedition.midlet.*;
import javax.microedition.media.*;
import java.io.*;

public class MediaPlayerStreamSuite extends MIDlet {
    public MediaPlayerStreamSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Player Stream Test ===");
        
        testPlayerFromStream();
        
        System.out.println("Media player stream test completed");
    }
    
    private void testPlayerFromStream() {
        System.out.println("Test: Create player from stream");
        
        try {
            InputStream is = getClass().getResourceAsStream("/music.mid");
            if (is == null) {
                System.out.println("Resource not found");
                return;
            }
            
            Player player = Manager.createPlayer(is, "audio/midi");
            if (player != null) {
                System.out.println("Player created from stream");
                System.out.print("State: ");
                System.out.println(player.getState());
                
                player.close();
            }
            
            is.close();
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
