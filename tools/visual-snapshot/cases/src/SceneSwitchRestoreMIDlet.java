import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

/**
 * 复现：存档 → 按键触发场景切换(释放+重建 derived sprites) → 读档 → 精灵不渲染
 * 关键：static mutable buffer 是触发条件
 */
public class SceneSwitchRestoreMIDlet extends MIDlet {
    public void startApp() {
        Display.getDisplay(this).setCurrent(new GameCanvas_B());
    }
    public void pauseApp() {}
    public void destroyApp(boolean b) {}
}

class GameCanvas_B extends Canvas {

    static Image u;
    static Graphics aG;

    private Image[] aY;
    private boolean switched = false;

    GameCanvas_B() {
        setFullScreenMode(true);
        u = Image.createImage(240, 320);
        aG = u.getGraphics();
        loadSprites("/sprites.png", 9);
    }

    private void loadSprites(String path, int count) {
        try {
            Image image = Image.createImage(path);
            aY = new Image[count];
            for (int i = 0; i < count; i++) {
                aY[i] = Image.createImage(image, i * 16, 0, 16, 17, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void keyPressed(int keyCode) {
        if (!switched) {
            aY = null;
            System.gc();
            loadSprites("/sprites.png", 4);
            System.gc();
            switched = true;
            repaint();
        }
    }

    protected void paint(Graphics g) {
        aG.setColor(0x336699);
        aG.fillRect(0, 0, 240, 320);
        g.drawImage(u, 0, 0, Graphics.TOP | Graphics.LEFT);

        if (aY != null) {
            int cols = (aY.length <= 4) ? 2 : 3;
            for (int i = 0; i < aY.length; i++) {
                if (aY[i] != null) {
                    g.drawImage(aY[i], 40 + (i % cols) * 40, 60 + (i / cols) * 40,
                        Graphics.TOP | Graphics.LEFT);
                }
            }
        }
    }
}
