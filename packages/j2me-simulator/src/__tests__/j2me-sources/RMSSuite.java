import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;
import java.io.*;

public class RMSSuite extends MIDlet {
    private Display display;

    public RMSSuite() {
        display = Display.getDisplay(this);
        
        try {
            testRecordStore();
        } catch (Exception e) {
            System.out.print("RMS test error: ");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        try {
            testRecordEnumeration();
        } catch (Exception e) {
            System.out.print("RMS test error: ");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        try {
            testRecordListener();
        } catch (Exception e) {
            System.out.print("RMS test error: ");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void testRecordStore() throws RecordStoreException {
        System.out.println("=== RecordStore Test ===");
        
        String[] stores = RecordStore.listRecordStores();
        if (stores != null) {
            System.out.print("Existing stores count: ");
            System.out.println(stores.length);
            for (int i = 0; i < stores.length; i++) {
                System.out.print("Store ");
                System.out.print(i);
                System.out.print(": ");
                System.out.println(stores[i]);
            }
        } else {
            System.out.println("No existing stores");
        }
        
        try {
            RecordStore.deleteRecordStore("TestStore");
        } catch (RecordStoreNotFoundException e) {
            // Store doesn't exist yet, ignore
        }
        System.out.println("Deleted TestStore (if existed)");
        
        RecordStore rs = RecordStore.openRecordStore("TestStore", true);
        System.out.println("Opened/created TestStore");
        System.out.print("Store name: ");
        System.out.println(rs.getName());
        System.out.print("Store version: ");
        System.out.println(rs.getVersion());
        System.out.print("Num records: ");
        System.out.println(rs.getNumRecords());
        System.out.print("Size: ");
        System.out.println(rs.getSize());
        System.out.print("Size available: ");
        System.out.println(rs.getSizeAvailable());
        System.out.print("Last modified: ");
        System.out.println(rs.getLastModified());
        
        byte[] data1 = "Hello World".getBytes();
        int recordId1 = rs.addRecord(data1, 0, data1.length);
        System.out.print("Added record 1, ID: ");
        System.out.println(recordId1);
        System.out.print("Num records: ");
        System.out.println(rs.getNumRecords());
        System.out.print("Version: ");
        System.out.println(rs.getVersion());
        
        byte[] data2 = "Test Data".getBytes();
        int recordId2 = rs.addRecord(data2, 0, data2.length);
        System.out.print("Added record 2, ID: ");
        System.out.println(recordId2);
        System.out.print("Num records: ");
        System.out.println(rs.getNumRecords());
        
        byte[] data3 = new byte[100];
        for (int i = 0; i < data3.length; i++) {
            data3[i] = (byte)i;
        }
        int recordId3 = rs.addRecord(data3, 0, data3.length);
        System.out.print("Added record 3 (100 bytes), ID: ");
        System.out.println(recordId3);
        
        int size1 = rs.getRecordSize(recordId1);
        System.out.print("Record 1 size: ");
        System.out.println(size1);
        
        byte[] retrieved1 = rs.getRecord(recordId1);
        System.out.print("Retrieved record 1: ");
        System.out.println(new String(retrieved1));
        
        byte[] buffer = new byte[100];
        int len = rs.getRecord(recordId2, buffer, 0);
        System.out.print("Retrieved record 2 length: ");
        System.out.println(len);
        System.out.print("Retrieved record 2: ");
        System.out.println(new String(buffer, 0, len));
        
        byte[] newData1 = "Updated Hello".getBytes();
        rs.setRecord(recordId1, newData1, 0, newData1.length);
        System.out.println("Updated record 1");
        System.out.print("Version: ");
        System.out.println(rs.getVersion());
        
        byte[] retrieved1Updated = rs.getRecord(recordId1);
        System.out.print("Retrieved updated record 1: ");
        System.out.println(new String(retrieved1Updated));
        
        rs.deleteRecord(recordId2);
        System.out.println("Deleted record 2");
        System.out.print("Num records: ");
        System.out.println(rs.getNumRecords());
        
        int nextId = rs.getNextRecordID();
        System.out.print("Next record ID: ");
        System.out.println(nextId);
        
        rs.closeRecordStore();
        System.out.println("Closed TestStore");
        
        RecordStore rs2 = RecordStore.openRecordStore("TestStore", false);
        System.out.println("Reopened TestStore");
        System.out.print("Num records after reopen: ");
        System.out.println(rs2.getNumRecords());
        
        try {
            byte[] deletedRecord = rs2.getRecord(recordId2);
            System.out.println("ERROR: Should not retrieve deleted record");
        } catch (InvalidRecordIDException e) {
            System.out.println("Correctly threw InvalidRecordIDException for deleted record");
        }
        
        rs2.closeRecordStore();
        
        RecordStore.deleteRecordStore("TestStore");
        System.out.println("Deleted TestStore");
    }
    
    private void testRecordEnumeration() throws RecordStoreException {
        System.out.println("\n=== RecordEnumeration Test ===");
        
        RecordStore rs = RecordStore.openRecordStore("EnumTest", true);
        
        for (int i = 1; i <= 5; i++) {
            String data = "Record ";
            byte[] dataBytes1 = data.getBytes();
            byte[] dataBytes2 = String.valueOf(i).getBytes();
            byte[] combined = new byte[dataBytes1.length + dataBytes2.length];
            System.arraycopy(dataBytes1, 0, combined, 0, dataBytes1.length);
            System.arraycopy(dataBytes2, 0, combined, dataBytes1.length, dataBytes2.length);
            rs.addRecord(combined, 0, combined.length);
        }
        System.out.println("Added 5 records");
        
        RecordEnumeration enum1 = rs.enumerateRecords(null, null, false);
        System.out.println("Created enumeration");
        System.out.print("Num records: ");
        System.out.println(enum1.numRecords());
        System.out.print("Has next: ");
        System.out.println(enum1.hasNextElement());
        System.out.print("Has previous: ");
        System.out.println(enum1.hasPreviousElement());
        
        int count = 0;
        while (enum1.hasNextElement()) {
            int recordId = enum1.nextRecordId();
            byte[] data = rs.getRecord(recordId);
            count++;
            System.out.print("Record ");
            System.out.print(count);
            System.out.print(" ID: ");
            System.out.print(recordId);
            System.out.print(", Data: ");
            System.out.println(new String(data));
        }
        
        enum1.reset();
        System.out.println("Reset enumeration");
        System.out.print("Has next after reset: ");
        System.out.println(enum1.hasNextElement());
        
        enum1.rebuild();
        System.out.println("Rebuilt enumeration");
        
        enum1.destroy();
        System.out.println("Destroyed enumeration");
        
        RecordFilter filter = new RecordFilter() {
            public boolean matches(byte[] candidate) {
                String str = new String(candidate);
                return str.indexOf('2') >= 0 || str.indexOf('4') >= 0;
            }
        };
        
        RecordEnumeration enum2 = rs.enumerateRecords(filter, null, false);
        System.out.println("Created filtered enumeration (records with 2 or 4)");
        System.out.print("Filtered num records: ");
        System.out.println(enum2.numRecords());
        
        while (enum2.hasNextElement()) {
            byte[] data = enum2.nextRecord();
            System.out.print("Filtered record: ");
            System.out.println(new String(data));
        }
        
        enum2.destroy();
        
        RecordComparator comparator = new RecordComparator() {
            public int compare(byte[] rec1, byte[] rec2) {
                String s1 = new String(rec1);
                String s2 = new String(rec2);
                return s1.compareTo(s2);
            }
        };
        
        RecordEnumeration enum3 = rs.enumerateRecords(null, comparator, false);
        System.out.println("Created sorted enumeration");
        
        while (enum3.hasNextElement()) {
            byte[] data = enum3.nextRecord();
            System.out.print("Sorted record: ");
            System.out.println(new String(data));
        }
        
        enum3.destroy();
        rs.closeRecordStore();
        RecordStore.deleteRecordStore("EnumTest");
    }
    
    private void testRecordListener() throws RecordStoreException {
        System.out.println("\n=== RecordListener Test ===");
        
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
        
        byte[] data2 = "Test 2".getBytes();
        int id2 = rs.addRecord(data2, 0, data2.length);
        
        byte[] newData = "Updated Test 1".getBytes();
        rs.setRecord(id1, newData, 0, newData.length);
        
        rs.deleteRecord(id2);
        
        rs.removeRecordListener(listener);
        System.out.println("Removed record listener");
        
        byte[] data3 = "Test 3".getBytes();
        rs.addRecord(data3, 0, data3.length);
        System.out.println("Added record after removing listener (no event)");
        
        rs.closeRecordStore();
        RecordStore.deleteRecordStore("ListenerTest");
    }

    protected void startApp() {
        Canvas canvas = new Canvas() {
            protected void paint(Graphics g) {
                g.setColor(255, 255, 255);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(0, 0, 0);
                g.drawString("RMS Test Complete", 10, 10, Graphics.TOP | Graphics.LEFT);
            }
        };
        
        display.setCurrent(canvas);
        canvas.repaint();
        System.out.println("RMSSuite started");
    }

    protected void pauseApp() {
        System.out.println("RMSSuite paused");
    }

    protected void destroyApp(boolean unconditional) {
        System.out.println("RMSSuite destroyed");
        
        try {
            RecordStore.deleteRecordStore("TestStore");
            RecordStore.deleteRecordStore("EnumTest");
            RecordStore.deleteRecordStore("ListenerTest");
        } catch (Exception e) {
        }
    }
}
