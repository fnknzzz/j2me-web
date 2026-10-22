import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class ListAlertSuite extends MIDlet implements CommandListener {
    private Display display;
    private Command exitCommand;

    public ListAlertSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== List and Alert Test ===");
        
        testListExclusive();
        testListMultiple();
        testListImplicit();
        testAlert();
        testAlertTypes();
        
        System.out.println("List and Alert test completed");
    }
    
    private void testListExclusive() {
        System.out.println("Test 1: Exclusive List");
        
        List list = new List("Select One", Choice.EXCLUSIVE);
        list.append("Item 1", null);
        list.append("Item 2", null);
        list.append("Item 3", null);
        
        System.out.print("List title: ");
        System.out.println(list.getTitle());
        System.out.print("List size: ");
        System.out.println(list.size());
        
        list.setSelectedIndex(1, true);
        System.out.print("Selected index: ");
        System.out.println(list.getSelectedIndex());
        System.out.print("Is selected (1): ");
        System.out.println(list.isSelected(1));
        
        System.out.print("Item text (1): ");
        System.out.println(list.getString(1));
        
        list.set(1, "Updated Item 2", null);
        System.out.print("Updated item text (1): ");
        System.out.println(list.getString(1));
        
        list.insert(1, "New Item", null);
        System.out.print("Size after insert: ");
        System.out.println(list.size());
        
        list.delete(1);
        System.out.print("Size after delete: ");
        System.out.println(list.size());
    }
    
    private void testListMultiple() {
        System.out.println("Test 2: Multiple List");
        
        List list = new List("Select Multiple", Choice.MULTIPLE);
        list.append("Option A", null);
        list.append("Option B", null);
        list.append("Option C", null);
        list.append("Option D", null);
        
        System.out.print("List size: ");
        System.out.println(list.size());
        
        list.setSelectedIndex(0, true);
        list.setSelectedIndex(2, true);
        
        boolean[] selected = new boolean[list.size()];
        int count = list.getSelectedFlags(selected);
        System.out.print("Selected count: ");
        System.out.println(count);
        
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                System.out.print("Selected: ");
                System.out.println(list.getString(i));
            }
        }
        
        list.setSelectedFlags(new boolean[]{false, true, true, false});
        System.out.println("Selection flags updated");
    }
    
    private void testListImplicit() {
        System.out.println("Test 3: Implicit List");
        
        List list = new List("Menu", Choice.IMPLICIT);
        list.append("New Game", null);
        list.append("Continue", null);
        list.append("Settings", null);
        list.append("Exit", null);
        
        System.out.print("List size: ");
        System.out.println(list.size());
        
        exitCommand = new Command("Back", Command.BACK, 1);
        list.addCommand(exitCommand);
        list.setCommandListener(this);
        
        System.out.println("Implicit list created with commands");
    }
    
    private void testAlert() {
        System.out.println("Test 4: Alert basics");
        
        Alert alert = new Alert("Warning");
        System.out.print("Alert title: ");
        System.out.println(alert.getTitle());
        
        alert.setString("This is a warning message");
        System.out.print("Alert text: ");
        System.out.println(alert.getString());
        
        alert.setTimeout(3000);
        System.out.print("Alert timeout: ");
        System.out.println(alert.getTimeout());
        
        alert.setTimeout(Alert.FOREVER);
        System.out.print("Alert timeout (FOREVER): ");
        System.out.println(alert.getTimeout());
        
        try {
            Image img = Image.createImage(32, 32);
            alert.setImage(img);
            System.out.println("Alert image set");
            
            Image retrievedImg = alert.getImage();
            if (retrievedImg != null) {
                System.out.println("Alert image retrieved");
            }
        } catch (Exception e) {
            System.out.print("Error: ");
            System.out.println(e.getMessage());
        }
    }
    
    private void testAlertTypes() {
        System.out.println("Test 5: Alert types");
        
        Alert infoAlert = new Alert("Info", "Information message", null, AlertType.INFO);
        System.out.print("Info alert type: ");
        System.out.println(infoAlert.getType() == AlertType.INFO);
        
        Alert warnAlert = new Alert("Warning", "Warning message", null, AlertType.WARNING);
        System.out.print("Warning alert type: ");
        System.out.println(warnAlert.getType() == AlertType.WARNING);
        
        Alert errorAlert = new Alert("Error", "Error message", null, AlertType.ERROR);
        System.out.print("Error alert type: ");
        System.out.println(errorAlert.getType() == AlertType.ERROR);
        
        Alert confirmAlert = new Alert("Confirm", "Confirmation message", null, AlertType.CONFIRMATION);
        System.out.print("Confirmation alert type: ");
        System.out.println(confirmAlert.getType() == AlertType.CONFIRMATION);
        
        Alert alarmAlert = new Alert("Alarm", "Alarm message", null, AlertType.ALARM);
        System.out.print("Alarm alert type: ");
        System.out.println(alarmAlert.getType() == AlertType.ALARM);
        
        System.out.println("Alert types tested");
    }
    
    public void commandAction(Command c, Displayable d) {
        System.out.print("Command triggered: ");
        System.out.println(c.getLabel());
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
