import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * Visual coverage for texture paths not exercised by M3GTextureBoardMIDlet.
 *
 * Panels (3 cols x 2 rows):
 *   0: textured triangle (must not fall back to a flat vertex color)
 *   1: indexed quad with an unused fifth vertex
 *   2: four vertices, but indices repeat only the left triangle
 *   3: identity texture transform (control)
 *   4: translated texture transform (must differ from panel 3)
 *   5: rotated texture transform with REPEAT + LINEAR filtering
 *
 * Panels 0-2 verify that texture rendering follows the actual triangle index
 * buffer. Panels 3-5 verify that Texture2D transforms are applied before
 * wrapping and filtering.
 */
public class M3GTextureMissingPathsBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 3;
            int ch = h / 2;

            g.setColor(34, 36, 40);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 6; i++) {
                int x = (i % 3) * cw;
                int y = (i / 3) * ch;
                g.setColor(92, 96, 104);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }

            render(g, 0, 0, cw, ch, makeTriangle(), new TriangleStripArray(0, new int[]{3}),
                    makeTexture(false, false));
            render(g, cw, 0, cw, ch, makeFiveVertexQuad(),
                    new TriangleStripArray(new int[]{1, 2, 3, 4}, new int[]{4}),
                    makeTexture(false, false));
            render(g, cw * 2, 0, cw, ch, makeQuad(),
                    new TriangleStripArray(new int[]{0, 1, 2, 0, 1, 2}, new int[]{3, 3}),
                    makeTexture(false, false));

            Texture2D identity = makeTexture(false, false);
            render(g, 0, ch, cw, ch, makeQuad(), new TriangleStripArray(0, new int[]{4}), identity);

            Texture2D translated = makeTexture(false, false);
            translated.translate(0.35f, 0.20f, 0.0f);
            render(g, cw, ch, cw, ch, makeQuad(), new TriangleStripArray(0, new int[]{4}), translated);

            Texture2D rotatedLinearRepeat = makeTexture(true, true);
            rotatedLinearRepeat.postRotate(35.0f, 0.0f, 0.0f, 1.0f);
            render(g, cw * 2, ch, cw, ch, makeQuadScaledUV(2.5f),
                    new TriangleStripArray(0, new int[]{4}), rotatedLinearRepeat);
        }

        private static Texture2D makeTexture(boolean repeat, boolean linear) {
            int size = 4;
            byte[] data = new byte[size * size * 3];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int off = (y * size + x) * 3;
                    if (((x + y) & 1) == 0) {
                        data[off] = (byte) 245;
                        data[off + 1] = (byte) 45;
                        data[off + 2] = (byte) 35;
                    } else {
                        data[off] = (byte) 20;
                        data[off + 1] = (byte) 190;
                        data[off + 2] = (byte) 235;
                    }
                }
            }
            Texture2D texture = new Texture2D(new Image2D(Image2D.RGB, size, size, data));
            texture.setBlending(Texture2D.FUNC_REPLACE);
            texture.setWrapping(repeat ? Texture2D.WRAP_REPEAT : Texture2D.WRAP_CLAMP,
                    repeat ? Texture2D.WRAP_REPEAT : Texture2D.WRAP_CLAMP);
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,
                    linear ? Texture2D.FILTER_LINEAR : Texture2D.FILTER_NEAREST);
            return texture;
        }

        private static VertexBuffer makeTriangle() {
            VertexArray positions = new VertexArray(3, 3, 2);
            positions.set(0, 3, new short[]{
                -72, -65, 0,
                 72, -65, 0,
                  0,  70, 0
            });
            VertexArray uv = new VertexArray(3, 2, 2);
            uv.set(0, 3, new short[]{0, 100, 100, 100, 50, 0});
            return makeBuffer(positions, uv, 0.01f);
        }

        private static VertexBuffer makeFiveVertexQuad() {
            VertexArray positions = new VertexArray(5, 3, 2);
            positions.set(0, 5, new short[]{
                300, 300, 0,
                -72, -65, 0,
                 72, -65, 0,
                -72,  65, 0,
                 72,  65, 0
            });
            VertexArray uv = new VertexArray(5, 2, 2);
            uv.set(0, 5, new short[]{
                0, 0,
                0, 100,
                100, 100,
                0, 0,
                100, 0
            });
            return makeBuffer(positions, uv, 0.01f);
        }

        private static VertexBuffer makeQuad() {
            return makeQuadScaledUV(1.0f);
        }

        private static VertexBuffer makeQuadScaledUV(float scale) {
            VertexArray positions = new VertexArray(4, 3, 2);
            positions.set(0, 4, new short[]{
                -72, -65, 0,
                 72, -65, 0,
                -72,  65, 0,
                 72,  65, 0
            });
            short max = (short) (100.0f * scale);
            VertexArray uv = new VertexArray(4, 2, 2);
            uv.set(0, 4, new short[]{0, max, max, max, 0, 0, max, 0});
            return makeBuffer(positions, uv, 0.01f);
        }

        private static VertexBuffer makeBuffer(VertexArray positions, VertexArray uv, float uvScale) {
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vb.setTexCoords(0, uv, uvScale, new float[]{0, 0});
            vb.setDefaultColor(0xFFB0B0B0);
            return vb;
        }

        private static void render(Graphics g, int x, int y, int w, int h,
                VertexBuffer vertices, TriangleStripArray indices, Texture2D texture) {
            Appearance appearance = new Appearance();
            appearance.setTexture(0, texture);

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
                g3d.render(vertices, indices, appearance, new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
