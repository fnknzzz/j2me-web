package com.com.example;

public class NullInvokeSuite {
    static class Foo {
        void bar() { /* no-op */ }
    }

    public static void main(String[] args) {
        try {
            Foo f = null;
            // invokevirtual on null → 必须抛出 NullPointerException
            f.bar();
            System.out.println("NO_EXCEPTION");
        } catch (NullPointerException e) {
            System.out.println("NPE");
        }
    }
}

