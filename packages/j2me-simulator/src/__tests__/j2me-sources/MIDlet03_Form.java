import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class MIDlet03_Form extends MIDlet {
    private Display display;
    private Form form;

    public MIDlet03_Form() {
        display = Display.getDisplay(this);
        form = new Form("Test Form");
        System.out.println("Form created");
    }

    protected void startApp() {
        System.out.print("Form title: ");
        System.out.println(form.getTitle());
        display.setCurrent(form);
        System.out.println("Form set as current");
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
