import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSEnumerationSuite extends MIDlet {
    protected void startApp() {
        try {
            testEnumeration();
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testEnumeration() throws RecordStoreException {
        System.out.println("=== RecordEnumeration Test ===");
        
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
            byte[] data = enum1.nextRecord();
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
        
        rs.closeRecordStore();
        RecordStore.deleteRecordStore("EnumTest");
        System.out.println("Test completed");
    }
    
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
