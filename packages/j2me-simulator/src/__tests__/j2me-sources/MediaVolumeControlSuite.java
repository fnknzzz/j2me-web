import javax.microedition.midlet.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;

public class MediaVolumeControlSuite extends MIDlet {
    public MediaVolumeControlSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Volume Control Test ===");
        
        testVolumeControl();
        
        System.out.println("Media volume control test completed");
    }
    
    private void testVolumeControl() {
        System.out.println("Test: VolumeControl");
        
        try {
            Player player = Manager.createPlayer("/audio.wav");
            if (player == null) {
                System.out.println("Player is null");
                return;
            }
            
            player.realize();
            
            VolumeControl vc = (VolumeControl) player.getControl("VolumeControl");
            if (vc != null) {
                System.out.println("VolumeControl obtained");
                
                int level = vc.getLevel();
                System.out.print("Current volume level: ");
                System.out.println(level);
                
                int newLevel = vc.setLevel(75);
                System.out.print("New volume level: ");
                System.out.println(newLevel);
                
                boolean muted = vc.isMuted();
                System.out.print("Is muted: ");
                System.out.println(muted);
                
                vc.setMute(true);
                System.out.print("Muted: ");
                System.out.println(vc.isMuted());
                
                vc.setMute(false);
                System.out.print("Unmuted: ");
                System.out.println(vc.isMuted());
            } else {
                System.out.println("VolumeControl not available");
            }
            
            player.close();
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
