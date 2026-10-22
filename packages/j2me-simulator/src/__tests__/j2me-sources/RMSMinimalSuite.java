import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSMinimalSuite extends MIDlet {
    public RMSMinimalSuite() {
    }

    protected void startApp() {
        System.out.println("=== RMS Minimal Test ===");
        
        try {
            System.out.println("Step 1: Opening record store");
            RecordStore rs = RecordStore.openRecordStore("test", true);
            System.out.println("Step 2: Record store opened successfully");
            
            System.out.println("Step 3: Closing record store");
            rs.closeRecordStore();
            System.out.println("Step 4: Record store closed");
            
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
        
        System.out.println("Test completed");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {
        try {
            RecordStore.deleteRecordStore("test");
        } catch (Exception e) {}
    }
}
