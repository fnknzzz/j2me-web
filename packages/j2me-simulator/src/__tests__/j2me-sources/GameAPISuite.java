import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.midlet.*;

public class GameAPISuite extends MIDlet {
    private Display display;
    private GameCanvas canvas;

    class TestGameCanvas extends GameCanvas {
        private LayerManager layerManager;
        private Sprite sprite;
        private TiledLayer tiledLayer;
        
        public TestGameCanvas() {
            super(false);
            
            try {
                testSprite();
                testTiledLayer();
                testLayerManager();
            } catch (Exception e) {
                System.out.print("Game API test error: ");
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
        
        private void testSprite() {
            System.out.println("=== Sprite Test ===");
            
            Image spriteImage = Image.createImage(64, 32);
            Graphics g = spriteImage.getGraphics();
            
            g.setColor(255, 0, 0);
            g.fillRect(0, 0, 32, 32);
            
            g.setColor(0, 255, 0);
            g.fillRect(32, 0, 32, 32);
            
            sprite = new Sprite(spriteImage, 32, 32);
            System.out.println("Created sprite with 2 frames");
            System.out.print("Frame count: ");
            System.out.println(sprite.getFrameSequenceLength());
            System.out.print("Raw frame count: ");
            System.out.println(sprite.getRawFrameCount());
            
            System.out.print("Initial frame: ");
            System.out.println(sprite.getFrame());
            sprite.setFrame(1);
            System.out.print("After setFrame(1): ");
            System.out.println(sprite.getFrame());
            sprite.setFrame(0);
            System.out.print("After setFrame(0): ");
            System.out.println(sprite.getFrame());
            
            sprite.nextFrame();
            System.out.print("After nextFrame(): ");
            System.out.println(sprite.getFrame());
            sprite.prevFrame();
            System.out.print("After prevFrame(): ");
            System.out.println(sprite.getFrame());
            
            int[] sequence = {0, 1, 0, 1};
            sprite.setFrameSequence(sequence);
            System.out.print("Set frame sequence length: ");
            System.out.println(sprite.getFrameSequenceLength());
            
            sprite.setPosition(100, 100);
            System.out.print("Position: (");
            System.out.print(sprite.getX());
            System.out.print(", ");
            System.out.print(sprite.getY());
            System.out.println(")");
            
            sprite.move(10, 20);
            System.out.print("After move(10, 20): (");
            System.out.print(sprite.getX());
            System.out.print(", ");
            System.out.print(sprite.getY());
            System.out.println(")");
            
            System.out.print("Width: ");
            System.out.println(sprite.getWidth());
            System.out.print("Height: ");
            System.out.println(sprite.getHeight());
            
            sprite.defineReferencePixel(16, 16);
            System.out.print("Reference pixel: (");
            System.out.print(sprite.getRefPixelX());
            System.out.print(", ");
            System.out.print(sprite.getRefPixelY());
            System.out.println(")");
            
            sprite.setRefPixelPosition(150, 150);
            System.out.print("After setRefPixelPosition: (");
            System.out.print(sprite.getX());
            System.out.print(", ");
            System.out.print(sprite.getY());
            System.out.println(")");
            
            System.out.print("Is visible: ");
            System.out.println(sprite.isVisible());
            sprite.setVisible(false);
            System.out.print("After setVisible(false): ");
            System.out.println(sprite.isVisible());
            sprite.setVisible(true);
            System.out.print("After setVisible(true): ");
            System.out.println(sprite.isVisible());
            
            sprite.setTransform(Sprite.TRANS_NONE);
            System.out.println("Transform NONE applied");
            
            sprite.setTransform(Sprite.TRANS_MIRROR);
            System.out.println("Transform MIRROR applied");
            
            sprite.setTransform(Sprite.TRANS_MIRROR_ROT180);
            System.out.println("Transform MIRROR_ROT180 applied");
            
            sprite.setTransform(Sprite.TRANS_ROT90);
            System.out.println("Transform ROT90 applied");
            
            sprite.setTransform(Sprite.TRANS_ROT180);
            System.out.println("Transform ROT180 applied");
            
            sprite.setTransform(Sprite.TRANS_ROT270);
            System.out.println("Transform ROT270 applied");
            
            Sprite sprite2 = new Sprite(spriteImage, 32, 32);
            sprite2.setPosition(120, 120);
            
            boolean collides = sprite.collidesWith(sprite2, false);
            System.out.print("Sprite collision (pixel-level=false): ");
            System.out.println(collides);
            
            boolean collidesPixel = sprite.collidesWith(sprite2, true);
            System.out.print("Sprite collision (pixel-level=true): ");
            System.out.println(collidesPixel);
            
            Image testImage = Image.createImage(50, 50);
            boolean collidesImage = sprite.collidesWith(testImage, 100, 100, false);
            System.out.print("Sprite-Image collision: ");
            System.out.println(collidesImage);
            
            boolean collidesTiled = sprite.collidesWith(tiledLayer, false);
            System.out.print("Sprite-TiledLayer collision: ");
            System.out.println(collidesTiled);
        }
        
        private void testTiledLayer() {
            System.out.println("\n=== TiledLayer Test ===");
            
            Image tileImage = Image.createImage(48, 16);
            Graphics g = tileImage.getGraphics();
            
            g.setColor(255, 0, 0);
            g.fillRect(0, 0, 16, 16);
            
            g.setColor(0, 255, 0);
            g.fillRect(16, 0, 16, 16);
            
            g.setColor(0, 0, 255);
            g.fillRect(32, 0, 16, 16);
            
            tiledLayer = new TiledLayer(10, 10, tileImage, 16, 16);
            System.out.println("Created TiledLayer 10x10");
            System.out.print("Columns: ");
            System.out.println(tiledLayer.getColumns());
            System.out.print("Rows: ");
            System.out.println(tiledLayer.getRows());
            System.out.print("Cell width: ");
            System.out.println(tiledLayer.getCellWidth());
            System.out.print("Cell height: ");
            System.out.println(tiledLayer.getCellHeight());
            
            tiledLayer.setCell(0, 0, 1);
            tiledLayer.setCell(1, 0, 2);
            tiledLayer.setCell(2, 0, 3);
            tiledLayer.setCell(0, 1, 2);
            tiledLayer.setCell(1, 1, 3);
            tiledLayer.setCell(2, 1, 1);
            System.out.println("Set cells");
            
            System.out.print("Cell(0,0): ");
            System.out.println(tiledLayer.getCell(0, 0));
            System.out.print("Cell(1,0): ");
            System.out.println(tiledLayer.getCell(1, 0));
            System.out.print("Cell(2,0): ");
            System.out.println(tiledLayer.getCell(2, 0));
            
            tiledLayer.fillCells(3, 3, 4, 4, 2);
            System.out.println("Filled cells (3,3) to (6,6) with tile 2");
            System.out.print("Cell(4,4): ");
            System.out.println(tiledLayer.getCell(4, 4));
            
            int[] staticTiles = {1, 2, 3};
            tiledLayer.createAnimatedTile(1);
            System.out.println("Created animated tile");
            
            tiledLayer.setAnimatedTile(-1, 2);
            System.out.println("Set animated tile -1 to static tile 2");
            
            tiledLayer.setCell(5, 5, -1);
            System.out.println("Set cell(5,5) to animated tile");
            System.out.print("Cell(5,5): ");
            System.out.println(tiledLayer.getCell(5, 5));
            
            tiledLayer.setPosition(50, 50);
            System.out.print("TiledLayer position: (");
            System.out.print(tiledLayer.getX());
            System.out.print(", ");
            System.out.print(tiledLayer.getY());
            System.out.println(")");
            
            tiledLayer.move(10, 10);
            System.out.print("After move: (");
            System.out.print(tiledLayer.getX());
            System.out.print(", ");
            System.out.print(tiledLayer.getY());
            System.out.println(")");
            
            System.out.print("TiledLayer width: ");
            System.out.println(tiledLayer.getWidth());
            System.out.print("TiledLayer height: ");
            System.out.println(tiledLayer.getHeight());
            
            System.out.print("Is visible: ");
            System.out.println(tiledLayer.isVisible());
            tiledLayer.setVisible(false);
            System.out.print("After setVisible(false): ");
            System.out.println(tiledLayer.isVisible());
            tiledLayer.setVisible(true);
        }
        
        private void testLayerManager() {
            System.out.println("\n=== LayerManager Test ===");
            
            layerManager = new LayerManager();
            
            layerManager.append(tiledLayer);
            System.out.println("Appended TiledLayer");
            System.out.print("Layer count: ");
            System.out.println(layerManager.getSize());
            
            layerManager.append(sprite);
            System.out.println("Appended Sprite");
            System.out.print("Layer count: ");
            System.out.println(layerManager.getSize());
            
            Layer layer0 = layerManager.getLayerAt(0);
            Layer layer1 = layerManager.getLayerAt(1);
            System.out.println("Got layers at index 0 and 1");
            
            layerManager.insert(sprite, 0);
            System.out.println("Inserted sprite at index 0");
            System.out.print("Layer count: ");
            System.out.println(layerManager.getSize());
            
            layerManager.remove(sprite);
            System.out.println("Removed sprite");
            System.out.print("Layer count: ");
            System.out.println(layerManager.getSize());
            
            layerManager.setViewWindow(0, 0, 240, 320);
            System.out.println("Set view window to 240x320");
            
            Graphics canvasG = getGraphics();
            canvasG.setColor(255, 255, 255);
            canvasG.fillRect(0, 0, getWidth(), getHeight());
            
            layerManager.paint(canvasG, 0, 0);
            System.out.println("Painted layer manager");
            
            flushGraphics();
        }
        
        public void run() {
            Graphics g = getGraphics();
            g.setColor(0, 0, 0);
            g.drawString("Game API Test Complete", 10, 10, Graphics.TOP | Graphics.LEFT);
            flushGraphics();
        }
    }

    public GameAPISuite() {
        canvas = new TestGameCanvas();
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        display.setCurrent(canvas);
        ((TestGameCanvas)canvas).run();
        System.out.println("GameAPISuite started");
    }

    protected void pauseApp() {
        System.out.println("GameAPISuite paused");
    }

    protected void destroyApp(boolean unconditional) {
        System.out.println("GameAPISuite destroyed");
    }
}
