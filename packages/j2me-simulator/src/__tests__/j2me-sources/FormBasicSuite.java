import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormBasicSuite extends MIDlet implements CommandListener {
    private Display display;
    private Form form;
    private Command exitCommand;
    private Command okCommand;

    public FormBasicSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form Basic Test ===");
        
        testFormBasics();
        
        System.out.println("Form basic test completed");
    }
    
    private void testFormBasics() {
        System.out.println("Test: Form basics");
        
        form = new Form("Test Form");
        System.out.print("Form title: ");
        System.out.println(form.getTitle());
        
        form.setTitle("Updated Title");
        System.out.print("Updated title: ");
        System.out.println(form.getTitle());
        
        System.out.print("Form width: ");
        System.out.println(form.getWidth());
        System.out.print("Form height: ");
        System.out.println(form.getHeight());
        
        exitCommand = new Command("Exit", Command.EXIT, 1);
        okCommand = new Command("OK", Command.OK, 1);
        
        form.addCommand(exitCommand);
        form.addCommand(okCommand);
        form.setCommandListener(this);
        
        System.out.println("Commands added");
    }
    
    public void commandAction(Command c, Displayable d) {
        System.out.print("Command triggered: ");
        System.out.println(c.getLabel());
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
