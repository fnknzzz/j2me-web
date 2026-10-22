package com.com.example;

public class FloatSuite {
  public static void main(String[] args) {
    Float f0 = new Float(1.5f);
    System.out.println("f0-f-" + (int) f0.floatValue());
    System.out.println("f0-d-" + (int) f0.doubleValue());
    System.out.println("f0-i-" + f0.intValue());
    System.out.println("f0-l-" + (long) f0.longValue());
    System.out.println("f0-s-" + (int) f0.shortValue());
    System.out.println("f0-b-" + (int) f0.byteValue());
    System.out.println("f0-eq-" + f0.equals(new Float(1.5f)));
    System.out.println("f0-nan-" + new Float(Float.NaN).isNaN());
    System.out.println("f0-inf-" + new Float(Float.POSITIVE_INFINITY).isInfinite());

    System.out.println("F-nan-" + Float.isNaN(0f));
    System.out.println("F-inf-" + Float.isInfinite(Float.POSITIVE_INFINITY));
    System.out.println("F-ts-" + Float.toString(2.0f));
    int bits = Float.floatToIntBits(2.0f);
    System.out.println("F-f2i-" + bits);
    float back = Float.intBitsToFloat(bits);
    System.out.println("F-i2f-" + (int) back);

    Float vf = Float.valueOf("2.5");
    System.out.println("F-vo-" + (int) vf.floatValue());
    float pf = Float.parseFloat("3.5");
    System.out.println("F-pf-" + (int) pf);
  }
}
