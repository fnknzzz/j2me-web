import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class HelloMIDlet extends MIDlet implements CommandListener {
    private Display display;
    private Form form;
    private Command exitCommand;

    public HelloMIDlet() {
        display = Display.getDisplay(this);
        form = new Form("Hello J2ME");
        exitCommand = new Command("Exit", Command.EXIT, 1);
        
        form.append("Hello, J2ME World!");
        form.addCommand(exitCommand);
        form.setCommandListener(this);
    }

    protected void startApp() {
        display.setCurrent(form);
        System.out.println("HelloMIDlet started");
        System.out.print("Form title: ");
        System.out.println(form.getTitle());
    }

    protected void pauseApp() {
        System.out.println("HelloMIDlet paused");
    }

    protected void destroyApp(boolean unconditional) {
        System.out.println("HelloMIDlet destroyed");
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCommand) {
            System.out.println("Exit command triggered");
            destroyApp(true);
            notifyDestroyed();
        }
    }
}
