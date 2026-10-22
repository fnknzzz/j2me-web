import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Node;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;

public class M3GBridgeRegressionTest {
    private static int failed;

    public static void main(String[] args) {
        checkImageMutability();
        checkBackendCapabilities();
        checkPerspectiveCorrection();
        checkReferences();
        checkDuplicate();
        checkTransformTable();
        checkTriangleStripIndices();
        checkVertexBufferCountAndTexCoords();
        checkNodeStateAndLinks();
        System.out.println("FAILED: " + failed);
    }

    private static void checkImageMutability() {
        Image2D staticImage = new Image2D(Image2D.RGB, 1, 1, new byte[] { 1, 2, 3 });
        Image2D dynamicImage = new Image2D(Image2D.RGB, 1, 1);
        check("Image2D pixel constructor commits immutable", !staticImage.isMutable());
        check("Image2D size constructor stays mutable", dynamicImage.isMutable());
    }

    private static void checkBackendCapabilities() {
        java.util.Hashtable properties = Graphics3D.getProperties();
        check("Graphics3D reports WebGPU MSAA unsupported",
                !((Boolean) properties.get("supportAntialiasing")).booleanValue());
        check("Graphics3D reports mipmapping supported",
                ((Boolean) properties.get("supportMipmapping")).booleanValue());
        check("Graphics3D reports perspective hint supported",
                ((Boolean) properties.get("supportPerspectiveCorrection")).booleanValue());
        check("Graphics3D reports backend texture dimension",
                ((Integer) properties.get("maxTextureDimension")).intValue() == 1024);
        check("Graphics3D reports backend texture unit count",
                ((Integer) properties.get("numTextureUnits")).intValue() == 2);
    }

    private static void checkPerspectiveCorrection() {
        PolygonMode polygonMode = new PolygonMode();
        check("PolygonMode perspective correction defaults off",
                !polygonMode.isPerspectiveCorrectionEnabled());
        polygonMode.setPerspectiveCorrectionEnable(true);
        check("PolygonMode enables perspective correction",
                polygonMode.isPerspectiveCorrectionEnabled());
        polygonMode.setPerspectiveCorrectionEnable(false);
        check("PolygonMode disables perspective correction",
                !polygonMode.isPerspectiveCorrectionEnabled());
    }

    private static void checkReferences() {
        Appearance appearance = new Appearance();
        Material material = new Material();
        appearance.setMaterial(material);
        Object3D[] references = new Object3D[1];
        check("Object3D.getReferences count", appearance.getReferences(references) == 1);
        check("Object3D.getReferences fills caller array", references[0] == material);
    }

    private static void checkDuplicate() {
        Group root = new Group();
        Group child = new Group();
        root.addChild(child);
        Group copy = (Group) root.duplicate();
        check("Object3D.duplicate returns instance", copy != null && copy != root);
        check("Object3D.duplicate copies child", copy.getChildCount() == 1);
        check("Object3D.duplicate fixes Java parent link", copy.getChild(0).getParent() == copy);
    }

    private static void checkTransformTable() {
        Transform transform = new Transform();
        transform.postTranslate(2.0f, 3.0f, 4.0f);
        float[] vectors = { 1.0f, 2.0f, 3.0f, 1.0f };
        transform.transform(vectors);
        check("Transform.transform x", close(vectors[0], 3.0f));
        check("Transform.transform y", close(vectors[1], 5.0f));
        check("Transform.transform z", close(vectors[2], 7.0f));
        check("Transform.transform w", close(vectors[3], 1.0f));
    }

    private static void checkTriangleStripIndices() {
        TriangleStripArray explicit = new TriangleStripArray(
                new int[] { 7, 2, 9, 4, 6, 8 },
                new int[] { 3, 3 });
        int[] explicitIndices = new int[6];
        explicit.getIndices(explicitIndices);
        check("TriangleStripArray explicit count", explicit.getIndexCount() == 6);
        check("TriangleStripArray explicit values",
                explicitIndices[0] == 7 && explicitIndices[1] == 2
                && explicitIndices[2] == 9 && explicitIndices[3] == 4
                && explicitIndices[4] == 6 && explicitIndices[5] == 8);

        TriangleStripArray implicit = new TriangleStripArray(5, new int[] { 3, 4 });
        int[] implicitIndices = new int[7];
        implicit.getIndices(implicitIndices);
        check("TriangleStripArray implicit count", implicit.getIndexCount() == 7);
        for (int i = 0; i < implicitIndices.length; i++) {
            check("TriangleStripArray implicit value " + i, implicitIndices[i] == 5 + i);
        }
    }

    private static void checkVertexBufferCountAndTexCoords() {
        VertexArray positions = new VertexArray(3, 3, 1);
        positions.set(0, 3, new byte[] {
                0, 0, 0,
                1, 0, 0,
                0, 1, 0
        });
        VertexArray texCoords = new VertexArray(3, 3, 1);
        texCoords.set(0, 3, new byte[] {
                0, 0, 0,
                1, 0, 0,
                0, 1, 0
        });
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(positions, 2.0f, new float[] { 1.0f, 2.0f, 3.0f });
        buffer.setTexCoords(0, texCoords, 0.5f, new float[] { 4.0f, 5.0f, 6.0f });
        float[] scaleBias = new float[4];
        check("VertexBuffer.getVertexCount", buffer.getVertexCount() == 3);
        check("VertexBuffer.getTexCoords returns source", buffer.getTexCoords(0, scaleBias) == texCoords);
        check("VertexBuffer.getTexCoords scale", close(scaleBias[0], 0.5f));
        check("VertexBuffer.getTexCoords bias",
                close(scaleBias[1], 4.0f) && close(scaleBias[2], 5.0f)
                && close(scaleBias[3], 6.0f));
    }

    private static void checkNodeStateAndLinks() {
        Group root = new Group();
        Group child = new Group();
        root.addChild(child);
        child.setAlphaFactor(0.25f);
        check("Node alpha factor", close(child.getAlphaFactor(), 0.25f));
        check("Node parent native bridge", child.getParent() == root);
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.0001f;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }
}
