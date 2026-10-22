import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.m3g.World;
import javax.microedition.midlet.MIDlet;

/**
 * Retained-mode Graphics3D state board.
 * Panels (2 cols x 2 rows):
 *   0: render(World) leaves active camera and exactly one World light in Graphics3D state
 *   1: immediate render after render(World) reuses the World active camera
 *   2: immediate lit render after render(World) reuses the World light
 *   3: render(Node) does not collect Light nodes from the rendered subtree
 */
public class M3GRetainedStateBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cw = w / 2;
            int ch = h / 2;
            g.setColor(24, 26, 31);
            g.fillRect(0, 0, w, h);

            panelWorldLeavesState(g, 0, 0, cw, ch);
            panelImmediateUsesWorldCamera(g, cw, 0, cw, ch);
            panelImmediateUsesWorldLight(g, 0, ch, cw, ch);
            panelRenderNodeDoesNotCollectLight(g, cw, ch, cw, ch);

            for (int i = 0; i < 4; i++) {
                int x = (i % 2) * cw;
                int y = (i / 2) * ch;
                g.setColor(88, 92, 102);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }
        }

        private static void panelWorldLeavesState(Graphics g, int vx, int vy, int vw, int vh) {
            Graphics3D g3d = Graphics3D.getInstance();
            WorldFixture fixture = makeWorldFixture(0xFF17212B);
            boolean ok = false;
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(makeFarCamera(), new Transform());
                g3d.addLight(makeAmbientLight(0x00ff0000), null);
                g3d.render(fixture.world);

                Transform cameraTransform = new Transform();
                Camera currentCamera = g3d.getCamera(cameraTransform);
                float[] cam = new float[16];
                cameraTransform.get(cam);

                Transform lightTransform = new Transform();
                Light currentLight = g3d.getLight(0, lightTransform);
                float[] light = new float[16];
                lightTransform.get(light);

                ok = currentCamera == fixture.camera
                        && g3d.getLightCount() == 1
                        && currentLight == fixture.light
                        && close(cam[3], 24.0f)
                        && close(cam[11], 180.0f)
                        && close(light[3], -20.0f);
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
            g.setColor(ok ? 36 : 120, ok ? 118 : 36, 46);
            g.fillRect(vx + 18, vy + 18, vw - 36, vh - 36);
            g.setColor(ok ? 80 : 200, ok ? 230 : 60, 90);
            g.fillRect(vx + vw / 2 - 42, vy + vh / 2 - 42, 84, 84);
        }

        private static void panelImmediateUsesWorldCamera(Graphics g, int vx, int vy, int vw, int vh) {
            Graphics3D g3d = Graphics3D.getInstance();
            WorldFixture fixture = makeWorldFixture(0xFF111827);
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(makeFarCamera(), new Transform());
                g3d.render(fixture.world);
                g3d.render(makeFlatTriangle(0xFFFFD24A), new TriangleStripArray(0, new int[]{3}),
                        new Appearance(), new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelImmediateUsesWorldLight(Graphics g, int vx, int vy, int vw, int vh) {
            Graphics3D g3d = Graphics3D.getInstance();
            WorldFixture fixture = makeWorldFixture(0xFF172214);
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.render(fixture.world);
                g3d.render(makeNormalTriangle(), new TriangleStripArray(0, new int[]{3}),
                        makeMaterialAppearance(), new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelRenderNodeDoesNotCollectLight(Graphics g, int vx, int vy, int vw, int vh) {
            Graphics3D g3d = Graphics3D.getInstance();
            WorldFixture fixture = makeWorldFixture(0xFF17141F);
            Group group = new Group();
            group.addChild(makeAmbientLight(0x0000ff00));
            group.addChild(new Mesh(makeNormalTriangle(), new TriangleStripArray(0, new int[]{3}),
                    makeMaterialAppearance()));
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.render(fixture.world);
                g3d.resetLights();
                g3d.setCamera(fixture.camera, fixture.cameraTransform);
                g3d.render(group, new Transform());
            } catch (Throwable ignored) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static WorldFixture makeWorldFixture(int backgroundColor) {
            World world = new World();
            Background bg = new Background();
            bg.setColor(backgroundColor);
            world.setBackground(bg);

            Camera camera = new Camera();
            camera.setPerspective(60.0f, 1.0f, 1.0f, 1000.0f);
            Group cameraParent = new Group();
            cameraParent.setTranslation(24.0f, 0.0f, 180.0f);
            cameraParent.addChild(camera);
            world.addChild(cameraParent);
            world.setActiveCamera(camera);

            Light light = makeAmbientLight(0x00ffffff);
            Group lightParent = new Group();
            lightParent.setTranslation(-20.0f, 0.0f, 0.0f);
            lightParent.addChild(light);
            world.addChild(lightParent);

            return new WorldFixture(world, camera, cameraParentTransform(), light);
        }

        private static Transform cameraParentTransform() {
            Transform t = new Transform();
            t.postTranslate(24.0f, 0.0f, 180.0f);
            return t;
        }

        private static Camera makeFarCamera() {
            Camera camera = new Camera();
            camera.setPerspective(60.0f, 1.0f, 1.0f, 1000.0f);
            camera.setTranslation(0.0f, 0.0f, 900.0f);
            return camera;
        }

        private static Light makeAmbientLight(int color) {
            Light light = new Light();
            light.setMode(Light.AMBIENT);
            light.setColor(color);
            light.setIntensity(1.0f);
            return light;
        }

        private static Appearance makeMaterialAppearance() {
            Appearance app = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.AMBIENT, 0x00d8d8d8);
            mat.setColor(Material.DIFFUSE, 0x00d8d8d8);
            app.setMaterial(mat);
            return app;
        }

        private static VertexBuffer makeFlatTriangle(int color) {
            VertexArray positions = new VertexArray(3, 3, 2);
            positions.set(0, 3, new short[]{0, 58, 0, -56, -44, 0, 56, -44, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return vb;
        }

        private static VertexBuffer makeNormalTriangle() {
            VertexArray positions = new VertexArray(3, 3, 2);
            positions.set(0, 3, new short[]{0, 58, 0, -56, -44, 0, 56, -44, 0});
            VertexArray normals = new VertexArray(3, 3, 1);
            normals.set(0, 3, new byte[]{0, 0, 127, 0, 0, 127, 0, 0, 127});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(positions, 1.0f, new float[]{0, 0, 0});
            vb.setNormals(normals);
            vb.setDefaultColor(0xFF101010);
            return vb;
        }

        private static boolean close(float a, float b) {
            return Math.abs(a - b) < 0.01f;
        }
    }

    private static final class WorldFixture {
        final World world;
        final Camera camera;
        final Transform cameraTransform;
        final Light light;

        WorldFixture(World world, Camera camera, Transform cameraTransform, Light light) {
            this.world = world;
            this.camera = camera;
            this.cameraTransform = cameraTransform;
            this.light = light;
        }
    }
}
