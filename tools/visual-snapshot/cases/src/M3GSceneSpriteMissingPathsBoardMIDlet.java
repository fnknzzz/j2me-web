import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Sprite3D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.m3g.World;
import javax.microedition.midlet.MIDlet;

/**
 * Regression board for Sprite3D crop direction and retained camera transforms.
 *
 * Panels (3 cols x 2 rows):
 *   0: normal Sprite3D crop
 *   1: negative crop width mirrors horizontally
 *   2: negative crop height mirrors vertically
 *   3: direct active camera transform (control)
 *   4: equivalent active camera transform inherited from one parent Group
 *   5: equivalent active camera transform inherited from two parent Groups
 */
public class M3GSceneSpriteMissingPathsBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 3;
            int ch = h / 2;
            g.setColor(33, 36, 42);
            g.fillRect(0, 0, w, h);

            renderSprite(g, 0, 0, cw, ch, 0);
            renderSprite(g, cw, 0, cw, ch, 1);
            renderSprite(g, cw * 2, 0, cw, ch, 2);
            renderWorld(g, 0, ch, cw, ch, 0);
            renderWorld(g, cw, ch, cw, ch, 1);
            renderWorld(g, cw * 2, ch, cw, ch, 2);

            for (int i = 0; i < 6; i++) {
                int x = (i % 3) * cw;
                int y = (i / 3) * ch;
                g.setColor(88, 92, 102);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }
        }

        private static void renderSprite(Graphics g, int x, int y, int w, int h, int cropMode) {
            Sprite3D sprite = new Sprite3D(true, makeAsymmetricImage(), new Appearance());
            if (cropMode == 1) {
                sprite.setCrop(0, 0, -8, 8);
            } else if (cropMode == 2) {
                sprite.setCrop(0, 0, 8, -8);
            }

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                Camera camera = new Camera();
                camera.setPerspective(60.0f, (float) w / (float) h, 1.0f, 500.0f);
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(0.0f, 0.0f, 180.0f);
                g3d.setCamera(camera, cameraTransform);
                Transform transform = new Transform();
                transform.postScale(32.0f, 32.0f, 1.0f);
                g3d.render(sprite, transform);
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Image2D makeAsymmetricImage() {
            int size = 8;
            byte[] pixels = new byte[size * size * 3];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int offset = (y * size + x) * 3;
                    if (x < 4 && y < 4) {
                        pixels[offset] = (byte) 240;
                        pixels[offset + 1] = 35;
                        pixels[offset + 2] = 35;
                    } else if (x >= 4 && y < 4) {
                        pixels[offset] = 20;
                        pixels[offset + 1] = (byte) 190;
                        pixels[offset + 2] = (byte) 235;
                    } else if (x < 4) {
                        pixels[offset] = 35;
                        pixels[offset + 1] = (byte) 210;
                        pixels[offset + 2] = 70;
                    } else {
                        pixels[offset] = (byte) 245;
                        pixels[offset + 1] = (byte) 190;
                        pixels[offset + 2] = 25;
                    }
                }
            }
            return new Image2D(Image2D.RGB, size, size, pixels);
        }

        private static void renderWorld(Graphics g, int x, int y, int w, int h, int parentDepth) {
            World world = new World();
            Background bg = new Background();
            bg.setColor(0xFF21242A);
            world.setBackground(bg);
            Camera camera = new Camera();
            camera.setPerspective(60.0f, (float) w / (float) h, 1.0f, 500.0f);
            camera.setTranslation(parentDepth == 0 ? 28.0f : 0.0f, 0.0f, 180.0f);

            if (parentDepth == 0) {
                world.addChild(camera);
            } else if (parentDepth == 1) {
                Group parent = new Group();
                parent.setTranslation(28.0f, 0.0f, 0.0f);
                parent.addChild(camera);
                world.addChild(parent);
            } else {
                Group outer = new Group();
                outer.setTranslation(10.0f, 0.0f, 0.0f);
                Group inner = new Group();
                inner.setTranslation(18.0f, 0.0f, 0.0f);
                inner.addChild(camera);
                outer.addChild(inner);
                world.addChild(outer);
            }
            world.setActiveCamera(camera);
            world.addChild(makeTriangleMesh());

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(x, y, w, h);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.render(world);
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static Mesh makeTriangleMesh() {
            VertexArray positions = new VertexArray(3, 3, 2);
            positions.set(0, 3, new short[]{0, 52, 0, -48, -38, 0, 48, -38, 0});
            VertexArray colors = new VertexArray(3, 4, 1);
            colors.set(0, 3, new byte[]{
                (byte) 245, 45, 35, (byte) 255,
                20, (byte) 190, (byte) 235, (byte) 255,
                35, (byte) 210, 70, (byte) 255
            });
            VertexBuffer vertices = new VertexBuffer();
            vertices.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vertices.setColors(colors);
            return new Mesh(vertices, new TriangleStripArray(0, new int[]{3}), new Appearance());
        }
    }
}
