import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

public class RMSReadWriteSuite extends MIDlet {
    private Display display;

    public RMSReadWriteSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== RMS Read/Write ===");
        
        try {
            RecordStore rs = RecordStore.openRecordStore("rwStore", true);
            
            byte[] writeData = "Test Data".getBytes();
            System.out.println("Writing record");
            int id = rs.addRecord(writeData, 0, writeData.length);
            System.out.print("Written record ID: ");
            System.out.println(id);
            
            System.out.println("Reading record");
            byte[] readData = rs.getRecord(id);
            String readStr = new String(readData);
            System.out.print("Read data: ");
            System.out.println(readStr);
            
            System.out.print("Record size: ");
            System.out.println(rs.getRecordSize(id));
            
            byte[] updateData = "Updated".getBytes();
            System.out.println("Updating record");
            rs.setRecord(id, updateData, 0, updateData.length);
            
            byte[] readUpdated = rs.getRecord(id);
            String updatedStr = new String(readUpdated);
            System.out.print("Updated data: ");
            System.out.println(updatedStr);
            
            rs.closeRecordStore();
            
        } catch (Exception e) {
            System.out.print("RMS error: ");
            System.out.println(e.getMessage());
        }
        
        System.out.println("RMSReadWriteSuite started");
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {
        try {
            RecordStore.deleteRecordStore("rwStore");
        } catch (Exception e) {}
    }
}
