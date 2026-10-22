import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.io.*;

/**
 * 最小复现：宠物王国2紫 大地图渲染逻辑。
 * 精确复刻 b.class + c.class + h.class 的地图加载和渲染。
 *
 * 数据流:
 *   e.a("poke2map", mapId) → byte[] segment
 *   c.a(segment) → header解析 + h.if() tile切割 + 填充 cell 矩阵
 *   b.a(Graphics) → copyArea滚动 + drawTileRect增量绘制
 *   b.if(Graphics) → 主渲染入口
 */
public class TileMapRepro extends MIDlet {
    Display display;
    MapCanvas canvas;

    // === 常量 ===
    static final int TILE_SIZE = 16;
    static final int SCREEN_W = 240;
    static final int SCREEN_H = 320;

    // === 变换映射表 (对应 e.a[] static int[]) ===
    // Sprite.TRANS_* 常量: NONE=0, ROT90=5, ROT180=3, ROT270=6
    // MIRROR=2, MIRROR_ROT90=7, MIRROR_ROT180=1, MIRROR_ROT270=4
    static final int[] TRANSFORM_MAP = {0, 6, 3, 5, 2, 7, 1, 4};

    // === e.if 全局读取指针 (对应 e.if:I) ===
    int readPos;

    // === 地图数据 (对应 b.a6, b.al) ===
    int mapCols;  // a6
    int mapRows;  // al

    // === 双层地图格子 (对应 b.z, b.k) ===
    byte[][] layerBottom;  // [col][row]
    byte[][] layerTop;     // [col][row]

    // === Tileset 图片 (对应 c.new[]) ===
    Image[] bottomTileImages;
    Image[] topTileImages;



    // === 当前地图编号 (对应 b.y) ===
    int currentMapId = 5; // 选一个较大的地图

    public void startApp() {
        display = Display.getDisplay(this);
        canvas = new MapCanvas(this);
        display.setCurrent(canvas);
        try {
            initMap();
        } catch (Exception e) {
            System.out.println("[TileMapRepro] ERROR: " + e);
            e.printStackTrace();
        }
        canvas.repaint();
    }

    public void pauseApp() {}
    public void destroyApp(boolean u) {}

    // ================================================================
    //  e.a(String, int) —— 按索引加载 .dat 段
    // ================================================================

    byte[] loadDatSegment(String path, int index) throws IOException {
        DataInputStream dis = new DataInputStream(getClass().getResourceAsStream(path));
        dis.readByte(); // 跳过 1 字节 header
        for (int i = 0; i < index; i++) {
            dis.skipBytes(dis.readShort() & 0xFFFF);
        }
        byte[] result = new byte[dis.readShort() & 0xFFFF];
        dis.readFully(result);
        dis.close();
        return result;
    }

    // ================================================================
    //  h.g(int) —— 从 pic.dat 加载第 index 张 sheet Image
    // ================================================================

    Image loadSheet(int index) throws IOException {
        byte[] png = loadDatSegment("/pic.dat", index);
        return Image.createImage(png, 0, png.length);
    }

    // ================================================================
    //  e.a(Image, int, int, int, int) —— 从 sheet 按线性索引裁切 tile
    // ================================================================

    Image cutTile(Image sheet, int tileIndex, int tileW, int tileH, int transform) {
        int cols = sheet.getWidth() / tileW;
        int x = (tileIndex % cols) * tileW;
        int y = (tileIndex / cols) * tileH;
        int trans = (transform >= 0 && transform < TRANSFORM_MAP.length) ? TRANSFORM_MAP[transform] : 0;
        return Image.createImage(sheet, x, y, tileW, tileH, trans);
    }

    // ================================================================
    //  h.if(byte[], int, int) —— 列式读取 tile 定义并切割
    // ================================================================

    Image[] loadTileImages(byte[] data, int tileCount, int offset) throws IOException {
        Image[] tiles = new Image[tileCount];
        int lastSheetIdx = -1;
        Image sheet = null;

        for (int i = 0; i < tileCount; i++) {
            int sheetIdx = data[offset + tileCount + i] & 0xFF;
            int posIdx = data[offset + tileCount * 2 + i] & 0xFF;
            int transform = data[offset + tileCount * 3 + i] & 0xFF;

            if (sheetIdx != lastSheetIdx) {
                sheet = loadSheet(sheetIdx);
                lastSheetIdx = sheetIdx;
            }
            tiles[i] = cutTile(sheet, posIdx, TILE_SIZE, TILE_SIZE, transform);
        }
        return tiles;
    }

    // ================================================================
    //  初始化 —— b.byte() + b.case()
    // ================================================================

    void initMap() throws IOException {
        loadMap(currentMapId);
        System.out.println("[TileMapRepro] Map #" + currentMapId + ": " + mapCols + "x" + mapRows
            + " btm=" + bottomTileImages.length + " top=" + topTileImages.length);
    }

    /**
     * b.case() —— 加载地图数据
     *
     * 精确对应字节码流程:
     * 1. e.a("poke2map", y) → byte[] data
     * 2. e.if = 0
     * 3. a6 = data[e.if++], al = data[e.if++]
     * 4. void.a(data, a6, al) → bottom layer
     * 5. G.a(data, a6, al) → top layer
     * 6. 计算摄像机边界
     */
    void loadMap(int mapId) throws IOException {
        byte[] data = loadDatSegment("/poke2map", mapId);
        System.gc();

        readPos = 0; // e.if = 0

        // 读取地图尺寸
        mapCols = data[readPos++] & 0xFF;  // a6
        mapRows = data[readPos++] & 0xFF;  // al

        // === 底层: c.a(data, a6, al) ===
        // c.a(data) header: int, for, do, a, skip2, if(tileCount)
        int btmInt = data[readPos++] & 0xFF;   // startCol
        int btmFor = data[readPos++] & 0xFF;   // startRow
        int btmDo = data[readPos++] & 0xFF;    // colCount
        int btmA = data[readPos++] & 0xFF;     // rowCount
        readPos += 2;                           // skip 2
        int btmTileCount = data[readPos++] & 0xFF;  // tile数量

        // c.a(data, tileCount) → h.if(data, tileCount, readPos)
        // h.if 读取列式数据但不修改 readPos
        bottomTileImages = loadTileImages(data, btmTileCount, readPos);

        // c.if(data, tileCount, readPos) 复制 collision bytes (我们跳过)
        // 然后 e.if += tileCount * 4
        readPos += btmTileCount * 4;

        // 填充 bottom cells: for col in [btmInt..btmInt+btmDo), row in [btmFor..btmFor+btmA)
        layerBottom = new byte[mapCols][mapRows];
        // 初始化为 -1
        for (int c = 0; c < mapCols; c++)
            for (int r = 0; r < mapRows; r++)
                layerBottom[c][r] = -1;
        // 按原始字节码: 外层 for(col=btmFor..btmFor+btmA), 内层 for(row=btmInt..btmInt+btmDo)
        // 等等，重新看字节码: 外层 istore 5 (初始=btmFor), 比较 < btmA+btmFor
        //                   内层 istore 6 (初始=btmInt), 比较 < btmDo+btmInt
        //                   map[col6][row5] = data[pos++]
        // 所以外层是 row (btmFor..btmFor+btmA), 内层是 col (btmInt..btmInt+btmDo)
        for (int row = btmFor; row < btmFor + btmA; row++) {
            for (int col = btmInt; col < btmInt + btmDo; col++) {
                if (col < mapCols && row < mapRows) {
                    layerBottom[col][row] = data[readPos];
                }
                readPos++;
            }
        }

        // === 上层: c.a(data, a6, al) ===
        int topInt = data[readPos++] & 0xFF;
        int topFor = data[readPos++] & 0xFF;
        int topDo = data[readPos++] & 0xFF;
        int topA = data[readPos++] & 0xFF;
        readPos += 2;
        int topTileCount = data[readPos++] & 0xFF;

        topTileImages = loadTileImages(data, topTileCount, readPos);
        readPos += topTileCount * 4;

        layerTop = new byte[mapCols][mapRows];
        for (int c = 0; c < mapCols; c++)
            for (int r = 0; r < mapRows; r++)
                layerTop[c][r] = -1;

        if (topTileCount > 0) {
            for (int row = topFor; row < topFor + topA; row++) {
                for (int col = topInt; col < topInt + topDo; col++) {
                    if (col < mapCols && row < mapRows && readPos < data.length) {
                        layerTop[col][row] = data[readPos];
                    }
                    readPos++;
                }
            }
        }
    }

    void renderToScreen(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);

        int rows = (SCREEN_H / TILE_SIZE) + 1;
        int cols = (SCREEN_W / TILE_SIZE) + 1;

        for (int row = 0; row < mapRows && row < rows; row++) {
            for (int col = 0; col < mapCols && col < cols; col++) {
                int drawX = col * TILE_SIZE + 8;
                int drawY = row * TILE_SIZE + 16;

                int idx = layerBottom[col][row] & 0xFF;
                if (idx != 0xFF && idx < bottomTileImages.length && bottomTileImages[idx] != null) {
                    g.drawImage(bottomTileImages[idx], drawX, drawY,
                        Graphics.HCENTER | Graphics.BOTTOM);
                }

                int tidx = layerTop[col][row] & 0xFF;
                if (tidx != 0xFF && tidx < topTileImages.length && topTileImages[tidx] != null) {
                    g.drawImage(topTileImages[tidx], drawX, drawY,
                        Graphics.HCENTER | Graphics.BOTTOM);
                }
            }
        }
    }
}

class MapCanvas extends Canvas {
    TileMapRepro midlet;

    MapCanvas(TileMapRepro m) {
        this.midlet = m;
        setFullScreenMode(true);
    }

    protected void paint(Graphics g) {
        midlet.renderToScreen(g);
    }

    protected void keyPressed(int keyCode) {
        repaint();
    }
}
