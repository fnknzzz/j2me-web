package javax.microedition.m3g;

public class VertexArray extends Object3D {
    public VertexArray(int numVertices, int numComponents, int componentSize) {
        super(_ctor(Interface.getHandle(), numVertices, numComponents, validateComponentSize(componentSize)));
    }

    VertexArray(int handle) {
        super(handle);
    }

    public void set(int startIndex, int length, short[] values) {
        if (values == null) {
            throw new NullPointerException();
        }
        _setShort(handle, startIndex, length, values);
    }

    public void set(int startIndex, int length, byte[] values) {
        if (values == null) {
            throw new NullPointerException();
        }
        _setByte(handle, startIndex, length, values);
    }

    public void get(int firstVertex, int numVertices, byte[] values) {
        _getByte(handle, firstVertex, numVertices, values);
    }

    public void get(int firstVertex, int numVertices, short[] values) {
        _getShort(handle, firstVertex, numVertices, values);
    }

    public int getComponentCount() {
        return _getComponentCount(handle);
    }

    public int getComponentType() {
        return _getComponentType(handle);
    }

    public int getVertexCount() {
        return _getVertexCount(handle);
    }

    private static int validateComponentSize(int componentSize) {
        if (componentSize != 1 && componentSize != 2) {
            throw new IllegalArgumentException();
        }
        return componentSize;
    }

    private native static int _ctor(int hInterface, int numVertices, int numComponents, int componentSize);
    private native static void _setByte(int handle, int first, int count, byte[] src);
    private native static void _setShort(int handle, int first, int count, short[] src);
    private native static void _getByte(int handle, int firstVertex, int numVertices, byte[] values);
    private native static void _getShort(int handle, int firstVertex, int numVertices, short[] values);
    private native static int _getComponentCount(int handle);
    private native static int _getComponentType(int handle);
    private native static int _getVertexCount(int handle);
}
