package com.com.example;

public class ShortSuite {
  public static void main(String[] args) {
    System.out.println(Short.parseShort("32767"));
    System.out.println(Short.parseShort("-32768"));
    System.out.println(Short.parseShort("100"));
    System.out.println(Short.parseShort("7F", 16));
    System.out.println(Short.parseShort("77", 8));

    try {
      System.out.println(Short.parseShort("32768"));
      System.out.println("OK");
    } catch (NumberFormatException e) {
      System.out.println("NFE_OVERFLOW");
    }

    try {
      System.out.println(Short.parseShort("-32769"));
      System.out.println("OK");
    } catch (NumberFormatException e) {
      System.out.println("NFE_UNDERFLOW");
    }

    try {
      System.out.println(Short.parseShort("abc"));
      System.out.println("OK");
    } catch (NumberFormatException e) {
      System.out.println("NFE_INVALID");
    }

    Short s1 = new Short((short) 100);
    Short s2 = new Short((short) -100);
    Short s3 = new Short((short) 100);

    System.out.println(s1.shortValue());
    System.out.println(s2.shortValue());

    System.out.println(s1.toString());
    System.out.println(s2.toString());

    System.out.println(s1.hashCode());
    System.out.println(s2.hashCode());

    System.out.println(s1.equals(s3));
    System.out.println(s1.equals(s2));
    System.out.println(s1.equals(new Short((short) 100)));

    System.out.println(Short.MIN_VALUE);
    System.out.println(Short.MAX_VALUE);
  }
}
