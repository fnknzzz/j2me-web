import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormSpacerSuite extends MIDlet {
    private Display display;
    private Form form;

    public FormSpacerSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form Spacer Test ===");
        
        form = new Form("Spacer Test");
        testSpacer();
        
        System.out.println("Form Spacer test completed");
    }
    
    private void testSpacer() {
        System.out.println("Test: Spacer");
        
        Spacer spacer = new Spacer(100, 20);
        form.append(spacer);
        
        System.out.print("Spacer min width: ");
        System.out.println(spacer.getMinimumWidth());
        System.out.print("Spacer min height: ");
        System.out.println(spacer.getMinimumHeight());
        
        spacer.setMinimumSize(150, 30);
        System.out.print("Updated min width: ");
        System.out.println(spacer.getMinimumWidth());
        System.out.print("Updated min height: ");
        System.out.println(spacer.getMinimumHeight());
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
