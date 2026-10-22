package javax.microedition.m3g;

public abstract class Object3D {
    int handle;
    private Object userObject;

    Object3D(int handle) {
        this.handle = handle;
        if (handle != 0) {
            _addRef(handle);
            Interface.register(this);
        }
    }

    public void setUserObject(Object obj) {
        this.userObject = obj;
    }

    public Object getUserObject() {
        return userObject;
    }

    public int getUserID() {
        return _getUserID(handle);
    }

    public void setUserID(int userID) {
        _setUserID(handle, userID);
    }

    public void addAnimationTrack(AnimationTrack animationTrack) {
        _addAnimationTrack(handle, animationTrack.handle);
    }

    public void removeAnimationTrack(AnimationTrack animationTrack) {
        if (animationTrack != null) {
            _removeAnimationTrack(handle, animationTrack.handle);
        }
    }

    public int getAnimationTrackCount() {
        return _getAnimationTrackCount(handle);
    }

    public AnimationTrack getAnimationTrack(int index) {
        int h = _getAnimationTrack(handle, index);
        return h != 0 ? new AnimationTrack(h) : null;
    }

    public int animate(int time) {
        return _animate(handle, time);
    }

    public Object3D duplicate() {
        int h = _duplicate(handle, null);
        return h != 0 ? Interface.getObjectInstance(h) : null;
    }

    public Object3D find(int userID) {
        int h = _find(handle, userID);
        return h != 0 ? Interface.getObjectInstance(h) : null;
    }

    public int getReferences(Object3D[] refs) {
        int count = _getReferences(handle, null);
        if (refs != null) {
            if (refs.length < count) {
                throw new IllegalArgumentException();
            }
            int[] handles = new int[count];
            _getReferences(handle, handles);
            for (int i = 0; i < count; i++) {
                refs[i] = Interface.getObjectInstance(handles[i]);
            }
        }
        return count;
    }

    static Object3D getInstance(int handle) {
        return Interface.getObjectInstance(handle);
    }

    private native static void _addRef(int handle);
    private native static int _animate(int handle, int time);
    private native static void _setUserID(int handle, int userID);
    private native static int _getUserID(int handle);
    private native static int _addAnimationTrack(int handle, int animTrack);
    private native static void _removeAnimationTrack(int handle, int animTrack);
    private native static int _getAnimationTrackCount(int handle);
    private native static int _getAnimationTrack(int handle, int index);
    private native static int _duplicate(int handle, int[] refs);
    private native static int _getReferences(int handle, int[] refs);
    private native static int _find(int handle, int userID);
}
