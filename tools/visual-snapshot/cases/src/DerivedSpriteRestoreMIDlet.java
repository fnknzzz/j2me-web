import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

/**
 * 最小复现：derived image (createImage region from resource) 在 save-load 后不渲染。
 *
 * 核心路径：
 *   1. 从 JAR resource 加载 sprite sheet → resource image
 *   2. 从 resource image 裁切 derived images
 *   3. paint() 绘制 derived images
 *   4. save-load 后 derived images surface 恢复失败 → 精灵不可见
 *
 * 期望：蓝灰色背景 + 9 个彩色小人。
 * Bug：背景可见，小人不可见。
 */
public class DerivedSpriteRestoreMIDlet extends MIDlet {
    public void startApp() {
        Display.getDisplay(this).setCurrent(new StaticSpriteCanvas());
    }
    public void pauseApp() {}
    public void destroyApp(boolean b) {}
}

class StaticSpriteCanvas extends Canvas {
    private Image[] sprites;

    StaticSpriteCanvas() {
        setFullScreenMode(true);

        // 从 JAR resource 加载 sprite sheet (resource image)
        Image sheet = null;
        try {
            sheet = Image.createImage("/sprites.png");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 从 resource image 裁切 9 个 derived images
        if (sheet != null) {
            sprites = new Image[9];
            for (int i = 0; i < 9; i++) {
                sprites[i] = Image.createImage(sheet, i * 16, 0, 16, 17, 0);
            }
        }
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(0x336699);
        g.fillRect(0, 0, w, h);

        if (sprites != null) {
            int startX = (w - 3 * 32) / 2 + 16;
            int startY = (h - 3 * 32) / 2 + 16;
            for (int i = 0; i < 9; i++) {
                int col = i % 3;
                int row = i / 3;
                g.drawImage(sprites[i], startX + col * 32, startY + row * 32,
                    Graphics.HCENTER | Graphics.VCENTER);
            }
        }
    }

    protected void keyPressed(int keyCode) {
        repaint();
    }
}
