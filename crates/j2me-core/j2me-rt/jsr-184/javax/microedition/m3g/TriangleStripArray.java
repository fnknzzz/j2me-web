package javax.microedition.m3g;

public class TriangleStripArray extends IndexBuffer {
    public TriangleStripArray(int firstIndex, int[] stripLengths) {
        super(createImplicit(firstIndex, stripLengths));
    }

    public TriangleStripArray(int[] indices, int[] stripLengths) {
        super(createExplicit(indices, stripLengths));
    }

    TriangleStripArray(int handle) {
        super(handle);
    }

    public int getIndexCount() {
        return _getIndexCount(handle);
    }

    public void getIndices(int[] indices) {
        if (indices == null) {
            throw new NullPointerException();
        }
        if (indices.length < _getIndexCount(handle)) {
            throw new IllegalArgumentException();
        }
        _getIndices(handle, indices);
    }

    private static int createImplicit(int firstIndex, int[] stripLengths) {
        if (stripLengths == null) {
            throw new NullPointerException();
        }
        return _createImplicit(Interface.getHandle(), firstIndex, stripLengths);
    }

    private static int createExplicit(int[] indices, int[] stripLengths) {
        if (indices == null || stripLengths == null) {
            throw new NullPointerException();
        }
        return _createExplicit(Interface.getHandle(), indices, stripLengths);
    }

    private native static int _createImplicit(int hInterface, int first, int[] lengths);
    private native static int _createExplicit(int hInterface, int[] indices, int[] lengths);
    private native static int _getIndexCount(int handle);
    private native static void _getIndices(int handle, int[] indices);
}
