import javax.microedition.midlet.*;
import javax.microedition.media.*;

public class MediaPlayToneSuite extends MIDlet {
    public MediaPlayToneSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Play Tone Test ===");
        
        testPlayTone();
        
        System.out.println("Media play tone test completed");
    }
    
    private void testPlayTone() {
        System.out.println("Test: Play tone");
        
        try {
            Manager.playTone(60, 500, 100);
            System.out.println("Tone played: note 60, duration 500ms, volume 100");
            
            Manager.playTone(64, 300, 80);
            System.out.println("Tone played: note 64, duration 300ms, volume 80");
            
            Manager.playTone(67, 400, 90);
            System.out.println("Tone played: note 67, duration 400ms, volume 90");
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
