package javax.microedition.m3g;

import java.lang.ref.WeakReference;
import java.util.Hashtable;

class Interface {
    private static final int ANIMATION_CONTROLLER = 1;
    private static final int ANIMATION_TRACK = 2;
    private static final int APPEARANCE = 3;
    private static final int BACKGROUND = 4;
    private static final int CAMERA = 5;
    private static final int COMPOSITING_MODE = 6;
    private static final int FOG = 7;
    private static final int GROUP = 8;
    private static final int IMAGE_2D = 9;
    private static final int INDEX_BUFFER = 10;
    private static final int KEYFRAME_SEQUENCE = 11;
    private static final int LIGHT = 12;
    private static final int MATERIAL = 14;
    private static final int MESH = 15;
    private static final int MORPHING_MESH = 16;
    private static final int POLYGON_MODE = 19;
    private static final int SKINNED_MESH = 21;
    private static final int SPRITE_3D = 22;
    private static final int TEXTURE_2D = 23;
    private static final int VERTEX_ARRAY = 26;
    private static final int VERTEX_BUFFER = 27;
    private static final int WORLD = 28;

    private static Interface instance = null;
    int handle;
    private final Hashtable liveObjects = new Hashtable();

    private Interface() {
        handle = _ctor();
    }

    static final Interface getInstance() {
        if (instance == null) {
            instance = new Interface();
        }
        return instance;
    }

    static final int getHandle() {
        return getInstance().handle;
    }

    static final void register(Object3D obj) {
        if (obj != null && obj.handle != 0) {
            Interface self = getInstance();
            self.liveObjects.put(new Integer(obj.handle), new WeakReference(obj));
        }
    }

    static final void register(Loader obj) {
    }

    static final Object3D findObject(int handle) {
        Interface self = getInstance();
        Integer key = new Integer(handle);
        WeakReference reference =
                (WeakReference) self.liveObjects.get(key);
        if (reference == null) {
            return null;
        }
        Object3D obj = (Object3D) reference.get();
        if (obj == null) {
            self.liveObjects.remove(key);
        }
        return obj;
    }

    static final Object3D getObjectInstance(int handle) {
        if (handle == 0) {
            return null;
        }

        Object3D obj = findObject(handle);
        if (obj != null) {
            return obj;
        }

        switch (_getClassID(handle)) {
        case ANIMATION_CONTROLLER:
            return new AnimationController(handle);
        case ANIMATION_TRACK:
            return new AnimationTrack(handle);
        case APPEARANCE:
            return new Appearance(handle);
        case BACKGROUND:
            return new Background(handle);
        case CAMERA:
            return new Camera(handle);
        case COMPOSITING_MODE:
            return new CompositingMode(handle);
        case FOG:
            return new Fog(handle);
        case GROUP:
            return new Group(handle);
        case IMAGE_2D:
            return new Image2D(handle);
        case INDEX_BUFFER:
            return new TriangleStripArray(handle);
        case KEYFRAME_SEQUENCE:
            return new KeyframeSequence(handle);
        case LIGHT:
            return new Light(handle);
        case MATERIAL:
            return new Material(handle);
        case MESH:
            return new Mesh(handle);
        case MORPHING_MESH:
            return new MorphingMesh(handle);
        case POLYGON_MODE:
            return new PolygonMode(handle);
        case SKINNED_MESH:
            return new SkinnedMesh(handle);
        case SPRITE_3D:
            return new Sprite3D(handle);
        case TEXTURE_2D:
            return new Texture2D(handle);
        case VERTEX_ARRAY:
            return new VertexArray(handle);
        case VERTEX_BUFFER:
            return new VertexBuffer(handle);
        case WORLD:
            return new World(handle);
        default:
            return null;
        }
    }

    void integrityCheck() {
    }

    void signalShutdown() {
    }

    private native static int _ctor();
    private native static int _getClassID(int handle);
}
