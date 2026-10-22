package javax.microedition.m3g;

import java.io.*;
import java.util.Vector;
import java.util.Hashtable;
import javax.microedition.lcdui.Image;

public class Loader {
    int handle;

    private Loader() {
    }

    public static Object3D[] load(String name) throws IOException {
        if (name == null) {
            throw new NullPointerException();
        }
        int[] handles = _loadResource(Interface.getHandle(), name);
        if (handles == null) {
            throw new IOException("resource not found");
        }
        return wrap(handles);
    }

    public static Object3D[] load(byte[] data, int offset) throws IOException {
        if (data == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || offset > data.length) {
            throw new IndexOutOfBoundsException();
        }
        int[] handles = _load(Interface.getHandle(), data, offset);
        return wrap(handles);
    }

    private static Object3D[] wrap(int[] handles) throws IOException {
        Object3D[] objects = new Object3D[handles.length];
        for (int i = 0; i < handles.length; i++) {
            objects[i] = Interface.getObjectInstance(handles[i]);
            if (objects[i] == null) {
                throw new IOException("unknown class");
            }
        }
        return objects;
    }

    private native static int[] _load(int hInterface, byte[] data, int offset) throws IOException;
    private native static int[] _loadResource(int hInterface, String name) throws IOException;
}
