import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

/**
 * Regression repro for Canvas code that draws an in-game menu incrementally.
 *
 * The first paint draws the full game scene and then switches into menu mode.
 * Subsequent paints deliberately draw only the bottom menu strip, relying on
 * the existing display backbuffer for the rest of surface 0. A save taken after
 * the first paint restores Java state with menuOpen=true; load_state + repaint
 * then emits only incremental menu commands.
 */
public class IncrementalMenuRestoreMIDlet extends MIDlet {
    public void startApp() {
        Display.getDisplay(this).setCurrent(new IncrementalMenuCanvas());
    }
    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {}
}

class IncrementalMenuCanvas extends Canvas {
    private boolean initialized = false;
    private boolean menuOpen = false;
    private boolean dirtyScene = false;

    IncrementalMenuCanvas() {
        setFullScreenMode(true);
    }

    protected void paint(Graphics g) {
        if (!initialized) {
            drawScene(g);
            initialized = true;
            menuOpen = true;
            repaint();
            return;
        }

        if (menuOpen) {
            drawMenuStrip(g);
        } else if (dirtyScene) {
            drawDirtyScene(g);
        } else {
            drawScene(g);
        }
    }

    protected void keyPressed(int keyCode) {
        dirtyScene = true;
        menuOpen = false;
        repaint();
    }

    private void drawScene(Graphics g) {
        g.setColor(0x123456);
        g.fillRect(0, 0, 240, 320);
        g.setColor(0xFFFFFF);
        g.drawString("SAVED BACKBUFFER", 12, 12, Graphics.TOP | Graphics.LEFT);
    }

    private void drawMenuStrip(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(0, 292, 240, 28);
        g.setColor(0xFFFFFF);
        g.drawString("继续", 12, 298, Graphics.TOP | Graphics.LEFT);
    }

    private void drawDirtyScene(Graphics g) {
        g.setColor(0x7A1020);
        g.fillRect(0, 0, 240, 320);
        g.setColor(0xFFFFFF);
        g.drawString("DIRTY SURFACE", 12, 12, Graphics.TOP | Graphics.LEFT);
    }
}
