import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormDateFieldSuite extends MIDlet {
    private Display display;
    private Form form;

    public FormDateFieldSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form DateField Test ===");
        
        form = new Form("DateField Test");
        testDateField();
        
        System.out.println("Form DateField test completed");
    }
    
    private void testDateField() {
        System.out.println("Test: DateField");
        
        DateField dateField = new DateField("Date:", DateField.DATE);
        form.append(dateField);
        
        System.out.print("DateField label: ");
        System.out.println(dateField.getLabel());
        System.out.print("Input mode: ");
        System.out.println(dateField.getInputMode());
        
        java.util.Date date = new java.util.Date();
        dateField.setDate(date);
        System.out.println("Date set successfully");
        
        java.util.Date retrievedDate = dateField.getDate();
        if (retrievedDate != null) {
            System.out.println("Date retrieved successfully");
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
