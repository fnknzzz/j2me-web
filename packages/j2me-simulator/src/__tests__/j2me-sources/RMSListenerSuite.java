import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSListenerSuite extends MIDlet {
    protected void startApp() {
        try {
            testListener();
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testListener() throws RecordStoreException {
        System.out.println("=== RecordListener Test ===");
        
        RecordStore rs = RecordStore.openRecordStore("ListenerTest", true);
        
        RecordListener listener = new RecordListener() {
            public void recordAdded(RecordStore recordStore, int recordId) {
                System.out.print("Listener: Record added, ID: ");
                System.out.print(recordId);
                System.out.print(", Store: ");
                try {
                    System.out.println(recordStore.getName());
                } catch (RecordStoreNotOpenException e) {
                    System.out.println("[Closed Store]");
                }
            }
            
            public void recordChanged(RecordStore recordStore, int recordId) {
                System.out.print("Listener: Record changed, ID: ");
                System.out.print(recordId);
                System.out.print(", Store: ");
                try {
                    System.out.println(recordStore.getName());
                } catch (RecordStoreNotOpenException e) {
                    System.out.println("[Closed Store]");
                }
            }
            
            public void recordDeleted(RecordStore recordStore, int recordId) {
                System.out.print("Listener: Record deleted, ID: ");
                System.out.print(recordId);
                System.out.print(", Store: ");
                try {
                    System.out.println(recordStore.getName());
                } catch (RecordStoreNotOpenException e) {
                    System.out.println("[Closed Store]");
                }
            }
        };
        
        rs.addRecordListener(listener);
        System.out.println("Added record listener");
        
        byte[] data1 = "Test 1".getBytes();
        int id1 = rs.addRecord(data1, 0, data1.length);
        System.out.print("Added record 1, ID: ");
        System.out.println(id1);
        
        byte[] data2 = "Test 2".getBytes();
        int id2 = rs.addRecord(data2, 0, data2.length);
        System.out.print("Added record 2, ID: ");
        System.out.println(id2);
        
        byte[] newData = "Updated Test 1".getBytes();
        rs.setRecord(id1, newData, 0, newData.length);
        System.out.println("Updated record 1");
        
        rs.deleteRecord(id2);
        System.out.println("Deleted record 2");
        
        rs.removeRecordListener(listener);
        System.out.println("Removed record listener");
        
        byte[] data3 = "Test 3".getBytes();
        int id3 = rs.addRecord(data3, 0, data3.length);
        System.out.print("Added record 3 after removing listener, ID: ");
        System.out.println(id3);
        System.out.println("(no listener event should be triggered)");
        
        rs.closeRecordStore();
        RecordStore.deleteRecordStore("ListenerTest");
        System.out.println("Test completed");
    }
    
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
