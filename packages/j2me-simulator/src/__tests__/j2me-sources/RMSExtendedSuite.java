import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSExtendedSuite extends MIDlet {
    public RMSExtendedSuite() {
        try {
            testExtendedMethods();
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testExtendedMethods() throws RecordStoreException {
        System.out.println("=== RMS Extended Methods Test ===");
        
        RecordStore rs = RecordStore.openRecordStore("ExtendedTest", true);
        System.out.println("Opened store");
        
        System.out.print("Initial version: ");
        System.out.println(rs.getVersion());
        
        System.out.print("Size available: ");
        System.out.println(rs.getSizeAvailable());
        
        System.out.print("Last modified: ");
        System.out.println(rs.getLastModified());
        
        byte[] data1 = "Record 1".getBytes();
        int id1 = rs.addRecord(data1, 0, data1.length);
        System.out.print("Added record, ID: ");
        System.out.println(id1);
        
        System.out.print("Version after add: ");
        System.out.println(rs.getVersion());
        
        byte[] data2 = "Record 2".getBytes();
        int id2 = rs.addRecord(data2, 0, data2.length);
        System.out.print("Added record, ID: ");
        System.out.println(id2);
        
        System.out.print("Num records: ");
        System.out.println(rs.getNumRecords());
        
        System.out.print("Next record ID: ");
        System.out.println(rs.getNextRecordID());
        
        rs.deleteRecord(id1);
        System.out.println("Deleted record 1");
        
        System.out.print("Num records after delete: ");
        System.out.println(rs.getNumRecords());
        
        System.out.print("Version after delete: ");
        System.out.println(rs.getVersion());
        
        try {
            byte[] deleted = rs.getRecord(id1);
            System.out.println("ERROR: Should not get deleted record");
        } catch (InvalidRecordIDException e) {
            System.out.println("Correctly threw InvalidRecordIDException");
        }
        
        rs.closeRecordStore();
        RecordStore.deleteRecordStore("ExtendedTest");
        System.out.println("Test completed");
    }
    
    protected void startApp() {}
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
