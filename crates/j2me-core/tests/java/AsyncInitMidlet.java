import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;

/**
 * Reproduces the async-init pattern: <init> triggers Image.createImage() which
 * issues an async decode_image request. If startApp runs before the async
 * completes, this.image is null and accessing it causes a NullPointerException.
 *
 * Compiled against packages/jvm-rust/j2me-rt/classes.jar.
 * The precompiled .class is committed alongside this source so that build.rs
 * (which has no J2ME classpath) can skip recompiling it.
 *
 * Lifecycle output (happy path):
 *   AsyncInitMidlet: constructor start
 *   AsyncInitMidlet: image loaded w=1 h=1   (or w=0 h=0 for stub)
 *   AsyncInitMidlet: startApp ok
 *
 * Bug path (startApp before async completes):
 *   AsyncInitMidlet: constructor start
 *   NullPointerException  (image.getWidth() on null)
 */
public class AsyncInitMidlet extends MIDlet {

    // Minimal 1x1 RGB PNG — avoids any external resource dependency.
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

    public AsyncInitMidlet() {
        System.out.println("AsyncInitMidlet: constructor start");
        // This call issues an async decode_image request and blocks the
        // <init> thread until the host resolves it.
        image = Image.createImage(PNG_1x1, 0, PNG_1x1.length);
        System.out.println("AsyncInitMidlet: image loaded w=" + image.getWidth() + " h=" + image.getHeight());
    }

    protected void startApp() {
        // If <init> hasn't finished, image is null here -> NPE.
        System.out.println("AsyncInitMidlet: startApp ok w=" + image.getWidth());
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}
}
