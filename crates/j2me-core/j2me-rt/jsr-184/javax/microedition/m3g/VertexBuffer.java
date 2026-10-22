package javax.microedition.m3g;

public class VertexBuffer extends Object3D {
    private VertexArray positions;
    private VertexArray normals;
    private VertexArray colors;
    private VertexArray[] texCoords;

    public VertexBuffer() {
        super(_ctor(Interface.getHandle()));
    }

    VertexBuffer(int handle) {
        super(handle);
    }

    public int getVertexCount() {
        return _getVertexCount(handle);
    }

    public void setPositions(VertexArray positions, float scale, float[] bias) {
        _setVertices(handle, (positions != null) ? positions.handle : 0, scale, bias);
        this.positions = positions;
    }

    public void setTexCoords(int index, VertexArray texCoords, float scale, float[] bias) {
        _setTexCoords(handle, index, texCoords != null ? texCoords.handle : 0, scale, bias);
        if (this.texCoords == null) {
            this.texCoords = new VertexArray[Defs.NUM_TEXTURE_UNITS];
        }
        this.texCoords[index] = texCoords;
    }

    public void setNormals(VertexArray normals) {
        _setNormals(handle, normals != null ? normals.handle : 0);
        this.normals = normals;
    }

    public void setColors(VertexArray colors) {
        _setColors(handle, colors != null ? colors.handle : 0);
        this.colors = colors;
    }

    public VertexArray getPositions(float[] scaleBias) {
        _getArray(handle, Defs.GET_POSITIONS, scaleBias);
        return positions;
    }

    public VertexArray getTexCoords(int index, float[] scaleBias) {
        if (index < 0 || index >= Defs.NUM_TEXTURE_UNITS) {
            throw new IndexOutOfBoundsException();
        }
        _getArray(handle, Defs.GET_TEXCOORDS0 + index, scaleBias);
        return texCoords != null ? texCoords[index] : null;
    }

    public VertexArray getNormals() {
        return normals;
    }

    public VertexArray getColors() {
        return colors;
    }

    public void setDefaultColor(int ARGB) {
        _setDefaultColor(handle, ARGB);
    }

    public int getDefaultColor() {
        return _getDefaultColor(handle);
    }

    private static native int _ctor(int hInterface);
    private static native void _setColors(int hBuffer, int hArray);
    private static native void _setNormals(int hBuffer, int hArray);
    private static native void _setTexCoords(int hBuffer, int unit, int hArray, float scale, float[] bias);
    private static native void _setVertices(int hBuffer, int hArray, float scale, float[] bias);
    private static native void _setDefaultColor(int hBuffer, int ARGB);
    private static native int _getDefaultColor(int hBuffer);
    private static native int _getArray(int hBuffer, int which, float[] scaleBias);
    private static native int _getVertexCount(int hBuffer);
}
