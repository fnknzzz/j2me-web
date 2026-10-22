import com.mascotcapsule.micro3d.v3.ActionTable;
import com.mascotcapsule.micro3d.v3.AffineTrans;
import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.Figure;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import com.mascotcapsule.micro3d.v3.Light;
import com.mascotcapsule.micro3d.v3.Texture;
import com.mascotcapsule.micro3d.v3.Util3D;
import com.mascotcapsule.micro3d.v3.Vector3D;
import java.io.IOException;

public class MascotApiBehaviorTest {
    private static int passed;
    private static int failed;

    private interface Op {
        void run() throws Throwable;
    }

    public static void main(String[] args) {
        System.out.println("=== Mascot API Behavior Test ===");
        testVectorAndAffine();
        testLayoutLightAndEffect();
        testResourceValidation();
        testCommandListContract();
        testGraphicsWithoutTarget();
        System.out.println("PASSED: " + passed + " FAILED: " + failed);
        System.out.println("=== Tests Complete ===");
    }

    private static void testVectorAndAffine() {
        Vector3D first = new Vector3D(2, 3, 4);
        Vector3D second = new Vector3D(5, 6, 7);
        check("Vector3D instance dot product", first.innerProduct(second) == 56);
        check("Vector3D static dot product", Vector3D.innerProduct(first, second) == 56);
        Vector3D cross = Vector3D.outerProduct(first, second);
        check("Vector3D static outer product", cross.x == -3 && cross.y == 6 && cross.z == -3);
        first.outerProduct(new Vector3D(0, 1, 0));
        check("Vector3D instance outer product", first.x == -4 && first.y == 0 && first.z == 2);

        Vector3D unit = new Vector3D();
        unit.unit();
        check("Vector3D zero unit defaults forward", unit.x == 0 && unit.y == 0 && unit.z == 4096);
        expectThrows("Vector3D null copy", "java.lang.NullPointerException", new Op() {
            public void run() {
                new Vector3D((Vector3D) null);
            }
        });

        int[] values = identity();
        values[3] = 12;
        values[7] = 34;
        AffineTrans fromArray = new AffineTrans(values);
        int[] output = new int[14];
        fromArray.get(output, 1);
        check("AffineTrans array get/set", output[4] == 12 && output[8] == 34);

        AffineTrans fromMatrix = new AffineTrans(new int[][] {
            { 4096, 0, 0, 56 }, { 0, 4096, 0, 78 }, { 0, 0, 4096, 0 }
        });
        check("AffineTrans matrix constructor", fromMatrix.m03 == 56 && fromMatrix.m13 == 78);
        AffineTrans rotation = new AffineTrans();
        rotation.setRotationZ(1024);
        Vector3D rotated = rotation.transform(new Vector3D(4096, 0, 0));
        check("AffineTrans rotation", Math.abs(rotated.x) < 4 && Math.abs(rotated.y - 4096) < 4);
        expectThrows("AffineTrans invalid offset", "java.lang.IllegalArgumentException", new Op() {
            public void run() {
                new AffineTrans(new int[12], 1);
            }
        });
        check("Util3D trig and sqrt", Util3D.sin(512) == 2896 && Util3D.cos(0) == 4096
                && Util3D.sqrt(81) == 9);
    }

    private static void testLayoutLightAndEffect() {
        AffineTrans first = new AffineTrans(identity());
        AffineTrans second = new AffineTrans(identity());
        second.m03 = 77;
        FigureLayout layout = new FigureLayout(first, 320, 640, 7, 9);
        layout.setAffineTransArray(new AffineTrans[] { first, second });
        layout.selectAffineTrans(1);
        layout.setParallelSize(111, 222);
        layout.setCenter(12, 34);
        layout.setPerspective(64, 2048, 512);
        check("FigureLayout aliases", layout.getScaleX() == 320 && layout.getScaleY() == 640
                && layout.getParallelWidth() == 111 && layout.getParallelHeight() == 222
                && layout.getCenterX() == 12 && layout.getCenterY() == 34
                && layout.getAffineTrans().m03 == 77);
        layout.setPerspective(64, 2048, 280, 320);
        expectThrows("FigureLayout invalid FOV perspective", "java.lang.IllegalArgumentException", new Op() {
            public void run() {
                new FigureLayout().setPerspective(64, 64, 512);
            }
        });
        expectThrows("FigureLayout invalid WH perspective", "java.lang.IllegalArgumentException", new Op() {
            public void run() {
                new FigureLayout().setPerspective(64, 2048, -1, 320);
            }
        });

        Light light = new Light(new Vector3D(1, 2, 3), 400, 500);
        light.setParallelLightIntensity(600);
        light.setAmbientIntensity(700);
        light.setParallelLightDirection(new Vector3D(8, 9, 10));
        check("Light aliases", light.getDirIntensity() == 600
                && light.getParallelLightIntensity() == 600
                && light.getAmbIntensity() == 700 && light.getAmbientIntensity() == 700
                && light.getDirection().z == 10);

        Effect3D effect = new Effect3D();
        effect.setShadingType(Effect3D.TOON_SHADING);
        effect.setThreshold(30, 200, 50);
        effect.setSemiTransparentEnabled(false);
        effect.setSphereMap(null);
        check("Effect3D aliases", effect.getShading() == Effect3D.TOON_SHADING
                && effect.getToonThreshold() == 30 && effect.getToonHigh() == 200
                && effect.getToonLow() == 50 && !effect.isTransparency());
        expectThrows("Effect3D invalid shading", "java.lang.IllegalArgumentException", new Op() {
            public void run() {
                new Effect3D().setShading(9);
            }
        });
    }

    private static void testResourceValidation() {
        final Texture modelTexture = new Texture(bmp(), true);
        final Texture sphereTexture = new Texture(bmp(), false);
        Effect3D effect = new Effect3D();
        effect.setSphereMap(sphereTexture);
        check("Texture model and sphere-map constructors", effect.getSphereMap() == sphereTexture);
        expectThrows("Effect3D rejects model texture as sphere map", "java.lang.IllegalArgumentException", new Op() {
            public void run() {
                new Effect3D().setSphereMap(modelTexture);
            }
        });
        modelTexture.dispose();
        sphereTexture.dispose();
        pass("Texture dispose");
        expectThrows("Texture null bytes", "java.lang.NullPointerException", new Op() {
            public void run() {
                new Texture((byte[]) null, true);
            }
        });
        expectThrows("Texture invalid bytes", "java.lang.RuntimeException", new Op() {
            public void run() {
                new Texture(new byte[] { 0, 1 }, true);
            }
        });
        expectThrows("Figure invalid bytes", "java.lang.RuntimeException", new Op() {
            public void run() {
                new Figure(new byte[0]);
            }
        });
        expectThrows("ActionTable invalid bytes", "java.lang.RuntimeException", new Op() {
            public void run() {
                new ActionTable(new byte[0]);
            }
        });
        expectThrows("Figure missing resource", "java.io.IOException", new Op() {
            public void run() throws IOException {
                new Figure("/missing.mbac");
            }
        });
        expectThrows("ActionTable missing resource", "java.io.IOException", new Op() {
            public void run() throws IOException {
                new ActionTable("/missing.mtra");
            }
        });
    }

    private static void testCommandListContract() {
        check("Graphics3D command constants", Graphics3D.COMMAND_LIST_VERSION_1_0 == 0xFE000001
                && Graphics3D.COMMAND_END == 0x80000000
                && Graphics3D.COMMAND_CLIP == 0x84000000
                && Graphics3D.COMMAND_CENTER == 0x85000000
                && Graphics3D.COMMAND_PERSPECTIVE_FOV == 0x92000000
                && Graphics3D.COMMAND_PERSPECTIVE_WH == 0x93000000);
        check("Graphics3D command flag composition",
                (Graphics3D.COMMAND_AFFINE_INDEX | 3) == 0x87000003
                && (Graphics3D.COMMAND_ATTRIBUTE | Graphics3D.ENV_ATTR_LIGHTING
                        | Graphics3D.ENV_ATTR_TOON_SHADING) == 0x83000005
                && (Graphics3D.PRIMITVE_TRIANGLES | (2 << 16)
                        | Graphics3D.PDATA_COLOR_PER_COMMAND) == 0x03020400);
        check("Graphics3D point sprite flags",
                Graphics3D.PRIMITVE_POINT_SPRITES == 0x05000000
                && Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD == 0x1000
                && Graphics3D.POINT_SPRITE_PIXEL_SIZE == 1
                && Graphics3D.POINT_SPRITE_NO_PERS == 2);
    }

    private static void testGraphicsWithoutTarget() {
        Graphics3D graphics = new Graphics3D();
        graphics.drawCommandList((Texture[]) null, 0, 0, null, null, null);
        graphics.renderPrimitives(null, 0, 0, null, null, -1, 0, null, null, null, null);
        graphics.dispose();
        pass("Graphics3D unbound render entry points are no-op");
        expectThrows("Graphics3D flush without target", "java.lang.IllegalStateException", new Op() {
            public void run() {
                new Graphics3D().flush();
            }
        });
    }

    private static int[] identity() {
        return new int[] {
            4096, 0, 0, 0,
            0, 4096, 0, 0,
            0, 0, 4096, 0
        };
    }

    private static byte[] bmp() {
        byte[] data = new byte[1082];
        data[0] = 'B';
        data[1] = 'M';
        data[2] = (byte) 0x3A;
        data[3] = 0x04;
        data[10] = 0x36;
        data[11] = 0x04;
        data[14] = 40;
        data[18] = 1;
        data[22] = 1;
        data[26] = 1;
        data[28] = 8;
        data[34] = 4;
        data[54] = 48;
        data[55] = (byte) 160;
        data[56] = (byte) 240;
        return data;
    }

    private static void expectThrows(String name, String expected, Op operation) {
        try {
            operation.run();
            fail(name + " expected " + expected + " got none");
        } catch (Throwable error) {
            if (expected.equals(error.getClass().getName())) {
                pass(name);
            } else {
                fail(name + " expected " + expected + " got " + error.getClass().getName());
            }
        }
    }

    private static void check(String name, boolean value) {
        if (value) {
            pass(name);
        } else {
            fail(name);
        }
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void fail(String name) {
        failed++;
        System.out.println("FAIL: " + name);
    }
}
