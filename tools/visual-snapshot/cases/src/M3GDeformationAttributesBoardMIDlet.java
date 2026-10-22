import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.MorphingMesh;
import javax.microedition.m3g.SkinnedMesh;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * Regression board for attributes used after mesh deformation.
 *
 * Panels (3 cols x 2 rows):
 *   0: MorphingMesh preserves texture coordinates
 *   1: MorphingMesh preserves base normals for lighting
 *   2: SkinnedMesh preserves texture coordinates
 *   3: SkinnedMesh preserves normals for lighting
 *   4: ambient light uses Material.AMBIENT rather than Material.DIFFUSE
 *   5: MorphingMesh interpolates target normals
 */
public class M3GDeformationAttributesBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 3;
            int ch = h / 2;
            g.setColor(35, 37, 42);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 6; i++) {
                int x = (i % 3) * cw;
                int y = (i / 3) * ch;
                g.setColor(88, 92, 100);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }

            panelMorphTexture(g, 0, 0, cw, ch);
            panelMorphLighting(g, cw, 0, cw, ch);
            panelSkinnedTexture(g, cw * 2, 0, cw, ch);
            panelSkinnedLighting(g, 0, ch, cw, ch);
            panelAmbientMaterial(g, cw, ch, cw, ch);
            panelMorphTargetNormals(g, cw * 2, ch, cw, ch);
        }

        private static void panelMorphTexture(Graphics g, int x, int y, int w, int h) {
            VertexBuffer base = makeQuad(true, true, false, 0);
            VertexBuffer target = makeQuad(false, false, false, 18);
            MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target}, indices(), textureAppearance());
            mesh.setWeights(new float[]{1.0f});
            render(g, x, y, w, h, mesh, null);
        }

        private static void panelMorphLighting(Graphics g, int x, int y, int w, int h) {
            VertexBuffer base = makeQuad(false, true, false, 0);
            VertexBuffer target = makeQuad(false, false, false, -15);
            MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target}, indices(), litAppearance(0x0000dd55));
            mesh.setWeights(new float[]{0.75f});
            render(g, x, y, w, h, mesh, directionalLight());
        }

        private static void panelSkinnedTexture(Graphics g, int x, int y, int w, int h) {
            Group skeleton = new Group();
            Group bone = new Group();
            skeleton.addChild(bone);
            SkinnedMesh mesh = new SkinnedMesh(makeQuad(true, true, false, 0), indices(),
                    textureAppearance(), skeleton);
            mesh.addTransform(bone, 256, 0, 4);
            bone.postRotate(12.0f, 0.0f, 0.0f, 1.0f);
            render(g, x, y, w, h, mesh, null);
        }

        private static void panelSkinnedLighting(Graphics g, int x, int y, int w, int h) {
            Group skeleton = new Group();
            Group bone = new Group();
            skeleton.addChild(bone);
            SkinnedMesh mesh = new SkinnedMesh(makeQuad(false, true, false, 0), indices(),
                    litAppearance(0x00ddaa22), skeleton);
            mesh.addTransform(bone, 256, 0, 4);
            bone.setTranslation(12.0f, -8.0f, 0.0f);
            render(g, x, y, w, h, mesh, directionalLight());
        }

        private static void panelAmbientMaterial(Graphics g, int x, int y, int w, int h) {
            Material material = new Material();
            material.setColor(Material.AMBIENT, 0x002244ee);
            material.setColor(Material.DIFFUSE, 0x00ee3322);
            Appearance appearance = new Appearance();
            appearance.setMaterial(material);
            Light light = new Light();
            light.setMode(Light.AMBIENT);
            light.setColor(0x00ffffff);
            render(g, x, y, w, h,
                    new javax.microedition.m3g.Mesh(makeQuad(false, true, false, 0), indices(), appearance),
                    light);
        }

        private static void panelMorphTargetNormals(Graphics g, int x, int y, int w, int h) {
            VertexBuffer base = makeQuad(false, true, false, 0);
            VertexBuffer target = makeQuad(false, true, true, 0);
            MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target}, indices(),
                    litAppearance(0x00eeeeee));
            mesh.setWeights(new float[]{1.0f});
            render(g, x, y, w, h, mesh, directionalLight());
        }

        private static VertexBuffer makeQuad(boolean texture, boolean normals,
                boolean reverseNormals, int topOffset) {
            VertexArray positions = new VertexArray(4, 3, 2);
            positions.set(0, 4, new short[]{
                -58, -45, 0,
                 58, -45, 0,
                (short) (-58 + topOffset), 45, 0,
                (short) (58 + topOffset), 45, 0
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFF686868);
            if (normals) {
                byte z = (byte) (reverseNormals ? -127 : 127);
                VertexArray normalArray = new VertexArray(4, 3, 1);
                normalArray.set(0, 4, new byte[]{0, 0, z, 0, 0, z, 0, 0, z, 0, 0, z});
                vb.setNormals(normalArray);
            }
            if (texture) {
                VertexArray uv = new VertexArray(4, 2, 2);
                uv.set(0, 4, new short[]{0, 100, 100, 100, 0, 0, 100, 0});
                vb.setTexCoords(0, uv, 0.01f, new float[]{0, 0});
            }
            return vb;
        }

        private static TriangleStripArray indices() {
            return new TriangleStripArray(0, new int[]{4});
        }

        private static Appearance textureAppearance() {
            byte[] pixels = new byte[]{
                (byte) 245, 45, 35, 20, (byte) 190, (byte) 235,
                20, (byte) 190, (byte) 235, (byte) 245, 45, 35
            };
            Texture2D texture = new Texture2D(new Image2D(Image2D.RGB, 2, 2, pixels));
            texture.setBlending(Texture2D.FUNC_REPLACE);
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            Appearance appearance = new Appearance();
            appearance.setTexture(0, texture);
            return appearance;
        }

        private static Appearance litAppearance(int diffuseColor) {
            Material material = new Material();
            material.setColor(Material.AMBIENT, 0x00000000);
            material.setColor(Material.DIFFUSE, diffuseColor);
            Appearance appearance = new Appearance();
            appearance.setMaterial(material);
            return appearance;
        }

        private static Light directionalLight() {
            Light light = new Light();
            light.setMode(Light.DIRECTIONAL);
            light.setColor(0x00ffffff);
            return light;
        }

        private static void render(Graphics g, int x, int y, int w, int h,
                javax.microedition.m3g.Node node, Light light) {
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
                g3d.resetLights();
                if (light != null) {
                    g3d.addLight(light, null);
                }
                g3d.render(node, new Transform());
            } catch (Throwable ignored) {
            } finally {
                g3d.resetLights();
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
