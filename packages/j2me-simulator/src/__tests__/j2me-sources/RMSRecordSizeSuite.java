import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSRecordSizeSuite extends MIDlet {
    protected void startApp() {
        try {
            testRecordSize();
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testRecordSize() throws RecordStoreException {
        System.out.println("=== Record Size Test ===");
        
        RecordStore rs = RecordStore.openRecordStore("SizeTest", true);
        System.out.println("Opened store");
        
        System.out.print("Initial size: ");
        System.out.println(rs.getSize());
        
        byte[] data1 = "Hello World".getBytes();
        int id1 = rs.addRecord(data1, 0, data1.length);
        System.out.print("Added record 1, ID: ");
        System.out.println(id1);
        
        System.out.print("Size after add: ");
        System.out.println(rs.getSize());
        
        System.out.print("Record 1 size: ");
        System.out.println(rs.getRecordSize(id1));
        
        byte[] data2 = new byte[100];
        for (int i = 0; i < data2.length; i++) {
            data2[i] = (byte)i;
        }
        int id2 = rs.addRecord(data2, 0, data2.length);
        System.out.print("Added record 2 (100 bytes), ID: ");
        System.out.println(id2);
        
        System.out.print("Total size: ");
        System.out.println(rs.getSize());
        
        System.out.print("Record 2 size: ");
        System.out.println(rs.getRecordSize(id2));
        
        rs.closeRecordStore();
        RecordStore.deleteRecordStore("SizeTest");
        System.out.println("Test completed");
    }
    
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
