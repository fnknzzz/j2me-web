import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Fog;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/**
 * M3G advanced features board.
 * Panels (4 cols x 3 rows):
 *   Row 0: fog linear, fog exponential, fog colored, fog dense
 *   Row 1: background image, immediate scope filter, vertex alpha blend, depthOffset state
 *   Row 2: colorWrite disabled, multi-texture units, Transform.transform(VA) pre-baked, scope=-1 render
 */
public class M3GAdvancedFeaturesBoardMIDlet extends MIDlet {
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

            // Row 0: Fog
            panelFogLinear(g, 0, 0, cw, ch);
            panelFogExponential(g, cw, 0, cw, ch);
            panelFogColored(g, cw * 2, 0, cw, ch);
            panelFogDense(g, cw * 3, 0, cw, ch);

            // Row 1: Background image, scope filter, vertex alpha, depth offset state
            panelBackgroundImage(g, 0, ch, cw, ch);
            panelScopeImmediate(g, cw, ch, cw, ch);
            panelAlphaFactor(g, cw * 2, ch, cw, ch);
            panelDepthOffset(g, cw * 3, ch, cw, ch);

            // Row 2: colorWrite, multi-texture units, pre-transform, always-visible scope
            panelColorWriteOff(g, 0, ch * 2, cw, ch);
            panelMultiTexCoord(g, cw, ch * 2, cw, ch);
            panelPreTransformVA(g, cw * 2, ch * 2, cw, ch);
            panelImmediateScopeRender(g, cw * 3, ch * 2, cw, ch);
        }

        // --- Row 0: Fog ---

        private static void panelFogLinear(Graphics g, int vx, int vy, int vw, int vh) {
            Fog fog = new Fog();
            fog.setMode(Fog.LINEAR);
            fog.setLinear(100.0f, 300.0f);
            fog.setColor(0x00334455);
            renderFogScene(g, vx, vy, vw, vh, fog);
        }

        private static void panelFogExponential(Graphics g, int vx, int vy, int vw, int vh) {
            Fog fog = new Fog();
            fog.setMode(Fog.EXPONENTIAL);
            fog.setDensity(0.005f);
            fog.setColor(0x00554433);
            renderFogScene(g, vx, vy, vw, vh, fog);
        }

        private static void panelFogColored(Graphics g, int vx, int vy, int vw, int vh) {
            Fog fog = new Fog();
            fog.setMode(Fog.LINEAR);
            fog.setLinear(50.0f, 250.0f);
            fog.setColor(0x00002266);
            renderFogScene(g, vx, vy, vw, vh, fog);
        }

        private static void panelFogDense(Graphics g, int vx, int vy, int vw, int vh) {
            Fog fog = new Fog();
            fog.setMode(Fog.EXPONENTIAL);
            fog.setDensity(0.02f);
            fog.setColor(0x00666600);
            renderFogScene(g, vx, vy, vw, vh, fog);
        }

        private static void renderFogScene(Graphics g, int vx, int vy, int vw, int vh, Fog fog) {
            Appearance app = new Appearance();
            app.setFog(fog);
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 200);
            Transform model = new Transform();
            model.postRotate(25, 1, 0, 0);
            model.postRotate(35, 0, 1, 0);
            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(vb, idx, app, model);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        // --- Row 1 ---

        private static void panelBackgroundImage(Graphics g, int vx, int vy, int vw, int vh) {
            Image2D bgImg = makeGradientRGB(8, 8);
            Background bg = new Background();
            bg.setImage(bgImg);
            bg.setImageMode(Background.REPEAT, Background.REPEAT);
            bg.setCrop(0, 0, 8, 8);

            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 40, 0, -35, -30, 0, 35, -30, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFFFFFFFF);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                g3d.clear(bg);
                g3d.render(vb, idx, new Appearance(), new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelScopeImmediate(Graphics g, int vx, int vy, int vw, int vh) {
            // Camera scope=1 should accept the red triangle and reject the blue scope=2 triangle.
            VertexBuffer vb1 = makeTriVB(0xFFFF4444);
            VertexBuffer vb2 = makeTriVB(0xFF4444FF);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 500.0f);
            cam.setScope(1);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 150);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                Transform t1 = new Transform();
                t1.postTranslate(-25, 0, 0);
                g3d.render(vb1, idx, new Appearance(), t1, 1);
                Transform t2 = new Transform();
                t2.postTranslate(25, 0, 0);
                g3d.render(vb2, idx, new Appearance(), t2, 2);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelAlphaFactor(Graphics g, int vx, int vy, int vw, int vh) {
            // Render two cubes with different vertex alpha to simulate alpha factor
            VertexBuffer vbOpaque = buildColorCube();
            VertexBuffer vbAlpha = buildAlphaCube(100);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 250);

            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            Appearance app = new Appearance();
            app.setCompositingMode(cm);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                Transform t1 = new Transform();
                t1.postTranslate(-20, 0, -20);
                t1.postRotate(30, 1, 1, 0);
                g3d.render(vbOpaque, idx, new Appearance(), t1);
                Transform t2 = new Transform();
                t2.postTranslate(20, 0, 20);
                t2.postRotate(30, 1, 1, 0);
                g3d.render(vbAlpha, idx, app, t2);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelDepthOffset(Graphics g, int vx, int vy, int vw, int vh) {
            // Covers CompositingMode depthOffset state on a second overlapping primitive.
            VertexBuffer vb1 = makeTriVB(0xFFFF0000);
            VertexBuffer vb2 = makeTriVB(0xFF00FF00);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});

            CompositingMode cm = new CompositingMode();
            cm.setDepthOffset(1.0f, 1.0f);
            Appearance app2 = new Appearance();
            app2.setCompositingMode(cm);

            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 500.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 150);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(vb1, idx, new Appearance(), new Transform());
                // Same position but with depth offset - should render on top
                Transform t2 = new Transform();
                t2.postScale(0.7f, 0.7f, 1.0f);
                g3d.render(vb2, idx, app2, t2);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        // --- Row 2 ---

        private static void panelColorWriteOff(Graphics g, int vx, int vy, int vw, int vh) {
            // First pass: write depth only (colorWrite=false)
            // Second pass: render with depth test (should be masked by first pass)
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 200);

            CompositingMode cmDepthOnly = new CompositingMode();
            cmDepthOnly.setColorWriteEnable(false);
            Appearance appDepth = new Appearance();
            appDepth.setCompositingMode(cmDepthOnly);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                // Depth-only pass (small cube)
                Transform t1 = new Transform();
                t1.postScale(0.5f, 0.5f, 0.5f);
                t1.postRotate(30, 1, 1, 0);
                g3d.render(vb, idx, appDepth, t1);
                // Color pass (large cube, will be masked where small cube wrote depth)
                Transform t2 = new Transform();
                t2.postRotate(30, 1, 1, 0);
                g3d.render(vb, idx, new Appearance(), t2);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelMultiTexCoord(Graphics g, int vx, int vy, int vw, int vh) {
            // Quad with two active texture units and distinct texture coordinate sets.
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0, 4, new short[]{-60, -60, 0, 60, -60, 0, -60, 60, 0, 60, 60, 0});

            VertexArray tc0 = new VertexArray(4, 2, 2);
            tc0.set(0, 4, new short[]{0, 100, 100, 100, 0, 0, 100, 0});

            VertexArray tc1 = new VertexArray(4, 2, 2);
            tc1.set(0, 4, new short[]{0, 0, 100, 0, 0, 100, 100, 100});

            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setTexCoords(0, tc0, 0.01f, new float[]{0, 0});
            vb.setTexCoords(1, tc1, 0.01f, new float[]{0, 0});
            vb.setDefaultColor(0xFFCCCCCC);

            Image2D img0 = makeCheckerRGB(4, 4);
            Texture2D tex0 = new Texture2D(img0);
            tex0.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            tex0.setBlending(Texture2D.FUNC_MODULATE);

            Image2D img1 = makeColorBlockRGB(4, 4);
            Texture2D tex1 = new Texture2D(img1);
            tex1.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            tex1.setBlending(Texture2D.FUNC_MODULATE);

            Appearance app = new Appearance();
            app.setTexture(0, tex0);
            app.setTexture(1, tex1);

            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float) vw / vh, 1.0f, 500.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 150);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                g3d.render(vb, idx, app, new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelPreTransformVA(Graphics g, int vx, int vy, int vw, int vh) {
            // Use Transform.transform(VertexArray, float[], boolean) to pre-bake positions
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 50, 0, -40, -40, 0, 40, -40, 0});

            Transform t = new Transform();
            t.postRotate(45, 0, 0, 1);
            t.postScale(0.8f, 0.8f, 1.0f);

            float[] transformed = new float[3 * 4];
            t.transform(pos, transformed, true);

            // Now render with identity transform - the rotation is baked in
            VertexArray bakedPos = new VertexArray(3, 3, 2);
            bakedPos.set(0, 3, new short[]{
                (short)transformed[0], (short)transformed[1], (short)transformed[2],
                (short)transformed[4], (short)transformed[5], (short)transformed[6],
                (short)transformed[8], (short)transformed[9], (short)transformed[10]
            });

            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(bakedPos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(0xFF88CCFF);
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{3});

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.render(vb, idx, new Appearance(), new Transform());
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        private static void panelImmediateScopeRender(Graphics g, int vx, int vy, int vw, int vh) {
            // scope=-1 is the immediate-mode always-visible path.
            VertexBuffer vb = buildColorCube();
            TriangleStripArray idx = new TriangleStripArray(0, new int[]{4, 4, 4, 4, 4, 4});
            Camera cam = new Camera();
            cam.setPerspective(60.0f, (float)vw / vh, 1.0f, 1000.0f);
            Transform camT = new Transform();
            camT.postTranslate(0, 0, 250);

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(vx, vy, vw, vh);
                { Background _b = new Background(); _b.setColorClearEnable(false); g3d.clear(_b); }
                g3d.setCamera(cam, camT);
                // Render with scope=-1 (always visible)
                Transform t = new Transform();
                t.postRotate(30, 1, 1, 0);
                g3d.render(vb, idx, new Appearance(), t, -1);
            } catch (Throwable ex) {
            } finally {
                if (g3d.getTarget() != null) {
                    try { g3d.releaseTarget(); } catch (Throwable ignored) {}
                }
            }
        }

        // --- Data builders ---

        private static VertexBuffer makeTriVB(int color) {
            VertexArray pos = new VertexArray(3, 3, 2);
            pos.set(0, 3, new short[]{0, 40, 0, -35, -30, 0, 35, -30, 0});
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 1.0f, new float[]{0, 0, 0});
            vb.setDefaultColor(color);
            return vb;
        }

        private static VertexBuffer buildColorCube() {
            short[] positions = new short[]{
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
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
            VertexArray colArr = new VertexArray(24, 4, 1);
            colArr.set(0, 24, colorData);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colArr);
            return vb;
        }

        private static VertexBuffer buildAlphaCube(int alpha) {
            short[] positions = new short[]{
                -50, -50, 50,  50, -50, 50,  -50, 50, 50,  50, 50, 50,
                50, -50, -50,  -50, -50, -50,  50, 50, -50,  -50, 50, -50,
                -50, 50, 50,  50, 50, 50,  -50, 50, -50,  50, 50, -50,
                -50, -50, -50,  50, -50, -50,  -50, -50, 50,  50, -50, 50,
                50, -50, 50,  50, -50, -50,  50, 50, 50,  50, 50, -50,
                -50, -50, -50,  -50, -50, 50,  -50, 50, -50,  -50, 50, 50
            };
            byte a = (byte) alpha;
            byte[] colorData = new byte[24 * 4];
            for (int i = 0; i < 24; i++) {
                colorData[i*4] = (byte)200;
                colorData[i*4+1] = (byte)100;
                colorData[i*4+2] = (byte)255;
                colorData[i*4+3] = a;
            }
            VertexArray posArr = new VertexArray(24, 3, 2);
            posArr.set(0, 24, positions);
            VertexArray colArr = new VertexArray(24, 4, 1);
            colArr.set(0, 24, colorData);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(posArr, 1.0f, new float[]{0, 0, 0});
            vb.setColors(colArr);
            return vb;
        }

        private static Image2D makeGradientRGB(int w, int h) {
            byte[] data = new byte[w * h * 3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 3;
                    data[off] = (byte)(x * 255 / (w-1));
                    data[off+1] = (byte)(y * 255 / (h-1));
                    data[off+2] = (byte)128;
                }
            }
            return new Image2D(Image2D.RGB, w, h, data);
        }

        private static Image2D makeCheckerRGB(int w, int h) {
            byte[] data = new byte[w * h * 3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 3;
                    if (((x + y) & 1) == 0) {
                        data[off] = (byte)255; data[off+1] = (byte)255; data[off+2] = (byte)255;
                    }
                }
            }
            return new Image2D(Image2D.RGB, w, h, data);
        }

        private static Image2D makeColorBlockRGB(int w, int h) {
            byte[] data = new byte[w * h * 3];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int off = (y * w + x) * 3;
                    int quadrant = (x < w/2 ? 0 : 1) + (y < h/2 ? 0 : 2);
                    switch (quadrant) {
                        case 0: data[off]=(byte)255; break;
                        case 1: data[off+1]=(byte)255; break;
                        case 2: data[off+2]=(byte)255; break;
                        case 3: data[off]=(byte)255; data[off+1]=(byte)255; break;
                    }
                }
            }
            return new Image2D(Image2D.RGB, w, h, data);
        }
    }
}
