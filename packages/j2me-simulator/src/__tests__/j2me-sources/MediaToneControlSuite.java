import javax.microedition.midlet.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;

public class MediaToneControlSuite extends MIDlet {
    public MediaToneControlSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Tone Control Test ===");
        
        testToneControl();
        
        System.out.println("Media tone control test completed");
    }
    
    private void testToneControl() {
        System.out.println("Test: ToneControl");
        
        try {
            Player player = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
            if (player == null) {
                System.out.println("Tone player is null");
                return;
            }
            
            player.realize();
            
            ToneControl tc = (ToneControl) player.getControl("ToneControl");
            if (tc != null) {
                System.out.println("ToneControl obtained");
                
                byte[] sequence = new byte[]{
                    ToneControl.VERSION, 1,
                    ToneControl.TEMPO, 30,
                    ToneControl.BLOCK_START, 0,
                    60, 8,
                    62, 8,
                    64, 8,
                    65, 8,
                    ToneControl.BLOCK_END, 0,
                    ToneControl.PLAY_BLOCK, 0
                };
                
                tc.setSequence(sequence);
                System.out.println("Tone sequence set");
            } else {
                System.out.println("ToneControl not available");
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
