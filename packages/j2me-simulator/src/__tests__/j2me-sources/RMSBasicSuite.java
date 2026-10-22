import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSBasicSuite extends MIDlet {
    private Display display;

    public RMSBasicSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== RMS Basic Operations ===");
        
        try {
            System.out.println("Opening record store");
            RecordStore rs = RecordStore.openRecordStore("testStore", true);
            
            System.out.print("Store name: ");
            System.out.println(rs.getName());
            System.out.print("Num records: ");
            System.out.println(rs.getNumRecords());
            System.out.print("Size: ");
            System.out.println(rs.getSize());
            
            byte[] data1 = "Hello".getBytes();
            System.out.println("Adding record 1");
            int id1 = rs.addRecord(data1, 0, data1.length);
            System.out.print("Record ID: ");
            System.out.println(id1);
            
            byte[] data2 = "World".getBytes();
            System.out.println("Adding record 2");
            int id2 = rs.addRecord(data2, 0, data2.length);
            System.out.print("Record ID: ");
            System.out.println(id2);
            
            System.out.print("Num records after add: ");
            System.out.println(rs.getNumRecords());
            
            rs.closeRecordStore();
            System.out.println("Closed record store");
            
        } catch (Exception e) {
            System.out.print("RMS error: ");
            System.out.println(e.getMessage());
        }
        
        System.out.println("RMSBasicSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {
        try {
            RecordStore.deleteRecordStore("testStore");
        } catch (Exception e) {}
    }
}
