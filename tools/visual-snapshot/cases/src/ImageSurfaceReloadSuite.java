import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.io.*;

/**
 * MIDlet that decodes an image from byte array on first paint only.
 * Simulates a game that loads a scene image when entering a room.
 *
 * This reproduces the bug: after load_state, next_surface_id resets to saved value.
 * Between save and second load, frames run and allocate new surface IDs in JS.
 * When second load restores the ID counter, the first paint triggers DecodeImage
 * with a surface_id that already has a canvas in JS. The handler draws to the
 * stale canvas, then host_create_surface replaces it with a blank one → white screen.
 *
 * Strategy: Decode image only on first paint (img == null). After load_state restores
 * JVM state, img field is null again (heap is restored to saved state where img was
 * already set from the first paint). Wait — actually after load, the JVM is restored
 * to the save point where img was already decoded. So we need img to be null at save
 * time. We achieve this by saving early (before first decode completes) or by using
 * a flag that forces re-decode after each load.
 *
 * Simplified approach: Always decode on every paint. The last frame before screenshot
 * will have its image overwritten by the bug.
 */
public class ImageSurfaceReloadSuite extends MIDlet {
    public ImageSurfaceReloadSuite() {}

    protected void startApp() {
        byte[] iconBytes = null;
        try {
            InputStream is = getClass().getResourceAsStream("/icon.png");
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int b;
                while ((b = is.read()) != -1) {
                    baos.write(b);
                }
                is.close();
                iconBytes = baos.toByteArray();
            }
        } catch (IOException e) {}
        Display.getDisplay(this).setCurrent(new ImageCanvas(iconBytes));
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class ImageCanvas extends Canvas implements Runnable {
        private byte[] pngBytes;
        private Image img;
        private int frame;
        private boolean running = true;

        ImageCanvas(byte[] pngBytes) {
            this.pngBytes = pngBytes;
            new Thread(this).start();
        }

        public void run() {
            while (running) {
                repaint();
                serviceRepaints();
                try { Thread.sleep(150); } catch (InterruptedException e) { break; }
            }
        }

        protected void paint(Graphics g) {
            frame++;
            int w = getWidth(), h = getHeight();

            // Decode image from bytes every frame — forces a new DecodeImage async request
            // each time, allocating a new surface_id.
            if (pngBytes != null) {
                try {
                    img = Image.createImage(pngBytes, 0, pngBytes.length);
                } catch (Exception e) {
                    img = null;
                }
            }

            // Draw to display
            g.setColor(255, 255, 255);
            g.fillRect(0, 0, w, h);
            if (img != null) {
                g.drawImage(img, w / 2, h / 2, Graphics.HCENTER | Graphics.VCENTER);
                g.setColor(255, 0, 0);
                int iw = img.getWidth(), ih = img.getHeight();
                g.drawRect(w/2 - iw/2 - 1, h/2 - ih/2 - 1, iw + 2, ih + 2);
            }
        }
    }
}
