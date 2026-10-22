package com.com.example;

public class InstructionConversionSuite {
    public static void main(String[] args) {
        testL2I();
        testF2I();
        testD2I();
    }

    private static void testL2I() {
        System.out.println("=== testL2I ===");
        
        long a = 12345L;
        System.out.println("l2i(12345L) = " + (int)a);

        long b = 2147483648L; // Integer.MAX_VALUE + 1
        // 2147483648 = 0x80000000 -> (int) = -2147483648
        System.out.println("l2i(2147483648L) = " + (int)b);

        long c = -2147483649L; // Integer.MIN_VALUE - 1
        // -2147483649 = 0xFFFFFFFF7FFFFFFF -> (int) = 0x7FFFFFFF = 2147483647
        System.out.println("l2i(-2147483649L) = " + (int)c);
        
        long d = 0x123456789ABCDEF0L;
        // 0x9ABCDEF0 -> -1698898192
        System.out.println("l2i(0x123456789ABCDEF0L) = " + Integer.toHexString((int)d).toUpperCase());
    }

    private static void testF2I() {
        System.out.println("=== testF2I ===");
        
        float a = 123.45f;
        System.out.println("f2i(123.45f) = " + (int)a);

        float b = -123.45f;
        System.out.println("f2i(-123.45f) = " + (int)b);

        float c = Float.NaN;
        System.out.println("f2i(NaN) = " + (int)c);

        float d = Float.POSITIVE_INFINITY;
        System.out.println("f2i(Infinity) = " + (int)d);

        float e = Float.NEGATIVE_INFINITY;
        System.out.println("f2i(-Infinity) = " + (int)e);

        float f = 3e9f; // > MAX_VALUE
        System.out.println("f2i(3e9f) = " + (int)f);

        float g = -3e9f; // < MIN_VALUE
        System.out.println("f2i(-3e9f) = " + (int)g);
    }

    private static void testD2I() {
        System.out.println("=== testD2I ===");
        
        double a = 123.45;
        System.out.println("d2i(123.45) = " + (int)a);

        double b = -123.45;
        System.out.println("d2i(-123.45) = " + (int)b);

        double c = Double.NaN;
        System.out.println("d2i(NaN) = " + (int)c);

        double d = Double.POSITIVE_INFINITY;
        System.out.println("d2i(Infinity) = " + (int)d);

        double e = Double.NEGATIVE_INFINITY;
        System.out.println("d2i(-Infinity) = " + (int)e);

        double f = 1e20; // > MAX_VALUE
        System.out.println("d2i(1e20) = " + (int)f);

        double g = -1e20; // < MIN_VALUE
        System.out.println("d2i(-1e20) = " + (int)g);
    }
}
