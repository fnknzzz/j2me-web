import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

public final class AsyncSwitchMIDlet extends MIDlet {
    private static final byte[] PNG_1x1 = {
        (byte)-119, 80, 78, 71, 13, 10, 26, 10,
        0, 0, 0, 13, 73, 72, 68, 82,
        0, 0, 0, 1, 0, 0, 0, 1,
        8, 2, 0, 0, 0, (byte)-112, 119, 83, (byte)-34,
        0, 0, 0, 12, 73, 68, 65, 84,
        120, (byte)-100, 99, (byte)-8, (byte)-1, (byte)-1, 63, 0,
        5, (byte)-2, 2, (byte)-2, 13, (byte)-17, 70, (byte)-72,
        0, 0, 0, 0, 73, 69, 78, 68,
        (byte)-82, 66, 96, (byte)-126
    };

    private Image image;

    public AsyncSwitchMIDlet() {
        System.out.println("AsyncSwitchMIDlet constructor start");
        image = Image.createImage(PNG_1x1, 0, PNG_1x1.length);
        System.out.println("AsyncSwitchMIDlet constructor image w=" + image.getWidth());
    }

    protected void startApp() {
        System.out.println("AsyncSwitchMIDlet startApp w=" + image.getWidth());
        System.out.println("SUITE_SWITCH_ASYNC_START_OK");
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {
        System.out.println("AsyncSwitchMIDlet destroy start");
        Image destroyImage = Image.createImage(PNG_1x1, 0, PNG_1x1.length);
        System.out.println("AsyncSwitchMIDlet destroy image w=" + destroyImage.getWidth());
        System.out.println("SUITE_SWITCH_ASYNC_DESTROY_OK");
    }
}
