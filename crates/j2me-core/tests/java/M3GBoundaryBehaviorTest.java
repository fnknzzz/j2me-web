import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.AnimationController;
import javax.microedition.m3g.AnimationTrack;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Fog;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.KeyframeSequence;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Node;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.RayIntersection;
import javax.microedition.m3g.SkinnedMesh;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.m3g.World;

public class M3GBoundaryBehaviorTest {
    private static int passed = 0;
    private static int failed = 0;
    private static StringBuffer log = new StringBuffer();
    private static StringBuffer failures = new StringBuffer();

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    public static void main(String[] args) {
        runWithCrashReport("M3GBoundaryBehaviorMIDlet");
    }

    public static String run() {
        passed = 0;
        failed = 0;
        log = new StringBuffer();
        failures = new StringBuffer();

        println("=== M3G Boundary Behavior Test ===");
        testCameraBoundaries();
        testImage2DBoundaries();
        testBackgroundBoundaries();
        testTextureBoundaries();
        testAppearanceBoundaries();
        testMaterialBoundaries();
        testCompositingModeBoundaries();
        testFogBoundaries();
        testPolygonModeBoundaries();
        testAnimationBoundaries();
        testObject3DBoundaries();
        testTransformBoundaries();
        testVertexArrayBoundaries();
        testTriangleStripArrayBoundaries();
        testVertexBufferBoundaries();
        testLightBoundaries();
        testNodeAlignmentBoundaries();
        testNodeTransformToBoundaries();
        testGroupPickBoundaries();
        testGroupBoundaries();
        testGraphics3DBoundaries();
        println("PASSED: " + passed + " FAILED: " + failed);
        println("=== Tests Complete ===");
        return log.toString();
    }

    public static String runWithCrashReport(String owner) {
        try {
            run();
            return summaryForUi();
        } catch (Throwable t) {
            String output = owner + " crashed\n"
                    + t.getClass().getName() + ":";
            System.out.println(output);
            return output;
        }
    }

    public static String summaryForUi() {
        StringBuffer out = new StringBuffer();
        out.append("M3G Boundary Behavior\n");
        out.append("PASSED: ").append(passed).append(" FAILED: ").append(failed).append('\n');
        if (failed == 0) {
            out.append("\nAll tests passed.\n");
        } else {
            out.append("\nFailing tests:\n");
            out.append(failures.toString());
        }
        out.append("\n--- Full log ---\n");
        out.append(log.toString());
        return out.toString();
    }

    private static void testCameraBoundaries() {
        final Camera camera = new Camera();
        expectThrows("Camera.setPerspective fovy zero", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Camera().setPerspective(0.0f, 1.0f, 1.0f, 10.0f);
            }
        });
        expectThrows("Camera.setPerspective near zero", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Camera().setPerspective(45.0f, 1.0f, 0.0f, 10.0f);
            }
        });
        expectNoThrow("Camera.setPerspective far before near", new ThrowingRunnable() {
            public void run() {
                new Camera().setPerspective(45.0f, 1.0f, 10.0f, 1.0f);
            }
        });
        expectNoThrow("Camera.setParallel near equals far", new ThrowingRunnable() {
            public void run() {
                new Camera().setParallel(2.0f, 1.0f, 1.0f, 1.0f);
            }
        });
        camera.setParallel(2.0f, 1.0f, 1.0f, 1.0f);
        expectThrows("Camera.getProjection empty volume", "java.lang.ArithmeticException", new ThrowingRunnable() {
            public void run() {
                camera.getProjection(new Transform());
            }
        });
        final Camera validCamera = new Camera();
        validCamera.setParallel(2.0f, 1.0f, 1.0f, 2.0f);
        expectEquals("Camera.getProjection null transform", Camera.PARALLEL, validCamera.getProjection((Transform) null));
        expectEquals("Camera.getProjection null params", Camera.PARALLEL, validCamera.getProjection((float[]) null));
        expectThrows("Camera.getProjection params too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Camera().getProjection(new float[3]);
            }
        });
        expectThrows("Camera.setGeneric null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Camera().setGeneric(null);
            }
        });
    }

    private static void testImage2DBoundaries() {
        expectThrows("Image2D ctor invalid format", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.ALPHA - 1, 1, 1);
            }
        });
        expectThrows("Image2D ctor zero width", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 0, 1);
            }
        });
        expectThrows("Image2D ctor zero height", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 1, 0);
            }
        });
        expectThrows("Image2D ctor byte null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 1, 1, null);
            }
        });
        expectThrows("Image2D ctor byte too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 2, 2, new byte[3]);
            }
        });
        expectNoThrow("Image2D ctor LCDUI RGB image", new ThrowingRunnable() {
            public void run() {
                Image source = Image.createRGBImage(
                        new int[]{0xFF123456, 0xFFABCDEF},
                        2, 1, true);
                Image2D image = new Image2D(Image2D.RGB, source);
                expectEquals("Image2D LCDUI RGB width", 2, image.getWidth());
                expectEquals("Image2D LCDUI RGB height", 1, image.getHeight());
            }
        });
        expectNoThrow("Image2D ctor LCDUI RGBA image", new ThrowingRunnable() {
            public void run() {
                Image source = Image.createRGBImage(
                        new int[]{0x40112233, 0xFF445566},
                        2, 1, true);
                Image2D image = new Image2D(Image2D.RGBA, source);
                expectEquals("Image2D LCDUI RGBA width", 2, image.getWidth());
                expectEquals("Image2D LCDUI RGBA height", 1, image.getHeight());
            }
        });
        expectThrows("Image2D ctor palette null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 1, 1, new byte[1], null);
            }
        });
        expectThrows("Image2D.set immutable", "java.lang.IllegalStateException", new ThrowingRunnable() {
            public void run() {
                image(Image2D.RGB, 1, 1).set(0, 0, 1, 1, new byte[3]);
            }
        });
        expectThrows("Image2D.set null image", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 2, 2).set(0, 0, 1, 1, null);
            }
        });
        expectThrows("Image2D.set negative x", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 2, 2).set(-1, 0, 1, 1, new byte[3]);
            }
        });
        expectThrows("Image2D.set exceeds width", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 2, 2).set(1, 0, 2, 1, new byte[6]);
            }
        });
        expectThrows("Image2D.set data too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Image2D(Image2D.RGB, 2, 2).set(0, 0, 2, 2, new byte[11]);
            }
        });
    }

    private static void testBackgroundBoundaries() {
        final Background background = new Background();
        final Image2D rgb = image(Image2D.RGB, 4, 2);

        background.setCrop(9, 8, 1, 1);
        background.setImage(rgb);
        expectEquals("Background.setImage resets crop x", 0, background.getCropX());
        expectEquals("Background.setImage resets crop width", 4, background.getCropWidth());
        expectThrows("Background.setImage rejects alpha image", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Background().setImage(image(Image2D.ALPHA, 2, 2));
            }
        });
        expectThrows("Background.setCrop negative width", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Background().setCrop(0, 0, -1, 1);
            }
        });
        expectNoThrow("Background.setCrop zero size", new ThrowingRunnable() {
            public void run() {
                new Background().setCrop(0, 0, 0, 0);
            }
        });
        expectThrows("Background.setImageMode invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Background().setImageMode(Background.BORDER - 1, Background.REPEAT);
            }
        });
    }

    private static void testTextureBoundaries() {
        expectThrows("Texture2D ctor null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(null);
            }
        });
        expectThrows("Texture2D ctor non power of two", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(image(Image2D.RGB, 3, 4));
            }
        });
        expectThrows("Texture2D.setImage null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(image(Image2D.RGB, 2, 2)).setImage(null);
            }
        });
        expectThrows("Texture2D.setImage non power of two", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(image(Image2D.RGB, 2, 2)).setImage(image(Image2D.RGB, 2, 3));
            }
        });
        expectThrows("Texture2D.setFiltering invalid level", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(image(Image2D.RGB, 2, 2)).setFiltering(Texture2D.FILTER_BASE_LEVEL - 1, Texture2D.FILTER_NEAREST);
            }
        });
        expectThrows("Texture2D.setWrapping invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(image(Image2D.RGB, 2, 2)).setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_REPEAT + 1);
            }
        });
        expectThrows("Texture2D.setBlending invalid low", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(image(Image2D.RGB, 2, 2)).setBlending(Texture2D.FUNC_ADD - 1);
            }
        });
        expectThrows("Texture2D.setBlending invalid high", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Texture2D(image(Image2D.RGB, 2, 2)).setBlending(Texture2D.FUNC_REPLACE + 1);
            }
        });
    }

    private static void testAppearanceBoundaries() {
        expectThrows("Appearance.setLayer below range", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Appearance().setLayer(-64);
            }
        });
        expectThrows("Appearance.setLayer above range", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Appearance().setLayer(64);
            }
        });
        expectNoThrow("Appearance.setLayer bounds", new ThrowingRunnable() {
            public void run() {
                Appearance appearance = new Appearance();
                appearance.setLayer(-63);
                expectEquals("Appearance.getLayer lower bound", -63, appearance.getLayer());
                appearance.setLayer(63);
                expectEquals("Appearance.getLayer upper bound", 63, appearance.getLayer());
            }
        });
        expectThrows("Appearance.setTexture negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Appearance().setTexture(-1, null);
            }
        });
        expectThrows("Appearance.setTexture high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Appearance().setTexture(8, null);
            }
        });
        expectThrows("Appearance.getTexture negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Appearance().getTexture(-1);
            }
        });
        expectThrows("Appearance.getTexture high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Appearance().getTexture(8);
            }
        });
    }

    private static void testMaterialBoundaries() {
        expectThrows("Material.setColor zero target", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Material().setColor(0, 0);
            }
        });
        expectThrows("Material.setColor invalid target", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Material().setColor(Material.AMBIENT | 1, 0);
            }
        });
        expectThrows("Material.getColor combined target", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Material().getColor(Material.AMBIENT | Material.DIFFUSE);
            }
        });
        expectThrows("Material.getColor invalid target", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Material().getColor(1);
            }
        });
        expectThrows("Material.setShininess negative", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Material().setShininess(-0.1f);
            }
        });
        expectThrows("Material.setShininess too high", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Material().setShininess(128.1f);
            }
        });
    }

    private static void testCompositingModeBoundaries() {
        expectThrows("CompositingMode.setBlending below range", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new CompositingMode().setBlending(CompositingMode.ALPHA - 1);
            }
        });
        expectThrows("CompositingMode.setBlending above range", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new CompositingMode().setBlending(CompositingMode.REPLACE + 1);
            }
        });
        expectThrows("CompositingMode.setAlphaThreshold negative", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new CompositingMode().setAlphaThreshold(-0.01f);
            }
        });
        expectThrows("CompositingMode.setAlphaThreshold above one", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new CompositingMode().setAlphaThreshold(1.01f);
            }
        });
    }

    private static void testFogBoundaries() {
        expectThrows("Fog.setMode invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Fog().setMode(Fog.EXPONENTIAL - 1);
            }
        });
        expectNoThrow("Fog.setLinear near after far", new ThrowingRunnable() {
            public void run() {
                Fog fog = new Fog();
                fog.setLinear(2.0f, 1.0f);
                check("Fog.getNearDistance reversed", fog.getNearDistance() == 2.0f);
                check("Fog.getFarDistance reversed", fog.getFarDistance() == 1.0f);
            }
        });
        expectThrows("Fog.setDensity negative", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Fog().setDensity(-0.01f);
            }
        });
    }

    private static void testPolygonModeBoundaries() {
        expectThrows("PolygonMode.setCulling invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new PolygonMode().setCulling(PolygonMode.CULL_BACK - 1);
            }
        });
        expectThrows("PolygonMode.setWinding invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new PolygonMode().setWinding(PolygonMode.WINDING_CCW - 1);
            }
        });
        expectThrows("PolygonMode.setShading invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new PolygonMode().setShading(PolygonMode.SHADE_FLAT - 1);
            }
        });
    }

    private static void testAnimationBoundaries() {
        expectThrows("AnimationController.setActiveInterval reversed", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new AnimationController().setActiveInterval(2, 1);
            }
        });
        expectThrows("AnimationController.setWeight negative", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new AnimationController().setWeight(-0.01f);
            }
        });
        expectThrows("KeyframeSequence ctor zero keyframes", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(0, 1, KeyframeSequence.LINEAR);
            }
        });
        expectThrows("KeyframeSequence ctor zero components", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 0, KeyframeSequence.LINEAR);
            }
        });
        expectThrows("KeyframeSequence ctor invalid interpolation", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 1, KeyframeSequence.LINEAR - 1);
            }
        });
        expectThrows("KeyframeSequence ctor slerp wrong components", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 3, KeyframeSequence.SLERP);
            }
        });
        expectThrows("KeyframeSequence ctor squad wrong components", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 3, KeyframeSequence.SQUAD);
            }
        });
        expectThrows("KeyframeSequence.setKeyframe null value", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 2, KeyframeSequence.LINEAR).setKeyframe(0, 0, null);
            }
        });
        expectThrows("KeyframeSequence.setKeyframe negative index", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 2, KeyframeSequence.LINEAR).setKeyframe(-1, 0, new float[2]);
            }
        });
        expectThrows("KeyframeSequence.setKeyframe short value", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 2, KeyframeSequence.LINEAR).setKeyframe(0, 0, new float[1]);
            }
        });
        expectThrows("KeyframeSequence.setKeyframe negative time", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 2, KeyframeSequence.LINEAR).setKeyframe(0, -1, new float[2]);
            }
        });
        expectThrows("KeyframeSequence.getKeyframe index high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 2, KeyframeSequence.LINEAR).getKeyframe(1, null);
            }
        });
        expectThrows("KeyframeSequence.getKeyframe short value", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 2, KeyframeSequence.LINEAR).getKeyframe(0, new float[1]);
            }
        });
        expectThrows("KeyframeSequence.setValidRange first negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(2, 1, KeyframeSequence.LINEAR).setValidRange(-1, 0);
            }
        });
        expectThrows("KeyframeSequence.setValidRange last high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(2, 1, KeyframeSequence.LINEAR).setValidRange(0, 2);
            }
        });
        expectNoThrow("KeyframeSequence.setValidRange wrap", new ThrowingRunnable() {
            public void run() {
                KeyframeSequence sequence = new KeyframeSequence(3, 1, KeyframeSequence.LINEAR);
                sequence.setValidRange(2, 1);
                expectEquals("KeyframeSequence.getValidRangeFirst wrap", 2, sequence.getValidRangeFirst());
                expectEquals("KeyframeSequence.getValidRangeLast wrap", 1, sequence.getValidRangeLast());
            }
        });
        expectThrows("KeyframeSequence.setDuration zero", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 1, KeyframeSequence.LINEAR).setDuration(0);
            }
        });
        expectThrows("KeyframeSequence.setRepeatMode invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new KeyframeSequence(1, 1, KeyframeSequence.LINEAR).setRepeatMode(KeyframeSequence.CONSTANT - 1);
            }
        });
        expectThrows("AnimationTrack ctor null sequence", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new AnimationTrack(null, AnimationTrack.ALPHA);
            }
        });
        expectThrows("AnimationTrack ctor invalid property", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new AnimationTrack(new KeyframeSequence(1, 1, KeyframeSequence.LINEAR), AnimationTrack.ALPHA - 1);
            }
        });
    }

    private static void testObject3DBoundaries() {
        expectThrows("Object3D.getReferences too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                Appearance appearance = new Appearance();
                appearance.setMaterial(new Material());
                appearance.getReferences(new Object3D[0]);
            }
        });
        expectThrows("Object3D.addAnimationTrack null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Group().addAnimationTrack(null);
            }
        });
        expectNoThrow("Object3D.removeAnimationTrack null", new ThrowingRunnable() {
            public void run() {
                new Group().removeAnimationTrack(null);
            }
        });
        expectThrows("Object3D.getAnimationTrack negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Group().getAnimationTrack(-1);
            }
        });
        expectThrows("Object3D.getAnimationTrack empty", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Group().getAnimationTrack(0);
            }
        });
        expectNoThrow("Object3D.find returns scene child", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group child = new Group();
                child.setUserID(347178853);
                root.addChild(child);
                check("Object3D.find returns matching child", root.find(347178853) == child);
                check("Object3D.find returns null when missing", root.find(347178854) == null);
            }
        });
        expectNoThrow("Object3D.duplicate appearance shallow references", new ThrowingRunnable() {
            public void run() {
                Appearance appearance = new Appearance();
                Material material = new Material();
                Texture2D texture = new Texture2D(image(Image2D.RGB, 2, 2));
                appearance.setUserID(1234);
                appearance.setMaterial(material);
                appearance.setTexture(0, texture);
                appearance.setLayer(7);

                Appearance copy = (Appearance) appearance.duplicate();
                check("Object3D.duplicate returns new appearance", copy != appearance);
                expectEquals("Object3D.duplicate copies user id", 1234, copy.getUserID());
                expectEquals("Object3D.duplicate copies layer", 7, copy.getLayer());
                check("Object3D.duplicate shares material reference", copy.getMaterial() == material);
                check("Object3D.duplicate shares texture reference", copy.getTexture(0) == texture);
            }
        });
        expectNoThrow("Object3D.duplicate node subtree", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group child = new Group();
                child.setTranslation(2.0f, 3.0f, 4.0f);
                root.addChild(child);

                Group copy = (Group) root.duplicate();
                Group childCopy = (Group) copy.getChild(0);
                float[] translation = new float[3];
                childCopy.getTranslation(translation);
                check("Object3D.duplicate copies child node", childCopy != child);
                check("Object3D.duplicate root parent null", copy.getParent() == null);
                check("Object3D.duplicate child parent remapped", childCopy.getParent() == copy);
                check("Object3D.duplicate child transform x", approx(translation[0], 2.0f));
                check("Object3D.duplicate original parent unchanged", child.getParent() == root);
            }
        });
        expectNoThrow("Object3D.duplicate remaps internal node references", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group aligned = new Group();
                Group internalRef = new Group();
                Group externalRef = new Group();
                aligned.setAlignment(internalRef, Node.Z_AXIS, externalRef, Node.Y_AXIS);
                root.addChild(aligned);
                root.addChild(internalRef);

                Group copy = (Group) root.duplicate();
                Group alignedCopy = (Group) copy.getChild(0);
                Group internalRefCopy = (Group) copy.getChild(1);
                check("Object3D.duplicate remaps z reference",
                        alignedCopy.getAlignmentReference(Node.Z_AXIS) == internalRefCopy);
                check("Object3D.duplicate keeps external y reference",
                        alignedCopy.getAlignmentReference(Node.Y_AXIS) == externalRef);
            }
        });
        expectNoThrow("Object3D.duplicate world active camera remap", new ThrowingRunnable() {
            public void run() {
                World world = new World();
                Camera camera = new Camera();
                world.addChild(camera);
                world.setActiveCamera(camera);

                World copy = (World) world.duplicate();
                check("Object3D.duplicate remaps active camera",
                        copy.getActiveCamera() == copy.getChild(0));
                check("Object3D.duplicate active camera copied",
                        copy.getActiveCamera() != camera);
            }
        });
        expectNoThrow("Object3D.duplicate skinned mesh skeleton", new ThrowingRunnable() {
            public void run() {
                Group skeleton = new Group();
                final Group bone = new Group();
                skeleton.addChild(bone);
                SkinnedMesh mesh = new SkinnedMesh(vertexBuffer(),
                        new TriangleStripArray(0, new int[] { 3 }), null, skeleton);
                mesh.addTransform(bone, 100, 0, 2);

                final SkinnedMesh copy = (SkinnedMesh) mesh.duplicate();
                Group skeletonCopy = copy.getSkeleton();
                Node boneCopy = skeletonCopy.getChild(0);
                int[] indices = new int[2];
                float[] weights = new float[2];
                check("Object3D.duplicate copies skeleton", skeletonCopy != skeleton);
                check("Object3D.duplicate copies skeleton child", boneCopy != bone);
                expectEquals("Object3D.duplicate remaps skin influence",
                        2, copy.getBoneVertices(boneCopy, indices, weights));
                expectThrows("Object3D.duplicate rejects original bone",
                        "java.lang.IllegalArgumentException", new ThrowingRunnable() {
                    public void run() {
                        copy.getBoneVertices(bone, null, null);
                    }
                });
            }
        });
        expectNoThrow("Object3D.duplicate user subclass base class", new ThrowingRunnable() {
            public void run() {
                Group copy = (Group) new CustomGroup().duplicate();
                check("Object3D.duplicate drops user subclass type",
                        copy.getClass().getName().equals("javax.microedition.m3g.Group"));
            }
        });
    }

    private static void testTransformBoundaries() {
        expectThrows("Transform copy ctor null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Transform(null);
            }
        });
        expectThrows("Transform.set transform null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Transform().set((Transform) null);
            }
        });
        expectThrows("Transform.set matrix null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Transform().set((float[]) null);
            }
        });
        expectThrows("Transform.set matrix too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Transform().set(new float[15]);
            }
        });
        expectThrows("Transform.get matrix null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Transform().get(null);
            }
        });
        expectThrows("Transform.get matrix too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Transform().get(new float[15]);
            }
        });
        expectThrows("Transform.invert singular", "java.lang.ArithmeticException", new ThrowingRunnable() {
            public void run() {
                Transform t = new Transform();
                t.set(new float[] {
                    1.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 0.0f, 1.0f
                });
                t.invert();
            }
        });
        expectThrows("Transform.postMultiply null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Transform().postMultiply(null);
            }
        });
        expectThrows("Transform.postRotate zero axis", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Transform().postRotate(45.0f, 0.0f, 0.0f, 0.0f);
            }
        });
        expectThrows("Transform.postRotateQuat zero", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Transform().postRotateQuat(0.0f, 0.0f, 0.0f, 0.0f);
            }
        });
        expectThrows("Transform.transform vectors null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Transform().transform((float[]) null);
            }
        });
        expectThrows("Transform.transform vectors bad length", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Transform().transform(new float[3]);
            }
        });
    }

    private static void testVertexArrayBoundaries() {
        expectThrows("VertexArray ctor zero vertices", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(0, 3, 1);
            }
        });
        expectThrows("VertexArray ctor one component", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(1, 1, 1);
            }
        });
        expectThrows("VertexArray ctor invalid component size", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(1, 3, 3);
            }
        });
        expectThrows("VertexArray.set byte null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(2, 3, 1).set(0, 1, (byte[]) null);
            }
        });
        expectThrows("VertexArray.set short null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(2, 3, 2).set(0, 1, (short[]) null);
            }
        });
        expectThrows("VertexArray.set byte too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(2, 3, 1).set(0, 2, new byte[5]);
            }
        });
        expectThrows("VertexArray.set byte first negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(2, 3, 1).set(-1, 1, new byte[3]);
            }
        });
        expectThrows("VertexArray.set byte too many vertices", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(2, 3, 1).set(1, 2, new byte[6]);
            }
        });
        expectThrows("VertexArray.set wrong byte type", "java.lang.IllegalStateException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(2, 3, 2).set(0, 1, new byte[3]);
            }
        });
        expectThrows("VertexArray.get wrong short type", "java.lang.IllegalStateException", new ThrowingRunnable() {
            public void run() {
                new VertexArray(2, 3, 1).get(0, 1, new short[3]);
            }
        });
    }

    private static void testTriangleStripArrayBoundaries() {
        expectThrows("TriangleStripArray first strip null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(0, null);
            }
        });
        expectThrows("TriangleStripArray first strip empty", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(0, new int[0]);
            }
        });
        expectThrows("TriangleStripArray first strip too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(0, new int[] { 2 });
            }
        });
        expectThrows("TriangleStripArray first index negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(-1, new int[] { 3 });
            }
        });
        expectThrows("TriangleStripArray indexed indices null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(null, new int[] { 3 });
            }
        });
        expectThrows("TriangleStripArray indexed strip null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(new int[] { 0, 1, 2 }, null);
            }
        });
        expectThrows("TriangleStripArray indexed indices too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(new int[] { 0, 1 }, new int[] { 3 });
            }
        });
        expectThrows("TriangleStripArray indexed negative index", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new TriangleStripArray(new int[] { 0, -1, 2 }, new int[] { 3 });
            }
        });
    }

    private static void testVertexBufferBoundaries() {
        expectThrows("VertexBuffer.setPositions wrong components", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().setPositions(new VertexArray(2, 2, 1), 1.0f, null);
            }
        });
        expectThrows("VertexBuffer.setPositions incompatible count", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                VertexBuffer buffer = new VertexBuffer();
                buffer.setPositions(new VertexArray(2, 3, 1), 1.0f, null);
                buffer.setColors(new VertexArray(3, 3, 1));
            }
        });
        expectThrows("VertexBuffer.setTexCoords index negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().setTexCoords(-1, null, 1.0f, null);
            }
        });
        expectThrows("VertexBuffer.setTexCoords index high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().setTexCoords(8, null, 1.0f, null);
            }
        });
        expectThrows("VertexBuffer.getTexCoords index high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().getTexCoords(8, null);
            }
        });
        expectThrows("VertexBuffer.getPositions scaleBias too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().getPositions(new float[3]);
            }
        });
        expectNoThrow("VertexBuffer.getTexCoords unset short scaleBias", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().getTexCoords(0, new float[3]);
            }
        });
        expectThrows("VertexBuffer.getTexCoords set scaleBias too short", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                VertexBuffer buffer = new VertexBuffer();
                buffer.setTexCoords(0, new VertexArray(2, 2, 1), 1.0f, null);
                buffer.getTexCoords(0, new float[2]);
            }
        });
        expectThrows("VertexBuffer.setNormals wrong components", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().setNormals(new VertexArray(2, 2, 1));
            }
        });
        expectThrows("VertexBuffer.setColors wrong components", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new VertexBuffer().setColors(new VertexArray(2, 2, 1));
            }
        });
    }

    private static void testLightBoundaries() {
        expectThrows("Light.setMode invalid", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Light().setMode(Light.AMBIENT - 1);
            }
        });
        expectThrows("Light.setSpotAngle negative", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Light().setSpotAngle(-0.1f);
            }
        });
        expectThrows("Light.setSpotExponent too high", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Light().setSpotExponent(128.1f);
            }
        });
        expectThrows("Light.setAttenuation negative", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Light().setAttenuation(1.0f, -0.1f, 0.0f);
            }
        });
        expectThrows("Light.setAttenuation all zero", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Light().setAttenuation(0.0f, 0.0f, 0.0f);
            }
        });
    }

    private static void testNodeAlignmentBoundaries() {
        expectThrows("Node.setAlphaFactor negative", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Group().setAlphaFactor(-0.01f);
            }
        });
        expectThrows("Node.setAlphaFactor above one", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Group().setAlphaFactor(1.01f);
            }
        });
        expectThrows("Node.setAlignment invalid target", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Group().setAlignment(null, Node.NONE - 1, null, Node.NONE);
            }
        });
        expectThrows("Node.setAlignment self z ref", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                Group group = new Group();
                group.setAlignment(group, Node.Z_AXIS, null, Node.NONE);
            }
        });
        expectThrows("Node.setAlignment same ref target", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                Group ref = new Group();
                new Group().setAlignment(ref, Node.Z_AXIS, ref, Node.Z_AXIS);
            }
        });
        expectNoThrow("Node.setAlignment same ref different target", new ThrowingRunnable() {
            public void run() {
                Group ref = new Group();
                new Group().setAlignment(ref, Node.Z_AXIS, ref, Node.Y_AXIS);
            }
        });
        expectThrows("Node.getAlignmentReference invalid axis", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Group().getAlignmentReference(Node.X_AXIS);
            }
        });
        expectThrows("Node.getAlignmentTarget invalid axis", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Group().getAlignmentTarget(Node.X_AXIS);
            }
        });
        expectThrows("Node.align reference graph mismatch", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group child = new Group();
                root.addChild(child);
                root.align(new Group());
            }
        });
        expectThrows("Node.align null reference self", "java.lang.IllegalStateException", new ThrowingRunnable() {
            public void run() {
                Group child = new Group();
                child.setAlignment(null, Node.Z_AXIS, null, Node.NONE);
                child.align(null);
            }
        });
        expectThrows("Node.align fixed reference graph mismatch", "java.lang.IllegalStateException", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group child = new Group();
                root.addChild(child);
                child.setAlignment(new Group(), Node.Z_AXIS, null, Node.NONE);
                root.align(root);
            }
        });
        expectNoThrow("Node.align NONE preserves orientation", new ThrowingRunnable() {
            public void run() {
                Group node = new Group();
                node.setOrientation(33.0f, 0.0f, 1.0f, 0.0f);
                node.align(null);
                float[] orientation = new float[4];
                node.getOrientation(orientation);
                check("Node.align NONE angle", approx(orientation[0], 33.0f));
                check("Node.align NONE axis y", approx(orientation[2], 1.0f));
            }
        });
        expectNoThrow("Node.align z origin points at reference", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group node = new Group();
                Group target = new Group();
                target.setTranslation(10.0f, 0.0f, 0.0f);
                root.addChild(node);
                root.addChild(target);
                node.setAlignment(target, Node.ORIGIN, null, Node.NONE);
                root.align(root);
                float[] z = transformedAxis(node, 0.0f, 0.0f, 1.0f);
                check("Node.align z origin x", approx(z[0], 1.0f));
                check("Node.align z origin y", approx(z[1], 0.0f));
                check("Node.align z origin z", approx(z[2], 0.0f));
            }
        });
        expectNoThrow("Node.align z and y axes", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group node = new Group();
                Group reference = new Group();
                reference.setOrientation(90.0f, 0.0f, 0.0f, 1.0f);
                root.addChild(node);
                root.addChild(reference);
                node.setAlignment(reference, Node.Z_AXIS, reference, Node.Y_AXIS);
                root.align(root);
                float[] z = transformedAxis(node, 0.0f, 0.0f, 1.0f);
                float[] y = transformedAxis(node, 0.0f, 1.0f, 0.0f);
                check("Node.align z axis unchanged", approx(z[2], 1.0f));
                check("Node.align y axis x", approx(y[0], -1.0f));
                check("Node.align y axis y", approx(y[1], 0.0f));
            }
        });
    }

    private static void testNodeTransformToBoundaries() {
        Group a = new Group();
        Group b = new Group();
        check("Node.getTransformTo unrelated returns false", !a.getTransformTo(b, new Transform()));

        expectThrows("Node.getTransformTo singular inverse", "java.lang.ArithmeticException", new ThrowingRunnable() {
            public void run() {
                Group parent = new Group();
                Group child = new Group();
                child.setScale(0.0f, 1.0f, 1.0f);
                parent.addChild(child);
                parent.getTransformTo(child, new Transform());
            }
        });
    }

    private static void testGroupPickBoundaries() {
        expectThrows("Group.pick zero ray direction", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Group().pick(-1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, null);
            }
        });
        expectThrows("Group.pick camera null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Group().pick(-1, 0.5f, 0.5f, null, null);
            }
        });
        expectNoThrow("Group.pick ray hit mesh", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Mesh mesh = pickTriangleMesh(0.0f);
                root.addChild(mesh);
                RayIntersection ri = new RayIntersection();
                check("Group.pick ray hit result",
                        root.pick(-1, 0.0f, 0.0f, -2.0f, 0.0f, 0.0f, 4.0f, ri));
                check("Group.pick ray hit intersected", ri.getIntersected() == mesh);
                check("Group.pick ray hit distance", approx(ri.getDistance(), 0.5f));
                expectEquals("Group.pick ray hit submesh", 0, ri.getSubmeshIndex());
                float[] ray = new float[6];
                ri.getRay(ray);
                check("Group.pick ray stored origin z", approx(ray[2], -2.0f));
                check("Group.pick ray stored direction z", approx(ray[5], 4.0f));
            }
        });
        expectNoThrow("Group.pick ray miss preserves intersection", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Mesh mesh = pickTriangleMesh(0.0f);
                root.addChild(mesh);
                RayIntersection ri = new RayIntersection();
                check("Group.pick initial hit",
                        root.pick(-1, 0.0f, 0.0f, -2.0f, 0.0f, 0.0f, 4.0f, ri));
                check("Group.pick miss result",
                        !root.pick(-1, 5.0f, 5.0f, -2.0f, 0.0f, 0.0f, 4.0f, ri));
                check("Group.pick miss leaves ri", ri.getIntersected() == mesh);
            }
        });
        expectNoThrow("Group.pick nearest mesh wins", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Mesh far = pickTriangleMesh(1.0f);
                Mesh near = pickTriangleMesh(0.0f);
                root.addChild(far);
                root.addChild(near);
                RayIntersection ri = new RayIntersection();
                check("Group.pick nearest hit",
                        root.pick(-1, 0.0f, 0.0f, -2.0f, 0.0f, 0.0f, 4.0f, ri));
                check("Group.pick nearest intersected", ri.getIntersected() == near);
            }
        });
        expectNoThrow("Group.pick scope filtering", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Mesh mesh = pickTriangleMesh(0.0f);
                mesh.setScope(0x2);
                root.addChild(mesh);
                check("Group.pick out of scope",
                        !root.pick(0x1, 0.0f, 0.0f, -2.0f, 0.0f, 0.0f, 4.0f, null));
                check("Group.pick in scope",
                        root.pick(0x2, 0.0f, 0.0f, -2.0f, 0.0f, 0.0f, 4.0f, null));
            }
        });
        expectNoThrow("Group.pick pickability filtering", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Group child = new Group();
                Mesh mesh = pickTriangleMesh(0.0f);
                child.addChild(mesh);
                root.addChild(child);
                child.setPickingEnable(false);
                check("Group.pick disabled parent",
                        !root.pick(-1, 0.0f, 0.0f, -2.0f, 0.0f, 0.0f, 4.0f, null));
                child.setPickingEnable(true);
                mesh.setPickingEnable(false);
                check("Group.pick disabled mesh",
                        !root.pick(-1, 0.0f, 0.0f, -2.0f, 0.0f, 0.0f, 4.0f, null));
            }
        });
        expectNoThrow("Group.pick camera hit mesh", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                Camera camera = new Camera();
                camera.setPerspective(60.0f, 1.0f, 1.0f, 10.0f);
                camera.setTranslation(0.0f, 0.0f, 2.0f);
                Mesh mesh = pickTriangleMesh(0.0f);
                root.addChild(camera);
                root.addChild(mesh);
                RayIntersection ri = new RayIntersection();
                check("Group.pick camera hit", root.pick(-1, 0.5f, 0.5f, camera, ri));
                check("Group.pick camera intersected", ri.getIntersected() == mesh);
                float[] ray = new float[6];
                ri.getRay(ray);
                check("Group.pick camera ray origin near plane", approx(ray[2], 1.0f));
                check("Group.pick camera ray direction z", ray[5] < 0.0f);
            }
        });
        expectThrows("Group.pick camera unrelated", "java.lang.IllegalStateException", new ThrowingRunnable() {
            public void run() {
                Group root = new Group();
                root.pick(-1, 0.5f, 0.5f, new Camera(), null);
            }
        });
    }

    private static void testGroupBoundaries() {
        expectThrows("Group.addChild null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                new Group().addChild(null);
            }
        });
        expectThrows("Group.addChild self", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                Group group = new Group();
                group.addChild(group);
            }
        });
        expectThrows("Group.addChild world", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                new Group().addChild(new World());
            }
        });
        expectNoThrow("Group.addChild duplicate same parent", new ThrowingRunnable() {
            public void run() {
                Group parent = new Group();
                Group child = new Group();
                parent.addChild(child);
                parent.addChild(child);
                expectEquals("Group.addChild duplicate count", 1, parent.getChildCount());
            }
        });
        expectThrows("Group.addChild already parented elsewhere", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                Group first = new Group();
                Group second = new Group();
                Group child = new Group();
                first.addChild(child);
                second.addChild(child);
            }
        });
        expectNoThrow("Group.removeChild null", new ThrowingRunnable() {
            public void run() {
                new Group().removeChild(null);
            }
        });
        expectThrows("Group.getChild negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Group().getChild(-1);
            }
        });
        expectThrows("Group.getChild count", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                new Group().getChild(0);
            }
        });
    }

    private static void testGraphics3DBoundaries() {
        final Graphics3D g3d = Graphics3D.getInstance();
        final Graphics graphics = Image.createImage(32, 32).getGraphics();
        final Light light = new Light();
        final Transform singular = new Transform();
        singular.set(new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        });

        expectNoThrow("Graphics3D.releaseTarget without target", new ThrowingRunnable() {
            public void run() {
                g3d.releaseTarget();
            }
        });
        expectThrows("Graphics3D.bindTarget null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(null);
            }
        });
        expectThrows("Graphics3D.bindTarget invalid target", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(new Object());
            }
        });
        expectThrows("Graphics3D.bindTarget invalid hints", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(graphics, true, 1);
            }
        });
        expectNoThrow("Graphics3D.bindTarget graphics", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(graphics);
            }
        });
        expectThrows("Graphics3D.bindTarget while bound", "java.lang.IllegalStateException", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(graphics);
            }
        });
        g3d.releaseTarget();
        expectNoThrow("Graphics3D.bindTarget mutable RGB Image2D", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(new Image2D(Image2D.RGB, 4, 4));
                g3d.releaseTarget();
            }
        });
        expectNoThrow("Graphics3D.bindTarget mutable RGBA Image2D", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(new Image2D(Image2D.RGBA, 4, 4));
                g3d.releaseTarget();
            }
        });
        expectThrows("Graphics3D.bindTarget immutable Image2D", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(image(Image2D.RGB, 4, 4));
            }
        });
        expectThrows("Graphics3D.bindTarget ALPHA Image2D", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.bindTarget(new Image2D(Image2D.ALPHA, 4, 4));
            }
        });
        g3d.bindTarget(graphics);
        expectNoThrow("Graphics3D.clear null background", new ThrowingRunnable() {
            public void run() {
                g3d.clear(null);
            }
        });
        expectThrows("Graphics3D.clear RGBA background image on Graphics", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                Background background = new Background();
                background.setImage(image(Image2D.RGBA, 2, 2));
                g3d.clear(background);
            }
        });
        expectThrows("Graphics3D.setViewport zero width", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.setViewport(0, 0, 0, 1);
            }
        });
        expectThrows("Graphics3D.setViewport negative height", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.setViewport(0, 0, 1, -1);
            }
        });
        expectThrows("Graphics3D.setViewport width too large", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.setViewport(0, 0, 4097, 1);
            }
        });
        expectNoThrow("Graphics3D.setDepthRange reversed", new ThrowingRunnable() {
            public void run() {
                g3d.setDepthRange(1.0f, 0.0f);
            }
        });
        expectThrows("Graphics3D.setDepthRange outside", "java.lang.IllegalArgumentException", new ThrowingRunnable() {
            public void run() {
                g3d.setDepthRange(-0.01f, 1.0f);
            }
        });
        expectThrows("Graphics3D.setCamera singular", "java.lang.ArithmeticException", new ThrowingRunnable() {
            public void run() {
                g3d.setCamera(new Camera(), singular);
            }
        });
        expectThrows("Graphics3D.render world null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                g3d.render((World) null);
            }
        });
        expectThrows("Graphics3D.render node null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                g3d.render((Node) null, null);
            }
        });
        expectThrows("Graphics3D.render vertices null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                g3d.render(null, new TriangleStripArray(0, new int[] { 3 }), new javax.microedition.m3g.Appearance(), null);
            }
        });
        expectThrows("Graphics3D.render triangles null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                g3d.render(new VertexBuffer(), null, new javax.microedition.m3g.Appearance(), null);
            }
        });
        expectThrows("Graphics3D.render appearance null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                g3d.render(new VertexBuffer(), new TriangleStripArray(0, new int[] { 3 }), null, null);
            }
        });
        expectThrows("Graphics3D.addLight null", "java.lang.NullPointerException", new ThrowingRunnable() {
            public void run() {
                g3d.addLight(null, null);
            }
        });
        final int lightIndex = g3d.addLight(light, null);
        expectNoThrow("Graphics3D.setLight null removes", new ThrowingRunnable() {
            public void run() {
                g3d.setLight(lightIndex, null, null);
            }
        });
        check("Graphics3D.getLight removed returns null", g3d.getLight(lightIndex, null) == null);
        expectThrows("Graphics3D.getLight index high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                g3d.getLight(g3d.getLightCount(), null);
            }
        });
        expectThrows("Graphics3D.getLight index negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                g3d.getLight(-1, null);
            }
        });
        expectThrows("Graphics3D.setLight index high", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                g3d.setLight(g3d.getLightCount(), light, null);
            }
        });
        expectThrows("Graphics3D.setLight index negative", "java.lang.IndexOutOfBoundsException", new ThrowingRunnable() {
            public void run() {
                g3d.setLight(-1, light, null);
            }
        });
        g3d.releaseTarget();
        g3d.resetLights();
    }

    private static Image2D image(int format, int width, int height) {
        return new Image2D(format, width, height, new byte[width * height * bytesPerPixel(format)]);
    }

    private static VertexBuffer vertexBuffer() {
        VertexArray positions = new VertexArray(3, 3, 1);
        positions.set(0, 3, new byte[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0
        });
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(positions, 1.0f, null);
        return buffer;
    }

    private static Mesh pickTriangleMesh(float z) {
        VertexArray positions = new VertexArray(3, 3, 1);
        positions.set(0, 3, new byte[] {
            -1, -1, 0,
             1, -1, 0,
             0,  1, 0
        });
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(positions, 1.0f, new float[] {0.0f, 0.0f, z});
        PolygonMode polygonMode = new PolygonMode();
        polygonMode.setCulling(PolygonMode.CULL_NONE);
        Appearance appearance = new Appearance();
        appearance.setPolygonMode(polygonMode);
        return new Mesh(buffer, new TriangleStripArray(0, new int[] {3}), appearance);
    }

    private static int bytesPerPixel(int format) {
        switch (format) {
            case Image2D.ALPHA:
            case Image2D.LUMINANCE:
                return 1;
            case Image2D.LUMINANCE_ALPHA:
                return 2;
            case Image2D.RGB:
                return 3;
            case Image2D.RGBA:
                return 4;
            default:
                throw new IllegalArgumentException("format");
        }
    }

    private static float[] transformedAxis(Node node, float x, float y, float z) {
        Transform transform = new Transform();
        node.getCompositeTransform(transform);
        float[] vector = new float[] { x, y, z, 0.0f };
        transform.transform(vector);
        return vector;
    }

    private static boolean approx(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }

    private static void expectNoThrow(String name, ThrowingRunnable runnable) {
        try {
            runnable.run();
            pass(name);
        } catch (Throwable t) {
            fail(name + " unexpected " + t.getClass().getName());
        }
    }

    private static void expectThrows(String name, String expectedClassName, ThrowingRunnable runnable) {
        try {
            runnable.run();
            fail(name + " expected " + expectedClassName + " got none");
        } catch (Throwable t) {
            String actualClassName = t.getClass().getName();
            if (expectedClassName.equals(actualClassName)) {
                pass(name);
            } else {
                fail(name + " expected " + expectedClassName + " got " + actualClassName);
            }
        }
    }

    private static void expectEquals(String name, int expected, int actual) {
        check(name + " expected " + expected + " actual " + actual, expected == actual);
    }

    private static void pass(String name) {
        passed++;
        println("PASS: " + name);
    }

    private static void fail(String name) {
        failed++;
        failures.append(failed).append(". ").append(name).append('\n');
        println("FAIL: " + name);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            fail(name);
        }
    }

    private static void println(String line) {
        System.out.println(line);
        log.append(line).append('\n');
    }

    public static class CustomGroup extends Group {
    }
}
