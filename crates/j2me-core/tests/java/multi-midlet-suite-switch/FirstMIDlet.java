import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;

public final class FirstMIDlet extends MIDlet {
    private FirstCanvas canvas;

    protected void startApp() {
        SharedState.value = 41;
        System.out.println("FirstMIDlet start value=" + SharedState.value);
        if (canvas == null) {
            canvas = new FirstCanvas();
        }
        Display.getDisplay(this).setCurrent(canvas);
        canvas.repaint();
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {
        System.out.println("FirstMIDlet destroy");
    }

    private void exit() {
        notifyDestroyed();
    }

    private final class FirstCanvas extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(0xffffff);
            g.fillRect(0, 0, w, h);
            g.setColor(0x111111);
            g.drawString("FirstMIDlet screen", 8, 8, Graphics.TOP | Graphics.LEFT);
            g.drawString("Press 5 to exit", 8, 28, Graphics.TOP | Graphics.LEFT);
        }

        protected void keyPressed(int keyCode) {
            if (keyCode == KEY_NUM5 || getGameAction(keyCode) == FIRE) {
                exit();
            }
        }

        protected void pointerPressed(int x, int y) {
            repaint();
        }
    }
}
