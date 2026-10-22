import javax.microedition.midlet.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;
import java.io.*;

public class MediaPlayerSuite extends MIDlet implements PlayerListener {
    public MediaPlayerSuite() {
    }

    protected void startApp() {
        System.out.println("=== Media Player Test ===");
        
        testManagerBasics();
        testPlayerFromResource();
        testPlayerFromStream();
        testPlayerStates();
        testToneControl();
        testVolumeControl();
        testPlayTone();
        
        System.out.println("Media player test completed");
    }
    
    private void testManagerBasics() {
        System.out.println("Test 1: Manager basics");
        
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
    
    private void testPlayerFromResource() {
        System.out.println("Test 2: Create player from resource");
        
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
    
    private void testPlayerFromStream() {
        System.out.println("Test 3: Create player from stream");
        
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
                
                player.addPlayerListener(this);
                System.out.println("Player listener added");
                
                player.close();
            }
            
            is.close();
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testPlayerStates() {
        System.out.println("Test 4: Player states");
        
        try {
            Player player = Manager.createPlayer("/tone.mid");
            if (player == null) {
                System.out.println("Player is null");
                return;
            }
            
            System.out.print("State UNREALIZED: ");
            System.out.println(player.getState() == Player.UNREALIZED);
            
            player.realize();
            System.out.print("State REALIZED: ");
            System.out.println(player.getState() == Player.REALIZED);
            
            player.prefetch();
            System.out.print("State PREFETCHED: ");
            System.out.println(player.getState() == Player.PREFETCHED);
            
            player.start();
            System.out.print("State STARTED: ");
            System.out.println(player.getState() == Player.STARTED);
            
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
            System.out.print("State CLOSED: ");
            System.out.println(player.getState() == Player.CLOSED);
        } catch (Exception e) {
            System.out.print("Exception: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testToneControl() {
        System.out.println("Test 5: ToneControl");
        
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
    
    private void testVolumeControl() {
        System.out.println("Test 6: VolumeControl");
        
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
    
    private void testPlayTone() {
        System.out.println("Test 7: Play tone");
        
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
    
    public void playerUpdate(Player player, String event, Object eventData) {
        System.out.print("Player event: ");
        System.out.println(event);
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
