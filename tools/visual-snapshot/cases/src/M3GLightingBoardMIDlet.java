import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G lighting board.
 * Panels (4 cols x 3 rows):
 *   Row 0: no light (vertex color), ambient only, directional, omni
 *   Row 1: spot light, two lights, colored light, high intensity
 *   Row 2: material diffuse red, material emissive, vertex color tracking, shininess+specular
 */
public class M3GLightingBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            g.setColor(40, 40, 40);
            g.fillRect(0, 0, w, h);

            int cols = 4, rows = 3;
            int cw = w / cols, ch = h / rows;

            for (int i = 0; i < cols * rows; i++) {
                int cx = (i % cols) * cw;
                int cy = (i / cols) * ch;
                g.setColor(80, 80, 80);
                g.drawRect(cx + 2, cy + 2, cw - 5, ch - 5);
            }

            // Row 0
            panelNoLight(g, 0, 0, cw, ch);
            panelAmbient(g, cw, 0, cw, ch);
            panelDirectional(g, cw * 2, 0, cw, ch);
            panelOmni(g, cw * 3, 0, cw, ch);

            // Row 1
            panelSpot(g, 0, ch, cw, ch);
            panelTwoLights(g, cw, ch, cw, ch);
            panelColoredLight(g, cw * 2, ch, cw, ch);
            panelHighIntensity(g, cw * 3, ch, cw, ch);

            // Row 2
            panelMaterialDiffuse(g, 0, ch * 2, cw, ch);
            panelMaterialEmissive(g, cw, ch * 2, cw, ch);
            panelVertexColorTracking(g, cw * 2, ch * 2, cw, ch);
            panelSpecularShininess(g, cw * 3, ch * 2, cw, ch);
        }

        // --- Row 0 ---

        private static void panelNoLight(Graphics g, int vx, int vy, int vw, int vh) {
            renderLit(g, vx, vy, vw, vh, null, null, null, buildVertexColorCube());
        }

        private static void panelAmbient(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.AMBIENT);
            light.setColor(0x00ffffff);
            light.setIntensity(1.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.DIFFUSE, 0x00cccccc);
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, null, null);
        }

        private static void panelDirectional(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.DIRECTIONAL);
            light.setColor(0x00ffffff);
            light.setIntensity(1.0f);
            Transform lightT = new Transform();
            lightT.postRotate(-45.0f, 1.0f, 0.0f, 0.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, null);
        }

        private static void panelOmni(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.OMNI);
            light.setColor(0x00ffffff);
            light.setIntensity(1.5f);
            light.setAttenuation(1.0f, 0.005f, 0.0f);
            Transform lightT = new Transform();
            lightT.postTranslate(50.0f, 50.0f, 100.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, null);
        }

        // --- Row 1 ---

        private static void panelSpot(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.SPOT);
            light.setColor(0x00ffffff);
            light.setIntensity(2.0f);
            light.setSpotAngle(30.0f);
            light.setSpotExponent(10.0f);
            light.setAttenuation(1.0f, 0.0f, 0.0f);
            Transform lightT = new Transform();
            lightT.postTranslate(0.0f, 0.0f, 150.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, null);
        }

        private static void panelTwoLights(Graphics g, int vx, int vy, int vw, int vh) {
            Light light1 = new Light();
            light1.setMode(Light.DIRECTIONAL);
            light1.setColor(0x00ff0000);
            Transform l1T = new Transform();
            l1T.postRotate(-30.0f, 1.0f, 0.0f, 0.0f);

            Light light2 = new Light();
            light2.setMode(Light.DIRECTIONAL);
            light2.setColor(0x000000ff);
            Transform l2T = new Transform();
            l2T.postRotate(30.0f, 0.0f, 1.0f, 0.0f);

            Appearance app = new Appearance();
            Material mat = new Material();
            app.setMaterial(mat);
            renderLitMulti(g, vx, vy, vw, vh, app, new Light[]{light1, light2}, new Transform[]{l1T, l2T});
        }

        private static void panelColoredLight(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.DIRECTIONAL);
            light.setColor(0x0000ff88);
            light.setIntensity(1.5f);
            Transform lightT = new Transform();
            lightT.postRotate(-45.0f, 1.0f, 1.0f, 0.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, null);
        }

        private static void panelHighIntensity(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.DIRECTIONAL);
            light.setColor(0x00ffffff);
            light.setIntensity(5.0f);
            Transform lightT = new Transform();
            lightT.postRotate(-30.0f, 1.0f, 0.0f, 0.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, null);
        }

        // --- Row 2 ---

        private static void panelMaterialDiffuse(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.DIRECTIONAL);
            Transform lightT = new Transform();
            lightT.postRotate(-45.0f, 1.0f, 0.0f, 0.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.DIFFUSE, 0x00ff0000);
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, null);
        }

        private static void panelMaterialEmissive(Graphics g, int vx, int vy, int vw, int vh) {
            Appearance app = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.EMISSIVE, 0x0000ff00);
            app.setMaterial(mat);
            // Emissive should glow without any light
            renderLit(g, vx, vy, vw, vh, app, null, null, null);
        }

        private static void panelVertexColorTracking(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.DIRECTIONAL);
            Transform lightT = new Transform();
            lightT.postRotate(-45.0f, 1.0f, 0.0f, 0.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            mat.setVertexColorTrackingEnable(true);
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, buildVertexColorCube());
        }

        private static void panelSpecularShininess(Graphics g, int vx, int vy, int vw, int vh) {
            Light light = new Light();
            light.setMode(Light.DIRECTIONAL);
            Transform lightT = new Transform();
            lightT.postRotate(-30.0f, 1.0f, 0.0f, 0.0f);
            Appearance app = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.SPECULAR, 0x00ffffff);
            mat.setShininess(64.0f);
            app.setMaterial(mat);
            renderLit(g, vx, vy, vw, vh, app, light, lightT, null);
        }

        // --- Utilities ---

        private static void renderLit(Graphics g, int vx, int vy, int vw, int vh,
                Appearance app, Light light, Transform lightT, VertexBuffer customVb) {
            if (light != null) {
                renderLitMulti(g, vx, vy, vw, vh, app, new Light[]{light}, new Transform[]{lightT}, customVb);
            } else {
                renderLitMulti(g, vx, vy, vw, vh, app, null, null, customVb);
            }
        }

        private static void renderLitMulti(Graphics g, int vx, int vy, int vw, int vh,
                Appearance app, Light[] lights, Transform[] lightTs) {
            renderLitMulti(g, vx, vy, vw, vh, app, lights, lightTs, null);
        }

        private static void renderLitMulti(Graphics g, int vx, int vy, int vw, int vh,
                Appearance app, Light[] lights, Transform[] lightTs, VertexBuffer customVb) {
            VertexBuffer vb = customVb == null ? buildNormalCube() : customVb;
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0.0f, 0.0f, 200.0f);
            Transform model = new Transform();
            model.postRotate(25.0f, 1.0f, 0.0f, 0.0f);
            model.postRotate(35.0f, 0.0f, 1.0f, 0.0f);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.resetLights();
                if (lights != null) {
                    for (int i = 0; i < lights.length; i++) {
                        g3d.addLight(lights[i], lightTs != null ? lightTs[i] : null);
                    }
                }
                if (app == null) app = new Appearance();
                g3d.render(vb, idx, app, model);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static VertexBuffer buildNormalCube() {
            short[] positions = new short[]{
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
            };
            // Normals per face
            byte[] normals = new byte[]{
                // Front (0,0,127)
                0, 0, 127,  0, 0, 127,  0, 0, 127,  0, 0, 127,
                // Back (0,0,-127)
                0, 0, -127,  0, 0, -127,  0, 0, -127,  0, 0, -127,
                // Top (0,127,0)
                0, 127, 0,  0, 127, 0,  0, 127, 0,  0, 127, 0,
                // Bottom (0,-127,0)
                0, -127, 0,  0, -127, 0,  0, -127, 0,  0, -127, 0,
                // Right (127,0,0)
                127, 0, 0,  127, 0, 0,  127, 0, 0,  127, 0, 0,
                // Left (-127,0,0)
                -127, 0, 0,  -127, 0, 0,  -127, 0, 0,  -127, 0, 0
            };
            VertexArray posArr = new VertexArray(24, 3, 2);
            posArr.set(0, 24, positions);
            VertexArray normArr = new VertexArray(24, 3, 1);
            normArr.set(0, 24, normals);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setNormals(normArr);
            vb.setDefaultColor(0xFFCCCCCC);
            return vb;
        }

        private static VertexBuffer buildVertexColorCube() {
            short[] positions = new short[]{
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
            };
            byte[] normals = new byte[]{
                0, 0, 127,  0, 0, 127,  0, 0, 127,  0, 0, 127,
                0, 0, -127,  0, 0, -127,  0, 0, -127,  0, 0, -127,
                0, 127, 0,  0, 127, 0,  0, 127, 0,  0, 127, 0,
                0, -127, 0,  0, -127, 0,  0, -127, 0,  0, -127, 0,
                127, 0, 0,  127, 0, 0,  127, 0, 0,  127, 0, 0,
                -127, 0, 0,  -127, 0, 0,  -127, 0, 0,  -127, 0, 0
            };
            byte[] colorData = new byte[]{
                (byte)230, 60, 60, (byte)255, (byte)230, 60, 60, (byte)255,
                (byte)230, 60, 60, (byte)255, (byte)230, 60, 60, (byte)255,
                60, (byte)200, 60, (byte)255, 60, (byte)200, 60, (byte)255,
                60, (byte)200, 60, (byte)255, 60, (byte)200, 60, (byte)255,
                60, 100, (byte)230, (byte)255, 60, 100, (byte)230, (byte)255,
                60, 100, (byte)230, (byte)255, 60, 100, (byte)230, (byte)255,
                (byte)230, (byte)220, 50, (byte)255, (byte)230, (byte)220, 50, (byte)255,
                (byte)230, (byte)220, 50, (byte)255, (byte)230, (byte)220, 50, (byte)255,
                (byte)200, 60, (byte)200, (byte)255, (byte)200, 60, (byte)200, (byte)255,
                (byte)200, 60, (byte)200, (byte)255, (byte)200, 60, (byte)200, (byte)255,
                60, (byte)210, (byte)210, (byte)255, 60, (byte)210, (byte)210, (byte)255,
                60, (byte)210, (byte)210, (byte)255, 60, (byte)210, (byte)210, (byte)255
            };
            VertexArray posArr = new VertexArray(24, 3, 2);
            posArr.set(0, 24, positions);
            VertexArray normArr = new VertexArray(24, 3, 1);
            normArr.set(0, 24, normals);
            VertexArray colArr = new VertexArray(24, 4, 1);
            colArr.set(0, 24, colorData);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setNormals(normArr);
            vb.setColors(colArr);
            return vb;
        }
    }
}
