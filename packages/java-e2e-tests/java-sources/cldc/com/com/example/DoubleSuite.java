package com.com.example;

public class DoubleSuite {
  public static void main(String[] args) {
    Double d0 = new Double(2.5);
    System.out.println("d0-d-" + (int) d0.doubleValue());
    System.out.println("d0-f-" + (int) d0.floatValue());
    System.out.println("d0-i-" + d0.intValue());
    System.out.println("d0-l-" + (long) d0.longValue());
    System.out.println("d0-s-" + (int) d0.shortValue());
    System.out.println("d0-b-" + (int) d0.byteValue());
    System.out.println("d0-eq-" + d0.equals(new Double(2.5)));
    System.out.println("d0-nan-" + new Double(Double.NaN).isNaN());
    System.out.println("d0-inf-" + new Double(Double.POSITIVE_INFINITY).isInfinite());

    System.out.println("D-nan-" + Double.isNaN(0.0));
    System.out.println("D-inf-" + Double.isInfinite(Double.POSITIVE_INFINITY));
    System.out.println("D-ts-" + Double.toString(2.0));
    long bits = Double.doubleToLongBits(2.0);
    System.out.println("D-d2l-" + bits);
    double back = Double.longBitsToDouble(bits);
    System.out.println("D-l2d-" + (int) back);

    Double vd = Double.valueOf("3.5");
    System.out.println("D-vo-" + (int) vd.doubleValue());
    double pd = Double.parseDouble("4.5");
    System.out.println("D-pd-" + (int) pd);
  }
}
