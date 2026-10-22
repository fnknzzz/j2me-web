import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormGaugeSuite extends MIDlet {
    private Display display;
    private Form form;

    public FormGaugeSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form Gauge Test ===");
        
        form = new Form("Gauge Test");
        testGauge();
        
        System.out.println("Form Gauge test completed");
    }
    
    private void testGauge() {
        System.out.println("Test: Gauge");
        
        Gauge gauge = new Gauge("Progress:", true, 100, 50);
        form.append(gauge);
        
        System.out.print("Gauge label: ");
        System.out.println(gauge.getLabel());
        System.out.print("Max value: ");
        System.out.println(gauge.getMaxValue());
        System.out.print("Current value: ");
        System.out.println(gauge.getValue());
        System.out.print("Is interactive: ");
        System.out.println(gauge.isInteractive());
        
        gauge.setValue(75);
        System.out.print("Updated value: ");
        System.out.println(gauge.getValue());
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
