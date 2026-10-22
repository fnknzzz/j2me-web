package com.com.example;

public class FieldNarrowSuite {
  static byte b;
  static short s;
  static char c;

  public static void main(String[] args) {
    b = (byte) -3;
    s = (short) -3;
    c = (char) 3;
    System.out.println(b % 2);
    System.out.println(s % 2);
    System.out.println(c % 2);

    b = (byte) 128;
    s = (short) 32768;
    c = (char) -1;
    System.out.println(b);
    System.out.println(s);
    System.out.println(c % 2);
  }
}
