import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSDeleteSuite extends MIDlet {
    public RMSDeleteSuite() {
        try {
            testDelete();
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testDelete() throws RecordStoreException {
        System.out.println("=== RMS Delete Test ===");
        
        RecordStore rs = RecordStore.openRecordStore("DeleteTest", true);
        System.out.println("Opened store");
        
        byte[] data1 = "Record 1".getBytes();
        int id1 = rs.addRecord(data1, 0, data1.length);
        System.out.print("Added record 1, ID: ");
        System.out.println(id1);
        
        byte[] data2 = "Record 2".getBytes();
        int id2 = rs.addRecord(data2, 0, data2.length);
        System.out.print("Added record 2, ID: ");
        System.out.println(id2);
        
        byte[] data3 = "Record 3".getBytes();
        int id3 = rs.addRecord(data3, 0, data3.length);
        System.out.print("Added record 3, ID: ");
        System.out.println(id3);
        
        System.out.print("Num records: ");
        System.out.println(rs.getNumRecords());
        
        System.out.print("Version before delete: ");
        System.out.println(rs.getVersion());
        
        rs.deleteRecord(id2);
        System.out.println("Deleted record 2");
        
        System.out.print("Num records after delete: ");
        System.out.println(rs.getNumRecords());
        
        System.out.print("Version after delete: ");
        System.out.println(rs.getVersion());
        
        byte[] retrieved1 = rs.getRecord(id1);
        System.out.print("Retrieved record 1: ");
        System.out.println(new String(retrieved1));
        
        byte[] retrieved3 = rs.getRecord(id3);
        System.out.print("Retrieved record 3: ");
        System.out.println(new String(retrieved3));
        
        try {
            byte[] deleted = rs.getRecord(id2);
            System.out.println("ERROR: Should not get deleted record");
        } catch (InvalidRecordIDException e) {
            System.out.println("Correctly threw InvalidRecordIDException");
        }
        
        rs.closeRecordStore();
        System.out.println("Closed store");
        
        RecordStore rs2 = RecordStore.openRecordStore("DeleteTest", false);
        System.out.println("Reopened store");
        
        System.out.print("Num records after reopen: ");
        System.out.println(rs2.getNumRecords());
        
        try {
            byte[] deleted = rs2.getRecord(id2);
            System.out.println("ERROR: Should not get deleted record after reopen");
        } catch (InvalidRecordIDException e) {
            System.out.println("Correctly threw InvalidRecordIDException after reopen");
        }
        
        rs2.closeRecordStore();
        RecordStore.deleteRecordStore("DeleteTest");
        System.out.println("Test completed");
    }
    
    protected void startApp() {}
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
