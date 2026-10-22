import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

/**
 * Regression: after save → create new surfaces → load, the restored
 * next_surface_id causes newly allocated surface IDs to collide with
 * "ghost" surfaces still in iOS's SurfaceManager dictionary.
 *
 * Sequence:
 *   1. Constructor: create sprite32 (32x32 mutable, surfaceId=1)
 *   2. Save  (next_surface_id saved as 2)
 *   3. keyPress "5": create ghost64 (64x16 mutable, surfaceId=2)
 *                     keep reference alive so iOS surface is NOT destroyed
 *   4. Load  (next_surface_id restored to 2, but iOS still has surface 2 = 64x16)
 *   5. keyPress "0": create newSprite (32x32 mutable, surfaceId=2 allocated again)
 *                     → iOS createSurface(2,32,32) SKIPPED (already exists as 64x16)
 *                     → draw into wrong-sized surface → visual corruption
 *
 * Without fix (clearOffscreenSurfaces before load): newSprite is garbled
 * With fix: ghost surfaces are cleared, newSprite creates correctly
 */
public class StaleSurfaceRestoreMIDlet extends MIDlet {
    public void startApp() {
        Display.getDisplay(this).setCurrent(new StaleSurfaceCanvas());
    }
    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {}
}

class StaleSurfaceCanvas extends Canvas {
    private Image sprite;
    /** Kept alive to prevent GC from destroying the ghost surface */
    private Image ghost;
    private int phase = 0;

    StaleSurfaceCanvas() {
        setFullScreenMode(true);
        // Phase 0: initial 32x32 green sprite (surface 1)
        sprite = createFilledImage(32, 32, 0x00CC00);
    }

    protected void paint(Graphics g) {
        g.setColor(0x222222);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (sprite != null) {
            g.drawImage(sprite, 20, 20, Graphics.TOP | Graphics.LEFT);
            // Yellow border marks expected 32x32 area
            g.setColor(0xFFFF00);
            g.drawRect(19, 19, 33, 33);
        }
    }

    protected void keyPressed(int keyCode) {
        if (keyCode != Canvas.KEY_NUM0) {
            // After save: create a "ghost" surface with different dimensions.
            // This surface will remain in iOS's dict after load restores
            // next_surface_id, causing ID collision on next allocation.
            ghost = createFilledImage(64, 16, 0xAA0000);
            repaint();
        } else {
            // After load: create a NEW 32x32 sprite.
            // Rust allocates the same surface ID as the ghost (since
            // next_surface_id was restored). iOS should create a fresh
            // 32x32 surface, but without the fix it skips creation
            // because the 64x16 ghost surface with that ID still exists.
            sprite = createFilledImage(32, 32, 0x0066FF);
            repaint();
        }
    }

    private Image createFilledImage(int w, int h, int color) {
        Image img = Image.createImage(w, h);
        Graphics ig = img.getGraphics();
        ig.setColor(color);
        ig.fillRect(0, 0, w, h);
        // Cross pattern for visual identification
        ig.setColor(0xFFFFFF);
        ig.drawLine(0, 0, w - 1, h - 1);
        ig.drawLine(w - 1, 0, 0, h - 1);
        return img;
    }
}
