import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSFilterComparatorSuite extends MIDlet {
    protected void startApp() {
        try {
            testFilterAndComparator();
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testFilterAndComparator() throws RecordStoreException {
        System.out.println("=== Filter and Comparator Test ===");
        
        RecordStore rs = RecordStore.openRecordStore("FilterTest", true);
        
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
        
        RecordFilter filter = new RecordFilter() {
            public boolean matches(byte[] candidate) {
                String str = new String(candidate);
                return str.indexOf('2') >= 0 || str.indexOf('4') >= 0;
            }
        };
        
        RecordEnumeration enum1 = rs.enumerateRecords(filter, null, false);
        System.out.println("Created filtered enumeration (records with 2 or 4)");
        System.out.print("Filtered num records: ");
        System.out.println(enum1.numRecords());
        
        while (enum1.hasNextElement()) {
            byte[] data = enum1.nextRecord();
            System.out.print("Filtered record: ");
            System.out.println(new String(data));
        }
        
        enum1.destroy();
        
        RecordComparator comparator = new RecordComparator() {
            public int compare(byte[] rec1, byte[] rec2) {
                String s1 = new String(rec1);
                String s2 = new String(rec2);
                return s1.compareTo(s2);
            }
        };
        
        RecordEnumeration enum2 = rs.enumerateRecords(null, comparator, false);
        System.out.println("Created sorted enumeration");
        
        while (enum2.hasNextElement()) {
            byte[] data = enum2.nextRecord();
            System.out.print("Sorted record: ");
            System.out.println(new String(data));
        }
        
        enum2.destroy();
        
        RecordEnumeration enum3 = rs.enumerateRecords(filter, comparator, false);
        System.out.println("Created filtered and sorted enumeration");
        System.out.print("Filtered and sorted num records: ");
        System.out.println(enum3.numRecords());
        
        while (enum3.hasNextElement()) {
            byte[] data = enum3.nextRecord();
            System.out.print("Filtered and sorted record: ");
            System.out.println(new String(data));
        }
        
        enum3.destroy();
        
        rs.closeRecordStore();
        RecordStore.deleteRecordStore("FilterTest");
        System.out.println("Test completed");
    }
    
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
