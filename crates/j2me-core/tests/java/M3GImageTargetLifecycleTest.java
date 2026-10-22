import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;

public class M3GImageTargetLifecycleTest {
    public static void main(String[] args) {
        verifyTarget(Image2D.RGB, new byte[] {
                0x11, 0x22, 0x33,
                0x55, 0x66, 0x77
        });
        verifyTarget(Image2D.RGBA, new byte[] {
                0x11, 0x22, 0x33, 0x44,
                0x55, 0x66, 0x77, (byte) 0x88
        });
        System.out.println("PASS: Image2D target lifecycle");
    }

    private static void verifyTarget(int format, byte[] replacementPixels) {
        Image2D image = new Image2D(format, 2, 1);
        Graphics3D graphics3D = Graphics3D.getInstance();

        graphics3D.bindTarget(image);
        graphics3D.releaseTarget();

        if (!image.isMutable()) {
            throw new RuntimeException("Image2D target became immutable");
        }
        image.set(0, 0, 2, 1, replacementPixels);
    }
}
