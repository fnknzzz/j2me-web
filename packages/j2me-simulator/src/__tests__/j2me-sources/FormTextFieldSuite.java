import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormTextFieldSuite extends MIDlet {
    private Display display;
    private Form form;

    public FormTextFieldSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form TextField Test ===");
        
        form = new Form("TextField Test");
        testTextField();
        
        System.out.println("Form TextField test completed");
    }
    
    private void testTextField() {
        System.out.println("Test: TextField");
        
        TextField textField = new TextField("Name:", "John", 20, TextField.ANY);
        form.append(textField);
        
        System.out.print("TextField label: ");
        System.out.println(textField.getLabel());
        System.out.print("TextField text: ");
        System.out.println(textField.getString());
        System.out.print("Max size: ");
        System.out.println(textField.getMaxSize());
        System.out.print("Size: ");
        System.out.println(textField.size());
        
        textField.setString("Jane");
        System.out.print("Updated text: ");
        System.out.println(textField.getString());
        
        textField.insert(" Doe", textField.size());
        System.out.print("After insert: ");
        System.out.println(textField.getString());
        
        textField.delete(0, 4);
        System.out.print("After delete: ");
        System.out.println(textField.getString());
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
