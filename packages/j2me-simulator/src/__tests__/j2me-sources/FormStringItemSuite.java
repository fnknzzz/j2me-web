import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormStringItemSuite extends MIDlet {
    private Display display;
    private Form form;

    public FormStringItemSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form StringItem Test ===");
        
        form = new Form("StringItem Test");
        testStringItem();
        
        System.out.println("Form StringItem test completed");
    }
    
    private void testStringItem() {
        System.out.println("Test: StringItem");
        
        StringItem item = new StringItem("Label:", "Text content");
        form.append(item);
        
        System.out.print("StringItem label: ");
        System.out.println(item.getLabel());
        System.out.print("StringItem text: ");
        System.out.println(item.getText());
        
        item.setText("Updated text");
        System.out.print("Updated text: ");
        System.out.println(item.getText());
        
        item.setLabel("New Label:");
        System.out.print("Updated label: ");
        System.out.println(item.getLabel());
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
