package com.com.example;

public class IntegerSuite {
  public static void main(String[] args) {
    System.out.println(Integer.parseInt("FF", 16));
    System.out.println(Integer.parseInt("255"));
    System.out.println(Integer.parseInt("-100"));
    System.out.println(Integer.parseInt("77", 8));

    System.out.println(Integer.toString(255, 16));
    System.out.println(Integer.toString(255));
    System.out.println(Integer.toString(-100));

    System.out.println(Integer.toHexString(255));
    System.out.println(Integer.toHexString(-1));
    System.out.println(Integer.toOctalString(255));
    System.out.println(Integer.toBinaryString(5));
    System.out.println(Integer.toBinaryString(-1));

    System.out.println(Integer.valueOf("ff", 16).intValue());
    System.out.println(Integer.valueOf("123").intValue());

    Integer a = new Integer(5);
    Integer b = new Integer(7);
    Integer c = new Integer(-100);

    System.out.println(a.intValue());
    System.out.println(b.longValue());
    System.out.println(c.shortValue());
    System.out.println(c.byteValue());
    System.out.println(new Integer(100).floatValue());
    System.out.println(new Integer(100).doubleValue());

    System.out.println(a.toString());
    System.out.println(c.toString());

    System.out.println(a.hashCode());
    System.out.println(c.hashCode());

    System.out.println(a.equals(new Integer(5)));
    System.out.println(a.equals(b));
    System.out.println(a.equals(c));

    System.out.println(Integer.MIN_VALUE);
    System.out.println(Integer.MAX_VALUE);

    try {
      System.out.println(Integer.parseInt("2147483648"));
      System.out.println("OK_PARSE");
    } catch (NumberFormatException e) {
      System.out.println("NFE_PARSE");
    }

    try {
      System.out.println(Integer.parseInt("G", 16));
      System.out.println("OK_PARSE_RADIX");
    } catch (NumberFormatException e) {
      System.out.println("NFE_PARSE_RADIX");
    }

    try {
      System.out.println(Integer.valueOf("abc").intValue());
      System.out.println("OK_VOF");
    } catch (NumberFormatException e) {
      System.out.println("NFE_VOF");
    }
  }
}
