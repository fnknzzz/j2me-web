import javax.microedition.midlet.*;

public class MIDlet01_Lifecycle extends MIDlet {
    public MIDlet01_Lifecycle() {
        System.out.println("Constructor called");
    }

    protected void startApp() {
        System.out.println("startApp called");
    }

    protected void pauseApp() {
        System.out.println("pauseApp called");
    }

    protected void destroyApp(boolean unconditional) {
        System.out.println("destroyApp called");
    }
}
