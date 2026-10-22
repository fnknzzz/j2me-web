package com.nokia.mj.impl.rt.support;

public class Finalizer {
    public interface Callback {
        void invoke();
    }
    public Finalizer() {}
    public void invoke() {}
    public static void registerForFinalization(Object obj) {}
}
