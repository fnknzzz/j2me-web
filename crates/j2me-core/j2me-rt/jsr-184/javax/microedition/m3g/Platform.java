package javax.microedition.m3g;

class Platform {
    static void registerFinalizer(Object obj) {
    }

    static void finalizeObject(int handle, Interface iInterface) {
        _finalizeObject(handle);
    }

    static void executeInUIThread(Runnable r) {
        r.run();
    }

    static void heuristicGC() {
    }

    static boolean uiThreadAvailable() {
        return true;
    }

    static native void finalizeInterface(int handle);
    static native void _finalizeObject(int handle);
}
