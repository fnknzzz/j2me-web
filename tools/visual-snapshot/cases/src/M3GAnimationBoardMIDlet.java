import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.AnimationController;
import javax.microedition.m3g.AnimationTrack;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.KeyframeSequence;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.MorphingMesh;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G animation board.
 * Panels (3 cols x 3 rows):
 *   Row 0: translation, scale, quaternion orientation
 *   Row 1: spline translation, material diffuse color, visibility
 *   Row 2: morph weights, camera field of view, squad orientation
 */
public class M3GAnimationBoardMIDlet extends MIDlet {
    protected void startApp() { Display.getDisplay(this).setCurrent(new Board()); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        private static final int SAMPLE_TIME = 500;
        private static final int USER_ID_MATERIAL = 7003;

        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int cols = 3;
            int rows = 3;
            int cw = w / cols;
            int ch = h / rows;
            g.setColor(28, 30, 36);
            g.fillRect(0, 0, w, h);

            panelTranslation(g, 0, 0, cw, ch);
            panelScale(g, cw, 0, cw, ch);
            panelOrientation(g, cw * 2, 0, cw, ch);
            panelSplineTranslation(g, 0, ch, cw, ch);
            panelMaterialColor(g, cw, ch, cw, ch);
            panelVisibility(g, cw * 2, ch, cw, ch);
            panelMorph(g, 0, ch * 2, cw, ch);
            panelCameraFov(g, cw, ch * 2, cw, ch);
            panelSquadOrientation(g, cw * 2, ch * 2, cw, ch);

            for (int i = 0; i < cols * rows; i++) {
                int x = (i % cols) * cw;
                int y = (i / cols) * ch;
                g.setColor(86, 90, 102);
                g.drawRect(x + 2, y + 2, cw - 5, ch - 5);
            }
        }

        private static void panelTranslation(Graphics g, int vx, int vy, int vw, int vh) {
            Mesh mesh = makeMarker(0xFF4EA4FF);
            attach(mesh, AnimationTrack.TRANSLATION, linear(3,
                    new float[]{0, 0, 0}, new float[]{70, 0, 0}));
            mesh.animate(SAMPLE_TIME);
            renderChildNode(g, vx, vy, vw, vh, mesh);
        }

        private static void panelScale(Graphics g, int vx, int vy, int vw, int vh) {
            Mesh mesh = makeMarker(0xFFFFC857);
            attach(mesh, AnimationTrack.SCALE, linear(3,
                    new float[]{0.6f, 0.6f, 0.6f}, new float[]{1.8f, 1.8f, 1.8f}));
            mesh.animate(SAMPLE_TIME);
            renderChildNode(g, vx, vy, vw, vh, mesh);
        }

        private static void panelOrientation(Graphics g, int vx, int vy, int vw, int vh) {
            Mesh mesh = makeMarker(0xFFE66DD8);
            attach(mesh, AnimationTrack.ORIENTATION, slerp(
                    new float[]{0, 0, 0, 1}, quatZ(90)));
            mesh.animate(SAMPLE_TIME);
            renderChildNode(g, vx, vy, vw, vh, mesh);
        }

        private static void panelSplineTranslation(Graphics g, int vx, int vy, int vw, int vh) {
            Mesh mesh = makeMarker(0xFFFF8C42);
            attach(mesh, AnimationTrack.TRANSLATION, spline(3,
                    new float[]{-58, 0, 0}, new float[]{0, 34, 0}, new float[]{58, 0, 0}));
            mesh.animate(SAMPLE_TIME);
            renderChildNode(g, vx, vy, vw, vh, mesh);
        }

        private static void panelMaterialColor(Graphics g, int vx, int vy, int vw, int vh) {
            Material material = new Material();
            material.setUserID(USER_ID_MATERIAL);
            material.setColor(Material.DIFFUSE, 0x000000ff);
            Appearance appearance = new Appearance();
            appearance.setMaterial(material);
            Mesh mesh = makeLitTriangle(appearance);
            attach(material, AnimationTrack.DIFFUSE_COLOR, linear(3,
                    new float[]{0, 0, 1}, new float[]{1, 0, 0}));
            material.animate(SAMPLE_TIME);
            renderNode(g, vx, vy, vw, vh, mesh, makeDefaultCamera(vw, vh), true);
        }

        private static void panelVisibility(Graphics g, int vx, int vy, int vw, int vh) {
            Group group = new Group();
            Mesh left = makeMarker(0xFF38D96A);
            left.setTranslation(-46, 0, 0);
            Mesh right = makeMarker(0xFFFF4D4D);
            right.setTranslation(46, 0, 0);
            attach(right, AnimationTrack.VISIBILITY, step(new float[]{0}, new float[]{0}));
            group.addChild(left);
            group.addChild(right);
            group.animate(SAMPLE_TIME);
            renderNode(g, vx, vy, vw, vh, group);
        }

        private static void panelMorph(Graphics g, int vx, int vy, int vw, int vh) {
            VertexBuffer base = makeQuad(0, 0);
            VertexBuffer target = makeQuad(28, 20);
            MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target},
                    new TriangleStripArray(0, new int[]{4}), new Appearance());
            attach(mesh, AnimationTrack.MORPH_WEIGHTS, linear(1, new float[]{0}, new float[]{1}));
            mesh.animate(SAMPLE_TIME);
            renderNode(g, vx, vy, vw, vh, mesh);
        }

        private static void panelCameraFov(Graphics g, int vx, int vy, int vw, int vh) {
            Camera camera = new Camera();
            camera.setPerspective(20.0f, (float) vw / vh, 1.0f, 1000.0f);
            attach(camera, AnimationTrack.FIELD_OF_VIEW, linear(1, new float[]{20}, new float[]{80}));
            camera.animate(SAMPLE_TIME);
            Mesh mesh = makeCube(0xFF9AD7FF);
            mesh.setOrientation(25, 1, 1, 0);
            renderNode(g, vx, vy, vw, vh, mesh, camera);
        }

        private static void panelSquadOrientation(Graphics g, int vx, int vy, int vw, int vh) {
            Mesh mesh = makeCube(0xFFB38CFF);
            AnimationController controller = new AnimationController();
            controller.setActiveInterval(0, 0);
            controller.setPosition(0, 0);
            AnimationTrack track = new AnimationTrack(squad(
                    quatZ(0), quatZ(45), quatZ(120), quatZ(180)), AnimationTrack.ORIENTATION);
            track.setController(controller);
            mesh.addAnimationTrack(track);
            mesh.animate(SAMPLE_TIME);
            renderChildNode(g, vx, vy, vw, vh, mesh);
        }

        private static void attach(Object3D target, int property, KeyframeSequence sequence) {
            AnimationController controller = new AnimationController();
            controller.setActiveInterval(0, 1000);
            controller.setPosition(0, 0);
            AnimationTrack track = new AnimationTrack(sequence, property);
            track.setController(controller);
            target.addAnimationTrack(track);
        }

        private static KeyframeSequence linear(int components, float[] a, float[] b) {
            KeyframeSequence seq = new KeyframeSequence(2, components, KeyframeSequence.LINEAR);
            seq.setDuration(1000);
            seq.setKeyframe(0, 0, a);
            seq.setKeyframe(1, 1000, b);
            return seq;
        }

        private static KeyframeSequence slerp(float[] a, float[] b) {
            KeyframeSequence seq = new KeyframeSequence(2, 4, KeyframeSequence.SLERP);
            seq.setDuration(1000);
            seq.setKeyframe(0, 0, a);
            seq.setKeyframe(1, 1000, b);
            return seq;
        }

        private static KeyframeSequence spline(int components, float[] a, float[] b, float[] c) {
            KeyframeSequence seq = new KeyframeSequence(3, components, KeyframeSequence.SPLINE);
            seq.setDuration(1000);
            seq.setKeyframe(0, 0, a);
            seq.setKeyframe(1, 500, b);
            seq.setKeyframe(2, 1000, c);
            return seq;
        }

        private static KeyframeSequence squad(float[] a, float[] b, float[] c, float[] d) {
            KeyframeSequence seq = new KeyframeSequence(4, 4, KeyframeSequence.SQUAD);
            seq.setDuration(1000);
            seq.setKeyframe(0, 0, a);
            seq.setKeyframe(1, 333, b);
            seq.setKeyframe(2, 666, c);
            seq.setKeyframe(3, 1000, d);
            return seq;
        }

        private static KeyframeSequence step(float[] a, float[] b) {
            KeyframeSequence seq = new KeyframeSequence(2, a.length, KeyframeSequence.STEP);
            seq.setDuration(1000);
            seq.setKeyframe(0, 0, a);
            seq.setKeyframe(1, 1000, b);
            return seq;
        }

        private static float[] quatZ(float degrees) {
            double half = degrees * Math.PI / 360.0;
            return new float[]{0, 0, (float) Math.sin(half), (float) Math.cos(half)};
        }

        private static Mesh makeCube(int color) {
            short[] positions = new short[]{
                -24, -24, 24,  24, -24, 24,  -24, 24, 24,  24, 24, 24,
                24, -24, -24,  -24, -24, -24,  24, 24, -24,  -24, 24, -24,
                -24, 24, 24,  24, 24, 24,  -24, 24, -24,  24, 24, -24,
                -24, -24, -24,  24, -24, -24,  -24, -24, 24,  24, -24, 24,
                24, -24, 24,  24, -24, -24,  24, 24, 24,  24, 24, -24,
                -24, -24, -24,  -24, -24, 24,  -24, 24, -24,  -24, 24, 24
            };
            VertexArray pos = new VertexArray(24, 3, 2);
            pos.set(0, 24, positions);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return new Mesh(vb, new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4}), new Appearance());
        }

        private static Mesh makeMarker(int color) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{
                -42, -24, 0,
                20, -24, 0,
                -42, 30, 0,
                50, 4, 0
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return new Mesh(vb, new TriangleStripArray(0, new int[]{4}), new Appearance());
        }

        private static Mesh makeLitTriangle(Appearance appearance) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 56, 0, -54, -42, 0, 54, -42, 0});
            VertexArray normals = new VertexArray(3, 3, 1);
            normals.set(0, 3, new byte[]{0, 0, 127, 0, 0, 127, 0, 0, 127});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setNormals(normals);
            vb.setDefaultColor(0xFFFFFFFF);
            return new Mesh(vb, new TriangleStripArray(0, new int[]{3}), appearance);
        }

        private static VertexBuffer makeQuad(int ox, int oy) {
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{
                (short) (-48 + ox), (short) (-38 + oy), 0,
                (short) (48 + ox), (short) (-38 - oy), 0,
                (short) (-48 - ox), (short) (38 - oy), 0,
                (short) (48 - ox), (short) (38 + oy), 0
            });
            VertexArray colors = new VertexArray(4, 4, 1);
            colors.set(0, 4, new byte[]{
                (byte) 255, 90, 90, (byte) 255,
                90, (byte) 255, 90, (byte) 255,
                90, 90, (byte) 255, (byte) 255,
                (byte) 255, (byte) 255, 90, (byte) 255
            });
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colors);
            return vb;
        }

        private static void renderNode(Graphics g, int vx, int vy, int vw, int vh,
                javax.microedition.m3g.Node node) {
            Camera camera = new Camera();
            camera.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            renderNode(g, vx, vy, vw, vh, node, camera, false);
        }

        private static void renderChildNode(Graphics g, int vx, int vy, int vw, int vh,
                javax.microedition.m3g.Node child) {
            Group root = new Group();
            root.addChild(child);
            renderNode(g, vx, vy, vw, vh, root);
        }

        private static void renderNode(Graphics g, int vx, int vy, int vw, int vh,
                javax.microedition.m3g.Node node, Camera camera) {
            renderNode(g, vx, vy, vw, vh, node, camera, false);
        }

        private static Camera makeDefaultCamera(int vw, int vh) {
            Camera camera = new Camera();
            camera.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            return camera;
        }

        private static void renderNode(Graphics g, int vx, int vy, int vw, int vh,
                javax.microedition.m3g.Node node, Camera camera, boolean addDiffuseLight) {
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(0, 0, 190);
                g3d.setCamera(camera, cameraTransform);
                g3d.resetLights();
                if (addDiffuseLight) {
                    Light light = new Light();
                    light.setMode(Light.DIRECTIONAL);
                    light.setColor(0x00ffffff);
                    light.setIntensity(1.0f);
                    Transform lightTransform = new Transform();
                    lightTransform.postRotate(-45.0f, 1.0f, 0.0f, 0.0f);
                    g3d.addLight(light, lightTransform);
                }
                g3d.render(node, new Transform());
            } catch (Throwable t) {
                g.setColor(120, 32, 42);
                g.fillRect(vx + 8, vy + 8, vw - 16, vh - 16);
                g.setColor(255, 180, 180);
                String msg = t.getClass().getName();
                if (msg.length() > 28) {
                    msg = msg.substring(msg.length() - 28);
                }
                g.drawString(msg, vx + 10, vy + vh / 2, Graphics.LEFT | Graphics.TOP);
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
