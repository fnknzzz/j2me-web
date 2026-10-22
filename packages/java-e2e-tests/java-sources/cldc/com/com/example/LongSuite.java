package com.com.example;

public class LongSuite {
  public static void main(String[] args) {
    System.out.println(Long.parseLong("123456789"));
    System.out.println(Long.parseLong("-123456789"));
    System.out.println(Long.parseLong("9223372036854775807"));
    System.out.println(Long.parseLong("-9223372036854775808"));

    System.out.println(Long.parseLong("FF", 16));
    System.out.println(Long.parseLong("777", 8));
    System.out.println(Long.parseLong("1010", 2));

    System.out.println(Long.toString(123456789L));
    System.out.println(Long.toString(-123456789L));
    System.out.println(Long.toString(Long.MAX_VALUE));
    System.out.println(Long.toString(Long.MIN_VALUE));

    System.out.println(Long.toString(255L, 16));
    System.out.println(Long.toString(255L, 8));
    System.out.println(Long.toString(255L, 2));
    System.out.println(Long.toString(-1L, 16));

    Long l1 = new Long(100L);
    Long l2 = new Long(-100L);
    Long l3 = new Long(100L);

    System.out.println(l1.longValue());
    System.out.println(l2.longValue());
    System.out.println(l1.floatValue());
    System.out.println(l1.doubleValue());

    System.out.println(l1.toString());
    System.out.println(l2.toString());

    System.out.println(l1.hashCode());
    System.out.println(l2.hashCode());

    System.out.println(l1.equals(l3));
    System.out.println(l1.equals(l2));
    System.out.println(l1.equals(new Long(100L)));

    System.out.println(Long.MIN_VALUE);
    System.out.println(Long.MAX_VALUE);

    try {
      System.out.println(Long.parseLong("9223372036854775808"));
      System.out.println("OK_OVERFLOW");
    } catch (NumberFormatException e) {
      System.out.println("NFE_OVERFLOW");
    }

    try {
      System.out.println(Long.parseLong("abc"));
      System.out.println("OK_INVALID");
    } catch (NumberFormatException e) {
      System.out.println("NFE_INVALID");
    }

    try {
      System.out.println(Long.parseLong(""));
      System.out.println("OK_EMPTY");
    } catch (NumberFormatException e) {
      System.out.println("NFE_EMPTY");
    }
  }
}
