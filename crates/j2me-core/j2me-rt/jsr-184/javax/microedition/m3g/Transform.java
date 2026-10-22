package javax.microedition.m3g;

public class Transform {
    byte[] matrix = new byte[76];

    public Transform() {
        setIdentity();
    }

    public Transform(Transform other) {
        set(other);
    }

    public void setIdentity() {
        _setIdentity(matrix);
    }

    public void set(Transform transform) {
        System.arraycopy(transform.matrix, 0, this.matrix, 0, this.matrix.length);
    }

    public void set(float[] matrix) {
        checkMatrix(matrix);
        _setMatrix(this.matrix, matrix);
    }

    public void get(float[] matrix) {
        checkMatrix(matrix);
        _getMatrix(this.matrix, matrix);
    }

    public void invert() {
        _invert(matrix);
    }

    public void transpose() {
        _transpose(matrix);
    }

    public void postMultiply(Transform transform) {
        _mul(this.matrix, this.matrix, transform.matrix);
    }

    public void postScale(float sx, float sy, float sz) {
        _scale(matrix, sx, sy, sz);
    }

    public void postRotate(float angle, float ax, float ay, float az) {
        if (ax == 0.0f && ay == 0.0f && az == 0.0f) {
            throw new IllegalArgumentException();
        }
        _rotate(matrix, angle, ax, ay, az);
    }

    public void postRotateQuat(float qx, float qy, float qz, float qw) {
        if (qx == 0.0f && qy == 0.0f && qz == 0.0f && qw == 0.0f) {
            throw new IllegalArgumentException();
        }
        _rotateQuat(matrix, qx, qy, qz, qw);
    }

    public void postTranslate(float tx, float ty, float tz) {
        _translate(matrix, tx, ty, tz);
    }

    public void transform(float[] v) {
        if ((v.length % 4) != 0) {
            throw new IllegalArgumentException();
        }
        if (v.length != 0) {
            _transformTable(matrix, v);
        }
    }

    public void transform(VertexArray in, float[] out, boolean W) {
        if (in == null || out == null) {
            throw new NullPointerException();
        }
        _transformArray(matrix, in.handle, out, W);
    }

    private static void checkMatrix(float[] matrix) {
        if (matrix == null) {
            throw new NullPointerException();
        }
        if (matrix.length < 16) {
            throw new IllegalArgumentException();
        }
    }

    private static native void _mul(byte[] prod, byte[] left, byte[] right);
    private static native void _setIdentity(byte[] matrix);
    private static native void _setMatrix(byte[] matrix, float[] srcMatrix);
    private static native void _getMatrix(byte[] matrix, float[] dstMatrix);
    private static native void _invert(byte[] matrix);
    private static native void _transpose(byte[] matrix);
    private static native void _rotate(byte[] matrix, float angle, float ax, float ay, float az);
    private static native void _rotateQuat(byte[] matrix, float qx, float qy, float qz, float qw);
    private static native void _scale(byte[] matrix, float sx, float sy, float sz);
    private static native void _translate(byte[] matrix, float tx, float ty, float tz);
    private static native void _transformTable(byte[] matrix, float[] v);
    private static native void _transformArray(byte[] matrix, int handle, float[] out, boolean W);
}
