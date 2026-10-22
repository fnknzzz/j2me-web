package com.com.example;

public class ByteSuite {
  public static void main(String[] args) {
    System.out.println(Byte.parseByte("127"));
    System.out.println(Byte.parseByte("-128"));
    System.out.println(Byte.parseByte("100"));
    System.out.println(Byte.parseByte("-50"));

    System.out.println(Byte.parseByte("7F", 16));
    System.out.println(Byte.parseByte("77", 8));
    System.out.println(Byte.parseByte("1111111", 2));

    Byte b1 = new Byte((byte) 100);
    Byte b2 = new Byte((byte) -100);
    Byte b3 = new Byte((byte) 100);

    System.out.println(b1.byteValue());
    System.out.println(b2.byteValue());

    System.out.println(b1.toString());
    System.out.println(b2.toString());

    System.out.println(b1.hashCode());
    System.out.println(b2.hashCode());

    System.out.println(b1.equals(b3));
    System.out.println(b1.equals(b2));
    System.out.println(b1.equals(new Byte((byte) 100)));

    System.out.println(Byte.MIN_VALUE);
    System.out.println(Byte.MAX_VALUE);

    try {
      System.out.println(Byte.parseByte("128"));
      System.out.println("OK_OVERFLOW");
    } catch (NumberFormatException e) {
      System.out.println("NFE_OVERFLOW");
    }

    try {
      System.out.println(Byte.parseByte("-129"));
      System.out.println("OK_UNDERFLOW");
    } catch (NumberFormatException e) {
      System.out.println("NFE_UNDERFLOW");
    }

    try {
      System.out.println(Byte.parseByte("abc"));
      System.out.println("OK_INVALID");
    } catch (NumberFormatException e) {
      System.out.println("NFE_INVALID");
    }
  }
}
