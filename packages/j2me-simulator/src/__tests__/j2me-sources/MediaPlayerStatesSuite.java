import javax.microedition.midlet.*;
import javax.microedition.media.*;

public class MediaPlayerStatesSuite extends MIDlet {
    public MediaPlayerStatesSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Player States Test ===");
        
        testPlayerStates();
        
        System.out.println("Media player states test completed");
    }
    
    private void testPlayerStates() {
        System.out.println("Test: Player state transitions");
        
        try {
            Player player = Manager.createPlayer("/tone.mid");
            if (player == null) {
                System.out.println("Player is null");
                return;
            }
            
            int state1 = player.getState();
            System.out.print("State UNREALIZED: ");
            System.out.println(state1 == Player.UNREALIZED);
            
            player.realize();
            int state2 = player.getState();
            System.out.print("State REALIZED: ");
            System.out.println(state2 == Player.REALIZED);
            
            player.prefetch();
            int state3 = player.getState();
            System.out.print("State PREFETCHED: ");
            System.out.println(state3 == Player.PREFETCHED);
            
            player.start();
            int state4 = player.getState();
            System.out.print("State STARTED: ");
            System.out.println(state4 == Player.STARTED);
            
            long duration = player.getDuration();
            System.out.print("Duration: ");
            if (duration == Player.TIME_UNKNOWN) {
                System.out.println("TIME_UNKNOWN");
            } else {
                System.out.println(duration);
            }
            
            long mediaTime = player.getMediaTime();
            System.out.print("Media time: ");
            System.out.println(mediaTime);
            
            player.stop();
            System.out.print("State after stop: ");
            System.out.println(player.getState());
            
            player.deallocate();
            System.out.println("Player deallocated");
            
            player.close();
            int state5 = player.getState();
            System.out.print("State CLOSED: ");
            System.out.println(state5 == Player.CLOSED);
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
