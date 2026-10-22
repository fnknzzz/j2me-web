package javax.microedition.m3g;

public class Appearance extends Object3D {
    private CompositingMode compositingMode;
    private Fog fog;
    private Material material;
    private PolygonMode polygonMode;
    private Texture2D[] textures;

    public Appearance() {
        super(_ctor(Interface.getHandle()));
    }

    Appearance(int handle) {
        super(handle);
    }

    public void setCompositingMode(CompositingMode compositingMode) {
        _setCompositingMode(handle, compositingMode != null ? compositingMode.handle : 0);
        this.compositingMode = compositingMode;
    }

    public CompositingMode getCompositingMode() {
        return (CompositingMode) getInstance(_getCompositingMode(handle));
    }

    public void setFog(Fog fog) {
        _setFog(handle, fog != null ? fog.handle : 0);
        this.fog = fog;
    }

    public Fog getFog() {
        return (Fog) getInstance(_getFog(handle));
    }

    public void setPolygonMode(PolygonMode polygonMode) {
        _setPolygonMode(handle, polygonMode != null ? polygonMode.handle : 0);
        this.polygonMode = polygonMode;
    }

    public PolygonMode getPolygonMode() {
        return (PolygonMode) getInstance(_getPolygonMode(handle));
    }

    public void setLayer(int index) {
        _setLayer(handle, index);
    }

    public int getLayer() {
        return _getLayer(handle);
    }

    public void setMaterial(Material material) {
        _setMaterial(handle, material != null ? material.handle : 0);
        this.material = material;
    }

    public Material getMaterial() {
        return (Material) getInstance(_getMaterial(handle));
    }

    public void setTexture(int unit, Texture2D texture) {
        _setTexture(handle, unit, texture != null ? texture.handle : 0);
        if (textures == null) {
            textures = new Texture2D[Defs.NUM_TEXTURE_UNITS];
        }
        textures[unit] = texture;
    }

    public Texture2D getTexture(int unit) {
        return (Texture2D) getInstance(_getTexture(handle, unit));
    }

    private static native int _ctor(int hInterface);
    private static native int _getCompositingMode(int hApp);
    private static native int _getFog(int hApp);
    private static native int _getLayer(int hApp);
    private static native int _getMaterial(int hApp);
    private static native int _getPolygonMode(int hApp);
    private static native int _getTexture(int hApp, int unit);
    private static native void _setCompositingMode(int hApp, int hMode);
    private static native void _setFog(int hApp, int hFog);
    private static native void _setLayer(int hApp, int layer);
    private static native void _setMaterial(int hApp, int hMaterial);
    private static native void _setPolygonMode(int hApp, int hMode);
    private static native void _setTexture(int hApp, int unit, int hTexture);
}
