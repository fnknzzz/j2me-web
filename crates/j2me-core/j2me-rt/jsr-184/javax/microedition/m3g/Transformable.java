package javax.microedition.m3g;

public abstract class Transformable extends Object3D {
    Transformable(int handle) {
        super(handle);
    }

    public void setOrientation(float angle, float ax, float ay, float az) {
        _setOrientation(handle, angle, ax, ay, az, true);
    }

    public void postRotate(float angle, float ax, float ay, float az) {
        _setOrientation(handle, angle, ax, ay, az, false);
    }

    public void preRotate(float angle, float ax, float ay, float az) {
        _preRotate(handle, angle, ax, ay, az);
    }

    public void getOrientation(float[] angleAxis) {
        _getOrientation(handle, angleAxis);
    }

    public void setScale(float sx, float sy, float sz) {
        _setScale(handle, sx, sy, sz, true);
    }

    public void scale(float sx, float sy, float sz) {
        _setScale(handle, sx, sy, sz, false);
    }

    public void getScale(float[] xyz) {
        _getScale(handle, xyz);
    }

    public void setTranslation(float tx, float ty, float tz) {
        _setTranslation(handle, tx, ty, tz, true);
    }

    public void translate(float tx, float ty, float tz) {
        _setTranslation(handle, tx, ty, tz, false);
    }

    public void getTranslation(float[] xyz) {
        _getTranslation(handle, xyz);
    }

    public void setTransform(Transform transform) {
        _setTransform(handle, (transform != null) ? transform.matrix : null);
    }

    public void getTransform(Transform transform) {
        _getTransform(handle, transform.matrix);
    }

    public void getCompositeTransform(Transform transform) {
        _getComposite(handle, transform.matrix);
    }

    private static native void _setOrientation(int handle, float angle, float ax, float ay, float az, boolean absolute);
    private static native void _preRotate(int handle, float angle, float ax, float ay, float az);
    private static native void _getOrientation(int handle, float[] angleAxis);
    private static native void _setScale(int handle, float sx, float sy, float sz, boolean absolute);
    private static native void _getScale(int handle, float[] scale);
    private static native void _setTranslation(int handle, float tx, float ty, float tz, boolean absolute);
    private static native void _getTranslation(int handle, float[] translation);
    private static native void _setTransform(int handle, byte[] transform);
    private static native void _getTransform(int handle, byte[] transform);
    private static native void _getComposite(int handle, byte[] transform);
}
