import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSPersistenceSuite extends MIDlet {
    public RMSPersistenceSuite() {
        try {
            testPersistence();
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testPersistence() throws RecordStoreException {
        System.out.println("=== RMS Persistence Test ===");
        
        String[] stores = RecordStore.listRecordStores();
        if (stores != null) {
            System.out.print("Existing stores: ");
            System.out.println(stores.length);
        } else {
            System.out.println("No existing stores");
        }
        
        RecordStore rs = RecordStore.openRecordStore("PersistTest", true);
        System.out.println("Created new store");
        
        byte[] data1 = "Persistent Data 1".getBytes();
        int id1 = rs.addRecord(data1, 0, data1.length);
        System.out.print("Added record, ID: ");
        System.out.println(id1);
        
        byte[] data2 = "Persistent Data 2".getBytes();
        int id2 = rs.addRecord(data2, 0, data2.length);
        System.out.print("Added record, ID: ");
        System.out.println(id2);
        
        System.out.print("Version: ");
        System.out.println(rs.getVersion());
        
        System.out.print("Last modified: ");
        System.out.println(rs.getLastModified());
        
        rs.closeRecordStore();
        System.out.println("Closed store");
        
        String[] stores2 = RecordStore.listRecordStores();
        if (stores2 != null) {
            System.out.print("Stores after create: ");
            System.out.println(stores2.length);
            for (int i = 0; i < stores2.length; i++) {
                System.out.print("Store ");
                System.out.print(i);
                System.out.print(": ");
                System.out.println(stores2[i]);
            }
        }
        
        RecordStore rs2 = RecordStore.openRecordStore("PersistTest", false);
        System.out.println("Reopened store");
        
        System.out.print("Num records: ");
        System.out.println(rs2.getNumRecords());
        
        System.out.print("Version: ");
        System.out.println(rs2.getVersion());
        
        byte[] retrieved1 = rs2.getRecord(id1);
        System.out.print("Retrieved record 1: ");
        System.out.println(new String(retrieved1));
        
        byte[] retrieved2 = rs2.getRecord(id2);
        System.out.print("Retrieved record 2: ");
        System.out.println(new String(retrieved2));
        
        rs2.closeRecordStore();
        
        RecordStore.deleteRecordStore("PersistTest");
        System.out.println("Deleted store");
        
        String[] stores3 = RecordStore.listRecordStores();
        if (stores3 != null) {
            System.out.print("Stores after delete: ");
            System.out.println(stores3.length);
        } else {
            System.out.println("No stores after delete");
        }
        
        System.out.println("Test completed");
    }
    
    protected void startApp() {}
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
