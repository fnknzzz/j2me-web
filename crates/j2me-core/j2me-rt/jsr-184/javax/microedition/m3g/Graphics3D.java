package javax.microedition.m3g;

import javax.microedition.lcdui.Graphics;
import java.util.Hashtable;
import java.util.Vector;

public class Graphics3D {
    public static final int ANTIALIAS = 2;
    public static final int DITHER = 4;
    public static final int TRUE_COLOR = 8;
    public static final int OVERWRITE = 16;

    private static final int CAPABILITY_ANTIALIASING = 1;
    private static final int CAPABILITY_TRUE_COLOR = 2;
    private static final int CAPABILITY_DITHERING = 3;
    private static final int CAPABILITY_MIPMAPPING = 4;
    private static final int CAPABILITY_PERSPECTIVE_CORRECTION = 5;
    private static final int CAPABILITY_LOCAL_CAMERA_LIGHTING = 6;
    private static final int CAPABILITY_MAX_LIGHTS = 7;
    private static final int CAPABILITY_MAX_VIEWPORT_WIDTH = 8;
    private static final int CAPABILITY_MAX_VIEWPORT_HEIGHT = 9;
    private static final int CAPABILITY_MAX_TEXTURE_DIMENSION = 10;
    private static final int CAPABILITY_NUM_TEXTURE_UNITS = 11;
    private static final int CAPABILITY_MAX_TRANSFORMS_PER_VERTEX = 12;

    private static Graphics3D s_instance = null;

    int handle;
    private Camera camera = null;
    private Transform cameraTransform = null;
    private Vector lights = new Vector();
    private Vector lightTransforms = new Vector();
    private java.lang.Object currentTarget = null;
    private int offsetX, offsetY, hints = 0;
    private boolean depthEnabled = true;

    public static final Graphics3D getInstance() {
        if (s_instance == null) {
            s_instance = new Graphics3D();
        }
        return s_instance;
    }

    private Graphics3D() {
        handle = _ctor(Interface.getHandle());
        _addRef(handle);
    }

    public void bindTarget(java.lang.Object target) {
        bindTarget(target, true, 0);
    }

    public void bindTarget(java.lang.Object target, boolean depth, int flags) {
        if (currentTarget != null) {
            throw new IllegalStateException();
        }
        if (target == null) {
            throw new NullPointerException();
        }
        if ((flags & ~(ANTIALIAS | DITHER | TRUE_COLOR | OVERWRITE)) != 0) {
            throw new IllegalArgumentException();
        }

        if (target instanceof Graphics) {
            Graphics g = (Graphics) target;
            int clipX = g.getClipX();
            int clipY = g.getClipY();
            int clipW = g.getClipWidth();
            int clipH = g.getClipHeight();
            offsetX = g.getTranslateX();
            offsetY = g.getTranslateY();
            _bindGraphics(handle, 0, clipX + offsetX, clipY + offsetY, clipW, clipH, depth, flags, false);
            currentTarget = target;
            _beginTargetBatch();
        } else if (target instanceof Image2D) {
            Image2D img = (Image2D) target;
            int format = img.getFormat();
            if (!img.isMutable() || (format != Image2D.RGB && format != Image2D.RGBA)) {
                throw new IllegalArgumentException();
            }
            offsetX = offsetY = 0;
            _bindImage(handle, img.handle, depth, flags);
            currentTarget = target;
            _beginTargetBatch();
        } else {
            throw new IllegalArgumentException();
        }

        hints = flags;
        depthEnabled = depth;
    }

    public void releaseTarget() {
        if (currentTarget == null) {
            return;
        }
        if (currentTarget instanceof Graphics) {
            _releaseGraphics(handle, 0, false, false);
        } else if (currentTarget instanceof Image2D) {
            _releaseImage(handle);
        }
        _flushTargetBatch();
        currentTarget = null;
    }

    public void setViewport(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0
                || width > Defs.MAX_VIEWPORT_DIMENSION
                || height > Defs.MAX_VIEWPORT_DIMENSION) {
            throw new IllegalArgumentException();
        }
        _setViewport(handle, x + offsetX, y + offsetY, width, height);
    }

    public void clear(Background background) {
        _clear(handle, background != null ? background.handle : 0);
    }

    public void render(World world) {
        if (world == null) {
            throw new NullPointerException();
        }
        Camera activeCamera = world.getActiveCamera();
        if (activeCamera == null) {
            throw new IllegalStateException();
        }

        Transform activeCameraTransform = new Transform();
        if (!activeCamera.getTransformTo(world, activeCameraTransform)) {
            throw new IllegalStateException();
        }

        clear(world.getBackground());
        setCamera(activeCamera, activeCameraTransform);
        resetLights();
        collectSceneLights(world, new Transform());
        _renderWorld(handle, world.handle);
    }

    public void render(VertexBuffer vertices, IndexBuffer primitives, Appearance appearance, Transform transform) {
        render(vertices, primitives, appearance, transform, -1);
    }

    public void render(VertexBuffer vertices, IndexBuffer primitives, Appearance appearance, Transform transform, int scope) {
        _render(handle,
                vertices.handle,
                primitives.handle,
                appearance.handle,
                transform != null ? transform.matrix : null,
                scope);
    }

    public void render(Node node, Transform transform) {
        _renderNode(handle, node.handle, transform != null ? transform.matrix : null);
    }

    public void setCamera(Camera camera, Transform transform) {
        _setCamera(handle,
                   camera != null ? camera.handle : 0,
                   transform != null ? transform.matrix : null);
        this.camera = camera;
        this.cameraTransform = transform != null ? new Transform(transform) : null;
    }

    public int addLight(Light light, Transform transform) {
        int index = _addLight(handle, light.handle, transform != null ? transform.matrix : null);
        if (index < 0) {
            index = lights.size();
        }
        if (lights.size() < index + 1) {
            lights.setSize(index + 1);
        }
        if (lightTransforms.size() < index + 1) {
            lightTransforms.setSize(index + 1);
        }
        lights.setElementAt(light, index);
        lightTransforms.setElementAt(transform != null ? new Transform(transform) : null, index);
        return index;
    }

    public void setLight(int index, Light light, Transform transform) {
        _setLight(handle, index, light != null ? light.handle : 0, transform != null ? transform.matrix : null);
        lights.setElementAt(light, index);
        lightTransforms.setElementAt(transform != null ? new Transform(transform) : null, index);
    }

    public void resetLights() {
        _resetLights(handle);
        lights.removeAllElements();
        lightTransforms.removeAllElements();
    }

    public static final Hashtable getProperties() {
        Hashtable props = new Hashtable();
        props.put("supportAntialiasing", new java.lang.Boolean(
                _getBackendCapability(CAPABILITY_ANTIALIASING) != 0));
        props.put("supportTrueColor", new java.lang.Boolean(
                _getBackendCapability(CAPABILITY_TRUE_COLOR) != 0));
        props.put("supportDithering", new java.lang.Boolean(
                _getBackendCapability(CAPABILITY_DITHERING) != 0));
        props.put("supportMipmapping", new java.lang.Boolean(
                _getBackendCapability(CAPABILITY_MIPMAPPING) != 0));
        props.put("supportPerspectiveCorrection", new java.lang.Boolean(
                _getBackendCapability(CAPABILITY_PERSPECTIVE_CORRECTION) != 0));
        props.put("supportLocalCameraLighting", new java.lang.Boolean(
                _getBackendCapability(CAPABILITY_LOCAL_CAMERA_LIGHTING) != 0));
        props.put("maxLights", new java.lang.Integer(
                _getBackendCapability(CAPABILITY_MAX_LIGHTS)));
        props.put("maxViewportWidth", new java.lang.Integer(
                _getBackendCapability(CAPABILITY_MAX_VIEWPORT_WIDTH)));
        props.put("maxViewportHeight", new java.lang.Integer(
                _getBackendCapability(CAPABILITY_MAX_VIEWPORT_HEIGHT)));
        props.put("maxViewportDimension", new java.lang.Integer(
                Math.min(_getBackendCapability(CAPABILITY_MAX_VIEWPORT_WIDTH),
                        _getBackendCapability(CAPABILITY_MAX_VIEWPORT_HEIGHT))));
        props.put("maxTextureDimension", new java.lang.Integer(
                _getBackendCapability(CAPABILITY_MAX_TEXTURE_DIMENSION)));
        props.put("maxSpriteCropDimension", new java.lang.Integer(
                _getBackendCapability(CAPABILITY_MAX_TEXTURE_DIMENSION)));
        props.put("numTextureUnits", new java.lang.Integer(
                _getBackendCapability(CAPABILITY_NUM_TEXTURE_UNITS)));
        props.put("maxTransformsPerVertex", new java.lang.Integer(
                _getBackendCapability(CAPABILITY_MAX_TRANSFORMS_PER_VERTEX)));
        props.put("m3gRelease", new java.lang.String("04_wk49"));
        return props;
    }

    public void setDepthRange(float near, float far) {
        _setDepthRange(handle, near, far);
    }

    public Camera getCamera(Transform transform) {
        if (transform != null) {
            if (cameraTransform != null) {
                transform.set(cameraTransform);
            } else {
                transform.setIdentity();
            }
        }
        return camera;
    }

    public float getDepthRangeFar() {
        return _getDepthRangeFar(handle);
    }

    public float getDepthRangeNear() {
        return _getDepthRangeNear(handle);
    }

    public Light getLight(int index, Transform transform) {
        if (index < 0 || index >= lights.size()) {
            throw new IndexOutOfBoundsException();
        }
        if (transform != null) {
            Transform lightTransform = (Transform) lightTransforms.elementAt(index);
            if (lightTransform != null) {
                transform.set(lightTransform);
            } else {
                transform.setIdentity();
            }
        }
        return (Light) lights.elementAt(index);
    }

    public int getLightCount() {
        return lights.size();
    }

    public java.lang.Object getTarget() {
        return currentTarget;
    }

    public int getViewportHeight() {
        return _getViewportHeight(handle);
    }

    public int getViewportWidth() {
        return _getViewportWidth(handle);
    }

    public int getViewportX() {
        return _getViewportX(handle) - offsetX;
    }

    public int getViewportY() {
        return _getViewportY(handle) - offsetY;
    }

    public int getHints() {
        return hints;
    }

    public boolean isDepthBufferEnabled() {
        return depthEnabled;
    }

    // Native methods
    private native static int _ctor(int hInterface);
    private native static void _addRef(int hObject);
    private native static int _addLight(int handle, int hLight, byte[] transform);
    private native static boolean _bindGraphics(int handle, int surfaceHandle, int clipX, int clipY, int clipW, int clipH, boolean depth, int hintBits, boolean aIsProperRenderer);
    private native static void _bindImage(int handle, int imgHandle, boolean depth, int hintBits);
    private native static void _releaseGraphics(int handle, int surfaceHandle, boolean aIsImageTarget, boolean aIsProperRenderer);
    private native static void _releaseImage(int handle);
    private native static void _resetLights(int handle);
    private native static void _clear(int handle, int hBackground);
    private native static void _render(int handle, int hVtxBuffer, int hIdxBuffer, int hAppearance, byte[] transform, int scope);
    private native static void _renderNode(int handle, int hNode, byte[] transform);
    private native static void _renderWorld(int handle, int hWorld);
    private native static void _setCamera(int handle, int hCamera, byte[] transform);
    private native static void _setViewport(int handle, int x, int y, int width, int height);
    private native static void _setLight(int handle, int index, int hLight, byte[] transform);
    private native static void _setDepthRange(int handle, float near, float far);
    private native static void _getViewTransform(int handle, byte[] transform);
    private native static int _getCamera(int handle);
    private native static int _getLightTransform(int handle, int index, byte[] transform);
    private native static int _getLightCount(int handle);
    private native static float _getDepthRangeNear(int handle);
    private native static float _getDepthRangeFar(int handle);
    private native static int _getViewportX(int handle);
    private native static int _getViewportY(int handle);
    private native static int _getViewportWidth(int handle);
    private native static int _getViewportHeight(int handle);
    private native static boolean _isAASupported(int handle);
    private native static boolean _isProperRenderer();
    private static native void _beginTargetBatch();
    private static native void _flushTargetBatch();
    private static native int _getBackendCapability(int capability);

    private void collectSceneLights(Node node, Transform parentTransform) {
        if (!node.isRenderingEnabled()) {
            return;
        }

        Transform nodeTransform = new Transform(parentTransform);
        Transform local = new Transform();
        node.getCompositeTransform(local);
        nodeTransform.postMultiply(local);

        if (node instanceof Light) {
            addLight((Light) node, nodeTransform);
        }

        if (node instanceof Group) {
            Group group = (Group) node;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                collectSceneLights(group.getChild(i), nodeTransform);
            }
        }
    }
}
