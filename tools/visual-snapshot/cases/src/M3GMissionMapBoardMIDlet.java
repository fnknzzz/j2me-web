import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Loader;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.Transform;
import javax.microedition.midlet.MIDlet;

public class M3GMissionMapBoardMIDlet extends MIDlet {
    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Board());
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}

    private static final class Board extends Canvas {
        private final Mesh map;
        private final Camera camera = new Camera();
        private final Light light = new Light();

        Board() {
            Object3D[] mapObjects = loadMapObjects();
            map = findMesh(mapObjects, 5);
            light.setColor(0xFFFFFF);
            light.setMode(Light.AMBIENT);
            light.setIntensity(1.05f);
        }

        protected void paint(Graphics g) {
            if (map == null) {
                return;
            }

            Graphics3D g3d = Graphics3D.getInstance();
            try {
                g3d.bindTarget(g);
                g3d.setViewport(0, 105, 240, 140);

                Background background = new Background();
                background.setColor(0xff000000);
                g3d.clear(background);

                camera.setPerspective(70.0f, 1.7142857f, 1.0f, 10000.0f);
                Transform cameraTransform = new Transform();
                cameraTransform.postTranslate(-9.0f, 90.0f, 180.0f);
                g3d.setCamera(camera, cameraTransform);
                g3d.resetLights();
                g3d.addLight(light, null);

                Transform mapTransform = new Transform();
                mapTransform.postScale(0.67f, 0.67f, 0.67f);
                g3d.render(map, mapTransform);
            } finally {
                try {
                    g3d.releaseTarget();
                } catch (Throwable ignored) {}
            }
        }

        private static Object3D[] loadMapObjects() {
            try {
                return Loader.load("/missionmap.m3g");
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object3D find(Object3D[] roots, int userId) {
            if (roots == null || roots.length == 0 || roots[0] == null) {
                return null;
            }
            return roots[0].find(userId);
        }

        private static Mesh findMesh(Object3D[] roots, int userId) {
            Object3D object = find(roots, userId);
            return object instanceof Mesh ? (Mesh) object : null;
        }

    }
}
