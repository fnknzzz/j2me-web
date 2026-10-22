import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class MIDlet02_Display extends MIDlet {
    private Display display;

    public MIDlet02_Display() {
        System.out.println("Getting display...");
        display = Display.getDisplay(this);
        System.out.println("Display obtained");
    }

    protected void startApp() {
        System.out.print("Display class: ");
        System.out.println(display.getClass().getName());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }
}
