import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.io.*;

/**
 * 复现：从打包数据文件(pic.dat)加载 sprite sheet → 裁切 derived images →
 * 存档 → 切换场景(销毁旧sprites,重建新sprites) → 读档 → 精灵不渲染
 *
 * 与 SceneSwitchRestoreMIDlet 的关键差异：
 *   - sprite sheet 通过 Image.createImage(byte[], 0, length) 加载（模拟 pic.dat）
 *   - 这类图片不会被标记为 JarResource，save-load 时通过 pixel snapshot (DRAW_RGBA) 恢复
 *   - iOS 上 restorePendingImages() 在 flushPendingCommands() 之前执行，
 *     导致 derived image 恢复时 source surface 尚无像素数据
 *
 * 期望：蓝灰色背景 + 若干彩色小人精灵。
 * Bug：背景可见，小人全部空白/不可见。
 */
public class PackedSpriteRestoreMIDlet extends MIDlet {
    public void startApp() {
        Display.getDisplay(this).setCurrent(new PackedSpriteCanvas());
    }
    public void pauseApp() {}
    public void destroyApp(boolean b) {}
}

class PackedSpriteCanvas extends Canvas {

    static Image offscreen;
    static Graphics offscreenG;

    private Image[] sprites;
    private boolean switched = false;

    PackedSpriteCanvas() {
        setFullScreenMode(true);
        offscreen = Image.createImage(240, 320);
        offscreenG = offscreen.getGraphics();
        loadSpritesFromDat(9);
    }

    /**
     * 模拟 h.g(int) + h.a(image, idx, w, h, transform) 的流程：
     * 从 pic.dat 按索引读取 PNG 字节 → Image.createImage(byte[], 0, len) → 裁切 derived images
     *
     * 关键：Image.createImage(byte[], 0, len) 的字节不等于 JAR 中任何完整资源文件，
     * 因此 try_mark_image_surface_as_jar_resource 会失败，
     * 该 surface 只能通过 pixel snapshot (DRAW_RGBA) 恢复。
     */
    private void loadSpritesFromDat(int count) {
        try {
            // 读取 /pic.dat 的第 0 个条目（一张 sprite sheet PNG）
            byte[] pngBytes = loadDatSegment("/pic.dat", 0);
            Image sheet = Image.createImage(pngBytes, 0, pngBytes.length);

            sprites = new Image[count];
            int tileW = 16, tileH = 17;
            int cols = sheet.getWidth() / tileW;
            for (int i = 0; i < count; i++) {
                int x = (i % cols) * tileW;
                int y = (i / cols) * tileH;
                sprites[i] = Image.createImage(sheet, x, y, tileW, tileH, 0);
            }
        } catch (Exception e) {
            System.out.println("[PackedSpriteRestore] ERROR: " + e);
            e.printStackTrace();
        }
    }

    /**
     * 模拟 e.a(String, int)：从打包文件按索引提取子条目字节
     */
    private byte[] loadDatSegment(String path, int index) throws IOException {
        DataInputStream dis = new DataInputStream(
            getClass().getResourceAsStream(path));
        dis.readByte(); // 跳过 1 字节 header
        for (int i = 0; i < index; i++) {
            dis.skipBytes(dis.readShort() & 0xFFFF);
        }
        byte[] result = new byte[dis.readShort() & 0xFFFF];
        dis.readFully(result);
        dis.close();
        return result;
    }

    protected void keyPressed(int keyCode) {
        if (!switched) {
            // 模拟 b.h()：进入房间 → 销毁旧 sprites，重建新 sprites
            sprites = null;
            System.gc();
            loadSpritesFromDat(4); // 加载不同数量的 sprites（模拟不同房间）
            System.gc();
            switched = true;
            repaint();
        }
    }

    protected void paint(Graphics g) {
        // 绘制到离屏缓冲区（模拟 b.a(aG)）
        offscreenG.setColor(0x336699);
        offscreenG.fillRect(0, 0, 240, 320);

        // 将离屏缓冲区绘制到屏幕（模拟 ab.drawImage(u, ...)）
        g.drawImage(offscreen, 0, 0, Graphics.TOP | Graphics.LEFT);

        // 绘制精灵到屏幕（模拟 graphics.drawImage(aY[...], ...)）
        if (sprites != null) {
            int cols = (sprites.length <= 4) ? 2 : 3;
            for (int i = 0; i < sprites.length; i++) {
                if (sprites[i] != null) {
                    g.drawImage(sprites[i], 40 + (i % cols) * 40, 60 + (i / cols) * 40,
                        Graphics.TOP | Graphics.LEFT);
                }
            }
        }
    }
}
