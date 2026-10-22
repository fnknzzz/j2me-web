package com.com.example;

/**
 * 验证 Class.forName 能正确加载非 public 的类（包括内部类）。
 *
 * 覆盖点：
 * - 对 private static inner class 调用 Class.forName
 * - 对 package-private class 调用 Class.forName
 * - 通过 .class 字面量访问非 public inner class（编译器生成的 class$ 方法）
 * - 不存在的类仍应抛出 ClassNotFoundException
 */
public class ClassForNameNonPublicSuite {

    private static class PrivateInner {
        static String hello() { return "from PrivateInner"; }
    }

    static class PackageInner {
        static String hello() { return "from PackageInner"; }
    }

    public static void main(String[] args) {
        testPrivateInnerClassForName();
        testPackageInnerClassForName();
        testDotClassLiteral();
        testNonExistentStillFails();
    }

    private static void testPrivateInnerClassForName() {
        System.out.println("=== privateInnerForName ===");
        try {
            Class c = Class.forName("com.com.example.ClassForNameNonPublicSuite$PrivateInner");
            System.out.println("loaded = true");
            System.out.println("name = " + c.getName());
        } catch (ClassNotFoundException e) {
            System.out.println("loaded = false");
            System.out.println("error = " + e.getMessage());
        }
    }

    private static void testPackageInnerClassForName() {
        System.out.println("=== packageInnerForName ===");
        try {
            Class c = Class.forName("com.com.example.ClassForNameNonPublicSuite$PackageInner");
            System.out.println("loaded = true");
            System.out.println("name = " + c.getName());
        } catch (ClassNotFoundException e) {
            System.out.println("loaded = false");
            System.out.println("error = " + e.getMessage());
        }
    }

    private static void testDotClassLiteral() {
        System.out.println("=== dotClassLiteral ===");
        Class c = PrivateInner.class;
        System.out.println("literal = true");
        System.out.println("name = " + c.getName());
    }

    private static void testNonExistentStillFails() {
        System.out.println("=== nonExistentStillFails ===");
        try {
            Class.forName("com.com.example.ClassForNameNonPublicSuite$DoesNotExist");
            System.out.println("loaded = true");
        } catch (ClassNotFoundException e) {
            System.out.println("loaded = false");
        }
    }
}
